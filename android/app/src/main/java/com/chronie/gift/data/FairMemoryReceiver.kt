package com.chronie.gift.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import coil3.SingletonImageLoader

/**
 * Fair run-time memory mechanism adapter.
 *
 * Listens for TRIM (memory warning) and KILL (exception kill) broadcasts sent by the system:
 * - TRIM: release memory in time (clear Coil memory cache, etc.).
 * - KILL: save on-site data and then reply to the system. All persistent state of this app
 *   (current tab, theme, language) is already written to SharedPreferences in real time by
 *   TabManager / ThemeManager / LanguageManager, and is automatically restored by
 *   MainActivity / GiftApp on the next launch. Therefore KILL only needs to clear the
 *   in-memory cache.
 *
 * After receiving a broadcast, the result must be sent back to the system through the callback
 * IBinder within 3 seconds, otherwise the system will time out and kill the process.
 */
class FairMemoryReceiver private constructor() : IBinder.DeathRecipient {

    companion object {
        private const val TAG = "FairMemoryReceiver"

        const val ACTION_TRIM = "itgsa.intent.action.TRIM"
        const val ACTION_KILL = "itgsa.intent.action.KILL"

        private const val BUNDLE_KEY_COMMON = "common"
        private const val BUNDLE_KEY_EXTRA = "extra"
        private const val BUNDLE_KEY_NOTIFY_TYPE = "notifyType"
        private const val BUNDLE_KEY_NOTIFY_ID = "notifyId"
        private const val BUNDLE_KEY_REASON = "reason"
        private const val BUNDLE_KEY_ACTION = "action"
        private const val BUNDLE_KEY_CALLBACK = "callback"

        private const val BUNDLE_KEY_HEAP_ALLOC = "heapAlloc"
        private const val BUNDLE_KEY_HEAP_CAPACITY = "heapCapacity"
        private const val BUNDLE_KEY_PSS = "pss"
        private const val BUNDLE_KEY_PSS_LIMIT = "pssLimit"

        const val NOTIFY_TYPE_PSS = 1000
        const val NOTIFY_TYPE_JAVA_HEAP = 2000

        const val RESULT_SUCCESS = 0
        const val RESULT_FAILURE = 1

        const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION

        @Volatile
        private var INSTANCE: FairMemoryReceiver? = null

        fun getInstance(): FairMemoryReceiver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FairMemoryReceiver().also { INSTANCE = it }
            }
        }

        /** Convert the numeric notifyType to a human-readable name for logging. */
        private fun notifyTypeName(notifyType: Int): String = when (notifyType) {
            NOTIFY_TYPE_PSS -> "PSS"
            NOTIFY_TYPE_JAVA_HEAP -> "JavaHeap"
            else -> "Unknown($notifyType)"
        }
    }

    private var mRemote: IBinder? = null
    @Volatile
    private var mInitialized = false
    private var mHandler: Handler? = null
    private var mContext: Context? = null

    override fun binderDied() {
        synchronized(this) {
            mRemote?.let {
                try {
                    it.unlinkToDeath(this, 0)
                } catch (_: Exception) {
                    // ignore
                }
            }
            mRemote = null
        }
    }

    /**
     * Register the TRIM / KILL broadcast receiver. Calling this multiple times is idempotent.
     */
    fun initialize(context: Context) {
        synchronized(this) {
            if (mInitialized) return
            val ht = HandlerThread(TAG)
            ht.start()
            mHandler = Handler(ht.looper)
            mContext = context.applicationContext

            val filter = IntentFilter().apply {
                addAction(ACTION_TRIM)
                addAction(ACTION_KILL)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(mReceiver, filter, null, mHandler)
            }

            mInitialized = true
            Log.i(TAG, "FairMemoryReceiver initialized")
        }
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != ACTION_TRIM && action != ACTION_KILL) return

            val data = intent.extras ?: return
            val commonBundle = data.getBundle(BUNDLE_KEY_COMMON) ?: return

            val notifyType = commonBundle.getInt(BUNDLE_KEY_NOTIFY_TYPE)
            val notifyId = commonBundle.getInt(BUNDLE_KEY_NOTIFY_ID)
            val reason = commonBundle.getString(BUNDLE_KEY_REASON)
            val actionStr = commonBundle.getString(BUNDLE_KEY_ACTION)
            val callbackBinder = commonBundle.getBinder(BUNDLE_KEY_CALLBACK)

            val extraData = data.getBundle(BUNDLE_KEY_EXTRA)
            val heapAlloc = extraData?.getInt(BUNDLE_KEY_HEAP_ALLOC) ?: 0
            val heapCapacity = extraData?.getInt(BUNDLE_KEY_HEAP_CAPACITY) ?: 0
            val pss = extraData?.getInt(BUNDLE_KEY_PSS) ?: 0
            val pssLimit = extraData?.getInt(BUNDLE_KEY_PSS_LIMIT) ?: 0

            Log.i(
                TAG,
                "Received $action, type=${notifyTypeName(notifyType)}, notifyId=$notifyId, " +
                        "reason=$reason, action=$actionStr, " +
                        "heapAlloc=${heapAlloc}KB, heapCapacity=${heapCapacity}KB, " +
                        "pss=${pss}KB, pssLimit=${pssLimit}KB"
            )

            if (callbackBinder != null) {
                handleReceived(action, notifyType, notifyId, callbackBinder)
            } else {
                Log.w(TAG, "Callback binder not found in intent extras.")
            }
        }
    }

    private fun handleReceived(
        action: String?,
        notifyType: Int,
        notifyId: Int,
        callback: IBinder
    ) {
        if (!checkRemote(callback)) {
            Log.w(TAG, "Failed to bind to remote callback")
            return
        }

        val result = try {
            when (action) {
                ACTION_TRIM -> {
                    handleTrim(notifyType)
                    RESULT_SUCCESS
                }
                ACTION_KILL -> {
                    handleKill(notifyType)
                    RESULT_SUCCESS
                }
                else -> {
                    Log.w(TAG, "Unknown action: $action")
                    RESULT_FAILURE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle $action", e)
            RESULT_FAILURE
        }

        val replyData = Bundle().apply {
            putString("reply", "Gift app handled $action for ${notifyTypeName(notifyType)}")
        }
        reply(notifyType, notifyId, result, replyData)
    }

    /**
     * Handle memory warning: clear the image memory cache to reduce PSS.
     */
    private fun handleTrim(notifyType: Int) {
        Log.i(TAG, "Handling TRIM, type=${notifyTypeName(notifyType)}")
        clearCoilMemoryCache()
        // Hint the VM to run garbage collection to help reclaim released Native/Java objects
        System.gc()
    }

    /**
     * Handle exception kill: all persistent state of this app is saved to SharedPreferences
     * in real time (TabManager/ThemeManager/LanguageManager). On the next launch MainActivity
     * and GiftApp automatically restore the last tab/theme/language, so here we only need to
     * clear the in-memory cache and reply.
     */
    private fun handleKill(notifyType: Int) {
        Log.i(TAG, "Handling KILL, type=${notifyTypeName(notifyType)}")
        clearCoilMemoryCache()
    }

    private fun clearCoilMemoryCache() {
        try {
            val context = mContext ?: return
            val imageLoader = SingletonImageLoader.get(context)
            imageLoader.memoryCache?.clear()
            Log.i(TAG, "Coil memory cache cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear Coil memory cache", e)
        }
    }

    private fun checkRemote(callback: IBinder): Boolean {
        synchronized(this) {
            if (mRemote == null) {
                try {
                    mRemote = callback
                    callback.linkToDeath(this, 0)
                } catch (_: RemoteException) {
                    mRemote = null
                    return false
                }
            }
        }
        return true
    }

    /**
     * Send the handling result back to the system through the callback IBinder.
     * The system has a 3-second timeout.
     */
    fun reply(notifyType: Int, notifyId: Int, result: Int, extra: Bundle?) {
        synchronized(this) {
            val remote = mRemote ?: return
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInt(notifyType)
                data.writeInt(notifyId)
                data.writeInt(result)
                data.writeBundle(extra ?: Bundle())
                remote.transact(
                    TRANSACTION_EXCEPTION_REPLY, data, reply, IBinder.FLAG_ONEWAY
                )
                reply.readException()
            } catch (e: Exception) {
                Log.e(TAG, "reply failed.", e)
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }
}
