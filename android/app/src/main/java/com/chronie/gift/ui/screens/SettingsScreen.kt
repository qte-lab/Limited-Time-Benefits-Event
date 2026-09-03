package com.chronie.gift.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import com.chronie.gift.R
import com.chronie.gift.data.ThemeManager
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SettingsScreen(
    onThemeUpdated: (String) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    isCheckingUpdate: Boolean = false,
    onNavigateToLicenses: () -> Unit = {},
    onNavigateToFoodSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val themeManager = remember { ThemeManager(context) }

    val scrollBehavior = MiuixScrollBehavior()
    
    val themeOptions = listOf(
        stringResource(id = R.string.theme_auto),
        stringResource(id = R.string.theme_light),
        stringResource(id = R.string.theme_dark)
    )
    
    val themeCodes = listOf("auto", "light", "dark")
    
    val savedTheme = themeManager.getSavedTheme()
    val initialThemeIndex = themeCodes.indexOf(savedTheme).takeIf { it >= 0 } ?: 2
    
    var selectedThemeIndex by remember {
        mutableIntStateOf(initialThemeIndex)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.tab_settings),
                largeTitle = stringResource(id = R.string.tab_settings),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            // On large screens the settings content is capped to 80% width and centered so it
            // does not stretch across the whole window. The Answers screen is intentionally exempt.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isWideScreen) Alignment.TopCenter else Alignment.TopStart
            ) {
                LazyColumn(
                    modifier = (if (isWideScreen) Modifier.fillMaxWidth(0.8f) else Modifier.fillMaxWidth())
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    )
                ) {
            item {
                // Add a small space below largeTitle
                Spacer(modifier = Modifier.height(8.dp))
                
                SmallTitle(text = stringResource(id = R.string.general_settings_title))
                
                Card(
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    OverlayDropdownPreference(
                        title = stringResource(id = R.string.theme_settings),
                        items = themeOptions,
                        selectedIndex = selectedThemeIndex,
                        onSelectedIndexChange = { index ->
                            selectedThemeIndex = index
                            val themeCode = themeCodes[index]
                            themeManager.saveTheme(themeCode)
                            onThemeUpdated(themeCode)
                            Toast.makeText(context, context.getString(R.string.theme_switched, themeOptions[index]), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                
                SmallTitle(text = stringResource(id = R.string.food_settings_section))

                Card(
                    modifier = Modifier.padding(bottom = 16.dp),
                    pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = onNavigateToFoodSettings,
                    insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(id = R.string.food_manage_title),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                SmallTitle(text = stringResource(id = R.string.version_info))

                Card(
                    modifier = Modifier.padding(bottom = 12.dp),
                    pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = onCheckUpdate,
                    insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = if (isCheckingUpdate) stringResource(id = R.string.update_checking) else stringResource(id = R.string.update_check_manually),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }

                SmallTitle(text = stringResource(id = R.string.legal_settings))

                Card(
                    modifier = Modifier.padding(bottom = 24.dp),
                    pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = onNavigateToLicenses,
                    insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.open_source_licenses),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }

                val packageInfo = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }
                val versionName = packageInfo?.versionName ?: "unknown"
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter
                ) {
                    Text(
                        text = "v.$versionName",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )
                }
                }
            }
        }
        }
    }
}
