package com.chronie.gift

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.chronie.gift.data.GpcOAuthManager
import com.chronie.gift.data.LanguageManager
import com.chronie.gift.data.LocalNetworkPermission
import com.chronie.gift.ui.GiftApp
import com.chronie.gift.ui.theme.GiftTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var lanPermissionLauncher: ActivityResultLauncher<String>
    // Guards against launching the same permission dialog twice in a row
    // (onCreate -> onResume fire back-to-back at startup).
    private var lanPermissionPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load saved language setting (null means follow system language)
        val languageManager = LanguageManager(this)
        val savedLanguage = languageManager.getSavedLanguage()
        languageManager.applyLanguage(savedLanguage)

        // Android 17 (API 37) Local Network Protection (LNP): request the local
        // network permission up front so the LAN event server (192.168.10.9:3002)
        // is reachable. The dialog only appears when actually required (API 37+).
        lanPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            lanPermissionPending = false
            Log.d(TAG, "Local network permission ${if (granted) "granted" else "denied"}")
        }
        requestLanPermissionIfNeeded()

        // Restore any previously authorized GPC token.
        GpcOAuthManager.init(this)

        // Deep link from the GPC authorize redirect (gpcgift://oauth/callback).
        handleIntent(intent)

        setContent {
            GiftApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up a grant made after install / from settings without restarting.
        requestLanPermissionIfNeeded()
    }

    // Handle configuration changes to ensure language setting is applied
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Reapply language setting
        val languageManager = LanguageManager(this)
        val savedLanguage = languageManager.getSavedLanguage()
        languageManager.applyLanguage(savedLanguage)
    }

    /**
     * Requests ACCESS_LOCAL_NETWORK only when LNP is in effect (API 37+) and the
     * permission has not yet been granted. No-op below API 37 and when already granted.
     */
    private fun requestLanPermissionIfNeeded() {
        if (!LocalNetworkPermission.isRequired) return
        if (lanPermissionPending) return
        if (checkSelfPermission(LocalNetworkPermission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        lanPermissionPending = true
        lanPermissionLauncher.launch(LocalNetworkPermission.ACCESS_LOCAL_NETWORK)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Intercepts the OAuth redirect. When GPC finishes the authorization the
     * browser bounces to `gpcgift://oauth/callback?code=...`; we hand the code to
     * [GpcOAuthManager] which exchanges it for an access token.
     */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "gpcgift") {
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrEmpty()) {
                lifecycleScope.launch {
                    GpcOAuthManager.handleCallback(code)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GiftAppPreview() {
    GiftTheme {
        GiftApp()
    }
}
