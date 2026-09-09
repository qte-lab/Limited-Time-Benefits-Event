package com.chronie.gift.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

class UpdateChecker {
    private val client by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    @Serializable
    data class UpdateResponse(
        val success: Boolean,
        val data: List<String>?,
        val latest: String?,
        val latestSize: String?,
        val versionCode: Int?,
        val versionName: String?,
        val changelog: Map<String, String>?
    )

    suspend fun checkForUpdates(currentVersion: String, languageCode: String? = null): UpdateInfo? {
        return try {
            val apiBaseUrl = "http://192.168.10.9:3002"
            val response = client.get("$apiBaseUrl/api/download_apk").body<UpdateResponse>()
            
            if (response.success && response.versionName != null) {
                val latestVersion = response.versionName
                if (isNewVersionAvailable(currentVersion, latestVersion)) {
                    // Pick the changelog in the user's language; fall back to en, then zh-cn,
                    // then whatever languages the server returned.
                    val changelogContent = resolveChangelog(response.changelog, languageCode)
                    
                    return UpdateInfo(
                        versionCode = response.versionCode ?: 0,
                        latestVersion = latestVersion,
                        downloadUrl = "$apiBaseUrl/api/download_apk/${response.latest}",
                        changelog = changelogContent,
                        fileSize = response.latestSize ?: "0.0"
                    )
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Maps the app's language setting to a prioritized list of changelog keys and
     * returns the first one the server actually provided.
     *
     * [languageCode] is one of `null` (follow system), "zh-CN", "zh-TW", "en", "ja".
     * The changelog JSON uses lower-case region keys ("zh-cn", "zh-tw", "en", "ja").
     */
    private fun resolveChangelog(changelog: Map<String, String>?, languageCode: String?): String {
        if (changelog.isNullOrEmpty()) return ""

        val primary = when (languageCode) {
            "zh-CN" -> "zh-cn"
            "zh-TW" -> "zh-tw"
            "en" -> "en"
            "ja" -> "ja"
            else -> systemChangelogKey()
        }

        // Priority: requested language -> en -> zh-cn -> any other available language.
        val candidates = buildList {
            add(primary)
            if (primary != "en") add("en")
            if (primary != "zh-cn") add("zh-cn")
            addAll(changelog.keys)
        }.distinct()

        for (key in candidates) {
            changelog[key]?.let { return it }
        }
        return ""
    }

    /**
     * Resolves a changelog key for the "follow system language" case, based on the
     * currently active default [Locale] (which [LanguageManager] already updated).
     */
    private fun systemChangelogKey(): String {
        val locale = Locale.getDefault()
        return when {
            locale.language == "zh" &&
                (locale.country.equals("TW", ignoreCase = true) || locale.country.equals("HK", ignoreCase = true)) -> "zh-tw"
            locale.language == "zh" -> "zh-cn"
            locale.language == "ja" -> "ja"
            else -> "en"
        }
    }

    private fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
        return try {
            val currentClean = currentVersion.trim()
            val latestClean = latestVersion.removeSuffix(".apk").trim()
            
            if (currentClean.isEmpty() || latestClean.isEmpty()) {
                return false
            }

            val currentParts = currentClean.split(".")
            val latestParts = latestClean.split(".")

            val maxLen = maxOf(currentParts.size, latestParts.size)
            
            for (i in 0 until maxLen) {
                val currentPart = if (i < currentParts.size) currentParts[i] else "0"
                val latestPart = if (i < latestParts.size) latestParts[i] else "0"

                val currentNum = currentPart.toLongOrNull()
                val latestNum = latestPart.toLongOrNull()

                if (currentNum != null && latestNum != null) {
                    if (latestNum > currentNum) return true
                    if (latestNum < currentNum) return false
                } else {
                    val compare = currentPart.compareTo(latestPart)
                    if (compare < 0) return true
                    if (compare > 0) return false
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    data class UpdateInfo(
        val versionCode: Int = 0,
        val latestVersion: String? = null,
        val downloadUrl: String? = null,
        val changelog: String = "",
        val fileSize: String = "0.0"
    )
}