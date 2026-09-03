package com.chronie.gift.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a finished APK download to the system package installer.
 *
 * Android 7.0 made `file://` URIs unusable across app boundaries, so the URI is
 * normalised to a `content://` one (via [FileProvider] when DownloadManager
 * reports a plain file path) and the read permission is granted along with the
 * intent. From Android 8.0 the app additionally needs the user to allow
 * "install unknown apps" before any of this can succeed — see [canInstall].
 */
object ApkInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /**
     * Whether the installer can be launched right now.
     *
     * Below Android 8.0 installing side loaded APKs is a single global setting
     * the user has already accepted by having sideloading enabled, so there is
     * nothing to check.
     */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /**
     * Opens the system page where the user grants this app the right to install
     * packages. Returns false when the page could not be opened.
     */
    fun openInstallPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Opens the installer on the APK produced by [downloadId].
     *
     * Returns true when the installer screen was launched. A false return means
     * the caller should keep the fallback in place — DownloadManager itself
     * posts a "download complete" notification the user can tap to install.
     */
    fun installDownloaded(context: Context, downloadId: Long): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return false
        if (!isSuccessful(manager, downloadId)) return false

        val uri = manager.getUriForDownloadedFile(downloadId) ?: return false
        val contentUri = toContentUri(context, uri) ?: return false

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Required: the receiver runs from a broadcast, so there is no task to inherit.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false

        // Android 10+ may block activity starts from the background, and a ROM
        // can refuse the URI grant; neither should crash the app.
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /** True when the download finished successfully (a failed download has no file to install). */
    private fun isSuccessful(manager: DownloadManager, downloadId: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(downloadId)
        manager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
        return false
    }

    /**
     * Upgrades a `file://` URI to a shareable `content://` one.
     *
     * DownloadManager normally returns a `content://` URI for public directory
     * downloads, but some builds hand back a raw file path; only the latter has
     * to go through FileProvider (whose authority is declared in the manifest).
     */
    private fun toContentUri(context: Context, uri: Uri): Uri? {
        if (uri.scheme != "file") return uri
        val path = uri.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
