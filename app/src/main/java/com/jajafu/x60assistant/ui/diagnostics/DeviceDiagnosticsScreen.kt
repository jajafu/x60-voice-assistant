package com.jajafu.x60assistant.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jajafu.x60assistant.R
import com.jajafu.x60assistant.diagnostics.DeviceDiagnostics
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsSnapshot
import com.jajafu.x60assistant.data.AssistantSettings
import java.text.SimpleDateFormat
import java.util.Date
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
    onCancelSession: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRefresh: () -> Unit,
    onSaveSettings: (AssistantSettings, String) -> Unit,
    onTestConnection: (AssistantSettings, String) -> Unit,
    onDeleteApiKey: () -> Unit,
    onTestTts: (AssistantSettings) -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearPrivateData: () -> Unit,
) {
    Scaffold(modifier = Modifier.safeDrawingPadding()) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.m0_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            item {
                ServiceCard(
                    serviceRunning = serviceRunning,
                    diagnostics = m1Diagnostics,
                    onPrimaryAction = onPrimaryAction,
                    onPauseOrResume = onPauseOrResume,
                    onCancelSession = onCancelSession,
                )
            }

            item {
                ProviderSettingsCard(
                    saved = assistantSettings,
                    maskedApiKey = maskedApiKey,
                    message = settingsMessage,
                    testingConnection = testingConnection,
                    enabled = !serviceRunning,
                    onSave = onSaveSettings,
                    onTestConnection = onTestConnection,
                    onDeleteApiKey = onDeleteApiKey,
                )
            }

            item {
                VoiceSettingsCard(
                    saved = assistantSettings,
                    message = settingsMessage,
                    enabled = !serviceRunning,
                    onSave = { onSaveSettings(it, "") },
                    onTestTts = onTestTts,
                )
            }

            if (errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.permissions_title)) {
                    StatusRow(
                        label = stringResource(R.string.microphone_permission),
                        ready = diagnostics.microphonePermissionGranted,
                    )
                    HorizontalDivider()
                    StatusRow(
                        label = stringResource(R.string.notification_permission),
                        ready = diagnostics.notificationPermissionGranted,
                    )
                    if (!diagnostics.microphonePermissionGranted || !diagnostics.notificationPermissionGranted) {
                        OutlinedButton(
                            onClick = onOpenAppSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .height(52.dp)
                                .semantics { contentDescription = "開啟應用程式權限設定" },
                        ) {
                            Text(stringResource(R.string.open_app_settings))
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.power_title)) {
                    StatusRow(
                        label = stringResource(R.string.battery_optimization),
                        ready = diagnostics.ignoringBatteryOptimizations,
                        readyText = stringResource(R.string.not_optimized),
                        notReadyText = stringResource(R.string.optimized),
                    )
                    Text(
                        text = stringResource(R.string.battery_settings_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(52.dp)
                            .semantics { contentDescription = "開啟系統電池最佳化設定" },
                    ) {
                        Text(stringResource(R.string.open_battery_settings))
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.device_title)) {
                    InfoRow(stringResource(R.string.device_model), diagnostics.manufacturerAndModel)
                    InfoRow(stringResource(R.string.android_version), diagnostics.androidVersion)
                    InfoRow(stringResource(R.string.api_level), diagnostics.apiLevel.toString())
                    InfoRow(stringResource(R.string.system_build), diagnostics.buildDisplay)
                    InfoRow(stringResource(R.string.abis), diagnostics.supportedAbis)
                    InfoRow(stringResource(R.string.total_memory), diagnostics.totalMemory)
                    InfoRow(stringResource(R.string.available_memory), diagnostics.availableMemory)
                    InfoRow("電池", "${diagnostics.batteryPercent}%")
                    InfoRow("溫度", diagnostics.temperatureCelsius?.let { "$it °C" } ?: "—")
                    InfoRow("Thermal Status", diagnostics.thermalStatus?.toString() ?: "—")
                }
            }

            item {
                SectionCard(title = "喚醒詞與 Model Pack") {
                    InfoRow("喚醒詞", "小助手")
                    InfoRow("KWS", m1Diagnostics.modelId)
                    InfoRow("ASR", assistantSettings.asrModelId)
                    Text(
                        "模型 binary 不隨 Git 發布；啟動前會從 App Private Storage 驗證固定 SHA256，失敗時拒絕載入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = "隱私與診斷") {
                    Text(
                        "原始 PCM 永不落盤或上傳。逐字稿、回答與最近對話只留在記憶體；匯出只含長度、雜湊、狀態與安全錯誤碼。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onExportDiagnostics,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                    ) { Text("預覽規則後匯出安全診斷") }
                    OutlinedButton(
                        onClick = onClearPrivateData,
                        enabled = !serviceRunning,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                    ) { Text("清除設定、Key 與診斷") }
                }
            }

            item {
                SectionCard(title = "關於與開放原始碼") {
                    InfoRow("版本", "1.0.0-rc1")
                    Text(
                        "sherpa-onnx runtime：Apache-2.0。模型權重授權未由上游明示，因此只供本機評估、不得散布含模型 APK。完整聲明見 Repository 的 THIRD_PARTY_NOTICES.md。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.tts_title)) {
                    if (diagnostics.ttsEngines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_tts_engine),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        diagnostics.ttsEngines.forEachIndexed { index, engine ->
                            Text(text = engine, style = MaterialTheme.typography.bodyMedium)
                            if (index != diagnostics.ttsEngines.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.tts_m0_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "重新整理裝置診斷" },
                ) {
                    Text(stringResource(R.string.refresh_diagnostics))
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(
    serviceRunning: Boolean,
    diagnostics: M1DiagnosticsSnapshot,
    onPrimaryAction: () -> Unit,
    onPauseOrResume: () -> Unit,
    onCancelSession: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (serviceRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.service_status),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (serviceRunning) {
                    stringResource(R.string.service_running)
                } else {
                    stringResource(R.string.service_stopped)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = if (serviceRunning) {
                    stringResource(R.string.m1_listening_note)
                } else {
                    stringResource(R.string.m1_stopped_note)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(56.dp)
                    .semantics {
                        contentDescription = if (serviceRunning) "停止 M1 常駐 KWS" else "啟動 M1 常駐 KWS"
                    },
            ) {
                Text(
                    if (serviceRunning) {
                        stringResource(R.string.stop_service)
                    } else {
                        stringResource(R.string.start_service)
                    },
                )
            }
            if (serviceRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onPauseOrResume,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text(if (diagnostics.phase.name == "PAUSED") "恢復" else "暫停")
                    }
                    OutlinedButton(
                        onClick = onCancelSession,
                        enabled = diagnostics.phase.name !in setOf("IDLE_KWS", "PAUSED", "STARTING"),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("取消／閉嘴") }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            InfoRow(stringResource(R.string.kws_phase), diagnostics.phase.name)
            InfoRow(stringResource(R.string.audio_frame_count), diagnostics.audioFrameCount.toString())
            InfoRow(stringResource(R.string.wake_count), diagnostics.wakeCount.toString())
            InfoRow(
                stringResource(R.string.kws_score),
                diagnostics.lastWakeScore?.let {
                    String.format(Locale.ROOT, "%.2f（設定 boost；非 confidence）", it)
                } ?: "—",
            )
            InfoRow(
                stringResource(R.string.last_wake_timestamp),
                diagnostics.lastWakeTimestampEpochMs?.let(::formatTimestamp) ?: "—",
            )
            InfoRow(stringResource(R.string.service_uptime), formatDuration(diagnostics.serviceUptimeMs))
            InfoRow(stringResource(R.string.audio_error_count), diagnostics.audioErrorCount.toString())
            InfoRow(stringResource(R.string.dropped_frame_count), diagnostics.droppedFrameCount.toString())
            InfoRow(stringResource(R.string.audio_source), diagnostics.audioSource)
            InfoRow(
                stringResource(R.string.wake_lock_status),
                if (diagnostics.wakeLockHeld) stringResource(R.string.held) else stringResource(R.string.not_held),
            )
            InfoRow(stringResource(R.string.last_error), diagnostics.lastError?.name ?: "—")
            InfoRow(stringResource(R.string.kws_model_id), diagnostics.modelId)
            InfoRow(stringResource(R.string.kws_model_sha), diagnostics.modelSha256)
            InfoRow(stringResource(R.string.partial_transcript), diagnostics.partialTranscript.ifBlank { "—" })
            InfoRow(stringResource(R.string.final_transcript), diagnostics.finalTranscript.ifBlank { "—" })
            InfoRow(stringResource(R.string.asr_model_id), diagnostics.asrModelId)
            InfoRow(
                stringResource(R.string.asr_rtf),
                diagnostics.asrRtf?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—",
            )
            InfoRow(
                stringResource(R.string.asr_final_latency),
                diagnostics.asrFinalLatencyMs?.let { "$it ms" } ?: "—",
            )
            InfoRow("最後回答", diagnostics.answerPreview.ifBlank { "—" })
            InfoRow("LLM HTTP", diagnostics.llmHttpStatus?.toString() ?: "—")
            InfoRow("LLM First Token", diagnostics.llmFirstTokenLatencyMs?.let { "$it ms" } ?: "—")
            InfoRow("LLM Total", diagnostics.llmTotalLatencyMs?.let { "$it ms" } ?: "—")
            InfoRow("TTS Queue", "${diagnostics.ttsQueueDepth}（High-water ${diagnostics.ttsQueueHighWaterMark}）")
            InfoRow("TTS Engine / Voice", "${diagnostics.ttsEngine} / ${diagnostics.ttsVoice}")
        }
    }
}

@Composable
private fun ProviderSettingsCard(
    saved: AssistantSettings,
    maskedApiKey: String,
    message: String?,
    testingConnection: Boolean,
    enabled: Boolean,
    onSave: (AssistantSettings, String) -> Unit,
    onTestConnection: (AssistantSettings, String) -> Unit,
    onDeleteApiKey: () -> Unit,
) {
    var draft by remember(saved) { mutableStateOf(saved) }
    var apiKey by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    SectionCard(title = "LLM Provider") {
        Text(
            "OpenAI-compatible；Release 僅允許 HTTPS。API Key 由 Android Keystore 加密，欄位留白會保留現有 Key。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            supportingText = { Text("Key 不會寫入 DataStore、Log 或診斷匯出。") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = revealKey, onCheckedChange = { revealKey = it }, enabled = enabled)
            Text("暫時顯示輸入內容")
        }
        ToggleRow("Streaming", draft.streaming, enabled) { draft = draft.copy(streaming = it) }
        NumberSetting("Temperature", draft.temperature.toString(), enabled) {
            it.toDoubleOrNull()?.let { value -> draft = draft.copy(temperature = value.coerceIn(0.0, 2.0)) }
        }
        NumberSetting("Max Output Tokens", draft.maxOutputTokens.toString(), enabled) {
            it.toIntOrNull()?.let { value -> draft = draft.copy(maxOutputTokens = value) }
        }
        NumberSetting("Connect Timeout（秒）", draft.connectTimeoutSeconds.toString(), enabled) {
            it.toIntOrNull()?.let { value -> draft = draft.copy(connectTimeoutSeconds = value) }
        }
        NumberSetting("Read Timeout（秒）", draft.readTimeoutSeconds.toString(), enabled) {
            it.toIntOrNull()?.let { value -> draft = draft.copy(readTimeoutSeconds = value) }
        }
        NumberSetting("Context Turns", draft.contextTurns.toString(), enabled) {
            it.toIntOrNull()?.let { value -> draft = draft.copy(contextTurns = value) }
        }
        OutlinedTextField(
            value = draft.systemInstruction,
            onValueChange = { draft = draft.copy(systemInstruction = it) },
            enabled = enabled,
            label = { Text("System Instruction") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.contains("成功") || message.contains("完成")) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Button(
            onClick = { onTestConnection(draft.normalized(), apiKey) },
            enabled = enabled && !testingConnection && draft.baseUrl.isNotBlank() && draft.model.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
        ) {
            if (testingConnection) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
            } else Text("測試連線")
        }
        OutlinedButton(
            onClick = { onSave(draft.normalized(), apiKey) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
        ) { Text("儲存設定") }
        OutlinedButton(
            onClick = onDeleteApiKey,
            enabled = enabled && maskedApiKey != "未設定",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
        ) { Text("刪除 API Key") }
    }
}

@Composable
private fun VoiceSettingsCard(
    saved: AssistantSettings,
    message: String?,
    enabled: Boolean,
    onSave: (AssistantSettings) -> Unit,
    onTestTts: (AssistantSettings) -> Unit,
) {
    var draft by remember(saved) { mutableStateOf(saved) }
    SectionCard(title = "聆聽、Follow Up 與 TTS") {
        NumberSetting("等待說話（ms）", draft.waitForSpeechMs.toString(), enabled) {
            it.toLongOrNull()?.let { value -> draft = draft.copy(waitForSpeechMs = value) }
        }
        NumberSetting("最短語音（ms）", draft.minSpeechMs.toString(), enabled) {
            it.toLongOrNull()?.let { value -> draft = draft.copy(minSpeechMs = value) }
        }
        NumberSetting("結尾靜音（ms）", draft.trailingSilenceMs.toString(), enabled) {
            it.toLongOrNull()?.let { value -> draft = draft.copy(trailingSilenceMs = value) }
        }
        NumberSetting("最長單句（ms）", draft.maxUtteranceMs.toString(), enabled) {
            it.toLongOrNull()?.let { value -> draft = draft.copy(maxUtteranceMs = value) }
        }
        NumberSetting("Follow Up（秒，0–30）", draft.followUpSeconds.toString(), enabled) {
            it.toIntOrNull()?.let { value -> draft = draft.copy(followUpSeconds = value) }
        }
        Text("語速 ${String.format(Locale.ROOT, "%.1f", draft.speechRate)}", modifier = Modifier.padding(top = 12.dp))
        Slider(
            value = draft.speechRate,
            onValueChange = { draft = draft.copy(speechRate = it) },
            valueRange = 0.5f..2.0f,
            enabled = enabled,
        )
        Text("音高 ${String.format(Locale.ROOT, "%.1f", draft.pitch)}")
        Slider(
            value = draft.pitch,
            onValueChange = { draft = draft.copy(pitch = it) },
            valueRange = 0.5f..2.0f,
            enabled = enabled,
        )
        if (message != null && (message.contains("TTS") || message.contains("語音設定"))) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.contains("完成") || message.contains("儲存")) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = { onTestTts(draft.normalized()) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
        ) { Text("測試 TTS") }
        OutlinedButton(
            onClick = { onSave(draft.normalized()) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
        ) { Text("儲存語音設定") }
    }
}

@Composable
private fun SettingsField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun NumberSetting(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) =
    SettingsField(label, value, enabled, onChange)

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatTimestamp(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ready: Boolean,
    readyText: String = stringResource(R.string.granted),
    notReadyText: String = stringResource(R.string.not_granted),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Text(
            text = if (ready) readyText else notReadyText,
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
