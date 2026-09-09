package com.chronie.gift.ui.theme

import android.content.res.Resources
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

@Stable
class LanguageController(
    initialLanguageCode: String? = null,
) {
    var languageCode: String? by mutableStateOf(initialLanguageCode)

    val currentLocale: Locale
        get() = if (languageCode == null) {
            // Use system default language from system resources
            val systemConfig = Resources.getSystem().configuration
            systemConfig.locales[0]
        } else {
            when (languageCode) {
                "en" -> Locale.ENGLISH
                "ja" -> Locale.JAPANESE
                "zh-CN" -> Locale.SIMPLIFIED_CHINESE
                "zh-TW" -> Locale.TRADITIONAL_CHINESE
                else -> Locale.ENGLISH
            }
        }
}
