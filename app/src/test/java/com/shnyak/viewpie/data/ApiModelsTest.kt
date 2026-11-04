package com.shnyak.viewpie.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiModelsTest {
    private val gson = Gson()

    @Test
    fun panelDetailMatchesServerWireShape() {
        val json = """
            {
              "id": "panel1",
              "name": "Камеры 1-4",
              "layout": "a a b\na a b",
              "viewports": [
                {"slot": "a", "feeds": ["front", "yard"]},
                {"slot": "b", "feeds": ["gate"]}
              ]
            }
        """.trimIndent()
        val panel = gson.fromJson(json, PanelDetailDto::class.java)
        assertEquals("panel1", panel.id)
        assertEquals("Камеры 1-4", panel.name)
        assertEquals(listOf("front", "yard"), panel.viewports.first().feeds)
    }

    @Test
    fun statusSupportsOldServerAndOptionalMetrics() {
        val old = gson.fromJson(
            """{"paused":false,"active_panel":"panel1","cameras":["front"],"uptime_seconds":42}""",
            StatusDto::class.java
        )
        assertFalse(old.paused == true)
        assertEquals(42L, old.uptime_seconds)
        assertNull(old.cpu_load_percent)
        assertFalse(old.restart_available)

        val current = gson.fromJson(
            """{"cpu_load_percent":12.5,"load_average_1m":0.42,"restart_available":true}""",
            StatusDto::class.java
        )
        assertEquals(12.5, current.cpu_load_percent!!, 0.001)
        assertEquals(0.42, current.load_average_1m!!, 0.001)
        assertTrue(current.restart_available)
    }

    @Test
    fun settingsAndCameraTestResponsesDeserialize() {
        val settings = gson.fromJson(
            """{"spacing":4,"pause_off_screen":true,"background":"#101010","rotation_interval":30,"fullscreen_timeout":90}""",
            SettingsDto::class.java
        )
        assertEquals(4, settings.spacing)
        assertTrue(settings.pause_off_screen)
        assertEquals(30L, settings.rotation_interval)

        val result = gson.fromJson(
            """{"ok":true,"latency_ms":28,"error":null}""",
            CameraTestDto::class.java
        )
        assertTrue(result.ok)
        assertEquals(28L, result.latency_ms)
        assertNull(result.error)
    }
}
