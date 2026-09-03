package com.chronie.gift.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutElastic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronie.gift.R
import com.chronie.gift.data.FoodItem
import com.chronie.gift.data.FoodStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Sector colours, cycled when the menu has more than eight entries.
 *
 * Each pair is the (inner, outer) stop of the radial gradient used to fill the
 * sector, matching the eight `LinearGradient`s of the original wheel painter.
 */
private val WHEEL_COLORS: List<Pair<Color, Color>> = listOf(
    Color(0xFFEF5350) to Color(0xFFC62828), // red
    Color(0xFFFFA726) to Color(0xFFEF6C00), // orange
    Color(0xFFFFEE58) to Color(0xFFF9A825), // yellow
    Color(0xFF66BB6A) to Color(0xFF2E7D32), // green
    Color(0xFF42A5F5) to Color(0xFF1565C0), // blue
    Color(0xFFAB47BC) to Color(0xFF6A1B9A), // purple
    Color(0xFFEC407A) to Color(0xFFAD1457), // pink
    Color(0xFF26A69A) to Color(0xFF00695C), // teal
)

/**
 * The "今天吃什么" tab: a weighted spinning wheel.
 *
 * Ported from `flutter_app/lib/pages/food_page.dart`. The Flutter version kept
 * its food management in a dialog opened from a floating button on this page;
 * that dialog is now a second-level page reached from the Settings tab, so this
 * screen only spins the wheel and shows the result.
 */
@Composable
fun FoodScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        FoodStore.ensureLoaded(context)
    }

    val items = FoodStore.items

    // Cumulative rotation in degrees. Kept absolute (never reset) so consecutive
    // spins keep turning clockwise instead of snapping back to zero.
    val rotation = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var selectedFood by remember { mutableStateOf<FoodItem?>(null) }

    val emptyHint = stringResource(id = R.string.food_empty_toast)

    val spin = {
        if (!isSpinning) {
            val picked = FoodStore.randomFood()
            if (picked == null) {
                Toast.makeText(context, emptyHint, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    isSpinning = true
                    selectedFood = picked
                    val target = FoodStore.targetRotation(
                        current = rotation.value.toDouble(),
                        picked = picked,
                        items = items.toList()
                    )
                    rotation.animateTo(
                        target.toFloat(),
                        tween(durationMillis = 3000, easing = EaseOutElastic)
                    )
                    isSpinning = false
                }
            }
        }
    }

    val buttonText = when {
        isSpinning -> stringResource(id = R.string.food_spinning)
        selectedFood != null -> stringResource(id = R.string.food_spin_again)
        else -> stringResource(id = R.string.food_spin)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(title = stringResource(id = R.string.tab_food))
        }
    ) { paddingValues ->
        // The floating bottom bar lives in the *outer* GiftApp Scaffold and is
        // not counted by this screen's own inner Scaffold paddingValues (it is
        // only shown on narrow screens; wide screens use a top bar instead).
        // Reserve its height so the spin button / result card are not occluded.
        val isWide = LocalConfiguration.current.screenWidthDp >= 600
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(
                    if (!isWide) {
                        Modifier.navigationBarsPadding().padding(bottom = 80.dp)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Wheel(
                    items = items.toList(),
                    rotation = rotation.value,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!isSpinning) {
                if (selectedFood != null) {
                    ResultCard(food = selectedFood!!)
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (items.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.food_empty_hint),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            Button(
                onClick = spin,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSpinning
            ) {
                Text(
                    text = buttonText,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * The wheel plus its fixed pointer.
 *
 * [rotation] is the absolute angle in degrees the disc has been turned; the
 * pointer itself never moves, which is what makes the wheel "land" on a dish.
 * The sector fills are painted on a [Canvas] while the dish names are regular
 * [Text] composables rotated into place, which keeps ellipsis, font scaling and
 * shadow rendering consistent with the rest of the app.
 */
@Composable
private fun Wheel(
    items: List<FoodItem>,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val wheelSize: Dp = if (isWide) 300.dp else 240.dp
    val backdropSize: Dp = wheelSize + 40.dp
    val labelFontSize: TextUnit = if (isWide) 15.sp else 13.sp
    val emptyLabel = stringResource(id = R.string.food_empty_wheel)

    Box(
        modifier = modifier.size(backdropSize),
        contentAlignment = Alignment.Center
    ) {
        // Soft disc behind the wheel, replacing the Flutter glass container.
        Box(
            modifier = Modifier
                .size(backdropSize)
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Everything that turns with the disc.
        Box(
            modifier = Modifier
                .size(wheelSize)
                .rotate(rotation),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawSectors(items)
            }

            if (items.isNotEmpty()) {
                val total = items.sumOf { it.weight }.takeIf { it > 0.0 } ?: 1.0
                val labelRadius = wheelSize * 0.33f
                val wheelRadius = wheelSize / 2f
                var startAngle = -90f

                items.forEach { food ->
                    val sweep = (360f * (food.weight / total)).toFloat().coerceAtLeast(0.5f)
                    SectorLabel(
                        name = food.name,
                        midAngle = startAngle + sweep / 2f,
                        sweep = sweep,
                        labelRadius = labelRadius,
                        wheelRadius = wheelRadius,
                        baseFontSize = labelFontSize
                    )
                    startAngle += sweep
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                text = emptyLabel,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
        }

        // Pointer: fixed at 12 o'clock, the disc rotates underneath it. The
        // container is the same size as the disc and centred on it, so a child
        // aligned to its top edge lands exactly on the rim of the wheel.
        Box(
            modifier = Modifier.size(wheelSize),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.035f)
                    .fillMaxHeight(0.15f)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFEF5350), Color(0xFFC62828))
                        ),
                        shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(wheelSize * 0.17f)
                .background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFEF5350), Color(0xFFC62828))
                    ),
                    shape = CircleShape
                )
        )
    }
}

/**
 * One dish name, rotated so it reads radially (along the spoke) inside its sector,
 * exactly like the original Flutter wheel: `canvas.rotate(midAngle)` followed by a
 * horizontal [Text] makes the text run along the radius.
 *
 * [midAngle] is measured clockwise from 3 o'clock, the same convention the sectors
 * are painted with. The font auto-shrinks to fit inside both the wedge's arc
 * (tangential room) and the available radius (radial room), so even the thinnest
 * sector stays a single line that never bleeds into a neighbour.
 */
@Composable
private fun SectorLabel(
    name: String,
    midAngle: Float,
    sweep: Float,
    labelRadius: Dp,
    wheelRadius: Dp,
    baseFontSize: TextUnit
) {
    val radians = Math.toRadians(midAngle.toDouble())

    // Tangential room available at this radius for this wedge (the text height
    // after radial rotation must fit the arc).
    val arcPx = (2.0 * Math.PI * labelRadius.value * (sweep / 360f)).toFloat()
    // Radial room available for the text length (keep it inside the disc).
    val radialRoomPx = min(labelRadius.value, (wheelRadius.value - labelRadius.value))
        .coerceAtLeast(8f)
    val fontByArc = arcPx / 1.3f
    val fontByRadial = radialRoomPx / (name.length.coerceAtLeast(1) * 0.62f)
    val fontSize = min(baseFontSize.value, min(fontByArc, fontByRadial))
        .coerceAtLeast(8f)
        .sp

    Box(
        modifier = Modifier
            .offset(
                x = labelRadius * cos(radians).toFloat(),
                y = labelRadius * sin(radians).toFloat()
            )
            .rotate(midAngle),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MiuixTheme.textStyles.body2.copy(
                shadow = Shadow(color = Color.Black, blurRadius = 3f, offset = Offset(1f, 1f))
            )
        )
    }
}

/**
 * Paints one weighted sector per dish plus the borders.
 *
 * Sector 0 starts at 12 o'clock (-90 degrees) and sweeps clockwise, which is
 * the same convention [com.chronie.gift.data.FoodStore.targetRotation] uses to
 * work out where the wheel has to stop.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSectors(items: List<FoodItem>) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f
    val borderWidth = 4.dp.toPx()

    if (items.isEmpty()) {
        drawCircle(color = Color(0xFFBDBDBD), radius = radius)
        drawCircle(
            color = Color.Black,
            radius = radius - borderWidth / 2f,
            style = Stroke(borderWidth)
        )
        return
    }

    val totalWeight = items.sumOf { it.weight }.takeIf { it > 0.0 } ?: 1.0
    var startAngle = -90f

    items.forEachIndexed { index, food ->
        val sweep = (360f * (food.weight / totalWeight)).toFloat().coerceAtLeast(0.5f)
        val (inner, outer) = WHEEL_COLORS[index % WHEEL_COLORS.size]

        drawArc(
            brush = Brush.radialGradient(
                colors = listOf(inner, outer),
                center = center,
                radius = radius
            ),
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = true
        )

        // Divider between neighbouring sectors
        val edge = Math.toRadians(startAngle.toDouble())
        drawLine(
            color = Color.White,
            start = center,
            end = center + Offset(radius * cos(edge).toFloat(), radius * sin(edge).toFloat()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        startAngle += sweep
    }

    drawCircle(
        color = Color.Black,
        radius = radius - borderWidth / 2f,
        style = Stroke(borderWidth)
    )
    drawCircle(
        color = Color(0xFFFFD54F),
        radius = radius - borderWidth - 1.dp.toPx(),
        style = Stroke(1.dp.toPx())
    )
}

/** The winning dish, shown once the wheel has come to a stop. */
@Composable
private fun ResultCard(food: FoodItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.food_recommend),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = food.name,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (food.category.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = food.category,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
}
