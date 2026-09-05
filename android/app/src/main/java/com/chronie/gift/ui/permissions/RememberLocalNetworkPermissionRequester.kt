package com.chronie.gift.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.chronie.gift.data.LocalNetworkPermission

/**
 * Requester returned by [rememberLocalNetworkPermissionRequester].
 *
 * Call [ensure] with the local-network work. If the permission is already
 * granted (or the device is below Android 17, where LNP does not exist) the
 * block runs immediately; otherwise the runtime dialog is shown and the block
 * only runs after the user accepts. [onDenied] fires if the user declines.
 */
interface LocalNetworkPermissionRequester {
    fun ensure(onGranted: () -> Unit)
}

/**
 * Compose helper that wraps a local-network operation behind the Android 17
 * Local Network Protection (LNP) permission.
 *
 * The request must originate from an `Activity`/`ComponentActivity` context.
 * Within Compose, [LocalContext] already resolves to the host Activity, so this
 * is safe to call from any `@Composable` screen. Pure-Kotlin/non-Activity
 * callers (e.g. a plain data class) must instead *guard* with
 * [LocalNetworkPermission.isGranted] and let the screen request on their behalf.
 *
 * @param onDenied invoked once if the user denies the permission. Show a Toast
 *   or disable the LAN feature here.
 */
@Composable
fun rememberLocalNetworkPermissionRequester(
    onDenied: () -> Unit = {}
): LocalNetworkPermissionRequester {
    val context = LocalContext.current
    val currentOnDenied by rememberUpdatedState(onDenied)

    // Stable holder for the pending grant action; read fresh inside the callback.
    val pendingGranted = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingGranted.value
        pendingGranted.value = null
        if (granted) {
            action?.invoke()
        } else {
            currentOnDenied()
        }
    }

    return remember {
        object : LocalNetworkPermissionRequester {
            override fun ensure(onGranted: () -> Unit) {
                if (LocalNetworkPermission.isGranted(context)) {
                    onGranted()
                } else {
                    pendingGranted.value = onGranted
                    launcher.launch(LocalNetworkPermission.ACCESS_LOCAL_NETWORK)
                }
            }
        }
    }
}
