package com.chronie.gift.ui.screens

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.chronie.gift.R
import com.chronie.gift.data.ServerMonitorConfig
import com.chronie.gift.ui.permissions.rememberLocalNetworkPermissionRequester
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * In-app server status monitor.
 *
 * Renders [ServerMonitorConfig.SERVER_STATUS_URL] inside the system `WebView` (Chromium-based on
 * the device). This is the supported engine — no custom Chromium kernel (`libchrome.so`) is used.
 *
 * The top bar is a compact [SmallTopAppBar] (small title only) whose actions hold the refresh and
 * "open in browser" icon buttons, so the WebView gets the full height below it.
 *
 * Reaching the on-LAN server (http://192.168.10.9:5000) requires the Android 17 Local Network
 * Protection permission, so we gate the first load behind [rememberLocalNetworkPermissionRequester].
 */
@Composable
fun ServerStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pendingLoad by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val lnpRequester = rememberLocalNetworkPermissionRequester(
        onDenied = {
            isLoading = false
            loadError = context.getString(R.string.lan_permission_denied)
            Toast.makeText(context, context.getString(R.string.lan_permission_denied), Toast.LENGTH_LONG).show()
        }
    )

    // Ask for LAN permission, then flag the WebView to load once granted.
    LaunchedEffect(Unit) {
        lnpRequester.ensure { pendingLoad = true }
    }

    // Load only after both the WebView instance exists and the permission is granted.
    LaunchedEffect(webView, pendingLoad) {
        if (pendingLoad && webView != null) {
            webView?.loadUrl(ServerMonitorConfig.SERVER_STATUS_URL)
            pendingLoad = false
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.server_status_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(id = R.string.back),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(id = R.string.server_status_refresh),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(
                        onClick = {
                            try {
                                uriHandler.openUri(ServerMonitorConfig.SERVER_STATUS_URL)
                            } catch (_: Exception) {
                                Toast.makeText(context, context.getString(R.string.open_link_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Link,
                            contentDescription = stringResource(id = R.string.server_status_open_external),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                loadError = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = context.getString(R.string.server_status_error)
                                }
                            }
                        }
                        webView = this
                    }
                },
                onRelease = { it.destroy() }
            )

            if (isLoading && loadError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.server_status_loading),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )
                }
            }

            if (loadError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = loadError ?: "",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        IconButton(
                            onClick = {
                                loadError = null
                                isLoading = true
                                webView?.reload()
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(id = R.string.server_status_retry),
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
