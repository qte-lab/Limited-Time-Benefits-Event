package com.chronie.gift.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import androidx.compose.ui.unit.toIntSize
import com.chronie.gift.R
import com.chronie.gift.data.FoodItem
import com.chronie.gift.data.FoodStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.core.content.FileProvider
import java.io.File
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
 * The "What to eat" tab: a weighted spinning wheel.
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
    val shareTitle = stringResource(id = R.string.share_title)
    val shareFailedText = stringResource(id = R.string.share_failed)
    val shareContentDesc = stringResource(id = R.string.food_share)

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

    val localView = LocalView.current
    val graphicsLayer = rememberGraphicsLayer()

    /**
     * Shares a PNG snapshot of the page through the system share sheet.
     *
     * The page is rendered behind miuix's liquid-glass blur pipeline, which is
     * entirely GPU-bound (RenderEffect / AGSL RuntimeShader). The two "obvious"
     * screenshot paths both break on it: [androidx.core.view.drawToBitmap] forces
     * the view tree onto a software canvas where those shaders can't render (the
     * capture silently fails or throws), and [android.view.PixelCopy] from the
     * activity window returns ERROR_UNKNOWN whenever the surface doesn't line up
     * with the edge-to-edge decor view.
     *
     * The fix is to snapshot the page's own [GraphicsLayer] offscreen via
     * [GraphicsLayer.toImageBitmap], which renders through a HardwareRenderer and
     * so keeps every effect intact. The layer is recorded every frame by the
     * [Modifier.drawWithContent] applied to the Scaffold below. The legacy paths
     * stay as fallbacks so a capture still lands on devices where the GraphicsLayer
     * snapshot is unavailable.
     */
    val share: () -> Unit = {
        scope.launch {
            val reasons = mutableListOf<String>()
            val bitmap = captureShareBitmap(
                graphicsLayer = graphicsLayer,
                localView = localView,
                activity = context as? Activity,
                reasons = reasons,
            )
            Log.e("FoodShare", "capture result: ${if (bitmap != null) "${bitmap.width}x${bitmap.height} config=${bitmap.config}" else "null"} reasons=$reasons")
            if (bitmap == null) {
                val msg = reasons.joinToString(" | ").ifBlank { "未知原因" }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "截图失败: $msg", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            try {
                val file = File(context.cacheDir, "today_food_share.png")
                withContext(Dispatchers.IO) {
                    // GraphicsLayer.toImageBitmap() returns a Picture-backed
                    // hardware bitmap whose Picture.draw re-executes the layer's
                    // recorded commands. Drawing that bitmap onto a software
                    // Canvas (the old approach) therefore re-runs the layer draw
                    // on a software canvas and throws
                    // "software rendering does not support RenderEffect" because
                    // the recorded content includes miuix blur effects. Use
                    // Bitmap.copy() instead, which is HardwareRenderer-backed and
                    // never touches a software canvas. If copy itself fails, we
                    // fall through to direct compress (which works for ordinary
                    // ARGB_8888 bitmaps returned by PixelCopy).
                    val writable = if (bitmap.config == Bitmap.Config.HARDWARE) {
                        Log.e("FoodShare", "bitmap is HARDWARE, copying to ARGB_8888 via Bitmap.copy")
                        runCatching {
                            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        }.onFailure {
                            Log.e("FoodShare", "Bitmap.copy failed", it)
                        }.getOrNull()
                    } else bitmap
                    if (writable == null) {
                        throw IllegalStateException("无法转换 hardware bitmap 为可压缩格式")
                    }
                    file.outputStream().use { out ->
                        if (!writable.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw IllegalStateException("PNG 压缩失败 (config=${writable.config})")
                        }
                    }
                    if (writable !== bitmap) writable.recycle()
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                Log.e("FoodShare", "uri=$uri, file.size=${file.length()}")
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // LocalContext.current may not be an Activity under miuix's
                // context wrappers, and startActivity() from a non-Activity
                // context throws "Calling startActivity() from outside of an
                // Activity". Use the Activity explicitly when available; the
                // chooser itself also needs FLAG_ACTIVITY_NEW_TASK so it can be
                // launched from a non-Activity context as a fallback.
                val chooser = Intent.createChooser(shareIntent, shareTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val activity = context as? Activity
                withContext(Dispatchers.Main) {
                    if (activity != null) {
                        activity.startActivity(chooser)
                    } else {
                        context.startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                Log.e("FoodShare", "share failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "分享失败: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    val buttonText = when {
        isSpinning -> stringResource(id = R.string.food_spinning)
        selectedFood != null -> stringResource(id = R.string.food_spin_again)
        else -> stringResource(id = R.string.food_spin)
    }

    val buttonIcon = when {
        isSpinning -> MiuixIcons.Refresh
        selectedFood != null -> MiuixIcons.MindMap
        else -> MiuixIcons.Help
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                // Record the page into `graphicsLayer` every frame so a share can
                // snapshot it offscreen, where the GPU blur effects still render.
                graphicsLayer.record(size.toIntSize()) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            },
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.tab_food),
                actions = {
                    IconButton(onClick = share) {
                        Icon(
                            imageVector = MiuixIcons.Forward,
                            contentDescription = shareContentDesc
                        )
                    }
                }
            )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold
                    )
                }
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

/**
 * Produces a PNG-ready bitmap of the page, trying the capture paths in order of
 * fidelity: an offscreen [GraphicsLayer] snapshot first (keeps the GPU liquid-glass
 * blur effects intact), then [PixelCopy] of the activity window, then a software
 * [androidx.core.view.drawToBitmap] as a last resort. Each failure is logged under
 * the `FoodShare` tag so a still-broken capture is diagnosable from logcat.
 */
private suspend fun captureShareBitmap(
    graphicsLayer: GraphicsLayer,
    localView: View,
    activity: Activity?,
    reasons: MutableList<String>,
): Bitmap? {
    // 1. PixelCopy of the activity window — PRIMARY path.
    // Copies pixels straight off the window's Surface, so the GPU liquid-glass
    // blur effects (RenderEffect / AGSL RuntimeShader from miuix) are already
    // baked into the Surface and just get copied. No canvas is involved, so the
    // "software rendering does not support RenderEffect" exception that kills
    // GraphicsLayer.toImageBitmap() and View.drawToBitmap() cannot happen here.
    // The bitmap MUST match the window's surface size, so use decorView (the
    // window root) rather than the ComposeView, which may differ under edge-to-edge.
    if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val decorView = activity.window.decorView
        val width = decorView.width.coerceAtLeast(1)
        val height = decorView.height.coerceAtLeast(1)
        Log.e("FoodShare", "path1 PixelCopy decorView ${width}x${height}")
        val copied = runCatching { pixelCopyWindow(activity, width, height, reasons) }
            .onFailure {
                Log.e("FoodShare", "path1 PixelCopy threw", it)
                reasons += "PC:${it.javaClass.simpleName}:${it.message}"
            }
            .getOrNull()
        if (copied != null) {
            Log.e("FoodShare", "path1 ok ${copied.width}x${copied.height} config=${copied.config}")
            return copied
        }
    }

    // 2. GraphicsLayer offscreen snapshot — fallback. The bitmap returned here is
    // Picture-backed (Bitmap.createBitmap(Picture) on API 28+), so it CANNOT be
    // drawn onto a software Canvas — that re-executes graphicsLayer.draw() and
    // throws "software rendering does not support RenderEffect" because the
    // recorded content includes miuix blur effects. The caller must use
    // Bitmap.copy() (HardwareRenderer-backed) instead of Canvas.drawBitmap() to
    // convert it to a software config before PNG compression.
    val layerSize = graphicsLayer.size
    Log.e("FoodShare", "path2 GraphicsLayer.size=$layerSize")
    val gpu = runCatching {
        graphicsLayer.toImageBitmap().asAndroidBitmap()
    }.onFailure {
        Log.e("FoodShare", "path2 GraphicsLayer.toImageBitmap failed", it)
        reasons += "GL:${it.javaClass.simpleName}:${it.message}"
    }.getOrNull()
    if (gpu != null) {
        Log.e("FoodShare", "path2 ok ${gpu.width}x${gpu.height} config=${gpu.config}")
        return gpu
    }

    // 3. drawToBitmap — last resort, will almost certainly throw on this page
    // (software canvas + miuix RenderEffect), kept only so the failure is logged.
    Log.e("FoodShare", "path3 drawToBitmap")
    val fallback = runCatching { withContext(Dispatchers.Main) { localView.drawToBitmap() } }
        .onFailure {
            Log.e("FoodShare", "path3 drawToBitmap failed", it)
            reasons += "DTB:${it.javaClass.simpleName}:${it.message}"
        }
        .getOrNull()
    if (fallback != null) {
        Log.e("FoodShare", "path3 ok ${fallback.width}x${fallback.height} config=${fallback.config}")
    }
    return fallback
}

/**
 * Copies the activity window into a fresh [Bitmap] via [PixelCopy]. Returns `null`
 * (and recycles the scratch bitmap) on any copy error rather than throwing, so the
 * caller can fall through to the next capture strategy.
 */
private suspend fun pixelCopyWindow(
    activity: Activity,
    width: Int,
    height: Int,
    reasons: MutableList<String>,
): Bitmap? = suspendCancellableCoroutine { cont ->
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(activity.window, bitmap, { result ->
        Log.e("FoodShare", "PixelCopy result=$result (SUCCESS=${PixelCopy.SUCCESS})")
        if (result != PixelCopy.SUCCESS) {
            reasons += "PC:result=$result"
        }
        if (cont.isActive) {
            if (result == PixelCopy.SUCCESS) {
                cont.resume(bitmap, null)
            } else {
                bitmap.recycle()
                cont.resume(null, null)
            }
        }
    }, Handler(Looper.getMainLooper()))
    cont.invokeOnCancellation { bitmap.recycle() }
}
