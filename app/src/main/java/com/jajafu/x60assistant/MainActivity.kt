package com.jajafu.x60assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jajafu.x60assistant.data.AssistantSettings
import com.jajafu.x60assistant.data.SettingsRepository
import com.jajafu.x60assistant.diagnostics.DeviceDiagnostics
import com.jajafu.x60assistant.diagnostics.DeviceDiagnosticsCollector
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsRepository
import com.jajafu.x60assistant.diagnostics.DiagnosticsExporter
import com.jajafu.x60assistant.service.ServiceStartPolicy
import com.jajafu.x60assistant.service.ServiceStateRepository
import com.jajafu.x60assistant.service.VoiceForegroundService
import com.jajafu.x60assistant.llm.OpenAiCompatibleLlmProvider
import com.jajafu.x60assistant.security.AndroidKeystoreSecretStore
import com.jajafu.x60assistant.tts.AndroidTtsEngine
import com.jajafu.x60assistant.ui.diagnostics.DeviceDiagnosticsScreen
import com.jajafu.x60assistant.ui.theme.X60AssistantTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var diagnosticsCollector: DeviceDiagnosticsCollector
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var secretStore: AndroidKeystoreSecretStore
    private var diagnostics by mutableStateOf(emptyDiagnostics())
    private var errorMessage by mutableStateOf<String?>(null)
    private var startAfterPermissionGrant = false
    private var settingsMessage by mutableStateOf<String?>(null)
    private var testingConnection by mutableStateOf(false)
    private var pendingDiagnosticsExport: String? = null

    private val diagnosticsExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val content = pendingDiagnosticsExport
        pendingDiagnosticsExport = null
        if (uri != null && content != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }.onSuccess { settingsMessage = "診斷已匯出；內容不含 API Key、完整 URL、逐字稿或回答。" }
                .onFailure { settingsMessage = "診斷匯出失敗：${it.javaClass.simpleName}" }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshDiagnostics()
        if (granted && startAfterPermissionGrant) {
            startAfterPermissionGrant = false
            startM1Service()
        } else if (!granted) {
            startAfterPermissionGrant = false
            errorMessage = getString(R.string.notification_permission_denied)
        }
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshDiagnostics()
        if (!granted) {
            startAfterPermissionGrant = false
            errorMessage = getString(R.string.microphone_permission_denied)
            return@registerForActivityResult
        }

        if (ServiceStartPolicy.requiresNotificationPermission(Build.VERSION.SDK_INT) &&
            !hasPermission(POST_NOTIFICATIONS_PERMISSION)
        ) {
            notificationPermissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)
        } else if (startAfterPermissionGrant) {
            startAfterPermissionGrant = false
            startM1Service()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnosticsCollector = DeviceDiagnosticsCollector(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        secretStore = AndroidKeystoreSecretStore(applicationContext)
        refreshDiagnostics()

        setContent {
            val serviceRunning by ServiceStateRepository.running.collectAsStateWithLifecycle()
            val m1Diagnostics by M1DiagnosticsRepository.snapshot.collectAsStateWithLifecycle()
            val assistantSettings by settingsRepository.settings.collectAsStateWithLifecycle(AssistantSettings())
            X60AssistantTheme {
                DeviceDiagnosticsScreen(
                    diagnostics = diagnostics,
                    m1Diagnostics = m1Diagnostics,
                    serviceRunning = serviceRunning,
                    errorMessage = errorMessage,
                    assistantSettings = assistantSettings,
                    maskedApiKey = secretStore.maskedApiKey(),
                    settingsMessage = settingsMessage,
                    testingConnection = testingConnection,
                    onPrimaryAction = {
                        if (serviceRunning) stopM1Service() else requestPermissionsAndStart()
                    },
                    onPauseOrResume = {
                        sendServiceAction(
                            if (m1Diagnostics.phase == com.jajafu.x60assistant.orchestration.VoiceState.PAUSED) {
                                VoiceForegroundService.ACTION_RESUME
                            } else VoiceForegroundService.ACTION_PAUSE,
                        )
                    },
                    onCancelSession = { sendServiceAction(VoiceForegroundService.ACTION_CANCEL) },
                    onOpenAppSettings = ::openAppSettings,
                    onOpenBatterySettings = ::openBatteryOptimizationSettings,
                    onRefresh = ::refreshDiagnostics,
                    onSaveSettings = ::saveSettings,
                    onTestConnection = ::testConnection,
                    onDeleteApiKey = ::deleteApiKey,
                    onTestTts = ::testTts,
                    onExportDiagnostics = { exportDiagnostics(m1Diagnostics, assistantSettings) },
                    onClearPrivateData = ::clearPrivateData,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::diagnosticsCollector.isInitialized) refreshDiagnostics()
    }

    private fun requestPermissionsAndStart() {
        errorMessage = null
        startAfterPermissionGrant = true
        when {
            !hasPermission(Manifest.permission.RECORD_AUDIO) ->
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

            ServiceStartPolicy.requiresNotificationPermission(Build.VERSION.SDK_INT) &&
                !hasPermission(POST_NOTIFICATIONS_PERMISSION) ->
                notificationPermissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)

            else -> {
                startAfterPermissionGrant = false
                startM1Service()
            }
        }
    }

    private fun startM1Service() {
        refreshDiagnostics()
        if (!ServiceStartPolicy.canStart(
                diagnostics.microphonePermissionGranted,
                diagnostics.notificationPermissionGranted,
            )
        ) {
            errorMessage = getString(R.string.permissions_required)
            return
        }

        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, VoiceForegroundService::class.java),
            )
        } catch (exception: SecurityException) {
            errorMessage = getString(R.string.service_start_security_error)
        } catch (exception: IllegalStateException) {
            errorMessage = getString(R.string.service_start_background_error)
        }
    }

    private fun stopM1Service() {
        val stopIntent = Intent(this, VoiceForegroundService::class.java)
        stopService(stopIntent)
    }

    private fun sendServiceAction(action: String) {
        startService(Intent(this, VoiceForegroundService::class.java).apply { this.action = action })
    }

    private fun refreshDiagnostics() {
        diagnostics = diagnosticsCollector.collect()
    }

    private fun saveSettings(settings: AssistantSettings, apiKey: String) {
        lifecycleScope.launch {
            runCatching {
                settingsRepository.save(settings.copy(connectionVerified = false))
                if (apiKey.isNotBlank()) secretStore.saveApiKey(apiKey.toCharArray())
            }.onSuccess {
                settingsMessage = "設定已儲存；請按「測試連線」後再啟動服務。"
            }.onFailure {
                settingsMessage = "設定儲存失敗：${it.javaClass.simpleName}"
            }
        }
    }

    private fun testConnection(settings: AssistantSettings, apiKey: String) {
        if (testingConnection) return
        testingConnection = true
        settingsMessage = "正在測試 DNS、TLS、認證、Model 與 Endpoint…"
        lifecycleScope.launch {
            var provider: OpenAiCompatibleLlmProvider? = null
            runCatching {
                settingsRepository.save(settings.copy(connectionVerified = false))
                if (apiKey.isNotBlank()) secretStore.saveApiKey(apiKey.toCharArray())
                provider = OpenAiCompatibleLlmProvider(
                    settings = settings,
                    apiKeyProvider = secretStore::readApiKey,
                    allowHttp = BuildConfig.DEBUG,
                )
                provider.healthCheck()
            }.onSuccess { result ->
                settingsRepository.markConnectionVerified(result.success)
                settingsMessage = if (result.success) {
                    "連線成功（${result.latencyMs} ms）：${result.message}"
                } else {
                    "連線失敗於 ${result.stage}${result.httpStatus?.let { "（HTTP $it）" }.orEmpty()}：${result.message}"
                }
            }.onFailure {
                settingsRepository.markConnectionVerified(false)
                settingsMessage = "連線設定無效：${it.message ?: it.javaClass.simpleName}"
            }
            provider?.close()
            testingConnection = false
        }
    }

    private fun deleteApiKey() {
        secretStore.deleteApiKey()
        lifecycleScope.launch { settingsRepository.markConnectionVerified(false) }
        settingsMessage = "API Key 已刪除。"
    }

    private fun testTts(settings: AssistantSettings) {
        lifecycleScope.launch {
            val engine = AndroidTtsEngine(applicationContext, settings)
            runCatching {
                val status = engine.initialize()
                check(status.available) { status.message }
                engine.speak("小助手語音測試完成。")
                status
            }.onSuccess {
                settingsMessage = "TTS 測試完成：${it.locale}，${it.voice}"
            }.onFailure {
                settingsMessage = "TTS 測試失敗：${it.message ?: it.javaClass.simpleName}"
            }
            engine.close()
        }
    }

    private fun exportDiagnostics(
        runtime: com.jajafu.x60assistant.diagnostics.M1DiagnosticsSnapshot,
        settings: AssistantSettings,
    ) {
        pendingDiagnosticsExport = DiagnosticsExporter.build(
            runtime,
            diagnostics,
            settings,
            M1DiagnosticsRepository.ringLog.snapshot(),
        )
        diagnosticsExportLauncher.launch("x60-assistant-diagnostics.txt")
    }

    private fun clearPrivateData() {
        lifecycleScope.launch {
            settingsRepository.clear()
            secretStore.deleteApiKey()
            M1DiagnosticsRepository.clearDiagnostics()
            settingsMessage = "設定、API Key、記憶體內逐字稿／回答與診斷已清除。"
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toUri(),
        )
        startActivity(intent)
    }

    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    companion object {
        private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

        private fun emptyDiagnostics() = DeviceDiagnostics(
            androidVersion = "—",
            apiLevel = Build.VERSION.SDK_INT,
            buildDisplay = "—",
            manufacturerAndModel = "—",
            supportedAbis = "—",
            totalMemory = "—",
            availableMemory = "—",
            ttsEngines = emptyList(),
            microphonePermissionGranted = false,
            notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
            ignoringBatteryOptimizations = false,
            batteryPercent = -1,
            temperatureCelsius = null,
            thermalStatus = null,
        )
    }
}
