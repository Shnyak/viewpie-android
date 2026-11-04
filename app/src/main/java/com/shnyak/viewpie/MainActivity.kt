package com.shnyak.viewpie

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
    var host by remember { mutableStateOf("192.168.1.87") }
    var port by remember { mutableStateOf("8080") }
    var connected by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Введите адрес Raspberry Pi") }
    var status by remember { mutableStateOf<StatusDto?>(null) }
    var panels by remember { mutableStateOf<List<PanelSummaryDto>>(emptyList()) }
    var cameras by remember { mutableStateOf<List<CameraDto>>(emptyList()) }
    var selected by remember { mutableStateOf("Обзор") }
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
                    NavigationBarItem(selected = selected == item, onClick = { selected = item }, icon = {}, label = { Text(item) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("IP / адрес Raspberry Pi") }, singleLine = true)
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Порт") }, singleLine = true)
            Button(onClick = { connect() }) { Text("Проверить соединение") }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            when (selected) {
                "Обзор" -> {
                    Text("Состояние Viewpie", style = MaterialTheme.typography.titleLarge)
                    InfoLine("Подключение", if (connected) "Активно" else "Нет")
                    InfoLine("Активная панель", status?.active_panel_name ?: status?.active_panel ?: "—")
                    InfoLine("Полноэкранная камера", status?.fullscreen_camera ?: "—")
                    InfoLine("Потоки", status?.active_streams?.toString() ?: "—")
                    InfoLine("Температура CPU", status?.cpu_temp_c?.let { "$it °C" } ?: "—")
                    InfoLine("Время работы", status?.uptime_seconds?.let { "${it / 3600} ч" } ?: "—")
                }
                "Панели" -> {
                    Text("Готовые панели", style = MaterialTheme.typography.titleLarge)
                    panels.forEach { p -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(p.name ?: p.id, style = MaterialTheme.typography.titleMedium)
                        Text("Камер: ${p.camera_count ?: "—"}")
                        Button(onClick = { scope.launch { runCatching { api?.activatePanel(p.id) }.onSuccess { message = "Панель отправлена на монитор" }.onFailure { message = "Ошибка: ${it.message}" } } }) { Text("Показать на HDMI") }
                    } } }
                }
                "Камеры" -> {
                    Text("Камеры", style = MaterialTheme.typography.titleLarge)
                    cameras.forEach { c -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (c.running == true) "Поток запущен" else "Состояние: неизвестно")
                        Text("Независимый RTSP-просмотр будет подключён следующим этапом.")
                    } } }
                }
                "Монитор" -> {
                    Text("Управление монитором", style = MaterialTheme.typography.titleLarge)
                    Button(enabled = connected, onClick = { scope.launch { runCatching { api?.resume() }.onFailure { message = "Ошибка: ${it.message}" } } }) { Text("Возобновить вывод") }
                    Button(enabled = connected, onClick = { scope.launch { runCatching { api?.pause() }.onFailure { message = "Ошибка: ${it.message}" } } }) { Text("Пауза / чёрный экран") }
                    Button(enabled = connected, onClick = { scope.launch { runCatching { api?.grid() }.onFailure { message = "Ошибка: ${it.message}" } } }) { Text("Вернуться к сетке") }
                    Text("Перезагрузка Raspberry Pi и отдельное питание экрана пока недоступны в подтверждённом API.")
                }
                "Настройки" -> {
                    Text("Настройки", style = MaterialTheme.typography.titleLarge)
                    Button(enabled = connected, onClick = { scope.launch { runCatching { api?.reload() }.onSuccess { message = "Конфигурация перечитана" }.onFailure { message = "Ошибка: ${it.message}" } } }) { Text("Перечитать конфигурацию Viewpie") }
                    Text("Редактор раскладок в приложении не предусмотрен.")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
    }
}
