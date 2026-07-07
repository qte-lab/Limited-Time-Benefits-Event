package com.chronie.gift.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class ThemeManager(private val context: Context) {
    companion object {
        private const val THEME_KEY = "theme_mode"
        private const val DEFAULT_THEME = "auto"
    }

    // Save theme setting
    fun saveTheme(themeMode: String) {
        val preferences: SharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        preferences.edit { putString(THEME_KEY, themeMode) }
    }

    // Get saved theme setting
    fun getSavedTheme(): String {
        val preferences: SharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return preferences.getString(THEME_KEY, DEFAULT_THEME) ?: DEFAULT_THEME
    }

}