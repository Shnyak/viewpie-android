package com.shnyak.viewpie.data

data class StatusDto(
    val paused: Boolean? = null,
    val pause_off_screen: Boolean? = null,
    val screen_off: Boolean? = null,
    val active_panel: String? = null,
    val active_panel_name: String? = null,
    val fullscreen_camera: String? = null,
    val cameras: List<String> = emptyList(),
    val layout: String? = null,
    val spacing: Int? = null,
    val uptime_seconds: Long? = null,
    val cpu_temp_c: Double? = null,
    val active_streams: Int? = null,
    val panels_count: Int? = null,
    val cpu_load_percent: Double? = null,
    val load_average_1m: Double? = null,
    val restart_available: Boolean = false
)

data class PanelsResponse(
    val panels: List<PanelSummaryDto> = emptyList(),
    val active: String? = null
)

data class PanelSummaryDto(
    val id: String,
    val name: String? = null,
    val camera_count: Int? = null
)

data class PanelDetailDto(
    val id: String? = null,
    val name: String? = null,
    val layout: String? = null,
    val viewports: List<ViewportDto> = emptyList()
)

data class ViewportDto(
    val slot: String,
    val feeds: List<String> = emptyList()
)

data class CameraDto(
    val name: String,
    val url: String? = null,
    val fullscreen_url: String? = null,
    val ref_count: Int? = null,
    val running: Boolean? = null
)


data class SettingsDto(
    val spacing: Int = 0,
    val pause_off_screen: Boolean = false,
    val background: String = "#000000",
    val rotation_interval: Long = 0,
    val fullscreen_timeout: Long = 60
)

data class CameraTestDto(
    val ok: Boolean = false,
    val latency_ms: Long? = null,
    val error: String? = null
)
