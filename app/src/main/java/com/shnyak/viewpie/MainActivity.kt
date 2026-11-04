package com.shnyak.viewpie

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shnyak.viewpie.data.ApiFactory
import com.shnyak.viewpie.data.CameraDto
import com.shnyak.viewpie.data.PanelDetailDto
import com.shnyak.viewpie.data.PanelSummaryDto
import com.shnyak.viewpie.data.StatusDto
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ViewpieApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewpieApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("viewpie_connection", Context.MODE_PRIVATE)
    }
    var host by remember { mutableStateOf(preferences.getString("host", "192.168.1.87") ?: "192.168.1.87") }
    var port by remember { mutableStateOf(preferences.getString("port", "8080") ?: "8080") }
    var connected by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Введите адрес Raspberry Pi") }
    var status by remember { mutableStateOf<StatusDto?>(null) }
    var panels by remember { mutableStateOf<List<PanelSummaryDto>>(emptyList()) }
    var cameras by remember { mutableStateOf<List<CameraDto>>(emptyList()) }
    var selected by remember { mutableStateOf("Обзор") }
    var selectedPanel by remember { mutableStateOf<PanelDetailDto?>(null) }
    var selectedCamera by remember { mutableStateOf<CameraDto?>(null) }
    val scope = rememberCoroutineScope()
    var api by remember { mutableStateOf<com.shnyak.viewpie.data.ViewpieApi?>(null) }

    fun connect() {
        scope.launch {
            try {
                val client = ApiFactory.create(host, port.toInt())
                val snapshot = client.status()
                val panelList = client.panels()
                val cameraList = client.cameras()
                api = client
                status = snapshot
                panels = panelList.panels
                cameras = cameraList
                connected = true
                preferences.edit().putString("host", host).putString("port", port).apply()
                message = "Подключено"
            } catch (e: Exception) {
                connected = false
                message = "Ошибка подключения: ${e.message ?: "проверьте адрес и порт"}"
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Viewpie") }) },
        bottomBar = {
            NavigationBar {
                listOf("Обзор", "Панели", "Камеры", "Монитор", "Настройки").forEach { item ->
                    NavigationBarItem(
                        selected = selected == item,
                        onClick = { selected = item },
                        icon = {},
                        label = { Text(item) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP / адрес Raspberry Pi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Порт") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { connect() }) { Text("Проверить соединение") }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()

            when (selected) {
                "Обзор" -> {
                    Text("Состояние Viewpie", style = MaterialTheme.typography.titleLarge)
                    InfoLine("Подключение", if (connected) "Активно" else "Нет")
                    InfoLine("Активная панель", status?.active_panel_name ?: status?.active_panel ?: "—")
                    InfoLine("Полноэкранная камера", status?.fullscreen_camera ?: "—")
                    InfoLine("Камеры в конфигурации", status?.cameras?.size?.toString() ?: "—")
                    InfoLine("Потоки", status?.active_streams?.toString() ?: "—")
                    InfoLine("Температура CPU", status?.cpu_temp_c?.let { "$it °C" } ?: "—")
                    InfoLine("Время работы", status?.uptime_seconds?.let { "${it / 3600} ч" } ?: "—")
                }
                "Панели" -> {
                    if (selectedPanel != null) {
                        Text(selectedPanel?.name ?: selectedPanel?.id ?: "Просмотр панели", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { selectedPanel = null }) { Text("К списку панелей") }
                        Text("Независимый просмотр на телефоне — HDMI-монитор не переключается.")
                        PanelGridPreview(panel = selectedPanel!!, cameras = cameras)
                    } else {
                        Text("Готовые панели", style = MaterialTheme.typography.titleLarge)
                        panels.forEach { panel ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(panel.name ?: panel.id, style = MaterialTheme.typography.titleMedium)
                                    Text("Камер: ${panel.camera_count ?: "—"}")
                                    Button(
                                        enabled = connected,
                                        onClick = {
                                            scope.launch {
                                                runCatching { api?.panel(panel.id) }
                                                    .onSuccess { detail ->
                                                        if (detail != null) selectedPanel = detail
                                                        else message = "Не удалось получить конфигурацию панели"
                                                    }
                                                    .onFailure { message = "Ошибка загрузки панели: ${it.message}" }
                                            }
                                        }
                                    ) { Text("Просмотр на телефоне") }
                                    OutlinedButton(
                                        enabled = connected,
                                        onClick = {
                                            scope.launch {
                                                runCatching { api?.activatePanel(panel.id) }
                                                    .onSuccess { message = "Команда переключения HDMI отправлена" }
                                                    .onFailure { message = "Ошибка: ${it.message}" }
                                            }
                                        }
                                    ) { Text("Показать на HDMI") }
                                }
                            }
                        }
                    }
                }
                "Камеры" -> {
                    if (selectedCamera != null) {
                        Text(selectedCamera?.name ?: "Камера", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { selectedCamera = null }) { Text("К списку камер") }
                        val streamUrl = selectedCamera?.url
                        if (!streamUrl.isNullOrBlank()) {
                            RtspCameraPlayer(streamUrl, Modifier.fillMaxWidth().height(240.dp))
                        } else {
                            Text("Для этой камеры не задан RTSP URL.")
                        }
                        Text("Независимый просмотр: вывод Viewpie на HDMI не меняется.")
                    } else {
                        Text("Камеры", style = MaterialTheme.typography.titleLarge)
                        cameras.forEach { camera ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(camera.name, style = MaterialTheme.typography.titleMedium)
                                    Text(if (camera.running == true) "Поток используется Viewpie" else "Сейчас не используется Viewpie")
                                    Button(enabled = !camera.url.isNullOrBlank(), onClick = { selectedCamera = camera }) {
                                        Text("Смотреть на телефоне")
                                    }
                                }
                            }
                        }
                    }
                }
                "Монитор" -> {
                    Text("Управление монитором", style = MaterialTheme.typography.titleLarge)
                    Button(enabled = connected, onClick = {
                        scope.launch { runCatching { api?.resume() }.onSuccess { message = "Вывод возобновлён" }.onFailure { message = "Ошибка: ${it.message}" } }
                    }) { Text("Возобновить вывод") }
                    Button(enabled = connected, onClick = {
                        scope.launch { runCatching { api?.pause() }.onSuccess { message = "Пауза включена" }.onFailure { message = "Ошибка: ${it.message}" } }
                    }) { Text("Пауза / чёрный экран") }
                    Button(enabled = connected, onClick = {
                        scope.launch { runCatching { api?.grid() }.onSuccess { message = "Возврат к сетке выполнен" }.onFailure { message = "Ошибка: ${it.message}" } }
                    }) { Text("Вернуться к сетке") }
                    Text("Перезагрузка Raspberry Pi и отдельное управление питанием экрана пока не входят в подтверждённый API.")
                }
                "Настройки" -> {
                    Text("Настройки", style = MaterialTheme.typography.titleLarge)
                    Button(enabled = connected, onClick = {
                        scope.launch { runCatching { api?.reload() }.onSuccess { message = "Конфигурация перечитана" }.onFailure { message = "Ошибка: ${it.message}" } }
                    }) { Text("Перечитать конфигурацию Viewpie") }
                    Text("Редактор пользовательских раскладок не предусмотрен.")
                }
            }
        }
    }
}

@Composable
private fun PanelGridPreview(panel: PanelDetailDto, cameras: List<CameraDto>) {
    val rows = remember(panel.layout) {
        panel.layout.orEmpty().lines()
            .map { line -> line.trim().split(Regex("\\s+")).filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }
    }
    val feedBySlot = remember(panel.viewports) {
        panel.viewports.associate { viewport -> viewport.slot to viewport.feeds.firstOrNull() }
    }
    val cameraByName = remember(cameras) { cameras.associateBy { it.name } }

    if (rows.isEmpty()) {
        Text("В конфигурации панели не найдена сетка layout.")
        return
    }

    Column(
        Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 680.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        rows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { slot ->
                    val feedName = feedBySlot[slot]
                    val camera = feedName?.let { cameraByName[it] }
                    Card(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.fillMaxSize()) {
                            if (!camera?.url.isNullOrBlank()) {
                                RtspCameraPlayer(camera!!.url!!, Modifier.fillMaxSize())
                            } else {
                                Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.Center) {
                                    Text("Слот $slot", style = MaterialTheme.typography.titleMedium)
                                    Text(feedName ?: "Камера не назначена")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Text("Если в одном слоте настроено несколько камер, сейчас отображается первая камера этого слота.")
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
    }
}
