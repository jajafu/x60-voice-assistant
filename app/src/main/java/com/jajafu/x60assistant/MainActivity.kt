package com.jajafu.x60assistant

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
import com.jajafu.x60assistant.data.AssistantSettings
import com.jajafu.x60assistant.data.SettingsRepository
import com.jajafu.x60assistant.diagnostics.DeviceDiagnostics
import com.jajafu.x60assistant.diagnostics.DeviceDiagnosticsCollector
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsRepository
import com.jajafu.x60assistant.diagnostics.DiagnosticsExporter
import com.jajafu.x60assistant.service.ServiceStartPolicy
import com.jajafu.x60assistant.service.ServiceStateRepository
import com.jajafu.x60assistant.service.VoiceForegroundService
import com.jajafu.x60assistant.reminder.ReminderScheduler
import com.jajafu.x60assistant.reminder.ExactAlarmOnboardingPolicy
import com.jajafu.x60assistant.reminder.ReminderManager
import com.jajafu.x60assistant.reminder.ReminderPreferences
import com.jajafu.x60assistant.reminder.ReminderRecurrence
import com.jajafu.x60assistant.reminder.ScheduledReminder
import com.jajafu.x60assistant.reminder.ReminderStore
import com.jajafu.x60assistant.music.MusicAppCatalog
import com.jajafu.x60assistant.navigation.NavigationAppCatalog
import com.jajafu.x60assistant.navigation.NavigationAppOption
import com.jajafu.x60assistant.music.MusicAppOption
import com.jajafu.x60assistant.music.MusicCabinFavoriteAccess
import com.jajafu.x60assistant.music.AssistantUiVisibility
import com.jajafu.x60assistant.llm.OpenAiCompatibleLlmProvider
import com.jajafu.x60assistant.kws.WakePhraseCompiler
import com.jajafu.x60assistant.security.AndroidKeystoreSecretStore
import com.jajafu.x60assistant.tts.AndroidTtsEngine
import com.jajafu.x60assistant.tts.TtsEngineCatalog
import com.jajafu.x60assistant.tts.TtsEngineOption
import com.jajafu.x60assistant.tts.TtsVoiceOption
import com.jajafu.x60assistant.ui.diagnostics.DeviceDiagnosticsScreen
import com.jajafu.x60assistant.ui.theme.X60AssistantTheme
import com.jajafu.x60assistant.update.UpdateCheckResult
import com.jajafu.x60assistant.update.UpdateCheckWorker
import com.jajafu.x60assistant.update.UpdateChecker
import com.jajafu.x60assistant.update.UpdateDownload
import com.jajafu.x60assistant.update.UpdateDownloader
import com.jajafu.x60assistant.update.UpdateInfo
import com.jajafu.x60assistant.update.UpdateInstaller
import com.jajafu.x60assistant.update.UpdateUiState
import com.jajafu.x60assistant.update.describeUpdateFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

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
    private var scheduledReminders by mutableStateOf<List<ScheduledReminder>>(emptyList())
    private var reminderSoundName by mutableStateOf("系統預設鬧鐘")
    private var reminderMessage by mutableStateOf<String?>(null)
    private var ttsEngines by mutableStateOf<List<TtsEngineOption>>(emptyList())
    private var ttsVoices by mutableStateOf<List<TtsVoiceOption>>(emptyList())
    private var ttsVoicesLoading by mutableStateOf(false)
    private var ttsCatalog: TtsEngineCatalog? = null
    private var ttsVoicesJob: Job? = null
    private var updateUiState by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var pendingUpdateInfo: UpdateInfo? = null
    private var pendingUpdateApk: File? = null

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
        showExactAlarmOnboarding = shouldShowExactAlarmOnboarding()
        UpdateCheckWorker.schedule(applicationContext)

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
                    onOpenAppSettings = ::openAppSettings,
                    onOpenExactAlarmSettings = ::openExactAlarmSettings,
                    onOpenBatterySettings = ::openBatteryOptimizationSettings,
                    onRefresh = ::refreshDiagnostics,
                    onSaveSettings = ::saveSettings,
                    onTestConnection = ::testConnection,
                    onDeleteApiKey = ::deleteApiKey,
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
                    updateState = updateUiState,
                    onToggleAutoUpdate = ::toggleAutoUpdateCheck,
                    onCheckUpdate = ::checkAppUpdate,
                    onDownloadUpdate = ::downloadAppUpdate,
                    onInstallUpdate = ::installAppUpdate,
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
            refreshReminders()
            refreshReminderSound()
        }
    }

    override fun onStart() {
        super.onStart()
        AssistantUiVisibility.onStart()
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

    private fun saveSettings(settings: AssistantSettings, apiKey: String) {
        lifecycleScope.launch {
            var connectionStillVerified = false
            runCatching {
                WakePhraseCompiler.compile(assets, settings.wakePhrase)
                val current = settingsRepository.current()
                val providerUnchanged = settings.baseUrl.trim() == current.baseUrl &&
                    settings.model.trim() == current.model &&
                    settings.endpoint.trim() == current.endpoint &&
                    apiKey.isBlank()
                connectionStillVerified = current.connectionVerified && providerUnchanged
                settingsRepository.save(
                    settings.copy(connectionVerified = connectionStillVerified),
                )
                if (apiKey.isNotBlank()) secretStore.saveApiKey(apiKey.toCharArray())
            }.onSuccess {
                settingsMessage = if (connectionStillVerified) {
                    "設定已儲存。"
                } else {
                    "設定已儲存；請按「測試 LLM 連線」後再啟動服務。"
                }
            }.onFailure {
                settingsMessage = "設定儲存失敗：${it.javaClass.simpleName}"
            }
        }
    }

    private fun toggleAutoUpdateCheck(enabled: Boolean) {
        lifecycleScope.launch {
            runCatching {
                settingsRepository.save(settingsRepository.current().copy(autoUpdateCheck = enabled))
            }.onFailure {
                updateUiState = UpdateUiState.Failed("自動檢查設定儲存失敗：${it.javaClass.simpleName}")
            }
        }
    }

    private fun checkAppUpdate() {
        updateCheckJob?.cancel()
        updateUiState = UpdateUiState.Checking
        updateCheckJob = lifecycleScope.launch {
            val result = UpdateChecker().checkLatest(BuildConfig.VERSION_CODE)
            updateUiState = when (result) {
                UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(BuildConfig.VERSION_CODE)
                is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                is UpdateCheckResult.Failed ->
                    UpdateUiState.Failed("檢查更新失敗：" + describeUpdateFailure(result.failure, result.detail))
            }
        }
    }

    private fun downloadAppUpdate(info: UpdateInfo) {
        updateDownloadJob?.cancel()
        pendingUpdateInfo = info
        pendingUpdateApk = null
        updateDownloadJob = lifecycleScope.launch {
            UpdateDownloader().download(info, UpdateDownloader.updateDir(applicationContext)).collect { event ->
                updateUiState = when (event) {
                    is UpdateDownload.Progress -> UpdateUiState.Downloading(event.bytesRead, event.totalBytes)
                    is UpdateDownload.Done -> {
                        pendingUpdateApk = event.apkFile
                        UpdateUiState.ReadyToInstall(info.versionName)
                    }
                    is UpdateDownload.Failed -> {
                        pendingUpdateApk = null
                        UpdateUiState.Failed("下載失敗：" + describeUpdateFailure(event.failure, event.detail))
                    }
                }
            }
        }
    }

    private fun installAppUpdate() {
        val apk = pendingUpdateApk?.takeIf { it.isFile } ?: run {
            updateUiState = UpdateUiState.Failed("安裝檔遺失，請重新下載。")
            return
        }
        if (!UpdateInstaller.canInstallUnknownApps(this)) {
            startActivity(UpdateInstaller.unknownAppSourcesIntent(this))
            updateUiState = UpdateUiState.ReadyToInstall(
                pendingUpdateInfo?.versionName.orEmpty(),
                needPermissionHint = true,
            )
            return
        }
        runCatching { startActivity(UpdateInstaller.installIntent(this, apk)) }
            .onFailure { updateUiState = UpdateUiState.Failed("無法開啟安裝程式。") }
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
                engine.speak("${settings.wakePhrase}語音測試完成。")
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
            ReminderScheduler(applicationContext).cancelAll()
            ReminderPreferences(applicationContext).clear()
            secretStore.deleteApiKey()
            M1DiagnosticsRepository.clearDiagnostics()
            getSharedPreferences(ONBOARDING_PREFERENCES, MODE_PRIVATE).edit { clear() }
            settingsMessage = "設定、API Key、排程提醒、記憶體內逐字稿／回答與診斷已清除。"
            refreshReminders()
            refreshReminderSound()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun refreshMusicCabinFavoriteAccess() {
        musicCabinFavoriteAccessGranted = MusicCabinFavoriteAccess.isGranted(this)
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
            appVersionName = "",
            appVersionCode = 0,
        )
    }
}
