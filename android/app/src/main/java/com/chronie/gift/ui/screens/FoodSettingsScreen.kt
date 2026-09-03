package com.chronie.gift.ui.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chronie.gift.R
import com.chronie.gift.data.FoodItem
import com.chronie.gift.data.FoodStore
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Second-level page for the "今天吃什么" wheel, opened from the Settings tab.
 *
 * Replaces the `_showFoodManagementDialog` bottom sheet style dialog from
 * `flutter_app/lib/pages/food_page.dart`: same two panes (add a dish / list all
 * dishes), but pushed onto the app back stack so it gets the standard top app
 * bar, back gesture and screen transitions every other sub page in the app has.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun FoodSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        FoodStore.ensureLoaded(context)
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // The dish pending deletion — non-null only while the confirmation dialog is shown.
    var pendingDeleteFood by remember { mutableStateOf<FoodItem?>(null) }

    val tabs = listOf(
        stringResource(id = R.string.food_add_tab),
        stringResource(id = R.string.food_list_tab)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.food_manage_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // The floating bottom bar lives in the *outer* GiftApp Scaffold and is
        // not counted by this screen's own inner Scaffold paddingValues; it is
        // only shown on narrow screens (< 600dp), where a top bar is used instead.
        // Reserve its height so the tab row / last list item are not occluded.
        val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isWideScreen) {
                        Modifier.navigationBarsPadding().padding(bottom = 80.dp)
                    } else {
                        Modifier
                    }
                )
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // `TabRowWithContour` slides its indicator with an explicit tween (the
            // plain TabRow only moves when the inner LazyRow scrolls, which is
            // imperceptible with two tabs). The horizontal padding keeps the pill
            // off the screen edges, matching the panes below it.
            TabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp
                val horizontalPadding = if (isWideScreen) Modifier.fillMaxWidth(0.8f) else Modifier.fillMaxWidth()

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (isWideScreen) Alignment.TopCenter else Alignment.TopStart
                ) {
                    if (selectedTab == 0) {
                        AddFoodPane(modifier = horizontalPadding)
                    } else {
                        FoodListPane(modifier = horizontalPadding, onDelete = { pendingDeleteFood = it })
                    }
                }
            }

            // Second confirmation before a dish is removed.
            if (pendingDeleteFood != null) {
                val dismiss = LocalDismissState.current
                val foodName = pendingDeleteFood!!.name
                WindowDialog(
                    title = stringResource(id = R.string.food_delete_confirm_title),
                    summary = stringResource(id = R.string.food_delete_confirm_summary).format(foodName),
                    show = true,
                    onDismissRequest = {
                        dismiss?.invoke()
                        pendingDeleteFood = null
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = stringResource(id = R.string.food_cancel),
                            onClick = {
                                dismiss?.invoke()
                                pendingDeleteFood = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = stringResource(id = R.string.food_delete),
                            colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(
                                textColor = androidx.compose.ui.graphics.Color(0xFFE53935)
                            ),
                            onClick = {
                                FoodStore.delete(context, pendingDeleteFood!!.id)
                                dismiss?.invoke()
                                pendingDeleteFood = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Pane 1: type a dish name and a weight, then append it to the wheel. */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun AddFoodPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(TextFieldValue("")) }
    var weight by remember { mutableStateOf(TextFieldValue("1.0")) }

    val nameEmptyError = stringResource(id = R.string.food_name_empty)
    val addedTemplate = stringResource(id = R.string.food_added)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SmallTitle(text = stringResource(id = R.string.food_add_title))

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(id = R.string.food_name_label),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = weight,
            onValueChange = { weight = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(id = R.string.food_weight_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Text(
            text = stringResource(id = R.string.food_weight_helper),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val trimmed = name.text.trim()
                if (trimmed.isEmpty()) {
                    Toast.makeText(context, nameEmptyError, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val parsed = weight.text.trim().toDoubleOrNull()
                val finalWeight = if (parsed == null || parsed <= 0.0) 1.0 else parsed
                FoodStore.add(context, trimmed, finalWeight)
                Toast.makeText(context, String.format(addedTemplate, trimmed), Toast.LENGTH_SHORT).show()
                name = TextFieldValue("")
                weight = TextFieldValue("1.0")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(id = R.string.food_add),
                style = MiuixTheme.textStyles.body1,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}

/** Pane 2: every dish currently on the wheel, with its weight and a delete action. */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun FoodListPane(
    modifier: Modifier = Modifier,
    onDelete: (FoodItem) -> Unit = {}
) {
    val context = LocalContext.current
    // Named `foods` rather than `items`: the LazyColumn DSL function is also
    // called `items`, and a local of the same name would shadow it.
    val foods = FoodStore.items
    val restoredToast = stringResource(id = R.string.food_restore_done)
    val weightTemplate = stringResource(id = R.string.food_weight_format)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
    ) {
        if (foods.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.food_empty_list),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )
                }
            }
        } else {
            item {
                SmallTitle(text = stringResource(id = R.string.food_list_tab))
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(foods, key = { it.id }) { food ->
                Card(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = food.name,
                                style = MiuixTheme.textStyles.body1
                            )
                            Text(
                                text = String.format(weightTemplate, formatWeight(food.weight)),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                            )
                        }
                        IconButton(onClick = { onDelete(food) }) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = stringResource(id = R.string.food_delete),
                                tint = MiuixTheme.colorScheme.onSurfaceContainerVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    text = stringResource(id = R.string.food_restore_default),
                    onClick = {
                        FoodStore.restoreDefaults(context)
                        Toast.makeText(context, restoredToast, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

/** Trims a weight to at most two decimals so "1.0" does not render as "1.000000". */
private fun formatWeight(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
