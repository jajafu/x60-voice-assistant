package com.jajafu.x60assistant.ui.diagnostics

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jajafu.x60assistant.data.AssistantSettings
import com.jajafu.x60assistant.diagnostics.DeviceDiagnostics
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsSnapshot
import com.jajafu.x60assistant.orchestration.VoiceState
import com.jajafu.x60assistant.music.MusicAppOption
import com.jajafu.x60assistant.navigation.NavigationAppOption
import com.jajafu.x60assistant.reminder.ScheduledReminder
import com.jajafu.x60assistant.reminder.ReminderFrequency
import com.jajafu.x60assistant.reminder.ReminderRecurrence
import com.jajafu.x60assistant.reminder.label
import com.jajafu.x60assistant.tts.TtsEngineOption
import com.jajafu.x60assistant.tts.TtsVoiceOption
import com.jajafu.x60assistant.tts.displayLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DeviceDiagnosticsScreen(
    diagnostics: DeviceDiagnostics,
    m1Diagnostics: M1DiagnosticsSnapshot,
    serviceRunning: Boolean,
    errorMessage: String?,
    assistantSettings: AssistantSettings,
    maskedApiKey: String,
    settingsMessage: String?,
    testingConnection: Boolean,
    onPrimaryAction: () -> Unit,
    onPauseOrResume: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRefresh: () -> Unit,
    onSaveSettings: (AssistantSettings, String) -> Unit,
    onTestConnection: (AssistantSettings, String) -> Unit,
    onDeleteApiKey: () -> Unit,
    onTestTts: (AssistantSettings) -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearPrivateData: () -> Unit,
    showExactAlarmOnboarding: Boolean,
    onOpenExactAlarmFromOnboarding: () -> Unit,
    onDismissExactAlarmOnboarding: () -> Unit,
    compatibleMusicApps: List<MusicAppOption>,
    onOpenMusicApp: (String, String) -> Unit,
    compatibleNavApps: List<NavigationAppOption>,
    onOpenNavApp: (String, String) -> Unit,
    musicCabinFavoriteAccessGranted: Boolean,
    onOpenMusicCabinFavoriteAccess: () -> Unit,
    ttsEngines: List<TtsEngineOption>,
    ttsVoices: List<TtsVoiceOption>,
    ttsVoicesLoading: Boolean,
    onLoadTtsVoices: (String?) -> Unit,
    scheduledReminders: List<ScheduledReminder>,
    reminderSoundName: String,
    reminderMessage: String?,
    onSaveReminder: (String?, String, Long, ReminderRecurrence?) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onChooseReminderSound: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(AppSection.VOICE) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = section == AppSection.SETTINGS && showDiagnostics) {
        showDiagnostics = false
    }
    LaunchedEffect(serviceRunning) {
        if (serviceRunning) section = AppSection.VOICE
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar {
                AppSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = {
                            section = item
                            showDiagnostics = false
                        },
                        icon = { SectionIcon(item, section == item) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        when (section) {
            AppSection.VOICE -> VoicePage(
                modifier = Modifier.padding(scaffoldPadding),
                diagnostics = m1Diagnostics,
                serviceRunning = serviceRunning,
                wakePhrase = assistantSettings.wakePhrase,
                errorMessage = errorMessage,
                onPrimaryAction = onPrimaryAction,
                onPauseOrResume = onPauseOrResume,
            )

            AppSection.MODELS -> ModelsPage(
                modifier = Modifier.padding(scaffoldPadding),
                saved = assistantSettings,
                maskedApiKey = maskedApiKey,
                message = settingsMessage,
                testingConnection = testingConnection,
                enabled = !serviceRunning,
                onSave = onSaveSettings,
                onTestConnection = onTestConnection,
                onDeleteApiKey = onDeleteApiKey,
            )

            AppSection.SETTINGS -> if (showDiagnostics) DiagnosticsPage(
                modifier = Modifier.padding(scaffoldPadding),
                device = diagnostics,
                runtime = m1Diagnostics,
                serviceRunning = serviceRunning,
                settings = assistantSettings,
                onBack = { showDiagnostics = false },
                onOpenAppSettings = onOpenAppSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onRefresh = onRefresh,
                onExportDiagnostics = onExportDiagnostics,
                onClearPrivateData = onClearPrivateData,
            ) else SettingsPage(
                modifier = Modifier.padding(scaffoldPadding),
                saved = assistantSettings,
                message = settingsMessage,
                enabled = !serviceRunning,
                onSave = onSaveSettings,
                onTestTts = onTestTts,
                compatibleMusicApps = compatibleMusicApps,
                ttsEngines = ttsEngines,
                ttsVoices = ttsVoices,
                ttsVoicesLoading = ttsVoicesLoading,
                onLoadTtsVoices = onLoadTtsVoices,
                onOpenMusicApp = onOpenMusicApp,
                compatibleNavApps = compatibleNavApps,
                onOpenNavApp = onOpenNavApp,
                musicCabinFavoriteAccessGranted = musicCabinFavoriteAccessGranted,
                onOpenMusicCabinFavoriteAccess = onOpenMusicCabinFavoriteAccess,
                onOpenDiagnostics = { showDiagnostics = true },
            )

            AppSection.REMINDERS -> RemindersPage(
                modifier = Modifier.padding(scaffoldPadding),
                reminders = scheduledReminders,
                exactAlarmPermissionGranted = diagnostics.exactAlarmPermissionGranted,
                soundName = reminderSoundName,
                message = reminderMessage,
                onSave = onSaveReminder,
                onDelete = onDeleteReminder,
                onChooseSound = onChooseReminderSound,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            )

        }
    }

    if (showExactAlarmOnboarding) {
        AlertDialog(
            onDismissRequest = onDismissExactAlarmOnboarding,
            title = { Text("讓提醒準時響起") },
            text = {
                Text("小助手需要 Android 的「鬧鐘與提醒」權限，才能在省電或鎖屏時準時播放鈴聲與語音。你也可以稍後到「設定」中的診斷頁開啟。")
            },
            confirmButton = {
                Button(onClick = onOpenExactAlarmFromOnboarding) {
                    Text("前往授權")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissExactAlarmOnboarding) {
                    Text("稍後再說")
                }
            },
        )
    }
}

private enum class AppSection(val label: String) {
    VOICE("對話"),
    REMINDERS("提醒"),
    SETTINGS("設定"),
    MODELS("模型"),
}

@Composable
private fun SectionIcon(section: AppSection, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = section.label },
    ) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (section) {
            AppSection.VOICE -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .36f, size.height * .12f),
                    size = androidx.compose.ui.geometry.Size(size.width * .28f, size.height * .50f),
                    cornerRadius = CornerRadius(size.width * .14f),
                    style = stroke,
                )
                drawLine(color, Offset(size.width * .24f, size.height * .50f), Offset(size.width * .76f, size.height * .50f), stroke.width)
                drawLine(color, Offset(size.width * .50f, size.height * .62f), Offset(size.width * .50f, size.height * .82f), stroke.width)
                drawLine(color, Offset(size.width * .34f, size.height * .82f), Offset(size.width * .66f, size.height * .82f), stroke.width)
            }

            AppSection.SETTINGS -> {
                listOf(.28f, .50f, .72f).forEachIndexed { index, y ->
                    drawLine(color, Offset(size.width * .16f, size.height * y), Offset(size.width * .84f, size.height * y), stroke.width)
                    val x = if (index == 1) .62f else .38f
                    drawCircle(color, radius = 3.dp.toPx(), center = Offset(size.width * x, size.height * y))
                }
            }

            AppSection.REMINDERS -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * .34f,
                    center = center,
                    style = stroke,
                )
                drawLine(color, center, Offset(size.width * .50f, size.height * .30f), stroke.width, StrokeCap.Round)
                drawLine(color, center, Offset(size.width * .70f, size.height * .58f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * .25f, size.height * .17f), Offset(size.width * .13f, size.height * .29f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * .75f, size.height * .17f), Offset(size.width * .87f, size.height * .29f), stroke.width, StrokeCap.Round)
            }

            AppSection.MODELS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .20f, size.height * .20f),
                    size = androidx.compose.ui.geometry.Size(size.width * .60f, size.height * .60f),
                    cornerRadius = CornerRadius(size.width * .08f),
                    style = stroke,
                )
                drawCircle(color, radius = 2.dp.toPx(), center = center)
                drawLine(color, Offset(size.width * .10f, size.height * .36f), Offset(size.width * .20f, size.height * .36f), stroke.width)
                drawLine(color, Offset(size.width * .80f, size.height * .36f), Offset(size.width * .90f, size.height * .36f), stroke.width)
                drawLine(color, Offset(size.width * .10f, size.height * .64f), Offset(size.width * .20f, size.height * .64f), stroke.width)
                drawLine(color, Offset(size.width * .80f, size.height * .64f), Offset(size.width * .90f, size.height * .64f), stroke.width)
            }
        }
    }
}

@Composable
private fun VoicePage(
    modifier: Modifier,
    diagnostics: M1DiagnosticsSnapshot,
    serviceRunning: Boolean,
    wakePhrase: String,
    errorMessage: String?,
    onPrimaryAction: () -> Unit,
    onPauseOrResume: () -> Unit,
) {
    val phase = diagnostics.phase
    val transcript = diagnostics.partialTranscript.ifBlank { diagnostics.finalTranscript }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$wakePhrase 語音助理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (serviceRunning) "隨時可以說「$wakePhrase」" else "本機喚醒，雲端回答",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(if (serviceRunning) "運作中" else "未啟動", serviceRunning)
            }
        }

        if (errorMessage != null) item { MessageCard(errorMessage, error = true) }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (serviceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VoiceOrb(phase, serviceRunning)
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = stateTitle(phase, serviceRunning),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stateHint(phase, serviceRunning, wakePhrase),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (!serviceRunning) {
                        Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) {
                            Text("啟動語音助理")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(onClick = onPauseOrResume, modifier = Modifier.weight(1f).height(52.dp)) {
                                Text(if (phase == VoiceState.PAUSED) "恢復聆聽" else "暫停聆聽")
                            }
                            OutlinedButton(onClick = onPrimaryAction, modifier = Modifier.weight(1f).height(52.dp)) {
                                Text("停止服務")
                            }
                        }
                    }
                }
            }
        }

        item { Text("對話", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }

        if (transcript.isBlank() && diagnostics.answerPreview.isBlank()) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp).padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (serviceRunning) "喚醒後，你說的話與回答會顯示在這裡。" else "啟動後，這裡會成為乾淨的即時語音畫面。",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            if (transcript.isNotBlank()) {
                item { ConversationBubble("你${if (diagnostics.partialTranscript.isNotBlank()) " · 辨識中" else ""}", transcript, user = true) }
            }
            if (diagnostics.answerPreview.isNotBlank()) {
                item { ConversationBubble(wakePhrase, diagnostics.answerPreview, user = false) }
            }
        }

    }
}

@Composable
private fun VoiceOrb(phase: VoiceState, running: Boolean) {
    val color = when {
        !running -> MaterialTheme.colorScheme.onSurfaceVariant
        phase == VoiceState.ERROR -> MaterialTheme.colorScheme.error
        phase == VoiceState.SPEAKING -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().semantics { contentDescription = stateTitle(phase, running) }) {
            drawCircle(color.copy(alpha = .14f))
            drawCircle(color.copy(alpha = .18f), radius = size.minDimension * .36f)
            val heights = when (phase) {
                VoiceState.LISTENING, VoiceState.FOLLOW_UP -> listOf(.24f, .52f, .78f, .44f, .64f, .30f)
                VoiceState.THINKING, VoiceState.ROUTING, VoiceState.TRANSCRIBING -> listOf(.30f, .44f, .56f, .68f, .56f, .44f)
                VoiceState.SPEAKING -> listOf(.62f, .34f, .76f, .48f, .68f, .38f)
                else -> listOf(.20f, .28f, .36f, .36f, .28f, .20f)
            }
            val barWidth = size.minDimension * .055f
            val gap = size.minDimension * .07f
            val total = heights.size * barWidth + (heights.size - 1) * gap
            heights.forEachIndexed { index, fraction ->
                val x = (size.width - total) / 2 + index * (barWidth + gap) + barWidth / 2
                val half = size.height * fraction * .25f
                drawLine(color, Offset(x, center.y - half), Offset(x, center.y + half), barWidth, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ConversationBubble(label: String, text: String, user: Boolean) {
    Surface(
        color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp).padding(24.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ModelsPage(
    modifier: Modifier,
    saved: AssistantSettings,
    maskedApiKey: String,
    message: String?,
    testingConnection: Boolean,
    enabled: Boolean,
    onSave: (AssistantSettings, String) -> Unit,
    onTestConnection: (AssistantSettings, String) -> Unit,
    onDeleteApiKey: () -> Unit,
) {
    // 不以 saved 為 remember key：設定流每次發布都是新物件，否則外部更新會清掉整份草稿。
    var draft by remember { mutableStateOf(saved) }
    var lastLoaded by remember { mutableStateOf(saved) }
    // API Key 只寫不讀，刻意不隨設定流重載，避免蓋掉使用者正在輸入的新 Key。
    var apiKey by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    LaunchedEffect(saved) {
        // 只有在使用者尚未改動時才重新載入，避免編輯到一半被外部更新還原。
        if (saved != lastLoaded && draft == lastLoaded) {
            draft = saved
            lastLoaded = saved
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("模型", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "設定 LLM 服務；本機語音辨識固定使用內建 X-ASR。首次啟動語音服務時會解壓模型，請預留約 175 MB 空間。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!enabled) item { MessageCard("請先到「對話」停止服務，再修改模型設定。") }
        if (message != null) item { MessageCard(message, error = message.contains("失敗") || message.contains("無效")) }
        item {
            SectionCard("LLM Provider") {
                StatusPill(if (saved.connectionVerified) "已驗證" else "尚未驗證", saved.connectionVerified)
                SettingsField("Base URL", draft.baseUrl, enabled) { draft = draft.copy(baseUrl = it) }
                SettingsField("Model", draft.model, enabled) { draft = draft.copy(model = it) }
                SettingsField("Endpoint", draft.endpoint, enabled) { draft = draft.copy(endpoint = it) }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    enabled = enabled,
                    label = { Text("API Key（目前：$maskedApiKey）") },
                    singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    supportingText = { Text("留白會保留現有 Key；Key 不寫入 Log 或診斷。") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                ToggleRow("暫時顯示新輸入的 Key", revealKey, enabled) { revealKey = it }
                Button(
                    onClick = { onTestConnection(draft.normalized(), apiKey) },
                    enabled = enabled && !testingConnection && draft.baseUrl.isNotBlank() && draft.model.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
                ) {
                    if (testingConnection) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Text("測試 LLM 連線")
                }
                TextButton(
                    onClick = onDeleteApiKey,
                    enabled = enabled && maskedApiKey != "未設定",
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp),
                ) { Text("刪除 API Key") }
            }
        }

        item {
            Button(
                onClick = { onSave(draft.normalized(), apiKey) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("儲存 LLM 設定") }
        }
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier,
    saved: AssistantSettings,
    message: String?,
    enabled: Boolean,
    onSave: (AssistantSettings, String) -> Unit,
    onTestTts: (AssistantSettings) -> Unit,
    compatibleMusicApps: List<MusicAppOption>,
    onOpenMusicApp: (String, String) -> Unit,
    compatibleNavApps: List<NavigationAppOption>,
    onOpenNavApp: (String, String) -> Unit,
    musicCabinFavoriteAccessGranted: Boolean,
    onOpenMusicCabinFavoriteAccess: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    ttsEngines: List<TtsEngineOption>,
    ttsVoices: List<TtsVoiceOption>,
    ttsVoicesLoading: Boolean,
    onLoadTtsVoices: (String?) -> Unit,
) {
    // 不以 saved 為 remember key：設定流每次發布都是新物件，否則外部更新會清掉整份草稿。
    var draft by remember { mutableStateOf(saved) }
    var lastLoaded by remember { mutableStateOf(saved) }
    val numberFields = remember { NumberSettingsFields(saved) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val wakeError = wakePhraseError(draft.wakePhrase)
    val wakeAcknowledgementError = wakeAcknowledgementError(draft.wakeAcknowledgement)
    LaunchedEffect(saved) {
        // 只有在使用者尚未改動任何欄位時才重新載入，避免編輯到一半被外部更新還原。
        if (saved != lastLoaded && draft == lastLoaded && numberFields.isPristine(lastLoaded)) {
            draft = saved
            numberFields.assign(saved)
            lastLoaded = saved
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("設定", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "日常操作留在「對話」；只有需要調整時才來這裡。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (!enabled) item { MessageCard("請先到「對話」停止服務，再修改設定。") }
        if (message != null) item { MessageCard(message, error = message.contains("失敗") || message.contains("無效")) }

        item {
            SectionCard("喚醒方式") {
                ToggleRow("開啟 App 後自動開始聆聽", draft.autoStartListening, enabled) {
                    draft = draft.copy(autoStartListening = it)
                }
                Text(
                    "儲存後，下次開啟 App 且服務未啟動時，會自動開始聆聽；首次使用仍需授權麥克風與通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = draft.wakePhrase,
                    onValueChange = { draft = draft.copy(wakePhrase = it) },
                    enabled = enabled,
                    label = { Text("喚醒詞") },
                    supportingText = {
                        Text(wakeError ?: "輸入 2–${AssistantSettings.MAX_WAKE_PHRASE_LENGTH} 個常用中文字；儲存後重新啟動服務生效。")
                    },
                    isError = wakeError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "自訂詞會在手機上轉成 KWS 發音，不會送到網路。建議使用 3–5 個音節清楚、日常不常誤觸的詞。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = draft.wakeAcknowledgement,
                    onValueChange = { draft = draft.copy(wakeAcknowledgement = it) },
                    enabled = enabled,
                    label = { Text("喚醒語音回覆") },
                    supportingText = {
                        Text(wakeAcknowledgementError ?: "例如：我在。聽到回覆播完後即可說指令；開始聆聽前會清掉回覆聲音。")
                    },
                    isError = wakeAcknowledgementError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }

        item {
            SectionCard("音樂 App") {
                val selected = compatibleMusicApps.firstOrNull {
                    it.packageName == draft.musicPackageName && it.serviceName == draft.musicServiceName
                }
                val selectedLabel = when {
                    draft.musicPackageName.isBlank() -> ""
                    selected != null -> selected.appName
                    else -> draft.musicAppName.ifBlank { draft.musicPackageName }
                }
                val musicOptions = compatibleMusicApps.map {
                    AppChoice(appName = it.appName, packageName = it.packageName, detail = it.packageName)
                }
                AppPickerField(
                    label = "目前選用",
                    placeholder = "未啟用音樂控制",
                    selectedLabel = selectedLabel,
                    options = musicOptions,
                    enabled = enabled,
                    supportingText = "只列出有公開媒體控制服務的 App；畫面顯示名稱，實際以套件與服務 ID 連線。" +
                        if (draft.musicPackageName.isNotBlank() && selected == null) {
                            "「${draft.musicAppName.ifBlank { draft.musicPackageName }}」目前未偵測到，請重新選擇。"
                        } else {
                            ""
                        },
                    errorText = if (compatibleMusicApps.isEmpty()) {
                        "目前沒有偵測到相容的音樂 App。安裝或更新播放器後回到此頁重試。"
                    } else {
                        null
                    },
                    onSelect = { choice ->
                        draft = if (choice == null) {
                            draft.copy(musicAppName = "", musicPackageName = "", musicServiceName = "")
                        } else {
                            val app = compatibleMusicApps.first {
                                it.packageName == choice.packageName
                            }
                            draft.copy(
                                musicAppName = app.appName,
                                musicPackageName = app.packageName,
                                musicServiceName = app.serviceName,
                            )
                        }
                    },
                )
                if (draft.musicPackageName.isNotBlank()) {
                    AppDetailText(
                        "首次使用請開啟音樂 App，依畫面完成它需要的授權，並手動播放一首歌。一般播放控制不需要通知存取權。",
                    )
                    OutlinedButton(
                        onClick = { onOpenMusicApp(draft.musicPackageName, draft.musicAppName) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
                    ) {
                        Text("開啟${draft.musicAppName.ifBlank { "音樂 App" }}完成首次設定")
                    }
                    if (draft.musicPackageName == "com.jajafu.musiccabin") {
                        Text(
                            if (musicCabinFavoriteAccessGranted) "MusicCabin 收藏與單曲循環控制：通知存取權已開啟。"
                            else "語音收藏及單曲循環需要通知存取權；授權後小幫手才能送出指令。此權限也允許小幫手讀取其他通知。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (!musicCabinFavoriteAccessGranted) {
                            OutlinedButton(
                                onClick = onOpenMusicCabinFavoriteAccess,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
                            ) { Text("開啟 MusicCabin 控制所需的通知存取權") }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("導航 App") {
                val selectedNav = compatibleNavApps.firstOrNull { it.packageName == draft.navPackageName }
                val selectedNavLabel = when {
                    draft.navPackageName.isBlank() -> ""
                    selectedNav != null -> selectedNav.appName
                    else -> draft.navAppName.ifBlank { draft.navPackageName }
                }
                val navOptions = compatibleNavApps.map {
                    AppChoice(appName = it.appName, packageName = it.packageName, detail = it.packageName)
                }
                AppPickerField(
                    label = "目前選用",
                    placeholder = "未啟用語音導航",
                    selectedLabel = selectedNavLabel,
                    options = navOptions,
                    enabled = enabled,
                    supportingText = "說「導航到台北101」時，小助手會開啟此 App 的路線預覽頁，" +
                        "最後的開始導航仍需要你點一下。" +
                        if (draft.navPackageName.isNotBlank() && selectedNav == null) {
                            "「${draft.navAppName.ifBlank { draft.navPackageName }}」目前未偵測到，請重新選擇。"
                        } else {
                            ""
                        },
                    errorText = if (compatibleNavApps.isEmpty()) {
                        "目前沒有偵測到可用的導航 App。安裝後回到此頁重試。"
                    } else {
                        null
                    },
                    onSelect = { choice ->
                        draft = if (choice == null) {
                            draft.copy(navAppName = "", navPackageName = "")
                        } else {
                            val app = compatibleNavApps.first { it.packageName == choice.packageName }
                            draft.copy(navAppName = app.appName, navPackageName = app.packageName)
                        }
                    },
                )
                if (draft.navPackageName.isNotBlank()) {
                    AppDetailText(
                        "第一次使用請先開啟地圖 App 完成歡迎畫面與位置授權，" +
                            "之後語音才能直接帶入路線。",
                    )
                    OutlinedButton(
                        onClick = { onOpenNavApp(draft.navPackageName, draft.navAppName) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
                    ) {
                        Text("開啟${draft.navAppName.ifBlank { "導航 App" }}完成首次設定")
                    }
                }
            }
        }

        item {
            SectionCard("語音與追問") {
                TtsPicker(
                    draft = draft,
                    engines = ttsEngines,
                    voices = ttsVoices,
                    voicesLoading = ttsVoicesLoading,
                    enabled = enabled,
                    onSelectEngine = { engine ->
                        draft = draft.copy(ttsEngine = engine, ttsVoice = "")
                    },
                    onSelectVoice = { voice -> draft = draft.copy(ttsVoice = voice) },
                    onLoadVoices = onLoadTtsVoices,
                )
                Text("TTS 音量 ${String.format(Locale.ROOT, "%.2f", draft.ttsVolume)}", modifier = Modifier.padding(top = 16.dp))
                Slider(
                    draft.ttsVolume,
                    { draft = draft.copy(ttsVolume = it) },
                    valueRange = AssistantSettings.TTS_VOLUME_RANGE,
                    enabled = enabled,
                )
                Text(
                    "音量套用到 TTS 引擎，部分引擎可能忽略。1.00 是引擎預設值，超過會放大且可能破音。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("語速 ${String.format(Locale.ROOT, "%.1f", draft.speechRate)}", modifier = Modifier.padding(top = 16.dp))
                Slider(draft.speechRate, { draft = draft.copy(speechRate = it) }, valueRange = 0.5f..2.0f, enabled = enabled)
                Text("音高 ${String.format(Locale.ROOT, "%.1f", draft.pitch)}")
                Slider(draft.pitch, { draft = draft.copy(pitch = it) }, valueRange = 0.5f..2.0f, enabled = enabled)
                OutlinedButton(
                    onClick = { onTestTts(numberFields.applyTo(draft).normalized()) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                ) { Text("測試語音") }
                Text(
                    "引擎、音調與音量在啟動語音服務時套用，修改後請停止再重新啟動語音服務。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                NumberSetting(
                    label = "Follow Up（${numberFields.followUpSeconds.rangeLabel}）",
                    state = numberFields.followUpSeconds,
                    enabled = enabled,
                    hint = "設為 0 可關閉回答後的追問等待。",
                )
            }
        }

        item {
            SectionCard("進階設定") {
                OutlinedButton(
                    onClick = { advancedExpanded = !advancedExpanded },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (advancedExpanded) "收起進階設定" else "展開進階設定") }
                if (advancedExpanded) {
                    ToggleRow("Streaming", draft.streaming, enabled) { draft = draft.copy(streaming = it) }
                    NumberSetting(
                        label = "Temperature（${numberFields.temperature.rangeLabel}）",
                        state = numberFields.temperature,
                        enabled = enabled,
                        keyboardType = KeyboardType.Decimal,
                    )
                    NumberSetting(
                        label = "Max Output Tokens（${numberFields.maxOutputTokens.rangeLabel}）",
                        state = numberFields.maxOutputTokens,
                        enabled = enabled,
                    )
                    NumberSetting(
                        label = "Connect Timeout（${numberFields.connectTimeoutSeconds.rangeLabel}）",
                        state = numberFields.connectTimeoutSeconds,
                        enabled = enabled,
                    )
                    NumberSetting(
                        label = "Read Timeout（${numberFields.readTimeoutSeconds.rangeLabel}）",
                        state = numberFields.readTimeoutSeconds,
                        enabled = enabled,
                    )
                    NumberSetting(
                        label = "Context Turns（${numberFields.contextTurns.rangeLabel}）",
                        state = numberFields.contextTurns,
                        enabled = enabled,
                        hint = "設為 0 則每次提問都不帶入先前對話。",
                    )
                    NumberSetting(
                        label = "等待說話（${numberFields.waitForSpeechSeconds.rangeLabel}）",
                        state = numberFields.waitForSpeechSeconds,
                        enabled = enabled,
                        keyboardType = KeyboardType.Decimal,
                    )
                    NumberSetting(
                        label = "最短語音（${numberFields.minSpeechMs.rangeLabel}）",
                        state = numberFields.minSpeechMs,
                        enabled = enabled,
                    )
                    NumberSetting(
                        label = "結尾靜音（${numberFields.trailingSilenceMs.rangeLabel}）",
                        state = numberFields.trailingSilenceMs,
                        enabled = enabled,
                    )
                    NumberSetting(
                        label = "最長單句（${numberFields.maxUtteranceSeconds.rangeLabel}）",
                        state = numberFields.maxUtteranceSeconds,
                        enabled = enabled,
                        keyboardType = KeyboardType.Decimal,
                    )
                    OutlinedTextField(
                        value = draft.systemInstruction,
                        onValueChange = { draft = draft.copy(systemInstruction = it) },
                        enabled = enabled,
                        label = { Text("System Instruction") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            SectionCard("診斷與權限") {
                Text("檢查服務狀態、裝置權限與錯誤紀錄。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
                ) { Text("開啟診斷") }
            }
        }

        item {
            Button(
                onClick = { onSave(numberFields.applyTo(draft).normalized(), "") },
                enabled = enabled && wakeError == null && wakeAcknowledgementError == null && numberFields.allValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("儲存設定") }
        }
    }
}

@Composable
private fun RemindersPage(
    modifier: Modifier,
    reminders: List<ScheduledReminder>,
    exactAlarmPermissionGranted: Boolean,
    soundName: String,
    message: String?,
    onSave: (String?, String, Long, ReminderRecurrence?) -> Unit,
    onDelete: (String) -> Unit,
    onChooseSound: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    var addingReminder by rememberSaveable { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ScheduledReminder?>(null) }
    var deleteCandidate by remember { mutableStateOf<ScheduledReminder?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("提醒", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "語音與手動新增的提醒都會集中顯示在這裡。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!exactAlarmPermissionGranted) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("尚未允許精準鬧鐘", fontWeight = FontWeight.SemiBold)
                        Text(
                            "提醒可能無法在鎖屏或省電時準時響起。",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        OutlinedButton(
                            onClick = onOpenExactAlarmSettings,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                        ) { Text("前往授權") }
                    }
                }
            }
        }

        if (message != null) item { MessageCard(message) }

        item {
            SectionCard("鬧鐘鈴聲") {
                Text(soundName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "所有提醒會播放這個鬧鐘鈴聲，並搭配語音播報提醒內容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedButton(
                    onClick = onChooseSound,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                ) { Text("選擇鬧鐘鈴聲") }
            }
        }

        item {
            Button(
                onClick = { addingReminder = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("新增提醒") }
        }

        item {
            Text(
                "待執行提醒（${reminders.size}）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (reminders.isEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("目前沒有待執行提醒", fontWeight = FontWeight.Medium)
                        Text(
                            "你可以按上方新增，或對小助手說「50 分鐘後提醒我吃藥」。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        } else {
            items(reminders, key = { it.id }) { reminder ->
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(reminder.message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            formatReminderTime(reminder.triggerAtEpochMillis),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        reminder.recurrence?.let { recurrence ->
                            Text(
                                recurrence.label(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { editingReminder = reminder },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text("修改") }
                            TextButton(
                                onClick = { deleteCandidate = reminder },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    if (addingReminder || editingReminder != null) {
        ReminderEditorDialog(
            reminder = editingReminder,
            onDismiss = {
                addingReminder = false
                editingReminder = null
            },
            onSave = { id, reminderText, triggerAt, recurrence ->
                onSave(id, reminderText, triggerAt, recurrence)
                addingReminder = false
                editingReminder = null
            },
        )
    }

    deleteCandidate?.let { reminder ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("刪除提醒？") },
            text = { Text("「${reminder.message}」將不會再響起。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(reminder.id)
                    deleteCandidate = null
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ReminderEditorDialog(
    reminder: ScheduledReminder?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Long, ReminderRecurrence?) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val initialTime = remember(reminder?.id) {
        reminder?.let { Instant.ofEpochMilli(it.triggerAtEpochMillis).atZone(zone) }
            ?: ZonedDateTime.now(zone).plusHours(1).withSecond(0).withNano(0)
    }
    var reminderText by remember(reminder?.id) { mutableStateOf(reminder?.message.orEmpty()) }
    var triggerTime by remember(reminder?.id) { mutableStateOf(initialTime) }
    var repeatOption by remember(reminder?.id) {
        mutableStateOf(
            when (reminder?.recurrence?.frequency) {
                ReminderFrequency.DAILY -> ReminderRepeatOption.DAILY
                ReminderFrequency.WEEKLY -> ReminderRepeatOption.WEEKLY
                null -> ReminderRepeatOption.ONCE
            },
        )
    }
    var selectedWeekdays by remember(reminder?.id) {
        mutableStateOf(reminder?.recurrence?.daysOfWeek.orEmpty().toSet())
    }
    val validTime = repeatOption != ReminderRepeatOption.ONCE ||
        triggerTime.toInstant().toEpochMilli() > System.currentTimeMillis()
    val validWeekdays = repeatOption != ReminderRepeatOption.WEEKLY || selectedWeekdays.isNotEmpty()
    val canSave = reminderText.isNotBlank() && validTime && validWeekdays

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reminder == null) "新增提醒" else "修改提醒") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = reminderText,
                    onValueChange = { reminderText = it },
                    label = { Text("提醒內容") },
                    placeholder = { Text("例如：倒垃圾") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "重複",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReminderRepeatOption.entries.forEach { option ->
                        FilterChip(
                            selected = repeatOption == option,
                            onClick = { repeatOption = option },
                            label = { Text(option.label) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                if (repeatOption == ReminderRepeatOption.WEEKLY) {
                    Text(
                        "星期",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    WeekdayPicker(
                        selectedDays = selectedWeekdays,
                        onToggle = { day ->
                            selectedWeekdays = if (day in selectedWeekdays) {
                                selectedWeekdays - day
                            } else {
                                selectedWeekdays + day
                            }
                        },
                    )
                    if (!validWeekdays) {
                        Text(
                            "每週提醒至少要選擇一天。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (repeatOption == ReminderRepeatOption.ONCE) {
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        triggerTime = triggerTime.with(LocalDate.of(year, month + 1, day))
                                    },
                                    triggerTime.year,
                                    triggerTime.monthValue - 1,
                                    triggerTime.dayOfMonth,
                                ).show()
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text(triggerTime.format(DateTimeFormatter.ofPattern("MM/dd"))) }
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    triggerTime = triggerTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                                },
                                triggerTime.hour,
                                triggerTime.minute,
                                DateFormat.is24HourFormat(context),
                            ).show()
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(triggerTime.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                }
                if (!validTime) {
                    Text(
                        "提醒時間必須晚於現在。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val recurrence = when (repeatOption) {
                        ReminderRepeatOption.ONCE -> null
                        ReminderRepeatOption.DAILY -> ReminderRecurrence(
                            frequency = ReminderFrequency.DAILY,
                            hour = triggerTime.hour,
                            minute = triggerTime.minute,
                        )
                        ReminderRepeatOption.WEEKLY -> ReminderRecurrence(
                            frequency = ReminderFrequency.WEEKLY,
                            daysOfWeek = selectedWeekdays.sorted(),
                            hour = triggerTime.hour,
                            minute = triggerTime.minute,
                        )
                    }
                    onSave(
                        reminder?.id,
                        reminderText.trim(),
                        triggerTime.toInstant().toEpochMilli(),
                        recurrence,
                    )
                },
                enabled = canSave,
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private enum class ReminderRepeatOption(val label: String) {
    ONCE("單次"),
    DAILY("每天"),
    WEEKLY("每週"),
}

@Composable
private fun WeekdayPicker(
    selectedDays: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    listOf((1..4).toList(), (5..7).toList()).forEach { rowDays ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowDays.forEach { day ->
                FilterChip(
                    selected = day in selectedDays,
                    onClick = { onToggle(day) },
                    label = {
                        Text(
                            when (day) {
                                1 -> "一"
                                2 -> "二"
                                3 -> "三"
                                4 -> "四"
                                5 -> "五"
                                6 -> "六"
                                else -> "日"
                            },
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
            repeat(4 - rowDays.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun formatReminderTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd（E）HH:mm", Locale.TAIWAN))

@Composable
private fun DiagnosticsPage(
    modifier: Modifier,
    device: DeviceDiagnostics,
    runtime: M1DiagnosticsSnapshot,
    serviceRunning: Boolean,
    settings: AssistantSettings,
    onBack: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRefresh: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearPrivateData: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                Text("返回設定")
            }
            Text("診斷", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("權限、裝置與執行狀態集中在這裡。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard("執行狀態") {
                InfoRow("狀態", runtime.phase.name)
                InfoRow("喚醒次數", runtime.wakeCount.toString())
                InfoRow("服務時間", formatDuration(runtime.serviceUptimeMs))
                InfoRow("Audio Error / Dropped", "${runtime.audioErrorCount} / ${runtime.droppedFrameCount}")
                InfoRow("最後錯誤", runtime.lastError?.name ?: "—")
                InfoRow("LLM HTTP", runtime.llmHttpStatus?.toString() ?: "—")
                InfoRow("LLM 延遲", runtime.llmTotalLatencyMs?.let { "$it ms" } ?: "—")
            }
        }
        item {
            SectionCard("必要權限") {
                StatusRow("麥克風", device.microphonePermissionGranted)
                HorizontalDivider()
                StatusRow("通知", device.notificationPermissionGranted)
                HorizontalDivider()
                StatusRow("精準鬧鐘", device.exactAlarmPermissionGranted)
                if (!device.microphonePermissionGranted || !device.notificationPermissionGranted) {
                    OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) {
                        Text("開啟 App 權限設定")
                    }
                }
                if (!device.exactAlarmPermissionGranted) {
                    OutlinedButton(
                        onClick = onOpenExactAlarmSettings,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                    ) {
                        Text("允許精準鬧鐘")
                    }
                }
            }
        }
        item {
            SectionCard("背景與電源") {
                StatusRow("Android 電池最佳化", device.ignoringBatteryOptimizations, "已忽略", "最佳化中")
                Text(
                    "vivo 自啟動、後台高耗電與最近任務加鎖仍需手動確認。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) {
                    Text("開啟電池設定")
                }
            }
        }
        item {
            SectionCard("語音模型") {
                InfoRow("喚醒詞", settings.wakePhrase)
                InfoRow("KWS", runtime.modelId)
                InfoRow("ASR（設定）", settings.asrModelId)
                InfoRow("ASR（實際載入）", runtime.asrModelId.ifBlank { "—" })
                InfoRow("ASR RTF", runtime.asrRtf?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—")
                InfoRow("TTS", "${runtime.ttsEngine} / ${runtime.ttsVoice}")
            }
        }
        item {
            SectionCard("裝置") {
                InfoRow("型號", device.manufacturerAndModel)
                InfoRow("Android", "${device.androidVersion}（API ${device.apiLevel}）")
                InfoRow("記憶體", "${device.availableMemory} 可用 / ${device.totalMemory}")
                InfoRow("電池 / 溫度", "${device.batteryPercent}% / ${device.temperatureCelsius?.let { "$it °C" } ?: "—"}")
                InfoRow("ABI", device.supportedAbis)
            }
        }
        item {
            SectionCard("隱私與資料") {
                Text("原始 PCM 永不落盤或上傳；逐字稿與回答只留在目前 process 記憶體。")
                OutlinedButton(onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) {
                    Text("匯出安全診斷")
                }
                TextButton(
                    onClick = onClearPrivateData,
                    enabled = !serviceRunning,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp),
                ) { Text("清除設定、Key 與診斷") }
            }
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("重新整理診斷")
            }
        }
    }
}

/**
 * TTS 引擎與 voice 選擇器。引擎清單來自裝置；voice 清單需要綁定引擎才能取得，
 * 因此在選到的引擎改變時才重新載入，避免一進入設定頁就啟動 TTS。
 */
@Composable
private fun TtsPicker(
    draft: AssistantSettings,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    voicesLoading: Boolean,
    enabled: Boolean,
    onSelectEngine: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onLoadVoices: (String?) -> Unit,
) {
    var engineMenuExpanded by remember { mutableStateOf(false) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(draft.ttsEngine) { onLoadVoices(draft.ttsEngine) }

    val selectedEngine = engines.firstOrNull { it.packageName == draft.ttsEngine }
    val engineLabel = when {
        draft.ttsEngine.isBlank() -> "系統預設（${selectedEngine?.label ?: "自動選擇"}）"
        selectedEngine != null -> selectedEngine.label
        else -> "${draft.ttsEngine}（目前未偵測到）"
    }
    val selectedVoice = voices.firstOrNull { it.name == draft.ttsVoice }
    val voiceLabel = when {
        voicesLoading -> "讀取中…"
        draft.ttsVoice.isBlank() -> "自動選擇"
        selectedVoice != null -> selectedVoice.displayLabel()
        else -> draft.ttsVoice
    }

    Text("TTS 引擎", style = MaterialTheme.typography.labelLarge)
    Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        OutlinedButton(
            onClick = { engineMenuExpanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(engineLabel) }
        DropdownMenu(
            expanded = engineMenuExpanded,
            onDismissRequest = { engineMenuExpanded = false },
            modifier = Modifier.fillMaxWidth(.9f),
        ) {
            DropdownMenuItem(
                text = { Text("系統預設（自動選擇）") },
                onClick = {
                    onSelectEngine("")
                    engineMenuExpanded = false
                },
            )
            engines.forEach { engine ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(engine.label)
                            Text(
                                engine.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelectEngine(engine.packageName)
                        engineMenuExpanded = false
                    },
                )
            }
        }
    }

    Text("TTS 音調", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
    Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        OutlinedButton(
            onClick = { voiceMenuExpanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(voiceLabel) }
        DropdownMenu(
            expanded = voiceMenuExpanded,
            onDismissRequest = { voiceMenuExpanded = false },
            modifier = Modifier.fillMaxWidth(.9f),
        ) {
            DropdownMenuItem(
                text = { Text("自動選擇（優先離線中文）") },
                onClick = {
                    onSelectVoice("")
                    voiceMenuExpanded = false
                },
            )
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.displayLabel()) },
                    onClick = {
                        onSelectVoice(voice.name)
                        voiceMenuExpanded = false
                    },
                )
            }
        }
    }
    if (!voicesLoading && voices.isEmpty()) {
        Text(
            "這個引擎沒有回報可用音調。可到系統「文字轉語音輸出」下載語音，或改用其他引擎。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun MessageCard(message: String, error: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(
            message,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    Surface(
        color = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CircleShape,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun NumberSetting(
    label: String,
    state: NumberFieldState,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Number,
    hint: String? = null,
) {
    OutlinedTextField(
        value = state.text,
        onValueChange = state::update,
        enabled = enabled,
        label = { Text(label) },
        supportingText = {
            val message = state.error ?: hint
            if (message != null) Text(message)
        },
        isError = state.error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun StatusRow(
    label: String,
    ready: Boolean,
    readyText: String = "已允許",
    notReadyText: String = "未允許",
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            if (ready) readyText else notReadyText,
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun wakePhraseError(value: String): String? {
    val phrase = value.trim()
    val count = phrase.codePointCount(0, phrase.length)
    return when {
        count !in 2..AssistantSettings.MAX_WAKE_PHRASE_LENGTH -> "請輸入 2–${AssistantSettings.MAX_WAKE_PHRASE_LENGTH} 個中文字"
        phrase.codePoints().anyMatch { Character.UnicodeScript.of(it) != Character.UnicodeScript.HAN } -> "目前只支援中文喚醒詞"
        else -> null
    }
}

private fun wakeAcknowledgementError(value: String): String? {
    val response = value.trim()
    val count = response.codePointCount(0, response.length)
    return when {
        count == 0 -> "請輸入喚醒後要回覆的話"
        count > AssistantSettings.MAX_WAKE_ACKNOWLEDGEMENT_LENGTH ->
            "請勿超過 ${AssistantSettings.MAX_WAKE_ACKNOWLEDGEMENT_LENGTH} 個字"
        else -> null
    }
}

private fun stateTitle(phase: VoiceState, running: Boolean): String = when {
    !running -> "準備好開始"
    phase == VoiceState.STARTING -> "正在準備"
    phase == VoiceState.IDLE_KWS -> "等待喚醒"
    phase == VoiceState.WAKE_ACK -> "已喚醒"
    phase == VoiceState.LISTENING -> "正在聆聽"
    phase == VoiceState.TRANSCRIBING -> "正在辨識"
    phase == VoiceState.EXECUTING_ACTION -> "正在執行操作"
    phase in setOf(VoiceState.ROUTING, VoiceState.THINKING) -> "正在思考"
    phase == VoiceState.SPEAKING -> "正在回答"
    phase == VoiceState.FOLLOW_UP -> "等待追問"
    phase == VoiceState.PAUSED -> "已暫停"
    phase == VoiceState.RECOVERING -> "正在恢復"
    phase == VoiceState.ERROR -> "需要處理"
    else -> "語音助理"
}

private fun stateHint(phase: VoiceState, running: Boolean, wakePhrase: String): String = when {
    !running -> "啟動後，畫面會即時顯示你說的話與回答。"
    phase == VoiceState.IDLE_KWS -> "說「$wakePhrase」開始對話"
    phase == VoiceState.LISTENING -> "請直接說出你的問題"
    phase == VoiceState.THINKING || phase == VoiceState.ROUTING -> "回答會即時顯示在下方"
    phase == VoiceState.EXECUTING_ACTION -> "正在等待 App 回應"
    phase == VoiceState.SPEAKING -> "不需要時可立即停止回答"
    phase == VoiceState.FOLLOW_UP -> "現在可直接追問，不必再說喚醒詞"
    phase == VoiceState.PAUSED -> "麥克風目前沒有聆聽"
    phase == VoiceState.ERROR -> "請到「設定」中的診斷頁查看最後錯誤"
    else -> "請稍候"
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}

internal fun formatMillisecondsAsSeconds(milliseconds: Long): String =
    if (milliseconds % 1_000L == 0L) {
        (milliseconds / 1_000L).toString()
    } else {
        String.format(Locale.ROOT, "%.3f", milliseconds / 1_000.0).trimEnd('0').trimEnd('.')
    }
