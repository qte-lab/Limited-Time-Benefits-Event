package com.chronie.gift.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.chronie.gift.R
import androidx.core.net.toUri

class AppDownloadManager(private val context: Context) {
    fun downloadApk(url: String, fileName: String): Long {
        val request = DownloadManager.Request(url.toUri())
            .setTitle(context.getString(R.string.update_notification_title))
            .setDescription(context.getString(R.string.update_notification_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }
}