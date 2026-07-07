package com.chronie.gift.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.content.SharedPreferences
import java.util.Locale
import androidx.core.content.edit

class LanguageManager(private val context: Context) {
    companion object {
        private const val LANGUAGE_KEY = "preferred_language"
        private const val PREF_NAME = "app_preferences"
    }

    // Get SharedPreferences instance
    private fun getPreferences(): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Save language setting
    fun saveLanguage(languageCode: String) {
        val preferences = getPreferences()
        preferences.edit { putString(LANGUAGE_KEY, languageCode) }
    }

    // Clear language setting to follow system language
    fun clearLanguage() {
        val preferences = getPreferences()
        preferences.edit { remove(LANGUAGE_KEY) }
    }

    // Get saved language setting, returns null if not set (follow system language)
    fun getSavedLanguage(): String? {
        val preferences = getPreferences()
        return preferences.getString(LANGUAGE_KEY, null)
    }

    // Apply language setting
    fun applyLanguage(languageCode: String?) {
        val locale = if (languageCode == null) {
            // Use system default language from system resources
            val systemConfig = Resources.getSystem().configuration
            systemConfig.locales[0]
        } else {
            when (languageCode) {
                "zh-CN" -> Locale.SIMPLIFIED_CHINESE
                "zh-TW" -> Locale.TRADITIONAL_CHINESE
                "en" -> Locale.ENGLISH
                "ja" -> Locale.JAPANESE
                else -> Locale.SIMPLIFIED_CHINESE
            }
        }

        Locale.setDefault(locale)

        // Update current context configuration
        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        configuration.setLocale(locale)

        // Update resources configuration
        val localeList = LocaleList(locale)
        configuration.setLocales(localeList)

        context.createConfigurationContext(configuration)

        // Update application context configuration
        val appContext = context.applicationContext
        val appConfig = Configuration(appContext.resources.configuration)

        appConfig.setLocales(localeList)
        appContext.createConfigurationContext(appConfig)
    }
}