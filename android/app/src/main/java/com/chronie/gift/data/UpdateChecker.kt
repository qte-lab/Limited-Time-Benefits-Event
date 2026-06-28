package com.chronie.gift.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class UpdateChecker {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    @Serializable
    data class UpdateResponse(
        val success: Boolean,
        val data: List<String>?,
        val versionName: String?,
        val versionCode: Int?,
        val latestSize: String?,
        val changelog: Map<String, String>?
    )

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? {
        try {
            // Use configurable API address
            val apiBaseUrl = "http://192.168.10.6:3001"
            val response = client.get("$apiBaseUrl/api/download_apk").body<UpdateResponse>()
            
            if (response.success && response.versionName != null) {
                val latestVersion = response.versionName
                if (isNewVersionAvailable(currentVersion, latestVersion)) {
                    // Get changelog for current language
                    val languageCode = getCurrentLanguageCode()
                    val changelogContent = response.changelog?.get(languageCode) ?: response.changelog?.get("en") ?: ""
                    
                    return UpdateInfo(
                        versionCode = response.versionCode ?: 0,
                        latestVersion = latestVersion,
                        downloadUrl = "$apiBaseUrl/api/download_apk/$latestVersion",
                        changelog = changelogContent,
                        fileSize = response.latestSize ?: "0.0"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            client.close()
        }
        return null
    }

    private fun getCurrentLanguageCode(): String {
        val locale = java.util.Locale.getDefault()
        val language = locale.language
        val country = locale.country
        
        return when {
            language == "zh" && country == "CN" -> "zh-cn"
            language == "zh" && country == "TW" -> "zh-tw"
            language == "ja" -> "ja"
            else -> "en"
        }
    }

    private fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
        try {
            // Current version format: 1.yyyymmdd.hhmm
            // Latest version format: 1.yyyymmdd.hhmm.apk
            val currentParts = currentVersion.split(".")
            val latestParts = latestVersion.removeSuffix(".apk").split(".")

            if (currentParts.size < 3 || latestParts.size < 3) {
                return false
            }

            val currentVersionNum = currentParts[0].toInt()
            val currentDate = currentParts[1].toLong()
            val currentTime = currentParts[2].toInt()

            val latestVersionNum = latestParts[0].toInt()
            val latestDate = latestParts[1].toLong()
            val latestTime = latestParts[2].toInt()

            if (latestVersionNum > currentVersionNum) {
                return true
            } else if (latestVersionNum == currentVersionNum) {
                if (latestDate > currentDate) {
                    return true
                } else if (latestDate == currentDate) {
                    return latestTime > currentTime
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    data class UpdateInfo(
        val versionCode: Int = 0,
        val latestVersion: String? = null,
        val downloadUrl: String? = null,
        val changelog: String = "",
        val fileSize: String = "0.0"
    )
}