package com.chronie.gift.data

import com.chronie.gift.ui.screens.ApiClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Host-level facts shown in the dashboard header. */
@Serializable
data class MonitorSys(
    val ip: String = "",
    val port: Int = 0,
    /** Process runtime as `HH:MM:SS` — already locale-neutral. */
    val uptime: String = ""
)

/**
 * A single reading.
 *
 * @property k stable ASCII id, e.g. `CPU.Load`, `DASH.Uptime`, `FPS`. The localization key.
 * @property n server-rendered zh-CN label; only used when [k] is unknown to the app.
 * @property gid stable ASCII group id, e.g. `CPU`, `HOST`. Note this is *not* the [k] prefix:
 *   `MEM.Load` and `FPS` both report `gid = HOST`.
 * @property gn server-rendered zh-CN group label, emoji included (e.g. `💻CPU`).
 * @property gidx group sort index.
 * @property v formatted value; for a few `DASH.*` keys this contains zh-CN text that the
 *   localization layer re-formats (see `ServerMonitorLabels`).
 * @property u unit; may carry padding (the FPS reading arrives as `" FPS"`).
 * @property pct 0..100 fill ratio for rings and bars.
 * @property sts severity: 0 normal, 1 warning, 2 critical.
 * @property primary whether this reading contributes to the card's overall severity.
 */
@Serializable
data class MonitorItem(
    val k: String = "",
    val n: String = "",
    val gid: String = "",
    val gn: String = "",
    val gidx: Int = 999,
    val v: String = "",
    val u: String = "",
    val pct: Double = 0.0,
    val sts: Int = 0,
    val primary: Boolean = false
)

/** One `/api/snapshot` response. */
@Serializable
data class MonitorSnapshot(
    val sys: MonitorSys? = null,
    val items: List<MonitorItem> = emptyList()
)

/** How a group is laid out, mirroring the three layouts the web dashboard uses. */
enum class MonitorLayout {
    /** Key/value chips, always rendered first and full width. */
    DASH,

    /** Two oversized figures side by side (throughput style). */
    BIG,

    /** A ring for the headline reading plus a bar list for the rest. */
    STANDARD
}

/** [MonitorItem]s of one card, already ordered and classified. */
data class MonitorGroup(
    val gid: String,
    /** Server label including emoji; the UI localizes the text and keeps the emoji. */
    val serverName: String,
    val orderIndex: Int,
    val layout: MonitorLayout,
    /** Headline reading drawn as a ring. Non-null only for [MonitorLayout.STANDARD]. */
    val core: MonitorItem?,
    /** Everything except [core]. */
    val items: List<MonitorItem>,
    /** Highest severity among the group's `primary` readings. */
    val status: Int
)

/** Groups rendered with the oversized two-column layout. */
private val BIG_LAYOUT_GROUPS = setOf("NET", "DISK", "DATA")

/** The dashboard group is pinned above everything else, as on the web page. */
private const val DASH_ORDER_INDEX = -999

/**
 * Reproduces the web page's "is this the headline reading?" test.
 *
 * A percentage-based reading (or anything named `*Load*`) becomes the ring, while fan readings are
 * explicitly excluded even though they are reported in percent.
 */
private fun MonitorItem.isHeadlineReading(): Boolean {
    if (k.contains("Fan")) return false
    return k.contains("Load") || u.contains("%")
}

/**
 * Folds the flat reading list into renderable cards.
 *
 * Grouping is keyed on [MonitorItem.gid] rather than the [MonitorItem.k] prefix, and the ordering,
 * headline-reading choice and severity roll-up all follow the web dashboard so the native screen
 * stays visually faithful to it.
 */
fun MonitorSnapshot.toGroups(): List<MonitorGroup> {
    if (items.isEmpty()) return emptyList()

    // LinkedHashMap keeps first-seen order, which is the tie-breaker when two groups share a gidx.
    val buckets = LinkedHashMap<String, MutableList<MonitorItem>>()
    for (item in items) {
        buckets.getOrPut(item.gid.ifEmpty { "OTHER" }) { mutableListOf() }.add(item)
    }

    return buckets.map { (gid, readings) ->
        val layout = when {
            gid == "DASH" -> MonitorLayout.DASH
            gid in BIG_LAYOUT_GROUPS -> MonitorLayout.BIG
            else -> MonitorLayout.STANDARD
        }

        val core = if (layout == MonitorLayout.STANDARD) {
            readings.firstOrNull { it.isHeadlineReading() }
        } else {
            null
        }

        // The oversized layout only has room for two figures.
        val rest = readings.filter { it !== core }
            .let { if (layout == MonitorLayout.BIG) it.take(2) else it }

        val first = readings.first()
        MonitorGroup(
            gid = gid,
            serverName = first.gn,
            orderIndex = if (layout == MonitorLayout.DASH) DASH_ORDER_INDEX else first.gidx,
            layout = layout,
            core = core,
            items = rest,
            status = readings.filter { it.primary }.maxOfOrNull { it.sts } ?: 0
        )
    }.sortedBy { it.orderIndex }
}

/**
 * Reads the LiteMonitor JSON snapshot that backs the in-app dashboard.
 *
 * Reuses the shared [ApiClient] so the LAN timeouts configured for the event server apply here too.
 */
object ServerMonitorApi {
    private val json = Json { ignoreUnknownKeys = true }

    /** GET /api/snapshot. Throws on transport or parse failure; callers surface a retry. */
    suspend fun fetchSnapshot(url: String = ServerMonitorConfig.SNAPSHOT_URL): MonitorSnapshot {
        val text = withContext(Dispatchers.IO) {
            ApiClient.client.get(url).bodyAsText()
        }
        return json.decodeFromString<MonitorSnapshot>(text)
    }
}
