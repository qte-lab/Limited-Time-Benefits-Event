package com.chronie.gift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Thin wrapper around a Navigation 3 [NavBackStack] that offers the same vocabulary the old
 * `NavController` had.
 *
 * In Navigation 3 the back stack is an ordinary observable `MutableList` owned by the caller, so
 * every operation below is a plain list mutation. Keeping them in one place means call sites read
 * the same way they did before the migration, and the semantics are explicit instead of hidden
 * behind `NavOptions`.
 *
 * Mapping from the Navigation 2 API:
 * - `navigate(route)` -> [push]
 * - `popBackStack()` -> [pop]
 * - `popUpTo(route) { inclusive = ... }` -> [popTo]
 * - `popBackStack(startDestination, false)` -> [popToRoot]
 * - `launchSingleTop = true` -> `navigate(key, launchSingleTop = true)`
 */
@Stable
class GiftNavigator(val backStack: NavBackStack<NavKey>) {

    /** The destination currently displayed on top of the back stack. */
    val current: NavKey?
        get() = backStack.lastOrNull()

    /** True when [pop] would actually remove something. */
    val canPop: Boolean
        get() = backStack.size > 1

    /** Pushes [key] on top of the back stack. */
    fun push(key: NavKey) {
        backStack.add(key)
    }

    /**
     * Pops the top destination.
     *
     * Never empties the back stack: NavDisplay requires at least one entry, so the root
     * destination is kept and `false` is returned instead. The system back press is handled by
     * NavDisplay itself, which disables its back handler once only the root is left, letting the
     * press fall through to the Activity.
     */
    fun pop(): Boolean {
        if (!canPop) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /**
     * Pops until [key] is on top.
     *
     * @param inclusive when true [key] itself is popped as well.
     * @return false when [key] is not on the back stack, or when honoring the request would leave
     *   the back stack empty.
     */
    fun popTo(key: NavKey, inclusive: Boolean = false): Boolean {
        val index = backStack.lastIndexOf(key)
        if (index < 0) return false
        val newSize = if (inclusive) index else index + 1
        if (newSize < 1) return false
        while (backStack.size > newSize) {
            backStack.removeAt(backStack.lastIndex)
        }
        return true
    }

    /** Pops everything but the root destination. */
    fun popToRoot() {
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /** Replaces the top destination with [key] without growing the back stack. */
    fun replaceTop(key: NavKey) {
        if (backStack.isEmpty()) {
            backStack.add(key)
        } else {
            backStack[backStack.lastIndex] = key
        }
    }

    /**
     * Combined navigation entry point, mirroring `NavController.navigate(route) { ... }`.
     *
     * The order matches Navigation 2: [popUpTo] is applied first, then [launchSingleTop] is
     * evaluated against the resulting top of the stack.
     */
    fun navigate(
        key: NavKey,
        popUpTo: NavKey? = null,
        popUpToInclusive: Boolean = false,
        launchSingleTop: Boolean = false,
    ) {
        if (popUpTo != null) {
            popTo(popUpTo, popUpToInclusive)
        }
        if (launchSingleTop && current == key) return
        push(key)
    }
}

/**
 * Creates a [GiftNavigator] whose back stack survives configuration changes and process death.
 *
 * [rememberNavBackStack] serializes the keys through `kotlinx.serialization`, which is why every
 * [GiftNavKey] is annotated with `@Serializable`. [initial] is only used the very first time the
 * back stack is created; on restore the saved stack wins.
 */
@Composable
fun rememberGiftNavigator(vararg initial: NavKey): GiftNavigator {
    val backStack = rememberNavBackStack(*initial)
    return remember(backStack) { GiftNavigator(backStack) }
}
