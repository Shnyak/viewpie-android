package com.shnyak.viewpie.data

data class StatusDto(
    val paused: Boolean? = null,
    val pause_off_screen: Boolean? = null,
    val screen_off: Boolean? = null,
    val active_panel: String? = null,
    val active_panel_name: String? = null,
    val fullscreen_camera: String? = null,
    val cameras: Int? = null,
    val layout: String? = null,
    val spacing: Int? = null,
    val uptime_seconds: Long? = null,
    val cpu_temp_c: Double? = null,
    val active_streams: Int? = null,
    val panels_count: Int? = null
)

data class PanelsResponse(val panels: List<PanelSummaryDto> = emptyList(), val active: String? = null)
data class PanelSummaryDto(val id: String, val name: String? = null, val camera_count: Int? = null)
data class CameraDto(
    val name: String,
    val url: String? = null,
    val fullscreen_url: String? = null,
    val ref_count: Int? = null,
    val running: Boolean? = null
)
