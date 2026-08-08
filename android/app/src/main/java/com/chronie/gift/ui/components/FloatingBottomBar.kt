package com.chronie.gift.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.chronie.gift.ui.theme.liquid.DampedDragAnimation
import com.chronie.gift.ui.theme.liquid.InteractiveHighlight
import com.chronie.gift.ui.theme.liquid.InnerShadow
import com.chronie.gift.ui.theme.liquid.lens
import com.chronie.gift.ui.theme.liquid.rememberCombinedBackdrop
import com.chronie.gift.ui.theme.liquid.vibrancy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor

val LocalFloatingBottomBarContentColor = staticCompositionLocalOf { Color.Unspecified }
val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

// Composition locals for auto-width tab measurement
val LocalFloatingBottomBarOnTabMeasured = staticCompositionLocalOf<((Int, Float) -> Unit)?> { null }
val LocalFloatingBottomBarAutoWidth = staticCompositionLocalOf { false }

@Immutable
class FloatingBottomBarColors(
    val containerColor: Color,
    val indicatorColor: Color,
    val contentColor: Color,
    val activeContentColor: Color
)

object FloatingBottomBarDefaults {
    @Composable
    fun colors(
        containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        indicatorColor: Color = MiuixTheme.colorScheme.primary,
        contentColor: Color = MiuixTheme.colorScheme.onSurface,
        activeContentColor: Color = indicatorColor
    ): FloatingBottomBarColors = FloatingBottomBarColors(
        containerColor = containerColor,
        indicatorColor = indicatorColor,
        contentColor = contentColor,
        activeContentColor = activeContentColor
    )
}

enum class FloatingBottomBarMode {
    LiquidGlass,
    Blur,
    None
}

private val iosIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float = 0f,
): Highlight {
    val baseStyle = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val gMagSq = gx * gx + gy * gy
        val (lx0, ly0) = if (gMagSq > GRAVITY_DIR_THRESHOLD_SQ) {
            val invMag = 1f / sqrt(gMagSq)
            (gx * invMag) to (gy * invMag)
        } else {
            0f to -1f
        }
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val lx = c * lx0 - s * ly0
        val ly = s * lx0 + c * ly0
        basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + lx,
                y = LIGHT_REF_Y + ly,
                z = basePrimary.position.z,
            ),
        )
    }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tabIndex: Int = -1,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    val contentColor = LocalFloatingBottomBarContentColor.current
    val autoWidth = LocalFloatingBottomBarAutoWidth.current
    val onTabMeasured = LocalFloatingBottomBarOnTabMeasured.current

// Each item wraps its content width but fills the Row's height so that the
    // CircleShape clip doesn't shave off the bottom of the text. A minimum
    // width keeps the options from feeling cramped, even with short labels.
    // NOTE: do not add padding inside the item — the indicator's measurement
    // pipeline derives tab centers and pill width from the reported item
    // width, and inner padding desyncs it from the text position.
    Column(
    modifier
        .fillMaxHeight()
        .wrapContentWidth()
        .widthIn(min = 60.dp)
        .clip(CircleShape)
        .clickable(
            interactionSource = null,
            indication = null,
            role = Role.Tab,
            onClick = onClick
        )
        .then(
            if (autoWidth && tabIndex >= 0 && onTabMeasured != null) {
                Modifier.onGloballyPositioned { coords ->
                    onTabMeasured(tabIndex, coords.size.width.toFloat())
                }
            } else {
                Modifier
            }
        )
        .graphicsLayer {
            val s = scale()
            scaleX = s
            scaleY = s
        },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompositionLocalProvider(
            MiuixLocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}

@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    mode: FloatingBottomBarMode = FloatingBottomBarMode.LiquidGlass,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    autoWidth: Boolean = true,
    isTopMode: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val isInDark = MiuixTheme.colorSchemeMode == ColorSchemeMode.Dark
    val pillShape = remember { CircleShape }
    val isLiquidGlassMode = mode == FloatingBottomBarMode.LiquidGlass
    val isBlurMode = mode == FloatingBottomBarMode.Blur
    val containerColor =
        if (isLiquidGlassMode) colors.containerColor.copy(0.4f) else colors.containerColor

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    // Per-tab measured widths. Using mutableStateOf with a List so that
    // derivedStateOf reliably picks up changes.
    var tabMeasuredWidths by remember(tabsCount) {
        mutableStateOf(List(tabsCount) { 0f })
    }

    val onTabMeasured: (Int, Float) -> Unit = remember {
        { index, width ->
            if (index in tabMeasuredWidths.indices &&
                tabMeasuredWidths[index] != width && width > 0f
            ) {
                tabMeasuredWidths = tabMeasuredWidths.toMutableList().also {
                    it[index] = width
                }
            }
        }
    }

    // Bar dimensions — top mode is shorter (text-only), bottom mode is taller (icon + text)
    val barHeight = if (isTopMode) 48.dp else 64.dp
    val innerBarHeight = if (isTopMode) 40.dp else 56.dp
    val rowHorizontalPadding = 20.dp
    val rowPaddingPx = with(density) { rowHorizontalPadding.toPx() }
    val rowPaddingTotalPx = rowPaddingPx * 2f

    // Forward-reference for the drag value (set after dampedDragAnimation is created).
    val dragValueRef = remember { mutableFloatStateOf(0f) }

    // Tab centers and widths derived from measured widths + fixed gap.
    // Using spacedBy ensures the gap is always exactly `fixedGapPx`.
    val fixedGapPx = with(density) { 20.dp.toPx() }

    val allMeasured by remember {
        derivedStateOf {
            tabMeasuredWidths.size == tabsCount &&
                tabMeasuredWidths.all { it > 0f }
        }
    }

    // Tab centers (px, relative to the Row's content origin) computed from
    // measured widths and the fixed gap.
    val tabCentersPx: List<Float> by remember {
        derivedStateOf {
            if (allMeasured) {
                var cumulativeX = 0f
                tabMeasuredWidths.mapIndexed { i, w ->
                    val center = cumulativeX + w / 2f
                    cumulativeX += w + fixedGapPx
                    center
                }
            } else {
                emptyList()
            }
        }
    }

    // Extra horizontal padding on each side of the indicator pill so it doesn't
    // sit flush against the label text or icon.
    val indicatorExtraPadPx = with(density) { 12.dp.toPx() } // 6.dp per side

    // Indicator width = measured content width + extra padding on both sides.
    val indicatorWidthPx: Float by remember {
        derivedStateOf {
            if (tabMeasuredWidths.isEmpty() || tabMeasuredWidths.any { it <= 0f }) {
                72f + 2f * indicatorExtraPadPx
            } else {
                val v = dragValueRef.floatValue
                val lastIndex = tabMeasuredWidths.lastIndex
                val idx = v.toInt().coerceIn(0, lastIndex)
                val nextIdx = (idx + 1).coerceAtMost(lastIndex)
                val fraction = (v - idx).coerceIn(0f, 1f)
                val contentW = lerp(
                    tabMeasuredWidths.getOrElse(idx) { 72f },
                    tabMeasuredWidths.getOrElse(nextIdx) { 72f },
                    fraction
                )
                contentW + 2f * indicatorExtraPadPx
            }
        }
    }

    // Indicator center X (within the Row's content area, i.e. from the left padding edge).
    val indicatorCenterX: Float by remember {
        derivedStateOf {
            val centers = tabCentersPx
            if (centers.isEmpty()) {
                // Pre-measurement fallback: evenly distribute by index.
                val fallback = 72f
                val v = dragValueRef.floatValue
                rowPaddingPx + v * (fallback + fixedGapPx) + fallback / 2f
            } else {
                val v = dragValueRef.floatValue
                val lastIndex = centers.lastIndex
                val idx = v.toInt().coerceIn(0, lastIndex)
                val nextIdx = (idx + 1).coerceAtMost(lastIndex)
                val fraction = (v - idx).coerceIn(0f, 1f)
                val c0 = centers.getOrElse(idx) { 0f }
                val c1 = centers.getOrElse(nextIdx) { 0f }
                rowPaddingPx + lerp(c0, c1, fraction)
            }
        }
    }

    // Average tab width for drag sensitivity.
    val avgTabWidthPx: Float by remember {
        derivedStateOf {
            if (tabMeasuredWidths.isNotEmpty() && tabMeasuredWidths.all { it > 0f }) {
                tabMeasuredWidths.average().toFloat()
            } else {
                72f + fixedGapPx
            }
        }
    }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset: Float by remember(rubberBandPx, totalWidthPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex()) }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation = remember(
        animationScope, tabsCount, density, isLtr, avgTabWidthPx
    ) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (avgTabWidthPx == 0f) return@DampedDragAnimation false

                val currentValue = anim.value
                val centers = tabCentersPx
                val indicatorCenter = if (centers.isEmpty()) {
                    rowPaddingPx + currentValue * (72f + fixedGapPx) + 72f / 2f
                } else {
                    rowPaddingPx + centers.getOrElse(currentValue.toInt()) { 0f }
                }
                val globalTouchX = if (isLtr) {
                    indicatorCenter + offset.x
                } else {
                    totalWidthPx - indicatorCenter + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (avgTabWidthPx > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / avgTabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    // Keep dragValueRef in sync with the actual drag animation value.
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { dampedDragAnimation.value }
            .collectLatest { dragValueRef.floatValue = it }
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }.collectLatest { currentIndex = it }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    val interactiveHighlight =
        if (isLiquidGlassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remember(animationScope, avgTabWidthPx) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        val centers = tabCentersPx
                        val center = if (centers.isEmpty()) {
                            val v = dampedDragAnimation.value
                            rowPaddingPx + v * (72f + fixedGapPx) + 72f / 2f
                        } else {
                            rowPaddingPx + centers.getOrElse(
                                dampedDragAnimation.value.toInt()
                            ) { 0f }
                        }
                        Offset(
                            if (isLtr) center + panelOffset
                            else size.width - center + panelOffset,
                            size.height / 2f
                        )
                    }
                )
            }
        } else {
            null
        }

    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)

    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    // Outer Box — fills the width requested by the caller (e.g. 80% or wrapContent)
    // and centers the inner content so that when the bar is wider than its tabs,
    // the tabs are visually centered rather than left-aligned.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Inner Box — matches the actual content width so that translationX
        // values used for indicator positioning are absolute (relative to the
        // left edge of this box).
        Box(
            modifier = Modifier.wrapContentWidth(),
            contentAlignment = Alignment.CenterStart
        ) {

        // ---- Main Row (content layer) ----
        CompositionLocalProvider(
            LocalFloatingBottomBarContentColor provides colors.contentColor,
            LocalFloatingBottomBarAutoWidth provides autoWidth,
            LocalFloatingBottomBarOnTabMeasured provides onTabMeasured,
        ) {
            Row(
                Modifier
                    .wrapContentWidth()
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isInDark) 0.2f else 0.1f,
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .then(
                        if (isLiquidGlassMode) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    lens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx(),
                                    )
                                },
                                highlight = { baseHighlight.copy(alpha = 0.75f) },
                                layerBlock = {
                                    val width = size.width.coerceAtLeast(1f)
                                    val s = lerp(
                                        1f,
                                        1f + 16.dp.toPx() / width,
                                        dampedDragAnimation.pressProgress
                                    )
                                    scaleX = s
                                    scaleY = s
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                        } else if (isBlurMode) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    blur(25.dp.toPx(), 25.dp.toPx())
                                },
                                onDrawSurface = {
                                    drawRect(containerColor.copy(alpha = 0.65f))
                                },
                            )
                        } else {
                            Modifier.background(containerColor, pillShape)
                        }
                    )
                    .then(
                        if (isLiquidGlassMode && interactiveHighlight != null) {
                            interactiveHighlight.modifier
                        } else {
                            Modifier
                        }
                    )
                    .height(barHeight)
                    .padding(horizontal = rowHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                content = content
            )
        }

        // ---- Glass overlay layer (liquid glass mode only) ----
        if (isLiquidGlassMode) {
            CompositionLocalProvider(
                LocalFloatingBottomBarTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                },
                LocalFloatingBottomBarContentColor provides colors.activeContentColor,
                LocalFloatingBottomBarAutoWidth provides autoWidth,
                LocalFloatingBottomBarOnTabMeasured provides null,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(
                                    refractionHeight = 24.dp.toPx(),
                                    refractionAmount = 24.dp.toPx(),
                                )
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight?.modifier ?: Modifier)
                        .height(innerBarHeight)
                        .padding(horizontal = rowHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    content()
                }
            }
        }

        // ---- Indicator pill ----
        if (totalWidthPx > 0f) {
            val indicatorWidthDp = with(density) { indicatorWidthPx.toDp() }

            if (isLiquidGlassMode) {
                Box(
                    Modifier
                        .graphicsLayer {
                            // Position the pill so it's centered on the selected tab.
                            translationX = if (isLtr) {
                                indicatorCenterX - indicatorWidthPx / 2f + panelOffset
                            } else {
                                -(indicatorCenterX - indicatorWidthPx / 2f) + panelOffset
                            }
                        }
                        .then(interactiveHighlight?.gestureModifier ?: Modifier)
                        .then(dampedDragAnimation.modifier)
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    refractionHeight = 10.dp.toPx() * progress,
                                    refractionAmount = 14.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = {
                                pillHighlight.copy(alpha = dampedDragAnimation.pressProgress)
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = if (!isInDark) Color.Black.copy(alpha = 0.1f)
                                    else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = 8.dp * dampedDragAnimation.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDragAnimation.pressProgress,
                            )
                        }
                        .height(innerBarHeight)
                        .width(indicatorWidthDp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = if (isLtr) {
                                indicatorCenterX - indicatorWidthPx / 2f + panelOffset
                            } else {
                                -(indicatorCenterX - indicatorWidthPx / 2f) + panelOffset
                            }
                        }
                        .then(dampedDragAnimation.modifier)
                        .clip(pillShape)
                        .background(colors.indicatorColor.copy(alpha = 0.15f), pillShape)
                        .height(innerBarHeight)
                        .width(indicatorWidthDp)
                )
            }
        }
    }
}
}