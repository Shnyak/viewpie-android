package com.shnyak.viewpie

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shnyak.viewpie.data.ApiFactory
import com.shnyak.viewpie.data.CameraDto
import com.shnyak.viewpie.data.PanelDetailDto
import com.shnyak.viewpie.data.PanelSummaryDto
import com.shnyak.viewpie.data.SettingsDto
import com.shnyak.viewpie.data.StatusDto
import com.shnyak.viewpie.data.ViewpieApi
import kotlinx.coroutines.delay
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
    var settings by remember { mutableStateOf<SettingsDto?>(null) }
    var selected by remember { mutableStateOf("Обзор") }
    var selectedPanel by remember { mutableStateOf<PanelDetailDto?>(null) }
    var activePanelId by remember { mutableStateOf<String?>(null) }
    var mirrorMode by remember { mutableStateOf(false) }
    var mirrorPanel by remember { mutableStateOf<PanelDetailDto?>(null) }
    var mirrorCameraName by remember { mutableStateOf<String?>(null) }
    var selectedCamera by remember { mutableStateOf<CameraDto?>(null) }
    var phoneFullscreen by remember { mutableStateOf(false) }
    var fullscreenDuration by remember { mutableStateOf("60") }
    var cameraTests by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var savingSettings by remember { mutableStateOf(false) }
    var restartToken by remember { mutableStateOf("") }
    var showRestartConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var api by remember { mutableStateOf<ViewpieApi?>(null) }

    suspend fun refresh(client: ViewpieApi) {
        status = client.status()
        panels = client.panels().panels
        cameras = client.cameras()
    }

    fun connect() {
        scope.launch {
            try {
                val client = ApiFactory.create(host, port.toInt())
                refresh(client)
                settings = client.settings()
                api = client
                connected = true
                preferences.edit().putString("host", host).putString("port", port).apply()
                message = "Подключено к Viewpie"
            } catch (e: Exception) {
                connected = false
                api = null
                message = "Ошибка подключения: ${e.message ?: "проверьте адрес и порт"}"
            }
        }
    }

    LaunchedEffect(api, connected) {
        val client = api
        if (connected && client != null) {
            while (true) {
                delay(5000)
                runCatching { refresh(client) }
                    .onFailure { message = "Потеряна связь: ${it.message ?: "ошибка API"}" }
            }
        }
    }

    if (showRestartConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestartConfirmation = false },
            title = { Text("Перезагрузить Raspberry Pi?") },
            text = { Text("Вывод Viewpie прервётся. Дождитесь перезапуска Raspberry Pi и сервиса.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirmation = false
                    scope.launch {
                        runCatching { api?.restart("Bearer $restartToken") }
                            .onSuccess { message = "Команда перезагрузки принята" }
                            .onFailure { message = "Перезагрузка не выполнена: ${it.message}" }
                        restartToken = ""
                    }
                }) { Text("Перезагрузить") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirmation = false }) { Text("Отмена") }
            }
        )
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
                value = host, onValueChange = { host = it },
                label = { Text("IP / адрес Raspberry Pi") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it },
                label = { Text("Порт API") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { connect() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (connected) "Переподключиться" else "Подключиться")
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()

            when (selected) {
                "Обзор" -> {
                    Text("Состояние Viewpie", style = MaterialTheme.typography.titleLarge)
                    if (!mirrorMode) {
                        Button(
                            enabled = connected,
                            onClick = {
                                scope.launch {
                                    val fullscreen = status?.fullscreen_camera
                                    if (!fullscreen.isNullOrBlank()) {
                                        mirrorCameraName = fullscreen
                                        mirrorPanel = null
                                        mirrorMode = true
                                    } else {
                                        val panelId = activePanelId
                                        if (panelId == null) message = "Сервер не сообщил активную панель"
                                        else runCatching { api?.panel(panelId) }
                                            .onSuccess { detail ->
                                                if (detail != null) { mirrorPanel = detail; mirrorCameraName = null; mirrorMode = true }
                                                else message = "Не удалось загрузить активную панель"
                                            }
                                            .onFailure { message = "Ошибка просмотра: ${it.message}" }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Как на HDMI-мониторе") }
                    } else {
                        Text("Предпросмотр активного вывода", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { mirrorMode = false; mirrorPanel = null; mirrorCameraName = null }) { Text("Закрыть предпросмотр") }
                        if (mirrorCameraName != null) {
                            val cam = cameras.firstOrNull { it.name == mirrorCameraName }
                            val url = cam?.fullscreen_url ?: cam?.url
                            if (!url.isNullOrBlank()) RtspCameraPlayer(url, Modifier.fillMaxWidth().height(240.dp))
                            else Text("Не удалось найти RTSP-поток полноэкранной камеры: ${mirrorCameraName}")
                        } else if (mirrorPanel != null) {
                            Text(mirrorPanel?.name ?: "Активная панель")
                            PanelGridPreview(mirrorPanel!!, cameras, settings?.rotation_interval ?: 0L)
                        }
                    }
                    InfoLine("Подключение", if (connected) "Активно" else "Нет")
                    InfoLine("Активная панель", status?.active_panel_name ?: status?.active_panel ?: "—")
                    InfoLine("Полноэкранная камера", status?.fullscreen_camera ?: "—")
                    InfoLine("Камер в конфигурации", status?.cameras?.size?.toString() ?: "—")
                    InfoLine("Активных потоков", status?.active_streams?.toString() ?: "—")
                    InfoLine("Панелей", status?.panels_count?.toString() ?: "—")
                    InfoLine("Температура CPU", status?.cpu_temp_c?.let { "${it} °C" } ?: "Нет данных")
                    InfoLine("Загрузка CPU", status?.cpu_load_percent?.let { "%.1f%%".format(it) } ?: "Нет данных")
                    InfoLine("Load Average (1 мин)", status?.load_average_1m?.let { "%.2f".format(it) } ?: "Нет данных")
                    InfoLine("Время работы", status?.uptime_seconds?.let { formatUptime(it) } ?: "—")
                    InfoLine("Пауза Viewpie", if (status?.paused == true) "Включена" else "Выключена")
                    InfoLine("Физический экран", when (status?.screen_off) {
                        true -> "Выключен"
                        false -> "Включён"
                        null -> "Нет данных"
                    })
                    Text("Состояние обновляется автоматически каждые 5 секунд.")
                    if (status?.cpu_load_percent == null || status?.load_average_1m == null) {
                        Text("Метрики CPU и Load Average требуют обновлённой версии сервера Viewpie.")
                    }
                }
                "Панели" -> {
                    if (selectedPanel != null) {
                        Text(selectedPanel?.name ?: selectedPanel?.id ?: "Просмотр панели", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { selectedPanel = null }) { Text("К списку панелей") }
                        Text("Независимый просмотр на телефоне. HDMI-монитор не переключается.")
                        PanelGridPreview(panel = selectedPanel!!, cameras = cameras, rotationInterval = settings?.rotation_interval ?: 0L)
                    } else {
                        Text("Существующие панели", style = MaterialTheme.typography.titleLarge)
                        if (panels.isEmpty()) Text("Панели не найдены")
                        panels.forEach { panel ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(panel.name ?: panel.id, style = MaterialTheme.typography.titleMedium)
                                    Text("ID: ${panel.id} · камер: ${panel.camera_count ?: "—"}")
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
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Независимый просмотр на телефоне") }
                                    OutlinedButton(
                                        enabled = connected,
                                        onClick = {
                                            scope.launch {
                                                runCatching { api?.activatePanel(panel.id) }
                                                    .onSuccess { message = "Команда переключения HDMI отправлена" }
                                                    .onFailure { message = "Ошибка: ${it.message}" }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Показать эту панель на HDMI") }
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
                        var useMainStream by remember(selectedCamera?.name) { mutableStateOf(false) }
                        val phoneStreamUrl = if (useMainStream) selectedCamera?.fullscreen_url ?: streamUrl else streamUrl
                        if (!streamUrl.isNullOrBlank()) {
                            if (!selectedCamera?.fullscreen_url.isNullOrBlank()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Поток высокого разрешения")
                                    Switch(checked = useMainStream, onCheckedChange = { useMainStream = it })
                                }
                            }
                            RtspCameraPlayer(phoneStreamUrl ?: streamUrl, Modifier.fillMaxWidth().height(240.dp))
                            OutlinedButton(onClick = { phoneFullscreen = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Во весь экран телефона")
                            }
                            if (phoneFullscreen) {
                                FullscreenRtspDialog(phoneStreamUrl ?: streamUrl) { phoneFullscreen = false }
                            }
                        } else Text("Для этой камеры не задан RTSP URL.")
                        Text("Независимый просмотр: HDMI-вывод Viewpie не меняется.")
                    } else {
                        Text("Камеры", style = MaterialTheme.typography.titleLarge)
                        if (cameras.isEmpty()) Text("Камеры не найдены")
                        cameras.forEach { camera ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(camera.name, style = MaterialTheme.typography.titleMedium)
                                    Text(if (camera.running == true) "Декодер используется Viewpie" else "Декодер Viewpie не запущен")
                                    Text("Использований панелями: ${camera.ref_count ?: 0}")
                                    Button(
                                        enabled = !camera.url.isNullOrBlank(),
                                        onClick = { selectedCamera = camera },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Смотреть на телефоне") }
                                    OutlinedButton(
                                        enabled = connected,
                                        onClick = {
                                            scope.launch {
                                                cameraTests = cameraTests + (camera.name to "Проверка…")
                                                runCatching { api?.testCamera(camera.name) }
                                                    .onSuccess { result ->
                                                        cameraTests = cameraTests + (camera.name to when {
                                                            result == null -> "Нет ответа API"
                                                            result.ok -> "Доступна · ${result.latency_ms ?: "?"} мс"
                                                            else -> "Ошибка: ${result.error ?: "поток недоступен"}"
                                                        })
                                                    }
                                                    .onFailure {
                                                        cameraTests = cameraTests + (camera.name to "Ошибка: ${it.message}")
                                                    }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Проверить подключение камеры") }
                                    cameraTests[camera.name]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    OutlinedButton(
                                        enabled = connected,
                                        onClick = {
                                            scope.launch {
                                                val seconds = fullscreenDuration.toLongOrNull()
                                                if (seconds == null || seconds < 0) {
                                                    message = "Укажите длительность от 0 секунд"
                                                } else {
                                                    val body = if (seconds == 0L) mapOf("camera" to camera.name)
                                                        else mapOf("camera" to camera.name, "duration_seconds" to seconds)
                                                    runCatching { api?.fullscreen(body) }
                                                        .onSuccess { message = "Камера отправлена на HDMI" }
                                                        .onFailure { message = "Не удалось включить fullscreen: ${it.message}" }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Показать на HDMI во весь экран") }
                                }
                            }
                        }
                    }
                }
                "Монитор" -> {
                    Text("Управление HDMI-монитором", style = MaterialTheme.typography.titleLarge)
                    InfoLine("Состояние", if (status?.paused == true) "Пауза" else "Активен")
                    Button(
                        enabled = connected && status?.paused == true,
                        onClick = {
                            scope.launch {
                                runCatching { api?.resume() }
                                    .onSuccess { message = "Вывод возобновлён"; runCatching { api?.let { refresh(it) } } }
                                    .onFailure { message = "Ошибка: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Возобновить вывод") }
                    OutlinedButton(
                        enabled = connected && status?.paused != true,
                        onClick = {
                            scope.launch {
                                runCatching { api?.pause() }
                                    .onSuccess { message = "Пауза Viewpie включена"; runCatching { api?.let { refresh(it) } } }
                                    .onFailure { message = "Ошибка: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Пауза / чёрный кадр Viewpie") }
                    OutlinedButton(
                        enabled = connected && status?.paused != true,
                        onClick = {
                            scope.launch {
                                runCatching { api?.grid() }
                                    .onSuccess { message = "Возврат к сетке выполнен"; runCatching { api?.let { refresh(it) } } }
                                    .onFailure { message = "Ошибка: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Вернуться из fullscreen к сетке") }
                    OutlinedButton(
                        enabled = connected,
                        onClick = {
                            scope.launch {
                                runCatching { api?.let { refresh(it) } }
                                    .onSuccess { message = "Состояние обновлено" }
                                    .onFailure { message = "Ошибка: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Обновить состояние") }
                    Text("Для запуска камеры на HDMI выберите её в разделе «Камеры».")
                    Text("Пауза Viewpie выводит чёрный кадр и останавливает декодеры; это не отключение питания дисплея.")
                    HorizontalDivider()
                    Text("Перезагрузка Raspberry Pi", style = MaterialTheme.typography.titleMedium)
                    if (status?.restart_available == true) {
                        OutlinedTextField(
                            value = restartToken, onValueChange = { restartToken = it },
                            label = { Text("Токен перезагрузки") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = connected && restartToken.isNotBlank(),
                            onClick = { showRestartConfirmation = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Перезагрузить Raspberry Pi") }
                    } else {
                        Text("Сервер не сообщил о доступности защищённого API перезагрузки. Нужна серверная версия с VIEWPIE_RESTART_TOKEN.")
                    }
                }
                "Настройки" -> {
                    Text("Настройки Viewpie", style = MaterialTheme.typography.titleLarge)
                    val current = settings
                    if (current == null) {
                        Text("Настройки загружаются после подключения.")
                        Button(enabled = connected, onClick = {
                            scope.launch {
                                runCatching { api?.settings() }
                                    .onSuccess { settings = it }
                                    .onFailure { message = "Ошибка загрузки настроек: ${it.message}" }
                            }
                        }) { Text("Загрузить настройки") }
                    } else {
                        Text("Интервал сетки / переключения: ${current.rotation_interval} с")
                        var rotationText by remember(current.rotation_interval) { mutableStateOf(current.rotation_interval.toString()) }
                        OutlinedTextField(
                            value = rotationText, onValueChange = { rotationText = it.filter(Char::isDigit) },
                            label = { Text("Интервал ротации, секунды") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Таймаут полноэкранного режима: ${current.fullscreen_timeout} с")
                        var timeoutText by remember(current.fullscreen_timeout) { mutableStateOf(current.fullscreen_timeout.toString()) }
                        OutlinedTextField(
                            value = timeoutText, onValueChange = { timeoutText = it.filter(Char::isDigit) },
                            label = { Text("Таймаут fullscreen, секунды") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        var spacing by remember(current.spacing) { mutableFloatStateOf(current.spacing.toFloat()) }
                        Text("Отступ между камерами: ${spacing.toInt()}")
                        Slider(value = spacing, onValueChange = { spacing = it }, valueRange = 0f..30f, steps = 29)
                        var pauseOffScreen by remember(current.pause_off_screen) { mutableStateOf(current.pause_off_screen) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Пауза, когда экран выключен")
                            Switch(checked = pauseOffScreen, onCheckedChange = { pauseOffScreen = it })
                        }
                        var background by remember(current.background) { mutableStateOf(current.background) }
                        OutlinedTextField(
                            value = background, onValueChange = { background = it },
                            label = { Text("Цвет фона (например #000000)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = connected && !savingSettings,
                            onClick = {
                                val rotation = rotationText.toLongOrNull()
                                val timeout = timeoutText.toLongOrNull()
                                if (rotation == null || timeout == null || background.isBlank()) {
                                    message = "Проверьте значения настроек"
                                } else {
                                    scope.launch {
                                        savingSettings = true
                                        val patch = mapOf(
                                            "rotation_interval" to rotation,
                                            "fullscreen_timeout" to timeout,
                                            "spacing" to spacing.toInt(),
                                            "pause_off_screen" to pauseOffScreen,
                                            "background" to background
                                        )
                                        runCatching { api?.updateSettings(patch) }
                                            .onSuccess {
                                                settings = SettingsDto(spacing.toInt(), pauseOffScreen, background, rotation, timeout)
                                                message = "Настройки сохранены"
                                            }
                                            .onFailure { message = "Ошибка сохранения: ${it.message}" }
                                        savingSettings = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (savingSettings) "Сохранение…" else "Сохранить настройки") }
                    }
                    HorizontalDivider()
                    Button(
                        enabled = connected,
                        onClick = {
                            scope.launch {
                                runCatching { api?.reload() }
                                    .onSuccess { message = "Конфигурация перечитана"; runCatching { api?.let { refresh(it) } } }
                                    .onFailure { message = "Ошибка перечитывания: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Перечитать конфигурацию Viewpie") }
                    Text("Адрес и порт сохраняются на телефоне. Пароли камер не показываются в интерфейсе.")
                }
            }
        }
    }
}

@Composable
private fun PanelGridPreview(panel: PanelDetailDto, cameras: List<CameraDto>, rotationInterval: Long) {
    val rows = remember(panel.layout) {
        panel.layout.orEmpty().lines()
            .map { line -> line.trim().split(Regex("\\s+")).filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }
    }
    val feedBySlot = remember(panel.viewports) { panel.viewports.associate { it.slot to it.feeds } }
    val cameraByName = remember(cameras) { cameras.associateBy { it.name } }

    if (rows.isEmpty()) {
        Text("В конфигурации панели не найдена сетка layout")
        return
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 680.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        rows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { slot ->
                    PanelSlot(slot, feedBySlot[slot].orEmpty(), cameraByName, rotationInterval)
                }
            }
        }
    }
}

@Composable
private fun RowScope.PanelSlot(
    slot: String,
    feeds: List<String>,
    cameraByName: Map<String, CameraDto>,
    rotationInterval: Long
) {
    var selectedIndex by remember(feeds) { mutableIntStateOf(0) }
    LaunchedEffect(feeds, rotationInterval) {
        if (feeds.size > 1 && rotationInterval > 0) {
            while (true) {
                delay(rotationInterval.coerceAtLeast(1) * 1000L)
                selectedIndex = (selectedIndex + 1) % feeds.size
            }
        }
    }
    val feedName = feeds.getOrNull(selectedIndex)
    val camera = feedName?.let { cameraByName[it] }
    Card(Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.fillMaxSize()) {
            if (!camera?.url.isNullOrBlank()) {
                RtspCameraPlayer(camera!!.url!!, Modifier.fillMaxWidth().weight(1f))
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).padding(6.dp), verticalArrangement = Arrangement.Center) {
                    Text("Слот $slot", style = MaterialTheme.typography.titleMedium)
                    Text(feedName ?: "Камера не назначена")
                }
            }
            if (feeds.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selectedIndex = (selectedIndex - 1 + feeds.size) % feeds.size }) { Text("‹") }
                    Text("${selectedIndex + 1}/${feeds.size}", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { selectedIndex = (selectedIndex + 1) % feeds.size }) { Text("›") }
                }
            } else if (feedName != null) {
                Text(feedName, modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
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

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return if (days > 0) "${days} д ${hours} ч" else "${hours} ч ${minutes} мин"
}


@Composable
private fun FullscreenRtspDialog(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                RtspCameraPlayer(url, Modifier.fillMaxSize())
                FilledTonalButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) { Text("Закрыть") }
            }
        }
    }
}
