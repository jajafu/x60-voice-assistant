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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jajafu.x60assistant.R
import com.jajafu.x60assistant.diagnostics.DeviceDiagnostics
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceDiagnosticsScreen(
    diagnostics: DeviceDiagnostics,
    m1Diagnostics: M1DiagnosticsSnapshot,
    serviceRunning: Boolean,
    errorMessage: String?,
    onPrimaryAction: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRefresh: () -> Unit,
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
        }
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
