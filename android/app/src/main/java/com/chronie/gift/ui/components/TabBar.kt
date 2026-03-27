package com.chronie.gift.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.chronie.gift.R
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDisplayMode
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TabBar(
    selectedTab: String,
    onTabChange: (String) -> Unit
) {
    // Convert string tab to index
    val selectedIndex = when (selectedTab) {
        "home" -> 0
        "answers" -> 1
        "settings" -> 2
        else -> 0
    }

    // Define tab data using MiuixIcons
    val tabs = listOf(
        Triple("home", MiuixIcons.HorizontalSplit, stringResource(id = R.string.tab_home)),
        Triple("answers", MiuixIcons.ListView, stringResource(id = R.string.tab_answers)),
        Triple("settings", MiuixIcons.Settings, stringResource(id = R.string.tab_settings))
    )

    // Create NavigationItem list
    val icons = tabs.map { it.second }
    val labels = tabs.map { it.third }

    FloatingNavigationBar(
        mode = FloatingNavigationBarDisplayMode.IconOnly,
        color = MiuixTheme.colorScheme.background.copy(alpha = 0.7f)
    ) {
        tabs.forEachIndexed { index, item ->
            FloatingNavigationBarItem(
                icon = icons[index],
                label = labels[index],
                selected = selectedIndex == index,
                onClick = { onTabChange(tabs[index].first) }
            )
        }
    }
}