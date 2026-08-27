package com.chronie.gift

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.chronie.gift.data.GpcOAuthManager
import com.chronie.gift.ui.GiftApp
import com.chronie.gift.ui.theme.GiftTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Restore any previously authorized GPC token.
        GpcOAuthManager.init(this)

        // Deep link from the GPC authorize redirect (gpcgift://oauth/callback).
        handleIntent(intent)

        setContent {
            GiftApp()
        }
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
