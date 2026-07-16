package com.chronie.gift

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import com.chronie.gift.data.FairMemoryReceiver
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GiftApplication : Application() {

    companion object {
        private const val TAG = "GiftApplication"
        private const val CRASH_LOG_DIR = "crash_logs"
    }

    private var crashHandler: Thread.UncaughtExceptionHandler? = null
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun onCreate() {
        super.onCreate()
        initCrashHandler()
        // Initialize fair run-time memory adaptation, listen for TRIM/KILL broadcasts
        FairMemoryReceiver.getInstance().initialize(this)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun initCrashHandler() {
        // Get default exception handler
        crashHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        // Set custom exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Save crash log
            saveCrashLog(thread, throwable)
            
            // Call default exception handler to ensure app crash
            crashHandler?.uncaughtException(thread, throwable)
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        executorService.execute {
            try {
                // Create log directory
                val logDir = getCrashLogDir()
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                // Create log file
                val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                val logFileName = "crash-${dateFormat.format(Date())}.txt"
                val logFile = File(logDir, logFileName)

                // Write crash information
                FileWriter(logFile).use { fileWriter ->
                    // Write crash time
                    fileWriter.write("Crash Time: ${dateFormat.format(Date())}\n\n")

                    // Write device information
                    fileWriter.write("Device Information:\n")
                    fileWriter.write("- OS Version: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                    fileWriter.write("- Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    fileWriter.write("- App Version: ${getAppVersion(this)}\n\n")

                    // Write thread information
                    fileWriter.write("Thread Information:\n")
                    fileWriter.write("- Thread Name: ${thread.name}\n")
                    fileWriter.write("- Thread ID: ${thread.threadId()}\n\n")

                    // Write crash stack trace
                    fileWriter.write("Crash Stack Trace:\n")
                    val stringWriter = StringWriter()
                    val printWriter = PrintWriter(stringWriter)
                    throwable.printStackTrace(printWriter)
                    fileWriter.write(stringWriter.toString())
                }

                Log.d(TAG, "Crash log saved to: ${logFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash log", e)
            }
        }
    }

    private fun getCrashLogDir(): File {
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            // Use external storage
            File(getExternalFilesDir(null), CRASH_LOG_DIR)
        } else {
            // Use internal storage
            File(filesDir, CRASH_LOG_DIR)
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }
}