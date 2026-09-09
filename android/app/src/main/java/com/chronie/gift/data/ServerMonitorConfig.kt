package com.chronie.gift.data

/**
 * Configuration for the in-app server status monitor.
 *
 * The monitor opens an embedded WebView pointed at the on-LAN server status page. Change
 * [SERVER_STATUS_URL] to retarget the monitor (e.g. when the LAN server moves from :3002 to :5000).
 */
object ServerMonitorConfig {
    /** On-LAN server status page shown in the in-app browser launched from Settings. */
    const val SERVER_STATUS_URL: String = "http://192.168.10.9:5000"
}
