package com.chronie.gift.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android 17 / API 37 (Build.VERSION_CODES.CINNAMON_BUN).
 *
 * LNP is enforced only on API 37+. The app targets 37 and talks to an on-LAN
 * event server (http://192.168.10.9:3002), so this permission is mandatory there.
 */
private const val ANDROID_17_API = 37

/**
 * Central predicate for the Android 17 Local Network Protection (LNP) gate.
 *
 * Use [isRequired] / [isGranted] everywhere instead of repeating the API-37
 * check. On devices below API 37 LNP does not exist, so [isGranted] returns
 * `true` and callers can proceed immediately with no dialog and no regression.
 */
object LocalNetworkPermission {

    /**
     * Local Network Protection (LNP) permission string.
     *
     * Declared as a literal so the module compiles even if the platform stub's
     * `Manifest.permission.ACCESS_LOCAL_NETWORK` constant is missing. Equivalent to
     * `android.Manifest.permission.ACCESS_LOCAL_NETWORK`.
     */
    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    /** True when LNP is enforced: Android 17 (API 37) and above. */
    val isRequired: Boolean
        get() = Build.VERSION.SDK_INT >= ANDROID_17_API

    /**
     * Whether local-network access is currently allowed.
     *
     * - Below API 37: always `true` (no LNP).
     * - API 37+: `true` only when the user has granted ACCESS_LOCAL_NETWORK.
     */
    fun isGranted(context: Context): Boolean {
        if (!isRequired) return true
        return context.checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    }
}
