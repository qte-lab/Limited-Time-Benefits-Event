package com.chronie.gift.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.chronie.gift.R
import com.chronie.gift.data.MonitorGroup
import com.chronie.gift.data.MonitorItem

/**
 * Localization layer for the server status dashboard.
 *
 * The LiteMonitor web page is zh-CN only: every reading arrives with a Chinese label (`n`), a
 * Chinese group label (`gn`) and — for two of the dashboard keys — a Chinese-formatted *value*.
 * Rather than translating that text, we key off the stable ASCII identifiers the same payload
 * carries (`k` and `gid`) and resolve our own string resources, so the screen follows the app's
 * language setting like every other screen.
 *
 * Unknown identifiers fall back to the server's Chinese text instead of rendering blank, so a new
 * sensor appearing server-side still shows up (just untranslated) without an app update.
 *
 * All helpers are `@ReadOnlyComposable`: they only read resources and never emit UI, which makes
 * the conditional `stringResource` calls below safe.
 */

/* ------------------------------------------------------------------ */
/* Group labels                                                        */
/* ------------------------------------------------------------------ */

/**
 * Emoji the web page prefixes each group with. Kept verbatim (they are language-neutral) and
 * re-attached to the localized label so the native cards stay recognizable next to the web page.
 */
private val GROUP_EMOJI = mapOf(
    "DASH" to "\u2139\uFE0F",             // ℹ️
    "CPU" to "\uD83D\uDCBB",              // 💻
    "GPU" to "\uD83C\uDFAE",              // 🎮
    "HOST" to "\uD83D\uDDA5\uFE0F",       // 🖥️
    "BAT" to "\uD83D\uDD0B",              // 🔋
    "DISK" to "\uD83D\uDCBD",             // 💽
    "NET" to "\uD83C\uDF10",              // 🌐
    "DATA" to "\uD83D\uDCC8"              // 📈
)

private val GROUP_LABEL_RES = mapOf(
    "DASH" to R.string.monitor_group_dash,
    "CPU" to R.string.monitor_group_cpu,
    "GPU" to R.string.monitor_group_gpu,
    "HOST" to R.string.monitor_group_host,
    "BAT" to R.string.monitor_group_bat,
    "DISK" to R.string.monitor_group_disk,
    "NET" to R.string.monitor_group_net,
    "DATA" to R.string.monitor_group_data
)

/* ------------------------------------------------------------------ */
/* Reading labels                                                      */
/* ------------------------------------------------------------------ */

/**
 * Every reading the server is known to emit.
 *
 * Note the ids are not derivable from the group: `MEM.Load` and `FPS` are both reported under
 * `gid = HOST`.
 */
private val ITEM_LABEL_RES = mapOf(
    // ℹ️ Dashboard
    "DASH.HOST" to R.string.monitor_item_hostname,
    "DASH.Time" to R.string.monitor_item_time,
    "DASH.Uptime" to R.string.monitor_item_uptime,
    "DASH.IP" to R.string.monitor_item_lan_ip,
    // 💻 CPU
    "CPU.Load" to R.string.monitor_item_cpu_load,
    "CPU.Temp" to R.string.monitor_item_cpu_temp,
    "CPU.Clock" to R.string.monitor_item_cpu_clock,
    "CPU.Power" to R.string.monitor_item_cpu_power,
    "CPU.Voltage" to R.string.monitor_item_cpu_voltage,
    // 🎮 GPU
    "GPU.Load" to R.string.monitor_item_gpu_load,
    "GPU.Temp" to R.string.monitor_item_gpu_temp,
    "GPU.Clock" to R.string.monitor_item_gpu_clock,
    "GPU.VRAM" to R.string.monitor_item_gpu_vram,
    // 🖥️ Host
    "MEM.Load" to R.string.monitor_item_mem_load,
    "FPS" to R.string.monitor_item_fps,
    // 🔋 Battery
    "BAT.Percent" to R.string.monitor_item_bat_percent,
    "BAT.Power" to R.string.monitor_item_bat_power,
    "BAT.Voltage" to R.string.monitor_item_bat_voltage,
    "BAT.Current" to R.string.monitor_item_bat_current,
    // 💽 Disk
    "DISK.Read" to R.string.monitor_item_disk_read,
    "DISK.Write" to R.string.monitor_item_disk_write,
    // 🌐 Network
    "NET.Up" to R.string.monitor_item_net_up,
    "NET.Down" to R.string.monitor_item_net_down,
    // 📈 Data usage
    "DATA.DayUp" to R.string.monitor_item_data_day_up,
    "DATA.DayDown" to R.string.monitor_item_data_day_down
)

/* ------------------------------------------------------------------ */
/* Chinese-formatted values                                            */
/* ------------------------------------------------------------------ */

private const val KEY_TIME = "DASH.Time"
private const val KEY_UPTIME = "DASH.Uptime"

/** `周四 08:17:02` -> weekday token + the untouched clock part. */
private val CN_WEEKDAY_TIME = Regex("""^\s*周([一二三四五六日天])\s*(.*)$""")

// Duration components are matched independently so any subset/order still parses
// (`14时 20分`, `1天 2时 3分`, `45秒`, ...).
private val CN_DAYS = Regex("""(\d+)\s*天""")
private val CN_HOURS = Regex("""(\d+)\s*(?:小时|时)""")
private val CN_MINUTES = Regex("""(\d+)\s*分(?:钟)?""")
private val CN_SECONDS = Regex("""(\d+)\s*秒""")

private val WEEKDAY_RES = mapOf(
    '一' to R.string.monitor_weekday_mon,
    '二' to R.string.monitor_weekday_tue,
    '三' to R.string.monitor_weekday_wed,
    '四' to R.string.monitor_weekday_thu,
    '五' to R.string.monitor_weekday_fri,
    '六' to R.string.monitor_weekday_sat,
    '日' to R.string.monitor_weekday_sun,
    '天' to R.string.monitor_weekday_sun
)

/* ------------------------------------------------------------------ */
/* Public helpers                                                      */
/* ------------------------------------------------------------------ */

/** Localized card title, e.g. `💻 CPU`. Falls back to the server's own label. */
@Composable
@ReadOnlyComposable
fun monitorGroupTitle(group: MonitorGroup): String {
    val labelRes = GROUP_LABEL_RES[group.gid] ?: return group.serverName
    val emoji = GROUP_EMOJI[group.gid]
    val label = stringResource(id = labelRes)
    return if (emoji != null) "$emoji $label" else label
}

/** Localized reading label. Falls back to the server's Chinese label for unknown keys. */
@Composable
@ReadOnlyComposable
fun monitorItemLabel(item: MonitorItem): String {
    val labelRes = ITEM_LABEL_RES[item.k] ?: return item.n
    return stringResource(id = labelRes)
}

/**
 * Localized reading value.
 *
 * Only the two dashboard keys that embed Chinese words are rewritten; numeric readings are passed
 * through untouched so we never reformat (and risk corrupting) a server-formatted number.
 */
@Composable
@ReadOnlyComposable
fun monitorItemValue(item: MonitorItem): String = when (item.k) {
    KEY_TIME -> localizedWeekdayTime(item.v)
    KEY_UPTIME -> localizedDuration(item.v)
    else -> item.v
}

/** Unit as displayed. The FPS reading arrives space-padded (`" FPS"`), so trim it. */
val MonitorItem.displayUnit: String
    get() = u.trim()

/** [MonitorItem.pct] as a 0f..1f fraction for rings and bars. */
val MonitorItem.fraction: Float
    get() = (pct / 100.0).toFloat().coerceIn(0f, 1f)

/* ------------------------------------------------------------------ */
/* Internals                                                           */
/* ------------------------------------------------------------------ */

@Composable
@ReadOnlyComposable
private fun localizedWeekdayTime(raw: String): String {
    val match = CN_WEEKDAY_TIME.find(raw) ?: return raw
    val weekdayRes = WEEKDAY_RES[match.groupValues[1].first()] ?: return raw
    val weekday = stringResource(id = weekdayRes)
    val clock = match.groupValues[2].trim()
    return if (clock.isEmpty()) weekday else "$weekday $clock"
}

@Composable
@ReadOnlyComposable
private fun localizedDuration(raw: String): String {
    val days = CN_DAYS.find(raw)?.groupValues?.get(1)?.toIntOrNull()
    val hours = CN_HOURS.find(raw)?.groupValues?.get(1)?.toIntOrNull()
    val minutes = CN_MINUTES.find(raw)?.groupValues?.get(1)?.toIntOrNull()
    val seconds = CN_SECONDS.find(raw)?.groupValues?.get(1)?.toIntOrNull()

    // Nothing recognizable: leave the server string alone rather than blanking the field.
    if (days == null && hours == null && minutes == null && seconds == null) return raw

    val parts = ArrayList<String>(4)
    if (days != null) parts += stringResource(R.string.monitor_uptime_day, days)
    if (hours != null) parts += stringResource(R.string.monitor_uptime_hour, hours)
    if (minutes != null) parts += stringResource(R.string.monitor_uptime_minute, minutes)
    if (seconds != null) parts += stringResource(R.string.monitor_uptime_second, seconds)
    return parts.joinToString(" ")
}
