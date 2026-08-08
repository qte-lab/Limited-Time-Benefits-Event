package com.chronie.gift.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Stable string ids for the top level tabs.
 *
 * These values are persisted by [com.chronie.gift.data.TabManager] into SharedPreferences and are
 * intentionally kept identical to the route strings used before the Navigation 3 migration, so an
 * app that is updated in place still restores the tab the user left off on.
 */
object TabIds {
    const val HOME = "home"
    const val ANSWERS = "answers"
    const val SETTINGS = "settings"
}

/**
 * Base type for every destination of the app.
 *
 * Navigation 3 replaces string routes with plain Kotlin objects. Every concrete key must be
 * annotated with [Serializable] so that
 * [androidx.navigation3.runtime.rememberNavBackStack] can save and restore the back stack across
 * configuration changes and process death.
 *
 * Adding an argument to a destination is done by turning the object into a data class, e.g.
 * `@Serializable data class AnswerDetailKey(val fileName: String) : GiftNavKey`. The argument is
 * then a real, compile time checked constructor parameter instead of a string that has to be
 * encoded into a route and parsed back out.
 */
sealed interface GiftNavKey : NavKey

/** A destination that is reachable from the floating bottom bar. */
sealed interface TabNavKey : GiftNavKey {
    /** Stable id used for persistence, see [TabIds]. */
    val tabId: String
}

@Serializable
data object HomeKey : TabNavKey {
    override val tabId: String get() = TabIds.HOME
}

@Serializable
data object AnswersKey : TabNavKey {
    override val tabId: String get() = TabIds.ANSWERS
}

@Serializable
data object SettingsKey : TabNavKey {
    override val tabId: String get() = TabIds.SETTINGS
}

/** Open source licenses, pushed on top of [SettingsKey]. */
@Serializable
data object LicensesKey : GiftNavKey

/** Tabs in the order they are rendered by the floating bottom bar. */
val TAB_KEYS: List<TabNavKey> = listOf(HomeKey, AnswersKey, SettingsKey)

/** Maps a persisted [TabIds] value back to its key, falling back to [HomeKey]. */
fun tabKeyOf(tabId: String?): TabNavKey = TAB_KEYS.firstOrNull { it.tabId == tabId } ?: HomeKey
