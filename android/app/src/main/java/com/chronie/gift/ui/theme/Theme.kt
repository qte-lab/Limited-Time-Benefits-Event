package com.chronie.gift.ui.theme

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

val LocalLocale = staticCompositionLocalOf<Locale> { Locale.ENGLISH }

@Composable
fun GiftTheme(
    themeMode: String = "auto", // add themeMode parameter, support "light", "dark", "auto"
    content: @Composable () -> Unit
) {
    // let Compose decide whether to use dark theme based on themeMode
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        "auto" -> isSystemInDarkTheme()
        else -> isSystemInDarkTheme()
    }
    
    val colors = if (darkTheme) {
        top.yukonga.miuix.kmp.theme.darkColorScheme()
    } else {
        top.yukonga.miuix.kmp.theme.lightColorScheme()
    }

    MiuixTheme(colors = colors) {
        content()
    }
}

@Composable
fun GiftTheme(
    controller: ThemeController,
    content: @Composable () -> Unit
) {
    val colors = controller.currentColors()
    
    MiuixTheme(colors = colors) {
        content()
    }
}

@SuppressLint("LocalContextConfigurationRead")
@Composable
fun GiftTheme(
    controller: ThemeController,
    languageController: LanguageController,
    content: @Composable () -> Unit
) {
    val colors = controller.currentColors()
    val locale = languageController.currentLocale
    
    val context = LocalContext.current
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(locale)
    val newContext = context.createConfigurationContext(configuration)
    
    CompositionLocalProvider(
        LocalContext provides newContext,
        LocalLocale provides locale
    ) {
        MiuixTheme(colors = colors) {
            content()
        }
    }
}