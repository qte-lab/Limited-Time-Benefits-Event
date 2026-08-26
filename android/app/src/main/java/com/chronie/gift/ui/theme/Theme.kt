package com.chronie.gift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

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
