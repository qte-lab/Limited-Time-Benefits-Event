package com.chronie.gift.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronie.gift.R
import com.chronie.gift.data.MonitorGroup
import com.chronie.gift.data.MonitorItem
import com.chronie.gift.data.MonitorLayout
import com.chronie.gift.data.MonitorSnapshot
import com.chronie.gift.data.MonitorSys
import com.chronie.gift.data.ServerMonitorApi
import com.chronie.gift.data.ServerMonitorConfig
import com.chronie.gift.data.toGroups
import com.chronie.gift.ui.permissions.rememberLocalNetworkPermissionRequester
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * In-app server status monitor, rendered natively.
 *
 * The screen used to embed the LiteMonitor web page in a `WebView`, which pinned the whole
 * dashboard to zh-CN (the page ships Chinese labels only). It now polls that page's own JSON
 * endpoint — [ServerMonitorConfig.SNAPSHOT_URL] — and draws the readings with Miuix/Compose, so
 * every label follows the app's language setting. See `ServerMonitorLabels` for how the server's
 * stable ASCII ids (`k`/`gid`) are mapped onto string resources.
 *
 * The three card layouts mirror the web dashboard: a full-width key/value board, oversized
 * throughput figures, and a ring plus bar list for sensor groups.
 *
 * Reaching the on-LAN server requires the Android 17 Local Network Protection permission, so
 * polling is gated behind [rememberLocalNetworkPermissionRequester].
 */

/** Severity palette, matching the web page's `--c-0/1/2` custom properties. */
private val StatusNormal = Color(0xFF10B981)
private val StatusWarning = Color(0xFFF59E0B)
private val StatusCritical = Color(0xFFEF4444)

private fun statusColor(status: Int): Color = when (status) {
    1 -> StatusWarning
    2 -> StatusCritical
    else -> StatusNormal
}

@Composable
fun ServerStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var snapshot by remember { mutableStateOf<MonitorSnapshot?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // True while the most recent poll succeeded; drives the LIVE/OFFLINE badge.
    var isLive by remember { mutableStateOf(false) }
    var pollingEnabled by remember { mutableStateOf(false) }
    // Bumping this restarts the polling effect, which makes the next fetch immediate.
    var refreshToken by remember { mutableIntStateOf(0) }

    val lnpRequester = rememberLocalNetworkPermissionRequester(
        onDenied = {
            loadError = context.getString(R.string.lan_permission_denied)
            Toast.makeText(
                context,
                context.getString(R.string.lan_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    )

    // Single entry point for "start / retry / refresh": clears the error, then asks for the LAN
    // permission (a no-op once granted) before letting the polling effect run.
    val startMonitoring: () -> Unit = {
        loadError = null
        refreshToken++
        lnpRequester.ensure { pollingEnabled = true }
    }

    LaunchedEffect(Unit) { startMonitoring() }

    LaunchedEffect(pollingEnabled, refreshToken) {
        if (!pollingEnabled) return@LaunchedEffect
        while (true) {
            try {
                snapshot = ServerMonitorApi.fetchSnapshot()
                isLive = true
                loadError = null
            } catch (_: Exception) {
                isLive = false
                // Keep the last good readings on screen; only take over the screen if we never
                // managed to load anything.
                if (snapshot == null) {
                    loadError = context.getString(R.string.server_status_error)
                }
            }
            delay(ServerMonitorConfig.POLL_INTERVAL_MS)
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.server_status_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(id = R.string.back),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = startMonitoring) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(id = R.string.server_status_refresh),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(
                        onClick = {
                            try {
                                uriHandler.openUri(ServerMonitorConfig.SERVER_STATUS_URL)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.open_link_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Link,
                            contentDescription = stringResource(id = R.string.server_status_open_external),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp
            val currentSnapshot = snapshot
            val currentError = loadError

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isWideScreen) Alignment.TopCenter else Alignment.TopStart
            ) {
                when {
                    // Never loaded and the last attempt failed: full-screen error with a retry.
                    currentSnapshot == null && currentError != null -> CenteredMessage(
                        topPadding = paddingValues.calculateTopPadding(),
                        message = currentError,
                        color = MiuixTheme.colorScheme.primary,
                        action = {
                            IconButton(onClick = startMonitoring) {
                                Icon(
                                    imageVector = MiuixIcons.Refresh,
                                    contentDescription = stringResource(id = R.string.server_status_retry),
                                    tint = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    currentSnapshot == null -> CenteredMessage(
                        topPadding = paddingValues.calculateTopPadding(),
                        message = stringResource(id = R.string.server_status_loading),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )

                    else -> Dashboard(
                        snapshot = currentSnapshot,
                        isLive = isLive,
                        isWideScreen = isWideScreen,
                        topPadding = paddingValues.calculateTopPadding()
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Dashboard                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun Dashboard(
    snapshot: MonitorSnapshot,
    isLive: Boolean,
    isWideScreen: Boolean,
    topPadding: androidx.compose.ui.unit.Dp
) {
    // Recompute the card model only when the readings actually change.
    val groups = remember(snapshot) { snapshot.toGroups() }

    LazyColumn(
        modifier = if (isWideScreen) Modifier.fillMaxWidth(0.8f) else Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = topPadding,
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            StatusHeaderCard(sys = snapshot.sys, isLive = isLive)
        }

        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    text = stringResource(id = R.string.server_status_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(items = groups, key = { it.gid }) { group ->
                MonitorCard(group)
            }
        }
    }
}

/** Host address, process runtime and the live indicator. */
@Composable
private fun StatusHeaderCard(sys: MonitorSys?, isLive: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Product name of the monitoring server — intentionally not translated.
                Text(
                    text = "\u26A1 LiteMonitor",
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                LiveBadge(isLive = isLive)
            }

            if (sys != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KeyValueChip(
                        modifier = Modifier.weight(1f),
                        label = stringResource(id = R.string.server_status_label_ip),
                        value = if (sys.port > 0) "${sys.ip}:${sys.port}" else sys.ip,
                        valueColor = MiuixTheme.colorScheme.onSurface,
                        monospace = true
                    )
                    KeyValueChip(
                        modifier = Modifier.weight(1f),
                        label = stringResource(id = R.string.server_status_label_runtime),
                        value = sys.uptime,
                        valueColor = MiuixTheme.colorScheme.onSurface,
                        monospace = true
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBadge(isLive: Boolean) {
    val color = if (isLive) StatusNormal else StatusCritical
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(
                id = if (isLive) R.string.server_status_live else R.string.server_status_offline
            ),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/* ------------------------------------------------------------------ */
/* Cards                                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun MonitorCard(group: MonitorGroup) {
    // The dashboard card carries no severity of its own, so it gets a neutral accent.
    val accent = if (group.layout == MonitorLayout.DASH) {
        MiuixTheme.colorScheme.onSurfaceContainerVariant
    } else {
        statusColor(group.status)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = monitorGroupTitle(group),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                if (group.layout != MonitorLayout.DASH) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            }

            // Thin accent rule standing in for the web card's colored top bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent.copy(alpha = 0.55f))
            )

            when (group.layout) {
                MonitorLayout.DASH -> DashBoardGrid(group.items)
                MonitorLayout.BIG -> BigFigureRow(group.items)
                MonitorLayout.STANDARD -> StandardGroupBody(group)
            }
        }
    }
}

/** Full-width key/value board — two chips per row on phones. */
@Composable
private fun DashBoardGrid(items: List<MonitorItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    val unit = item.displayUnit
                    val value = monitorItemValue(item)
                    KeyValueChip(
                        modifier = Modifier.weight(1f),
                        label = monitorItemLabel(item),
                        value = if (unit.isEmpty()) value else "$value $unit",
                        valueColor = statusColor(item.sts)
                    )
                }
                // Keeps a trailing odd chip at half width instead of stretching it.
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Two oversized figures side by side, used for throughput groups. */
@Composable
private fun BigFigureRow(items: List<MonitorItem>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val color = statusColor(item.sts)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = monitorItemLabel(item),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = item.v,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                    maxLines = 1
                )
                Text(
                    text = item.displayUnit,
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = 0.8f)
                )
            }
            if (index == 0 && items.size > 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(52.dp)
                        .background(MiuixTheme.colorScheme.dividerLine)
                )
            }
        }
    }
}

/** Ring for the headline reading plus a bar list for the remaining sensors. */
@Composable
private fun StandardGroupBody(group: MonitorGroup) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        group.core?.let { core -> MetricRing(core) }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            group.items.forEach { item -> MetricBarRow(item) }
        }
    }
}

@Composable
private fun MetricRing(item: MonitorItem) {
    val color = statusColor(item.sts)
    val track = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val sweep by animateFloatAsState(targetValue = item.fraction, label = "ringSweep")

    Column(
        modifier = Modifier.width(108.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(104.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 9.dp.toPx()
                val diameter = minOf(size.width, size.height) - strokeWidth
                val topLeft = Offset(
                    x = (size.width - diameter) / 2f,
                    y = (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.v,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = item.displayUnit,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = monitorItemLabel(item),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MetricBarRow(item: MonitorItem) {
    val color = statusColor(item.sts)
    val track = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val progress by animateFloatAsState(targetValue = item.fraction, label = "barProgress")
    val unit = item.displayUnit

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = monitorItemLabel(item),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = item.v,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                    maxLines = 1
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " $unit",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )
                }
            }
        }
        // Drawn rather than composed so a 0% reading cannot hit fillMaxWidth(0f).
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = track, size = size, cornerRadius = radius)
            val filled = size.width * progress
            if (filled > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(filled.coerceAtLeast(size.height), size.height),
                    cornerRadius = radius
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Shared pieces                                                       */
/* ------------------------------------------------------------------ */

/** Rounded label-over-value tile used by the header and the dashboard board. */
@Composable
private fun KeyValueChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color,
    monospace: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            maxLines = 1
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = valueColor
        )
    }
}

@Composable
private fun CenteredMessage(
    topPadding: androidx.compose.ui.unit.Dp,
    message: String,
    color: Color,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = 32.dp, end = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body1,
                color = color,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(8.dp))
                action()
            }
        }
    }
}
