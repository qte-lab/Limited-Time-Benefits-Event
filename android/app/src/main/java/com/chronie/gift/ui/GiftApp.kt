package com.chronie.gift.ui

import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.chronie.gift.data.ThemeManager
import com.chronie.gift.data.TabManager
import com.chronie.gift.data.UpdateChecker
import com.chronie.gift.ui.components.FloatingBottomBar
import com.chronie.gift.ui.components.FloatingBottomBarItem
import com.chronie.gift.ui.components.FloatingBottomBarMode
import com.chronie.gift.data.AppDownloadManager
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.chronie.gift.ui.components.UpdateDialog
import com.chronie.gift.ui.navigation.AnswersKey
import com.chronie.gift.ui.navigation.HomeKey
import com.chronie.gift.ui.navigation.LicensesKey
import com.chronie.gift.ui.navigation.SettingsKey
import com.chronie.gift.ui.navigation.TAB_KEYS
import com.chronie.gift.ui.navigation.TabNavKey
import com.chronie.gift.ui.navigation.rememberGiftNavigator
import com.chronie.gift.ui.navigation.tabKeyOf
import com.chronie.gift.ui.screens.AnswerKeysScreen
import com.chronie.gift.ui.screens.QuizScreen
import com.chronie.gift.R
import com.chronie.gift.ui.screens.LicensesScreen
import com.chronie.gift.ui.screens.SettingsScreen
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import com.chronie.gift.ui.theme.GiftTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun GiftApp() {
    val context = LocalContext.current

    // Tab management
    val tabManager = remember { TabManager(context) }
    val savedTab = remember { tabManager.getSavedTab() }

    // Initial back stack: the home tab is always the root so that back always leads there, and the
    // tab the user left off on is restored on top of it. These keys are only applied the first time
    // the back stack is built; after a configuration change or process death the persisted stack is
    // restored instead.
    val initialBackStack = remember(savedTab) {
        val restoredTab = tabKeyOf(savedTab)
        if (restoredTab == HomeKey) {
            arrayOf<NavKey>(HomeKey)
        } else {
            arrayOf<NavKey>(HomeKey, restoredTab)
        }
    }

    // Navigation 3 back stack, wrapped so that call sites keep reading like the old NavController
    val navigator = rememberGiftNavigator(*initialBackStack)
    val backStack = navigator.backStack

    // The highlighted tab is derived from the back stack instead of being tracked separately, so
    // the bottom bar stays in sync when the user navigates back with the system gesture.
    val selectedTab: TabNavKey by remember {
        derivedStateOf { backStack.lastOrNull { it is TabNavKey } as? TabNavKey ?: HomeKey }
    }

    // Persist whichever tab is currently on top so the next launch restores it
    LaunchedEffect(selectedTab) {
        tabManager.saveTab(selectedTab.tabId)
    }

    
    // Theme management
    val themeManager = remember { ThemeManager(context) }
    val savedTheme = themeManager.getSavedTheme()
    val initialThemeMode = when (savedTheme) {
        "light" -> ColorSchemeMode.Light
        "dark" -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    
    var currentThemeMode by remember {
        mutableStateOf(initialThemeMode)
    }
    
    // Update theme mode callback
    val updateThemeMode = { newThemeMode: String ->
        val colorSchemeMode = when (newThemeMode) {
            "light" -> ColorSchemeMode.Light
            "dark" -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
        currentThemeMode = colorSchemeMode
        themeManager.saveTheme(newThemeMode)
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
        } catch (_: Exception) {
            "1.0.0"
        }
    }
    
    // Check for updates function
    val checkForUpdates = suspend {
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

    // Switching tabs pushes the tab onto the shared back stack, exactly like the previous
    // NavController based implementation. launchSingleTop keeps a tap on the already selected tab
    // from stacking a duplicate entry.
    //
    // To instead get the "classic" bottom bar behavior where the back stack never grows past two
    // entries, change this to:
    //   navigator.navigate(tab, popUpTo = HomeKey, launchSingleTop = true)
    val onTabSelected: (TabNavKey) -> Unit = { tab ->
        navigator.navigate(tab, launchSingleTop = true)
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
    val themeController = remember(currentThemeMode) {
        ThemeController(currentThemeMode)
    }
    GiftTheme(controller = themeController) {
        val backdrop = rememberLayerBackdrop()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp
            val navMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                FloatingBottomBarMode.LiquidGlass
            } else {
                FloatingBottomBarMode.None
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Bottom liquid-glass nav bar — visible on narrow screens (< 600dp)
                        AnimatedContent(
                            targetState = isWideScreen,
                            transitionSpec = {
                                if (targetState) {
                                    // Exiting bottom mode: slide down + fade out
                                    (fadeIn(tween(250))) togetherWith
                                        (slideOutVertically(tween(300)) { it } + fadeOut(tween(200)))
                                } else {
                                    // Entering bottom mode: slide up + fade in
                                    (slideInVertically(tween(300)) { it } + fadeIn(tween(250))) togetherWith
                                        (fadeOut(tween(200)))
                                }
                                .using(SizeTransform(clip = false))
                            },
                            label = "bottomNavTransition",
                        ) { wide ->
                            if (!wide) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FloatingBottomBar(
                                        modifier = Modifier.wrapContentWidth(),
                                        selectedIndex = {
                                            TAB_KEYS.indexOf(selectedTab).coerceAtLeast(0)
                                        },
                                        onSelected = { index ->
                                            onTabSelected(TAB_KEYS.getOrElse(index) { HomeKey })
                                        },
                                        backdrop = backdrop,
                                        tabsCount = 3,
                                        mode = navMode,
                                        autoWidth = true,
                                        isTopMode = false,
                                    ) {
                                        FloatingBottomBarItem(
                                            onClick = { onTabSelected(HomeKey) },
                                            tabIndex = 0,
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.HorizontalSplit,
                                                contentDescription = stringResource(R.string.tab_home),
                                            )
                                            Text(
                                                stringResource(R.string.tab_home),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                        FloatingBottomBarItem(
                                            onClick = { onTabSelected(AnswersKey) },
                                            tabIndex = 1,
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.ListView,
                                                contentDescription = stringResource(R.string.tab_answers),
                                            )
                                            Text(
                                                stringResource(R.string.tab_answers),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                        FloatingBottomBarItem(
                                            onClick = { onTabSelected(SettingsKey) },
                                            tabIndex = 2,
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Settings,
                                                contentDescription = stringResource(R.string.tab_settings),
                                            )
                                            Text(
                                                stringResource(R.string.tab_settings),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    topBar = {
                        // Top text-only nav bar — visible on wide screens (>= 600dp)
                        AnimatedContent(
                            targetState = isWideScreen,
                            transitionSpec = {
                                if (targetState) {
                                    // Entering top mode: slide down + fade in
                                    (slideInVertically(tween(300)) { -it } + fadeIn(tween(250))) togetherWith
                                        (fadeOut(tween(200)))
                                } else {
                                    // Exiting top mode: slide up + fade out
                                    (fadeIn(tween(250))) togetherWith
                                        (slideOutVertically(tween(300)) { -it } + fadeOut(tween(200)))
                                }
                                .using(SizeTransform(clip = false))
                            },
                            label = "topNavTransition",
                        ) { wide ->
                            if (wide) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FloatingBottomBar(
                                        modifier = Modifier.wrapContentWidth(),
                                        selectedIndex = {
                                            TAB_KEYS.indexOf(selectedTab).coerceAtLeast(0)
                                        },
                                        onSelected = { index ->
                                            onTabSelected(TAB_KEYS.getOrElse(index) { HomeKey })
                                        },
                                        backdrop = backdrop,
                                        tabsCount = 3,
                                        mode = navMode,
                                        autoWidth = true,
                                        isTopMode = true,
                                    ) {
                                        // Top mode: text-only, no icons
                                        FloatingBottomBarItem(
                                            onClick = { onTabSelected(HomeKey) },
                                            tabIndex = 0,
                                        ) {
                                            Text(
                                                stringResource(R.string.tab_home),
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        FloatingBottomBarItem(
                                            onClick = { onTabSelected(AnswersKey) },
                                            tabIndex = 1,
                                        ) {
                                            Text(
                                                stringResource(R.string.tab_answers),
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        FloatingBottomBarItem(
                                            onClick = { onTabSelected(SettingsKey) },
                                            tabIndex = 2,
                                        ) {
                                            Text(
                                                stringResource(R.string.tab_settings),
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Box(Modifier.layerBackdrop(backdrop)) {
                        NavDisplay(
                            backStack = backStack,
                            modifier = Modifier.fillMaxSize(),
                            onBack = { navigator.pop() },
                            // SaveableStateHolder keeps rememberSaveable state per destination across
                            // navigation, the ViewModelStore decorator scopes ViewModels (and their
                            // SavedStateHandle) to a single entry and clears them when it is popped.
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            transitionSpec = {
                                (slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(300))) togetherWith
                                    (slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300)))
                            },
                            popTransitionSpec = {
                                (slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(300))) togetherWith
                                    (slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300)))
                            },
                            // Drives the predictive back gesture with the same visuals as a normal pop
                            predictivePopTransitionSpec = {
                                (slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(300))) togetherWith
                                    (slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300)))
                            },
                            entryProvider = entryProvider {
                                entry<HomeKey> {
                                    QuizScreen()
                                }
                                entry<AnswersKey> {
                                    AnswerKeysScreen()
                                }
                                entry<SettingsKey> {
                                    SettingsScreen(
                                        onThemeUpdated = updateThemeMode,
                                        onCheckUpdate = {
                                            val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
                                            coroutineScope.launch {
                                                checkForUpdates()
                                            }
                                        },
                                        isCheckingUpdate = isCheckingUpdate,
                                        onNavigateToLicenses = {
                                            navigator.push(LicensesKey)
                                        }
                                    )
                                }
                                entry<LicensesKey> {
                                    LicensesScreen(
                                        onBack = {
                                            navigator.pop()
                                        }
                                    )
                                }
                            }
                        )
                    }
                }

                // Update dialog - placed outside Scaffold but inside Box to ensure correct z-order
                UpdateDialog(
                    show = showUpdateDialog,
                    versionName = latestVersion,
                    changelog = changelog,
                    fileSize = fileSize,
                    onUpdate = handleUpdate,
                    onDismiss = { showUpdateDialog = false }
                )
            }
        }
    }
}