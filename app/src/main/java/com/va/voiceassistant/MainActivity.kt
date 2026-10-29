package com.va.voiceassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.va.voiceassistant.data.AssistantSettings
import com.va.voiceassistant.data.SettingsRepository
import com.va.voiceassistant.update.AppUpdateChecker
import com.va.voiceassistant.update.AppUpdateDownloader
import com.va.voiceassistant.update.AppUpdateInfo
import com.va.voiceassistant.diagnostics.DeviceDiagnostics
import com.va.voiceassistant.diagnostics.DeviceDiagnosticsCollector
import com.va.voiceassistant.diagnostics.M1DiagnosticsRepository
import com.va.voiceassistant.diagnostics.DiagnosticsExporter
import com.va.voiceassistant.service.ServiceStartPolicy
import com.va.voiceassistant.service.ServiceStateRepository
import com.va.voiceassistant.service.VoiceForegroundService
import com.va.voiceassistant.reminder.ReminderScheduler
import com.va.voiceassistant.reminder.AndroidAlarmActionExecutor
import com.va.voiceassistant.reminder.ExactAlarmOnboardingPolicy
import com.va.voiceassistant.reminder.ReminderAlarmState
import com.va.voiceassistant.reminder.ReminderManager
import com.va.voiceassistant.reminder.ReminderPreferences
import com.va.voiceassistant.reminder.ReminderRecurrence
import com.va.voiceassistant.reminder.ScheduledReminder
import com.va.voiceassistant.reminder.ReminderStore
import com.va.voiceassistant.music.MusicAppCatalog
import com.va.voiceassistant.navigation.NavigationAppCatalog
import com.va.voiceassistant.navigation.NavigationAppOption
import com.va.voiceassistant.music.MusicAppOption
import com.va.voiceassistant.music.MusicCabinFavoriteAccess
import com.va.voiceassistant.music.AssistantUiVisibility
import com.va.voiceassistant.llm.OpenAiCompatibleLlmProvider
import com.va.voiceassistant.kws.WakePhraseCompiler
import com.va.voiceassistant.security.AndroidKeystoreSecretStore
import com.va.voiceassistant.tts.AndroidTtsEngine
import com.va.voiceassistant.tts.TtsEngineCatalog
import com.va.voiceassistant.tts.TtsEngineOption
import com.va.voiceassistant.tts.TtsVoiceOption
import com.va.voiceassistant.ui.diagnostics.DeviceDiagnosticsScreen
import com.va.voiceassistant.ui.theme.VoiceAssistantTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var diagnosticsCollector: DeviceDiagnosticsCollector
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var secretStore: AndroidKeystoreSecretStore
    private lateinit var reminderManager: ReminderManager
    private var diagnostics by mutableStateOf(emptyDiagnostics())
    private var errorMessage by mutableStateOf<String?>(null)
    private var startAfterPermissionGrant = false
    private var skipAutoStartOnNextStart = false
    private var settingsMessage by mutableStateOf<String?>(null)
    private var testingConnection by mutableStateOf(false)
    private var pendingDiagnosticsExport: String? = null
    private var showExactAlarmOnboarding by mutableStateOf(false)
    private var compatibleMusicApps by mutableStateOf<List<MusicAppOption>>(emptyList())
    private var compatibleNavApps by mutableStateOf<List<NavigationAppOption>>(emptyList())
    private var musicCabinFavoriteAccessGranted by mutableStateOf(false)
    private var overlayPermissionGranted by mutableStateOf(false)
    private var scheduledReminders by mutableStateOf<List<ScheduledReminder>>(emptyList())
    private var reminderSoundName by mutableStateOf("系統預設鬧鐘")
    private var reminderMessage by mutableStateOf<String?>(null)
    private var ttsEngines by mutableStateOf<List<TtsEngineOption>>(emptyList())
    private var ttsVoices by mutableStateOf<List<TtsVoiceOption>>(emptyList())
    private var ttsVoicesLoading by mutableStateOf(false)
    private var ttsCatalog: TtsEngineCatalog? = null
    private var ttsVoicesJob: Job? = null
    private var alarmRinging by mutableStateOf(false)
    private var appUpdate by mutableStateOf<AppUpdateInfo?>(null)
    private var updateDownloading by mutableStateOf(false)
    private var updateProgress by mutableStateOf(0)
    private var updateDownloaded by mutableStateOf(false)
    private var updateHasPartialDownload by mutableStateOf(false)
    private var updateCanInstall by mutableStateOf(false)
    private var updateIgnoredVersion by mutableStateOf<String?>(null)
    private var updateCheckJob: Job? = null
    private var updateChecking by mutableStateOf(false)
    private var updateCheckMessage by mutableStateOf("尚未檢查")

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
        refreshMusicApps()
        refreshMusicCabinFavoriteAccess()
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

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        @Suppress("DEPRECATION")
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        ReminderPreferences(applicationContext).setSoundUri(
            uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        )
        refreshReminderSound()
        reminderMessage = "提醒鈴聲已更新。"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skipAutoStartOnNextStart = savedInstanceState != null
        diagnosticsCollector = DeviceDiagnosticsCollector(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        secretStore = AndroidKeystoreSecretStore(applicationContext)
        reminderManager = ReminderManager(applicationContext)
        refreshDiagnostics()
        refreshMusicApps()
        refreshTtsEngines()
        refreshReminders()
        refreshReminderSound()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ReminderStore.changes.collect { refreshReminders() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ReminderAlarmState.ringing.collect { alarmRinging = it }
            }
        }
        showExactAlarmOnboarding = shouldShowExactAlarmOnboarding()

        setContent {
            val serviceRunning by ServiceStateRepository.running.collectAsStateWithLifecycle()
            val m1Diagnostics by M1DiagnosticsRepository.snapshot.collectAsStateWithLifecycle()
            val assistantSettings by settingsRepository.settings.collectAsStateWithLifecycle(AssistantSettings())
            VoiceAssistantTheme(themeMode = assistantSettings.themeMode) {
                DeviceDiagnosticsScreen(
                    diagnostics = diagnostics,
                    m1Diagnostics = m1Diagnostics,
                    serviceRunning = serviceRunning,
                    errorMessage = errorMessage,
                    assistantSettings = assistantSettings,
                    maskedLlmApiKey = secretStore.maskedApiKey(),
                    settingsMessage = settingsMessage,
                    testingConnection = testingConnection,
                    onPrimaryAction = {
                        if (serviceRunning) stopM1Service() else requestPermissionsAndStart()
                    },
                    onPauseOrResume = {
                        sendServiceAction(
                            if (m1Diagnostics.phase == com.va.voiceassistant.orchestration.VoiceState.PAUSED) {
                                VoiceForegroundService.ACTION_RESUME
                            } else VoiceForegroundService.ACTION_PAUSE,
                        )
                    },
                    onOpenAppSettings = ::openAppSettings,
                    onOpenExactAlarmSettings = ::openExactAlarmSettings,
                    onOpenBatterySettings = ::openBatteryOptimizationSettings,
                    onRefresh = ::refreshDiagnostics,
                    onSaveSettings = ::saveSettings,
                    onTestConnection = ::testConnection,
                    onDeleteLlmApiKey = ::deleteLlmApiKey,
                    onTestTts = ::testTts,
                    onExportDiagnostics = { exportDiagnostics(m1Diagnostics, assistantSettings) },
                    onClearPrivateData = ::clearPrivateData,
                    showExactAlarmOnboarding = showExactAlarmOnboarding,
                    onOpenExactAlarmFromOnboarding = {
                        finishExactAlarmOnboarding()
                        openExactAlarmSettings()
                    },
                    onDismissExactAlarmOnboarding = ::finishExactAlarmOnboarding,
                    compatibleMusicApps = compatibleMusicApps,
                    onOpenMusicApp = ::openMusicAppForSetup,
                    compatibleNavApps = compatibleNavApps,
                    onOpenNavApp = ::openNavAppForSetup,
                    overlayPermissionGranted = overlayPermissionGranted,
                    onOpenOverlaySettings = ::openOverlayPermissionSettings,
                    musicCabinFavoriteAccessGranted = musicCabinFavoriteAccessGranted,
                    onOpenMusicCabinFavoriteAccess = ::openMusicCabinFavoriteAccessSettings,
                    ttsEngines = ttsEngines,
                    ttsVoices = ttsVoices,
                    ttsVoicesLoading = ttsVoicesLoading,
                    onLoadTtsVoices = ::loadTtsVoices,
                    scheduledReminders = scheduledReminders,
                    reminderSoundName = reminderSoundName,
                    reminderMessage = reminderMessage,
                    onSaveReminder = ::saveReminder,
                    onDeleteReminder = ::deleteReminder,
                    onChooseReminderSound = ::openRingtonePicker,
                    alarmRinging = alarmRinging,
                    onStopAlarm = ::stopAlarmRinging,
                    updateVersion = appUpdate?.versionTag?.takeIf { it != updateIgnoredVersion },
                    updateFileSize = appUpdate?.let { formatBytes(it.sizeBytes) },
                    updateNotes = appUpdate?.notes?.take(100),
                    updateDownloading = updateDownloading,
                    updateProgress = updateProgress,
                    updateDownloaded = updateDownloaded,
                    updateHasPartialDownload = updateHasPartialDownload,
                    updateCanInstall = updateCanInstall,
                    updateChecking = updateChecking,
                    updateCheckMessage = updateCheckMessage,
                      onCheckForUpdate = { checkAppUpdate(forceRefresh = true) },
                    onDownloadUpdate = ::downloadAndInstallUpdate,
                    onInstallUpdate = ::installDownloadedUpdate,
                    onOpenUpdateInstallPermission = ::openUpdateInstallPermission,
                    onIgnoreUpdate = ::ignoreUpdate,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::diagnosticsCollector.isInitialized) {
            ReminderScheduler(applicationContext).rescheduleAll()
            refreshDiagnostics()
            refreshMusicApps()
            refreshMusicCabinFavoriteAccess()
            refreshOverlayPermission()
            refreshReminders()
            refreshReminderSound()
            refreshUpdateDownloadState()
        }
    }

    override fun onStart() {
        super.onStart()
        AssistantUiVisibility.onStart()
        // Check on every foreground launch, including Activity recreation with
        // a saved instance state. A failed check stays silent and does not hide
        // an update that was already found in this Activity instance.
        checkAppUpdate()
        if (skipAutoStartOnNextStart) {
            skipAutoStartOnNextStart = false
            return
        }
        lifecycleScope.launch {
            runCatching { settingsRepository.current() }
                .onSuccess { settings ->
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        settings.autoStartListening && !ServiceStateRepository.running.value &&
                        !startAfterPermissionGrant
                    ) {
                        requestPermissionsAndStart()
                    }
                }
                .onFailure { errorMessage = "無法讀取自動聆聽設定。" }
        }
    }

    override fun onStop() {
        AssistantUiVisibility.onStop()
        super.onStop()
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

    private fun refreshMusicApps() {
        compatibleMusicApps = MusicAppCatalog(applicationContext).compatibleApps()
        compatibleNavApps = NavigationAppCatalog(applicationContext).availableApps()
    }

    /**
     * 引擎清單讀取成本低，在 onCreate 取得一次；voice 清單要綁定引擎才拿得到，
     * 交給設定頁在選項改變時呼叫 [loadTtsVoices]。
     */
    private fun refreshTtsEngines() {
        val catalog = ttsCatalog ?: TtsEngineCatalog(applicationContext).also { ttsCatalog = it }
        // 系統預設引擎存在 tts_default_synth；只讀取，用來在清單上標示「系統預設」。
        val defaultEngine = Settings.Secure.getString(contentResolver, "tts_default_synth")
        ttsEngines = catalog.engines(defaultEngine)
    }

    private fun loadTtsVoices(enginePackage: String?) {
        val catalog = ttsCatalog ?: TtsEngineCatalog(applicationContext).also { ttsCatalog = it }
        ttsVoicesJob?.cancel()
        ttsVoicesLoading = true
        ttsVoices = emptyList()
        ttsVoicesJob = lifecycleScope.launch {
            ttsVoices = TtsEngineCatalog.preferredOrder(catalog.voices(enginePackage))
            ttsVoicesLoading = false
        }
    }

    private fun openMusicAppForSetup(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            settingsMessage = "找不到${appName.ifBlank { "音樂 App" }}，請重新選擇。"
            refreshMusicApps()
            return
        }
        runCatching {
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }.onSuccess {
            settingsMessage = "請依音樂 App 畫面完成必要授權，並先手動播放一首歌。"
        }.onFailure {
            settingsMessage = "無法開啟${appName.ifBlank { "音樂 App" }}：${it.javaClass.simpleName}"
        }
    }

    private fun openNavAppForSetup(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            settingsMessage = "找不到${appName.ifBlank { "導航 App" }}，請重新選擇。"
            refreshMusicApps()
            return
        }
        runCatching {
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }.onSuccess {
            settingsMessage = "請依地圖 App 畫面完成歡迎畫面與位置授權，之後語音才能直接帶入路線。"
        }.onFailure {
            settingsMessage = "無法開啟${appName.ifBlank { "導航 App" }}：${it.javaClass.simpleName}"
        }
    }

    private fun refreshReminders() {
        if (::reminderManager.isInitialized) scheduledReminders = reminderManager.pending()
    }

    private fun refreshReminderSound() {
        val uri = ReminderPreferences(applicationContext).soundUri()
        reminderSoundName = runCatching {
            RingtoneManager.getRingtone(applicationContext, uri)?.getTitle(applicationContext)
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "系統預設鬧鐘"
    }

    private fun saveReminder(
        id: String?,
        message: String,
        triggerAtEpochMillis: Long,
        recurrence: ReminderRecurrence?,
    ) {
        val result = runCatching {
            if (id == null) {
                reminderManager.create(message, triggerAtEpochMillis, recurrence)
            } else {
                reminderManager.update(id, message, triggerAtEpochMillis, recurrence)
            }
        }
        result.onSuccess {
            reminderMessage = if (id == null) "提醒已新增。" else "提醒已更新。"
            refreshReminders()
            refreshDiagnostics()
        }.onFailure {
            reminderMessage = "提醒儲存失敗：${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun deleteReminder(id: String) {
        runCatching { reminderManager.delete(id) }.onSuccess {
            reminderMessage = "提醒已刪除。"
            refreshReminders()
        }.onFailure {
            reminderMessage = "提醒刪除失敗：${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun openRingtonePicker() {
        val currentUri = ReminderPreferences(applicationContext).soundUri()
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "選擇提醒鈴聲")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        }
        runCatching { ringtonePickerLauncher.launch(intent) }
            .onFailure { reminderMessage = "此裝置無法開啟鈴聲選擇器。" }
    }

    private fun saveSettings(settings: AssistantSettings, llmApiKey: String) {
        lifecycleScope.launch {
            var connectionStillVerified = false
            runCatching {
                WakePhraseCompiler.compile(assets, settings.wakePhrase)
                val current = settingsRepository.current()
                val providerUnchanged = settings.baseUrl.trim() == current.baseUrl &&
                    settings.model.trim() == current.model &&
                    settings.endpoint.trim() == current.endpoint &&
                    llmApiKey.isBlank()
                connectionStillVerified = current.connectionVerified && providerUnchanged
                settingsRepository.save(
                    settings.copy(connectionVerified = connectionStillVerified),
                )
                if (llmApiKey.isNotBlank()) secretStore.saveApiKey(llmApiKey.toCharArray())
            }.onSuccess {
                settingsMessage = if (connectionStillVerified) {
                    "設定已儲存。"
                } else {
                    "設定已儲存；請按「測試 LLM 連線」後再啟動服務。"
                }
            }.onFailure {
                settingsMessage = "設定儲存失敗：${it.message ?: it.javaClass.simpleName}"
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
                WakePhraseCompiler.compile(assets, settings.wakePhrase)
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

    private fun deleteLlmApiKey() {
        secretStore.deleteApiKey()
        lifecycleScope.launch { settingsRepository.markConnectionVerified(false) }
        settingsMessage = "LLM API Key 已刪除。"
    }

    private fun testTts(settings: AssistantSettings) {
        lifecycleScope.launch {
            val engine = AndroidTtsEngine(applicationContext, settings)
            try {
                val status = engine.initialize()
                check(status.available) { status.message }
                engine.speak("${settings.wakePhrase}語音測試完成。")
                settingsMessage = "TTS 測試完成：${status.engine}，${status.voice}"
            } catch (error: Exception) {
                settingsMessage = "TTS 測試失敗：${error.message ?: error.javaClass.simpleName}"
            } finally {
                engine.close()
            }
        }
    }

    /** 1.0.11 移除雲端 TTS 後的一次性清理：刪除不再讀取的舊 Audio Key 加密檔。 */
    private fun deleteLegacyAudioApiKey() {
        val secrets = File(applicationContext.filesDir, "secrets")
        File(secrets, "audio_api_key.v1.json").delete()
        File(secrets, "audio_api_key.tmp").delete()
    }

    private fun exportDiagnostics(
        runtime: com.va.voiceassistant.diagnostics.M1DiagnosticsSnapshot,
        settings: AssistantSettings,
    ) {
        pendingDiagnosticsExport = DiagnosticsExporter.build(
            runtime,
            diagnostics,
            settings,
            M1DiagnosticsRepository.ringLog.snapshot(),
        )
        diagnosticsExportLauncher.launch("voiceassistant-diagnostics.txt")
    }

    private fun clearPrivateData() {
        lifecycleScope.launch {
            settingsRepository.clear()
            ReminderScheduler(applicationContext).cancelAll()
            ReminderPreferences(applicationContext).clear()
            secretStore.deleteApiKey()
            deleteLegacyAudioApiKey()
            M1DiagnosticsRepository.clearDiagnostics()
            getSharedPreferences(ONBOARDING_PREFERENCES, MODE_PRIVATE).edit { clear() }
            settingsMessage = "設定、API Key、排程提醒、記憶體內逐字稿／回答與診斷已清除。"
            refreshReminders()
            refreshReminderSound()
        }
    }

    /** 提醒頁的「停止鈴聲」：沿用鬧鐘指令通道，只在鈴聲響起時送出停止。 */
    private fun stopAlarmRinging() {
        AndroidAlarmActionExecutor(applicationContext).stop()
    }

    /** 按「稍後」：記住被忽略的版本並讓狀態訊息一致（卡片消失後設定頁不再顯示「發現新版本」）。 */
    private fun ignoreUpdate() {
        val tag = appUpdate?.versionTag ?: return
        updateIgnoredVersion = tag
        updateCheckMessage = "已按「稍後」忽略 $tag，出現更新版本才會再提示"
    }

    private fun checkAppUpdate(forceRefresh: Boolean = false) {
        updateCheckJob?.cancel()
        updateCheckJob = lifecycleScope.launch {
            updateChecking = true
            updateCheckMessage = "正在檢查更新…"
            try {
                val info = AppUpdateChecker.checkForUpdate(
                    localVersionName = BuildConfig.VERSION_NAME,
                    forceRefresh = forceRefresh,
                )
                appUpdate = info
                when {
                    info == null -> Unit
                    // 手動「立即檢查更新」是使用者主動關心，解除同版本忽略讓卡片與下載按鈕回來；
                    // 自動檢查只在出現不同版本時解除，避免亮屏重檢把「稍後」蓋掉。
                    forceRefresh || info.versionTag != updateIgnoredVersion -> updateIgnoredVersion = null
                }
                // 已安裝版本的暫存 APK 只能是殘檔（安裝會殺程序、沒機會自己清），
                // 依檔名標籤與本地版本比對清除，避免之後被誤標成「更新已下載完成」。
                withContext(Dispatchers.IO) {
                    AppUpdateDownloader.removeStaleStagedApks(applicationContext, BuildConfig.VERSION_NAME)
                }
                refreshUpdateDownloadState(info)
                updateCheckMessage = when {
                    info == null -> "目前已是最新版本"
                    info.versionTag == updateIgnoredVersion ->
                        "已按「稍後」忽略 ${info.versionTag}，出現更新版本才會再提示"
                    else -> "發現新版本 ${info.versionTag}"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateCheckMessage = "檢查失敗：${error.message ?: error.javaClass.simpleName}"
            } finally {
                if (currentCoroutineContext().isActive) updateChecking = false
            }
        }
    }

    private fun refreshUpdateDownloadState(info: AppUpdateInfo? = appUpdate) {
        updateCanInstall = !AppUpdateDownloader.needsInstallPermission(this)
        if (info == null) {
            updateDownloaded = false
            updateHasPartialDownload = false
            return
        }

        val staged = AppUpdateDownloader.updateApkFile(this, info.versionTag)
        AppUpdateDownloader.removeOtherStagedApks(staged)
        val expected = info.sizeBytes.takeIf { it > 0L }
        updateDownloaded = staged.isFile && staged.length() > 0L &&
            (expected == null || staged.length() == expected)
        val partialLength = AppUpdateDownloader.partialDownloadLength(staged)
        updateHasPartialDownload = !updateDownloaded && partialLength > 0L
        if (!updateDownloading) {
            updateProgress = when {
                updateDownloaded -> 100
                expected != null -> ((partialLength * 100L) / expected).toInt().coerceIn(0, 99)
                else -> 0
            }
        }
    }

    private fun downloadAndInstallUpdate() {
        val info = appUpdate ?: return
        if (updateDownloading) return
        updateDownloading = true
        refreshUpdateDownloadState(info)
        settingsMessage = "正在 App 內下載更新…"
        lifecycleScope.launch {
            try {
                AppUpdateDownloader.downloadApk(
                    applicationContext,
                    info.apkUrl,
                    info.versionTag,
                    onProgress = { downloaded, total ->
                        val expected = total ?: info.sizeBytes.takeIf { it > 0L }
                        val progress = if (expected != null) {
                            ((downloaded * 100L) / expected).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        lifecycleScope.launch(Dispatchers.Main.immediate) {
                            updateProgress = progress
                            updateHasPartialDownload = downloaded > 0L && progress < 100
                        }
                    },
                    expectedSizeBytes = info.sizeBytes,
                )
                updateDownloading = false
                updateDownloaded = true
                updateHasPartialDownload = false
                updateProgress = 100
                updateCanInstall = !AppUpdateDownloader.needsInstallPermission(this@MainActivity)
                settingsMessage = if (updateCanInstall) {
                    "更新已下載完成，請按「安裝更新」。"
                } else {
                    "更新已下載完成，請允許安裝未知應用後再按「安裝更新」。"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateDownloading = false
                refreshUpdateDownloadState(info)
                settingsMessage = "更新下載失敗，可稍後繼續下載：${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    private fun installDownloadedUpdate() {
        val info = appUpdate ?: return
        val apk = AppUpdateDownloader.updateApkFile(this, info.versionTag)
        if (!apk.isFile || apk.length() == 0L) {
            refreshUpdateDownloadState(info)
            settingsMessage = "更新檔尚未下載完成，請先按下載更新。"
            return
        }
        if (AppUpdateDownloader.needsInstallPermission(this)) {
            openUpdateInstallPermission()
            return
        }
        runCatching {
            startActivity(AppUpdateDownloader.installIntent(this, apk))
        }.onFailure {
            settingsMessage = "無法開啟安裝：${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun openUpdateInstallPermission() {
        settingsMessage = "請允許「安裝未知應用」，再回來按「安裝更新」。"
        runCatching {
            startActivity(AppUpdateDownloader.installPermissionIntent(this))
        }.onFailure {
            settingsMessage = "無法開啟安裝權限設定：${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes / 1_048_576.0
        return if (mb >= 1) "約 ${"%.0f".format(mb)} MB" else "約 ${bytes / 1024} KB"
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun refreshMusicCabinFavoriteAccess() {
        musicCabinFavoriteAccessGranted = MusicCabinFavoriteAccess.isGranted(this)
    }

    /** 「顯示在其他應用程式上層」授權後不會主動通知，回前景時重讀一次以更新設定頁提示。 */
    private fun refreshOverlayPermission() {
        overlayPermissionGranted = Settings.canDrawOverlays(this)
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri(),
        )
        runCatching { startActivity(intent) }
            .onFailure { openAppSettings() }
    }

    private fun openMusicCabinFavoriteAccessSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { openAppSettings() }
    }

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

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            "package:$packageName".toUri(),
        )
        runCatching { startActivity(intent) }
            .onFailure { openAppSettings() }
    }

    private fun shouldShowExactAlarmOnboarding(): Boolean = ExactAlarmOnboardingPolicy.shouldShow(
        apiLevel = Build.VERSION.SDK_INT,
        permissionGranted = diagnostics.exactAlarmPermissionGranted,
        promptAlreadyShown = getSharedPreferences(ONBOARDING_PREFERENCES, MODE_PRIVATE)
            .getBoolean(KEY_EXACT_ALARM_PROMPT_SHOWN, false),
    )

    private fun finishExactAlarmOnboarding() {
        getSharedPreferences(ONBOARDING_PREFERENCES, MODE_PRIVATE).edit {
            putBoolean(KEY_EXACT_ALARM_PROMPT_SHOWN, true)
        }
        showExactAlarmOnboarding = false
    }

    companion object {
        private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
        private const val ONBOARDING_PREFERENCES = "assistant_onboarding"
        private const val KEY_EXACT_ALARM_PROMPT_SHOWN = "exact_alarm_prompt_shown"

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
            exactAlarmPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
        )
    }
}
