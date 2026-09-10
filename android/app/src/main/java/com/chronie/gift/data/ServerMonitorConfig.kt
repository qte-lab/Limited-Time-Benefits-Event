package com.chronie.gift.data

/**
 * Configuration for the in-app server status monitor.
 *
 * The monitor no longer embeds the LiteMonitor web page in a `WebView`. Instead it polls the
 * page's own JSON endpoint ([SNAPSHOT_URL]) and renders the readings with native Miuix/Compose
 * widgets, which is what makes the dashboard translatable (the web page is zh-CN only).
 *
 * Change [SERVER_STATUS_URL] to retarget the monitor (e.g. when the LAN server moves).
 */
object ServerMonitorConfig {
    /** On-LAN LiteMonitor root. Still used by the "open in browser" action. */
    const val SERVER_STATUS_URL: String = "http://192.168.10.9:5000"

    /**
     * JSON snapshot that backs the native dashboard.
     *
     * Shape: `{"sys":{"ip","port","uptime"},"items":[{"k","n","gid","gn","gidx","v","u","pct","sts","primary"}]}`
     * The `k`/`gid` fields are stable ASCII identifiers, which is exactly what the localization
     * layer keys off — `n`/`gn` are the server's zh-CN display strings and are only a fallback.
     */
    const val SNAPSHOT_URL: String = "$SERVER_STATUS_URL/api/snapshot"

    /**
     * Poll cadence. The web page keeps a WebSocket open and receives roughly one push per
     * second; polling every two seconds keeps the readings live without hammering the LAN box.
     */
    const val POLL_INTERVAL_MS: Long = 2_000L
}
