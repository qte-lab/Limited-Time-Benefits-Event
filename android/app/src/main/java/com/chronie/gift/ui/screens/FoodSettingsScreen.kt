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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Second-level page for the "今天吃什么" wheel, opened from the Settings tab.
 *
 * Replaces the `_showFoodManagementDialog` bottom sheet style dialog from
 * `flutter_app/lib/pages/food_page.dart`: same two panes (add a dish / list all
 * dishes), but pushed onto the app back stack so it gets the standard top app
 * bar, back gesture and screen transitions every other sub page in the app has.
 *
 * The top bar uses a large title that collapses into the small one as the pane
 * below is pulled up; the tab row is pinned in the bar's `bottomContent` so it
 * stays reachable while the large title folds away.
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

    val scrollBehavior = MiuixScrollBehavior()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Both dialogs stay composed until their exit animation finishes, so each one
    // is driven by two pieces of state: the dish being acted on (kept alive until
    // `onDismissFinished`, which is when the dialog finally leaves the
    // composition) and a separate `show` flag that starts the hide animation as
    // soon as it flips to false.
    //
    // The dish pending deletion — non-null while the confirmation dialog is on screen.
    var pendingDeleteFood by remember { mutableStateOf<FoodItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // The dish being edited, plus the draft values backing the edit dialog. They
    // live here rather than inside the dialog so the dialog can be dismissed
    // (and even recomposed during the dismiss animation) without losing input.
    var editingFood by remember { mutableStateOf<FoodItem?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(TextFieldValue("")) }
    var editWeight by remember { mutableStateOf(TextFieldValue("")) }
    var editPrice by remember { mutableStateOf(TextFieldValue("")) }
    // Per-meal budget used to turn a price into a weight. Kept at screen level
    // so it carries over from one dish to the next within this session.
    var budget by remember { mutableStateOf(TextFieldValue(FoodStore.DEFAULT_BUDGET.formatInput())) }

    val manageTitle = stringResource(id = R.string.food_manage_title)
    val tabs = listOf(
        stringResource(id = R.string.food_add_tab),
        stringResource(id = R.string.food_list_tab)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = manageTitle,
                largeTitle = manageTitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                bottomContent = {
                    // `TabRowWithContour` slides its indicator with an explicit tween (the
                    // plain TabRow only moves when the inner LazyRow scrolls, which is
                    // imperceptible with two tabs). Hosting it in `bottomContent` pins it
                    // under the title: it rides up with the collapsing bar instead of
                    // scrolling off with the pane, and the Scaffold's top padding already
                    // accounts for its height.
                    TabRowWithContour(
                        tabs = tabs,
                        selectedTabIndex = selectedTab,
                        onTabSelected = { selectedTab = it },
                        modifier = Modifier
                            .padding(horizontal = TopAppBarDefaults.TitlePadding)
                            .padding(bottom = 12.dp)
                    )
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
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp
                val horizontalPadding = if (isWideScreen) Modifier.fillMaxWidth(0.8f) else Modifier.fillMaxWidth()

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (isWideScreen) Alignment.TopCenter else Alignment.TopStart
                ) {
                    if (selectedTab == 0) {
                        AddFoodPane(
                            modifier = horizontalPadding,
                            scrollBehavior = scrollBehavior
                        )
                    } else {
                        FoodListPane(
                            modifier = horizontalPadding,
                            scrollBehavior = scrollBehavior,
                            onDelete = {
                                pendingDeleteFood = it
                                showDeleteDialog = true
                            },
                            onEdit = { food ->
                                editingFood = food
                                editName = TextFieldValue(food.name)
                                editWeight = TextFieldValue(food.weight.formatInput())
                                // Left blank on purpose: a weight alone does not
                                // identify a price (the dish may have been added
                                // with a hand typed weight), so guessing one
                                // would present a made-up number as if it were
                                // the user's own.
                                editPrice = TextFieldValue("")
                                showEditDialog = true
                            }
                        )
                    }
                }
            }

            // Second confirmation before a dish is removed.
            if (pendingDeleteFood != null) {
                val foodName = pendingDeleteFood!!.name
                WindowDialog(
                    title = stringResource(id = R.string.food_delete_confirm_title),
                    summary = stringResource(id = R.string.food_delete_confirm_summary).format(foodName),
                    show = showDeleteDialog,
                    onDismissRequest = { showDeleteDialog = false },
                    onDismissFinished = { pendingDeleteFood = null }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = stringResource(id = R.string.food_cancel),
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = stringResource(id = R.string.food_delete),
                            colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(
                                textColor = androidx.compose.ui.graphics.Color(0xFFE53935)
                            ),
                            onClick = {
                                // Snapshot the id: the row is already gone from
                                // the list by the time the exit animation ends.
                                pendingDeleteFood?.let { FoodStore.delete(context, it.id) }
                                showDeleteDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Edit an existing dish: rename it, retype the weight, or let a price
            // work the weight out.
            if (editingFood != null) {
                val dish = editingFood!!
                val emptyNameError = stringResource(id = R.string.food_name_empty)
                val badWeightError = stringResource(id = R.string.food_weight_invalid)
                val savedTemplate = stringResource(id = R.string.food_saved)

                /** Recomputes the weight from price ÷ budget, if both are usable. */
                val recomputeWeight = { price: String, money: String ->
                    val p = price.trim().toDoubleOrNull()
                    val b = money.trim().toDoubleOrNull()
                    if (p != null && b != null) {
                        FoodStore.weightFromPrice(p, b)?.let { editWeight = TextFieldValue(it.formatInput()) }
                    }
                }

                WindowDialog(
                    title = stringResource(id = R.string.food_edit_title),
                    show = showEditDialog,
                    onDismissRequest = { showEditDialog = false },
                    onDismissFinished = { editingFood = null }
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        TextField(
                            value = editName,
                            onValueChange = { editName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(id = R.string.food_name_label),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(
                                value = editPrice,
                                onValueChange = {
                                    editPrice = it
                                    recomputeWeight(it.text, budget.text)
                                },
                                modifier = Modifier.weight(1f),
                                label = stringResource(id = R.string.food_price_label),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            TextField(
                                value = budget,
                                onValueChange = {
                                    budget = it
                                    recomputeWeight(editPrice.text, it.text)
                                },
                                modifier = Modifier.weight(1f),
                                label = stringResource(id = R.string.food_budget_label),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }

                        Text(
                            text = stringResource(id = R.string.food_price_helper)
                                .format(FoodStore.MAX_WEIGHT.formatInput()),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            value = editWeight,
                            onValueChange = { editWeight = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(id = R.string.food_weight_label),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                text = stringResource(id = R.string.food_cancel),
                                onClick = { showEditDialog = false },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                text = stringResource(id = R.string.food_save),
                                onClick = {
                                    val trimmed = editName.text.trim()
                                    if (trimmed.isEmpty()) {
                                        Toast.makeText(context, emptyNameError, Toast.LENGTH_SHORT).show()
                                        return@TextButton
                                    }
                                    val parsed = editWeight.text.trim().toDoubleOrNull()
                                    if (parsed == null || parsed <= 0.0) {
                                        Toast.makeText(context, badWeightError, Toast.LENGTH_SHORT).show()
                                        return@TextButton
                                    }
                                    FoodStore.update(context, dish.id, trimmed, parsed)
                                    Toast.makeText(context, savedTemplate.format(trimmed), Toast.LENGTH_SHORT).show()
                                    showEditDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pane 1: type a dish name and a weight, then append it to the wheel. */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun AddFoodPane(
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(TextFieldValue("")) }
    var weight by remember { mutableStateOf(TextFieldValue("1.0")) }

    val nameEmptyError = stringResource(id = R.string.food_name_empty)
    val addedTemplate = stringResource(id = R.string.food_added)

    Column(
        modifier = modifier
            .fillMaxSize()
            // Scrolling the pane is what collapses the large title; without the
            // nestedScroll link the bar would never receive the gesture.
            .nestedScrollIf(scrollBehavior)
            .verticalScroll(rememberScrollState())
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

/** Pane 2: every dish currently on the wheel, with its weight and edit/delete actions. */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun FoodListPane(
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    onDelete: (FoodItem) -> Unit = {},
    onEdit: (FoodItem) -> Unit = {}
) {
    val context = LocalContext.current
    // Named `foods` rather than `items`: the LazyColumn DSL function is also
    // called `items`, and a local of the same name would shadow it.
    val foods = FoodStore.items
    val restoredToast = stringResource(id = R.string.food_restore_done)
    val weightTemplate = stringResource(id = R.string.food_weight_format)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            // Feeds the pull gesture to the top bar so the large title collapses.
            .nestedScrollIf(scrollBehavior),
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
                        Row {
                            IconButton(onClick = { onEdit(food) }) {
                                Icon(
                                    imageVector = MiuixIcons.Edit,
                                    contentDescription = stringResource(id = R.string.food_edit),
                                    tint = MiuixTheme.colorScheme.onSurfaceContainerVariant
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

/** Applies [behavior]'s nested scroll connection, or does nothing when there is none. */
private fun Modifier.nestedScrollIf(behavior: ScrollBehavior?): Modifier =
    if (behavior == null) this else this.nestedScroll(behavior.nestedScrollConnection)

/** Trims a weight to at most two decimals so "1.0" does not render as "1.000000". */
private fun formatWeight(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/** Same trimming as [formatWeight], used for text that has to parse back into a double. */
private fun Double.formatInput(): String = formatWeight(this)
