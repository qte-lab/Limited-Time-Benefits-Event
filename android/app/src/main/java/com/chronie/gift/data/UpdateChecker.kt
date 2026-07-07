package com.chronie.gift.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? {
        return try {
            val apiBaseUrl = "http://192.168.10.9:3002"
            val response = client.get("$apiBaseUrl/api/download_apk").body<UpdateResponse>()
            
            if (response.success && response.versionName != null) {
                val latestVersion = response.versionName
                if (isNewVersionAvailable(currentVersion, latestVersion)) {
                    val languageCode = getCurrentLanguageCode()
                    val changelogContent = response.changelog?.get(languageCode) 
                        ?: response.changelog?.get("zh-cn") 
                        ?: response.changelog?.get("en") 
                        ?: ""
                    
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

    private fun getCurrentLanguageCode(): String {
        val locale = java.util.Locale.getDefault()
        val language = locale.language
        val country = locale.country
        
        return when (language) {
            "zh" if country == "CN" -> "zh-cn"
            "zh" if country == "TW" -> "zh-tw"
            "ja" -> "ja"
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