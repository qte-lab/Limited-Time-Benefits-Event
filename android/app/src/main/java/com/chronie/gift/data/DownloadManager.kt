package com.chronie.gift.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import com.chronie.gift.R

/**
 * Enqueues APK downloads with the system DownloadManager and, once one lands,
 * hands it straight to the package installer.
 *
 * DownloadManager already posts a notification the user can tap to install; the
 * receiver added here just saves that trip through the shade. It is registered
 * against the application context so a download that outlives the screen that
 * started it still gets picked up, and it unregisters itself as soon as its own
 * download reports back, so nothing is left listening afterwards.
 */
class AppDownloadManager(private val context: Context) {

    /**
     * Starts downloading [url] into the public Downloads folder.
     *
     * [fileName] should already carry the `.apk` suffix: the installer matches
     * on the MIME type we pass, but a missing extension makes the file useless
     * to anything else that finds it in Downloads.
     */
    fun downloadApk(url: String, fileName: String): Long {
        val request = DownloadManager.Request(url.toUri())
            .setTitle(context.getString(R.string.update_notification_title))
            .setDescription(context.getString(R.string.update_notification_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType(APK_MIME)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        watchForInstall(downloadId)
        return downloadId
    }

    /**
     * Listens for this download's completion broadcast and opens the installer.
     *
     * Only one download is tracked at a time — starting a new one drops the
     * previous listener, which also covers the case where an earlier download
     * was cancelled or failed and would otherwise never deliver a broadcast.
     */
    private fun watchForInstall(downloadId: Long) {
        val appContext = context.applicationContext
        unregister(appContext)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                unregister(ctx.applicationContext)
                ApkInstaller.installDownloaded(ctx.applicationContext, downloadId)
            }
        }

        // The three-argument overload only exists from API 26 up, and minSdk is 24.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The DownloadManager runs in its own system app UID rather than the
            // system UID, so an unexported receiver would miss the broadcast.
            appContext.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
        pendingReceiver = receiver
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"

        @Volatile
        private var pendingReceiver: BroadcastReceiver? = null

        private fun unregister(context: Context) {
            pendingReceiver?.let {
                runCatching { context.applicationContext.unregisterReceiver(it) }
            }
            pendingReceiver = null
        }
    }
}
