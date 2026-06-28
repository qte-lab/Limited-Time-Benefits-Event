package com.chronie.gift.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chronie.gift.data.LanguageManager
import com.chronie.gift.data.ThemeManager
import com.chronie.gift.data.TabManager
import com.chronie.gift.data.UpdateChecker
import com.chronie.gift.data.AppDownloadManager
import com.chronie.gift.ui.navigation.FloatingBottomBar
import com.chronie.gift.ui.navigation.FloatingBottomBarItem
import com.chronie.gift.ui.navigation.FloatingBottomBarMode
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.chronie.gift.ui.components.UpdateDialog
import com.chronie.gift.ui.navigation.NavRoutes
import com.chronie.gift.ui.screens.AnswersScreen
import com.chronie.gift.ui.screens.HomeScreen
import com.chronie.gift.R
import com.chronie.gift.ui.screens.LicensesScreen
import com.chronie.gift.ui.screens.SettingsScreen
import com.chronie.gift.ui.theme.ColorSchemeMode
import com.chronie.gift.ui.theme.GiftTheme
import com.chronie.gift.ui.theme.ThemeController
import com.chronie.gift.ui.theme.LanguageController
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun GiftApp() {
    val context = LocalContext.current
    
    // Compose manage navigation controller
    val navController = rememberNavController()
    
    // Tab management
    val tabManager = remember { TabManager(context) }
    val savedTab = tabManager.getSavedTab()
    
    // Selected tab
    var selectedTab by remember {
        mutableStateOf(savedTab)
    }
    
    // Theme management
    val themeManager = remember { ThemeManager(context) }
    val savedTheme = themeManager.getSavedTheme()
    val initialThemeMode = when (savedTheme) {
        "light" -> ColorSchemeMode.Light
        "dark" -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    
    val controller = remember {
        ThemeController(initialThemeMode)
    }
    
    // Language management
    val languageManager = remember { LanguageManager(context) }
    val savedLanguage = languageManager.getSavedLanguage()
    
    val languageController = remember {
        LanguageController(savedLanguage)
    }
    
    // Update theme mode callback
    val updateThemeMode = { newThemeMode: String ->
        val colorSchemeMode = when (newThemeMode) {
            "light" -> ColorSchemeMode.Light
            "dark" -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
        controller.colorSchemeMode = colorSchemeMode
        themeManager.saveTheme(newThemeMode)
    }
    
    // Update language callback
    val updateLanguageCode = { newLanguageCode: String? ->
        languageController.languageCode = newLanguageCode
        if (newLanguageCode == null) {
            languageManager.clearLanguage()
        } else {
            languageManager.saveLanguage(newLanguageCode)
        }
        languageManager.applyLanguage(newLanguageCode)
    }
    
    // Update check related states
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }
    var changelog by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("") }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    
    // Get current app version
    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    // Check for updates function
    val checkForUpdates = suspend { ->
        isCheckingUpdate = true
        try {
            val updateChecker = UpdateChecker()
            val updateInfo = withContext(Dispatchers.IO) {
                updateChecker.checkForUpdates(currentVersion)
            }
            
            if (updateInfo != null) {
                latestVersion = updateInfo.latestVersion ?: ""
                downloadUrl = updateInfo.downloadUrl ?: ""
                changelog = updateInfo.changelog
                fileSize = updateInfo.fileSize
                showUpdateDialog = true
                true
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.update_no_new_version), Toast.LENGTH_SHORT).show()
                }
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
            }
            false
        } finally {
            isCheckingUpdate = false
        }
    }
    
    // Automatically check for updates when app starts
    LaunchedEffect(Unit) {
        checkForUpdates()
    }
    
    // Navigate to saved tab when app starts
    LaunchedEffect(savedTab) {
        when (savedTab) {
            NavRoutes.Home -> navController.navigate(NavRoutes.Home)
            NavRoutes.Answers -> navController.navigate(NavRoutes.Answers)
            NavRoutes.Settings -> navController.navigate(NavRoutes.Settings)
        }
    }
    
    // Handle update download
    val handleUpdate = {
        try {
            val downloadManager = AppDownloadManager(context)
            val downloadId = downloadManager.downloadApk(downloadUrl, latestVersion)
            Toast.makeText(context, context.getString(R.string.update_start_download), Toast.LENGTH_SHORT).show()
            showUpdateDialog = false
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.update_download_failed), Toast.LENGTH_SHORT).show()
        }
    }
    
    // Move GiftTheme call here to ensure theme changes are applied immediately
    GiftTheme(controller = controller, languageController = languageController) {
        val backdrop = rememberLayerBackdrop()
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    FloatingBottomBar(
                        selectedIndex = {
                            when (selectedTab) {
                                NavRoutes.Home -> 0
                                NavRoutes.Answers -> 1
                                NavRoutes.Settings -> 2
                                else -> 0
                            }
                        },
                        onSelected = { index ->
                            val tab = when (index) {
                                0 -> NavRoutes.Home
                                1 -> NavRoutes.Answers
                                2 -> NavRoutes.Settings
                                else -> NavRoutes.Home
                            }
                            selectedTab = tab
                            tabManager.saveTab(tab)
                            when (tab) {
                                NavRoutes.Home -> navController.navigate(NavRoutes.Home)
                                NavRoutes.Answers -> navController.navigate(NavRoutes.Answers)
                                NavRoutes.Settings -> navController.navigate(NavRoutes.Settings)
                            }
                        },
                        backdrop = backdrop,
                        tabsCount = 3,
                        mode = FloatingBottomBarMode.LiquidGlass,
                    ) {
                        FloatingBottomBarItem(
                            onClick = {
                                val tab = NavRoutes.Home
                                selectedTab = tab
                                tabManager.saveTab(tab)
                                navController.navigate(NavRoutes.Home)
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.HorizontalSplit,
                                contentDescription = stringResource(R.string.tab_home)
                            )
                            Text(stringResource(R.string.tab_home))
                        }
                        FloatingBottomBarItem(
                            onClick = {
                                val tab = NavRoutes.Answers
                                selectedTab = tab
                                tabManager.saveTab(tab)
                                navController.navigate(NavRoutes.Answers)
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.ListView,
                                contentDescription = stringResource(R.string.tab_answers)
                            )
                            Text(stringResource(R.string.tab_answers))
                        }
                        FloatingBottomBarItem(
                            onClick = {
                                val tab = NavRoutes.Settings
                                selectedTab = tab
                                tabManager.saveTab(tab)
                                navController.navigate(NavRoutes.Settings)
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = stringResource(R.string.tab_settings)
                            )
                            Text(stringResource(R.string.tab_settings))
                        }
                    }
                }
            ) {
                Box(Modifier.layerBackdrop(backdrop)) {
                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.Home,
                    ) {
                        composable(NavRoutes.Home) {
                            HomeScreen()
                        }
                        composable(NavRoutes.Answers) {
                            AnswersScreen()
                        }
                        composable(NavRoutes.Settings) {
                            SettingsScreen(
                                onThemeUpdated = updateThemeMode,
                                onLanguageUpdated = updateLanguageCode,
                                currentLanguageCode = languageController.languageCode,
                                onCheckUpdate = {
                                    val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
                                    coroutineScope.launch {
                                        checkForUpdates()
                                    }
                                },
                                isCheckingUpdate = isCheckingUpdate,
                                onNavigateToLicenses = {
                                    navController.navigate(NavRoutes.Licenses)
                                }
                            )
                        }
                        composable(NavRoutes.Licenses) {
                            LicensesScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
            
            // Update dialog - placed outside Scaffold but inside Box to ensure correct z-order
            UpdateDialog(
                show = showUpdateDialog,
                version = latestVersion,
                changelog = changelog,
                fileSize = fileSize,
                onUpdate = handleUpdate,
                onDismiss = { showUpdateDialog = false }
            )
        }
    }
}