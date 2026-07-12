package com.chronie.gift.ui.theme

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.util.Locale

val LocalLocale = staticCompositionLocalOf<Locale> { Locale.ENGLISH }

@Composable
fun GiftTheme(
    themeMode: String = "auto",
    content: @Composable () -> Unit
) {
    val colorSchemeMode = when (themeMode) {
        "dark" -> ColorSchemeMode.Dark
        "light" -> ColorSchemeMode.Light
        else -> ColorSchemeMode.System
    }
    
    val darkTheme = when (colorSchemeMode) {
        ColorSchemeMode.Dark -> true
        ColorSchemeMode.Light -> false
        else -> isSystemInDarkTheme()
    }

    UpdateSystemUi(darkTheme)

    val controller = ThemeController(colorSchemeMode)
    MiuixTheme(controller = controller) {
        content()
    }
}

@Composable
fun GiftTheme(
    controller: ThemeController,
    content: @Composable () -> Unit
) {
    val darkTheme = when (controller.colorSchemeMode) {
        ColorSchemeMode.Dark -> true
        ColorSchemeMode.Light -> false
        else -> isSystemInDarkTheme()
    }
    
    UpdateSystemUi(darkTheme)
    
    MiuixTheme(controller = controller) {
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
    val locale = languageController.currentLocale
    val darkTheme = when (controller.colorSchemeMode) {
        ColorSchemeMode.Dark -> true
        ColorSchemeMode.Light -> false
        else -> isSystemInDarkTheme()
    }
    
    UpdateSystemUi(darkTheme)
    
    val context = LocalContext.current
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(locale)
    val newContext = context.createConfigurationContext(configuration)
    
    CompositionLocalProvider(
        LocalContext provides newContext,
        LocalLocale provides locale
    ) {
        MiuixTheme(controller = controller) {
            content()
        }
    }
}

@Composable
private fun UpdateSystemUi(darkTheme: Boolean) {
    val view = LocalView.current
    LaunchedEffect(darkTheme) {
        val context = view.context
        if (context is android.app.Activity) {
            val windowInsetsController = WindowCompat.getInsetsController(context.window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
        }
    }
}
