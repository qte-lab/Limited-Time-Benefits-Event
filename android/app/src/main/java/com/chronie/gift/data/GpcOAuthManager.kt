package com.chronie.gift.data

import android.content.Context
import android.content.SharedPreferences
import com.chronie.gift.ui.screens.ApiClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Pure-Kotlin OAuth2 authorization-code manager for the GPC (金猪币) backend.
 *
 * Flow:
 *  1. [buildAuthorizeUrl] fetches the client config from the event server
 *     (`/api/oauth/gpc-config`) and returns the GPC authorize URL.
 *  2. The caller opens that URL in the system browser; the user logs in and
 *     approves, then GPC redirects to `gpcgift://oauth/callback?code=...`.
 *  3. [MainActivity] catches the deep link and calls [handleCallback], which
 *     exchanges the code for an access token via `POST /api/oauth/token`.
 *  4. The token is persisted and exposed through [state]; the quiz screen
 *     collects it to know when the user is authorized.
 */
object GpcOAuthManager {
    private const val PREFS = "gpc_oauth"
    private const val K_TOKEN = "token"
    private const val K_UID = "uid"
    private const val K_UNAME = "uname"

    private val client = ApiClient.client
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<GpcAuthState>(GpcAuthState.Unauthorized)
    val state: StateFlow<GpcAuthState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var config: GpcOAuthConfig? = null

    /** Load any previously persisted token. Call once from [MainActivity.onCreate]. */
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs()
        val token = p.getString(K_TOKEN, null)
        if (!token.isNullOrEmpty()) {
            _state.value = GpcAuthState.Authorized(
                token = token,
                userId = p.getString(K_UID, null),
                username = p.getString(K_UNAME, null)
            )
        }
    }

    fun getToken(): String? = (_state.value as? GpcAuthState.Authorized)?.token

    private fun prefs(): SharedPreferences =
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun saveToken(token: String, userId: String?, username: String?) {
        prefs().edit().apply {
            putString(K_TOKEN, token)
            putString(K_UID, userId)
            putString(K_UNAME, username)
            apply()
        }
        _state.value = GpcAuthState.Authorized(token, userId, username)
    }

    /** Clears the persisted token and returns to the unauthorized state. */
    fun logout() {
        prefs().edit().clear().apply()
        config = null
        _state.value = GpcAuthState.Unauthorized
    }

    fun markAuthorizing() {
        if (_state.value !is GpcAuthState.Authorized) {
            _state.value = GpcAuthState.Authorizing
        }
    }

    fun markError(message: String) {
        _state.value = GpcAuthState.Error(message)
    }

    /** Fetches (and caches) the OAuth client config from the event server. */
    suspend fun ensureConfig(eventBaseUrl: String): GpcOAuthConfig {
        config?.let { return it }
        val text = withContext(Dispatchers.IO) {
            client.get("$eventBaseUrl/api/oauth/gpc-config").bodyAsText()
        }
        val cfg = json.decodeFromString<GpcOAuthConfig>(text)
        config = cfg
        return cfg
    }

    /** Builds the GPC authorize URL the user should open in a browser. */
    suspend fun buildAuthorizeUrl(eventBaseUrl: String): String {
        val cfg = ensureConfig(eventBaseUrl)
        val state = UUID.randomUUID().toString()
        val base = cfg.gpcBaseUrl.trimEnd('/')
        val enc: (String) -> String = { s ->
            java.net.URLEncoder.encode(s, "UTF-8")
        }
        return buildString {
            append(base)
            append("/api/oauth/authorize")
            append("?client_id=").append(enc(cfg.clientId))
            append("&redirect_uri=").append(enc(cfg.redirectUri))
            append("&response_type=code")
            append("&scope=").append(enc(cfg.scope))
            append("&state=").append(enc(state))
        }
    }

    /**
     * Exchanges an authorization code for an access token. Called from
     * [MainActivity] when the `gpcgift://oauth/callback` deep link arrives.
     */
    suspend fun handleCallback(code: String): Boolean {
        val cfg = config ?: run {
            _state.value = GpcAuthState.Error("授权配置缺失，请重试")
            return false
        }
        _state.value = GpcAuthState.Authorizing
        return try {
            val resp = withContext(Dispatchers.IO) {
                client.post("${cfg.gpcBaseUrl.trimEnd('/')}/api/oauth/token") {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("grant_type", "authorization_code")
                                append("code", code)
                                append("client_id", cfg.clientId)
                                append("client_secret", cfg.clientSecret)
                                append("redirect_uri", cfg.redirectUri)
                            }
                        )
                    )
                }
            }
            val text = resp.bodyAsText()
            val env = json.decodeFromString<ApiResponse<GpcTokenData>>(text)
            if (env.success && env.data != null && env.data.access_token.isNotEmpty()) {
                saveToken(env.data.access_token, env.data.userId, env.data.username)
                true
            } else {
                _state.value = GpcAuthState.Error(env.message ?: "授权失败")
                false
            }
        } catch (e: Exception) {
            _state.value = GpcAuthState.Error(e.message ?: "授权失败")
            false
        }
    }
}
