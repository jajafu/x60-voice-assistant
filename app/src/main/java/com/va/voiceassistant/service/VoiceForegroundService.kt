package com.va.voiceassistant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.va.voiceassistant.MainActivity
import com.va.voiceassistant.R
import com.va.voiceassistant.BuildConfig
import com.va.voiceassistant.asr.AsrModelPacks
import com.va.voiceassistant.asr.AsrRecognizerKind
import com.va.voiceassistant.asr.OfflineSpeechRecognizerEngine
import com.va.voiceassistant.asr.SherpaSpeechRecognizerEngine
import com.va.voiceassistant.asr.SystemSpeechRecognizerEngine
import com.va.voiceassistant.asr.SystemSpeechRecognizerSupport
import com.va.voiceassistant.asr.TraditionalChineseConverter
import com.va.voiceassistant.audio.AudioCaptureEngine
import com.va.voiceassistant.audio.WakeAcknowledgementPlayer
import com.va.voiceassistant.diagnostics.M1DiagnosticsRepository
import com.va.voiceassistant.diagnostics.VoiceErrorCode
import com.va.voiceassistant.data.SettingsRepository
import com.va.voiceassistant.llm.OpenAiAudioSpeechClient
import com.va.voiceassistant.llm.OpenAiCompatibleLlmProvider
import com.va.voiceassistant.llm.CloudAudioSettingsValidator
import com.va.voiceassistant.llm.LlmTranscriptCorrector
import com.va.voiceassistant.music.MusicCabinController
import com.va.voiceassistant.navigation.NavigationActionExecutor
import com.va.voiceassistant.navigation.NavigationAppCatalog
import com.va.voiceassistant.reminder.AndroidReminderActionExecutor
import com.va.voiceassistant.reminder.AndroidAlarmActionExecutor
import com.va.voiceassistant.kws.KwsModelPack
import com.va.voiceassistant.kws.SherpaWakeWordEngine
import com.va.voiceassistant.model.ModelPackManager
import com.va.voiceassistant.model.BundledAsrInstaller
import com.va.voiceassistant.orchestration.AndroidMonotonicClock
import com.va.voiceassistant.orchestration.VoiceOrchestrator
import com.va.voiceassistant.security.AndroidKeystoreSecretStore
import com.va.voiceassistant.tts.AndroidTtsEngine
import com.va.voiceassistant.tts.CloudTtsEngine
import com.va.voiceassistant.tts.SpeechOutputEngine
import com.va.voiceassistant.data.AssistantSettings
import com.va.voiceassistant.orchestration.VoiceState
import com.va.voiceassistant.vad.SherpaVadEngine
import com.va.voiceassistant.vad.SpeechEndpointConfig
import com.va.voiceassistant.vad.SpeechEndpointDetector
import com.va.voiceassistant.volume.AndroidVolumeActionExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean

class VoiceForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private val active = AtomicBoolean(false)
    private val cleanupStarted = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private var orchestrator: VoiceOrchestrator? = null
    private var audioCapture: AudioCaptureEngine? = null
    private var healthJob: Job? = null
    private var navExecutorForService: NavigationActionExecutor? = null

    /** 背景導航待開啟的路線 Intent；掛在常駐通知的「開啟地圖」按鈕，回到 idle 即清除。 */
    @Volatile private var pendingMapIntent: Intent? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeWakePhrase = AssistantSettings.DEFAULT_WAKE_PHRASE

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopFromUser()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                orchestrator?.pause()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                orchestrator?.resume()
                return START_NOT_STICKY
            }
            ACTION_CANCEL, ACTION_SILENCE -> {
                orchestrator?.cancelSession()
                return START_NOT_STICKY
            }
        }
        startAsForeground()
        if (active.compareAndSet(false, true)) {
            cleanupStarted.set(false)
            ServiceStateRepository.setRunning(true)
            M1DiagnosticsRepository.resetForStart(KwsModelPack.ID, KwsModelPack.ARCHIVE_SHA256)
            serviceScope.launch { initializeResources() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanup(removeNotification = true)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        val snapshot = M1DiagnosticsRepository.snapshot.value
        writer.println("state=${snapshot.phase}")
        writer.println("audio_frames=${snapshot.audioFrameCount}")
        writer.println("wake_count=${snapshot.wakeCount}")
        writer.println("wake_timestamp_epoch_ms=${snapshot.lastWakeTimestampEpochMs ?: "-"}")
        writer.println("service_uptime_ms=${snapshot.serviceUptimeMs}")
        writer.println("audio_errors=${snapshot.audioErrorCount}")
        writer.println("dropped_frames=${snapshot.droppedFrameCount}")
        writer.println("audio_source=${snapshot.audioSource}")
        writer.println("wake_lock_held=${snapshot.wakeLockHeld}")
        writer.println("last_error=${snapshot.lastError ?: "-"}")
        writer.println("kws_model_id=${snapshot.modelId}")
        writer.println("kws_model_archive_sha256=${snapshot.modelSha256}")
        writer.println("asr_model_id=${snapshot.asrModelId}")
        writer.println("asr_rtf=${snapshot.asrRtf ?: "-"}")
        writer.println("asr_final_latency_ms=${snapshot.asrFinalLatencyMs ?: "-"}")
        writer.println("transcript_length=${snapshot.transcriptLength}")
        writer.println("transcript_hash=${snapshot.transcriptHash}")
        writer.println("answer_length=${snapshot.answerLength}")
        writer.println("answer_hash=${snapshot.answerHash}")
        writer.println("llm_http_status=${snapshot.llmHttpStatus ?: "-"}")
        writer.println("llm_first_token_ms=${snapshot.llmFirstTokenLatencyMs ?: "-"}")
        writer.println("llm_total_ms=${snapshot.llmTotalLatencyMs ?: "-"}")
        writer.println("audio_queue=${snapshot.audioQueueDepth}/${snapshot.audioQueueHighWaterMark}")
        writer.println("tts_queue=${snapshot.ttsQueueDepth}/${snapshot.ttsQueueHighWaterMark}")
        writer.println("tts_engine=${snapshot.ttsEngine}")
        writer.println("tts_voice=${snapshot.ttsVoice}")
        writer.println("service_restart_count=${snapshot.serviceRestartCount}")
    }

    private suspend fun initializeResources() {
        try {
            if (!acquireWakeLock() || !active.get()) return
            val kwsVerification = KwsModelPack.verify(assets)
            if (!kwsVerification.valid) {
                failAndStop(VoiceErrorCode.KWS_MODEL_INVALID, kwsVerification.reason.orEmpty())
                return
            }
            val modelManager = ModelPackManager(applicationContext)
            val settings = SettingsRepository(applicationContext).current()
            activeWakePhrase = settings.wakePhrase
            val vadStatus = modelManager.verifyVadAsset()
            if (!vadStatus.valid) {
                failAndStop(VoiceErrorCode.VAD_INIT_FAILED, vadStatus.reason.orEmpty())
                return
            }
            val asrPack = AsrModelPacks.xAsr
            val llmSecretStore = AndroidKeystoreSecretStore(applicationContext)
            val audioSecretStore = AndroidKeystoreSecretStore(
                applicationContext,
                AndroidKeystoreSecretStore.Scope.AUDIO,
            )
            val hasLlmApiKey = llmSecretStore.maskedApiKey() != "未設定"
            val hasAudioApiKey = audioSecretStore.maskedApiKey() != "未設定"
            CloudAudioSettingsValidator.validate(settings, hasAudioApiKey, BuildConfig.DEBUG)?.let { detail ->
                // 音訊 Provider 的設定問題要與 LLM 分開，否則診斷會誤導成 LLM 故障。
                failAndStop(VoiceErrorCode.AUDIO_CONFIG_INVALID, detail)
                return
            }
            // 引擎選擇：先探測系統語音辨識（含中文支援），探測通過就直接用系統、
            // 不必先下載 X-ASR；都不行才要求 X-ASR 後備模型。
            val systemAsrProbe = SystemSpeechRecognizerSupport.probe(applicationContext)
            val systemAsrConfirmed = systemAsrProbe as? SystemSpeechRecognizerSupport.SystemRecognitionProbe.Confirmed
            val systemAsrLanguage = systemAsrConfirmed?.language
            // 問不到語言資訊仍會選用系統辨識，把未驗證的原因留在診斷，日後才追得出當時的取捨。
            systemAsrConfirmed?.unverifiedReason?.let(M1DiagnosticsRepository::recordSystemAsrUnverified)
            val useSystemAsr = systemAsrLanguage != null
            if (!useSystemAsr) {
                val asrStatus = BundledAsrInstaller(applicationContext).ensureInstalled()
                if (!asrStatus.valid) {
                    val probeReason = (systemAsrProbe as? SystemSpeechRecognizerSupport.SystemRecognitionProbe.Unavailable)?.reason.orEmpty()
                    // 提示要能同時說明兩條退路都不可行；reason 前綴供診斷顯示。
                    failAndStop(
                        VoiceErrorCode.ASR_MODEL_INVALID,
                        "missing-archive:系統語音辨識不可用（$probeReason）" +
                            "且 X-ASR 未下載；請到「模型」分頁按「下載 X-ASR 模型」",
                    )
                    return
                }
            }

            var orchestratorReference: VoiceOrchestrator? = null
            val llmProvider = if (
                settings.connectionVerified && settings.baseUrl.isNotBlank() &&
                settings.model.isNotBlank() && hasLlmApiKey
            ) {
                OpenAiCompatibleLlmProvider(
                    settings = settings,
                    apiKeyProvider = llmSecretStore::readApiKey,
                    allowHttp = BuildConfig.DEBUG,
                )
            } else {
                null
            }
            val useCloudTts = settings.cloudTtsReady(hasAudioApiKey)
            val ttsEngine: SpeechOutputEngine = if (useCloudTts) {
                CloudTtsEngine(
                    applicationContext,
                    settings,
                    OpenAiAudioSpeechClient(
                        settings = settings,
                        apiKeyProvider = audioSecretStore::readApiKey,
                        allowHttp = BuildConfig.DEBUG,
                    ),
                )
            } else {
                AndroidTtsEngine(applicationContext, settings)
            }
            val ttsStatus = ttsEngine.initialize()
            M1DiagnosticsRepository.setTtsStatus(ttsStatus.engine, ttsStatus.voice)
            if (!ttsStatus.available) {
                M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, ttsStatus.message)
            }
            val audio = AudioCaptureEngine(
                context = applicationContext,
                onFrame = M1DiagnosticsRepository::onAudioFrame,
                onDroppedFrame = M1DiagnosticsRepository::onDroppedFrame,
                onReadError = M1DiagnosticsRepository::onAudioError,
                onFatalError = { code, detail -> orchestratorReference?.onAudioFailure(code, detail) },
            )
            val created = VoiceOrchestrator(
                scope = serviceScope,
                audio = audio,
                kws = SherpaWakeWordEngine(assets, settings.wakePhrase),
                vad = SherpaVadEngine(assets),
                asr = if (systemAsrLanguage != null) {
                    SystemSpeechRecognizerEngine(
                        applicationContext,
                        waitForSpeechMs = settings.waitForSpeechMs,
                        maxUtteranceMs = settings.maxUtteranceMs,
                        language = systemAsrLanguage,
                    )
                } else when (asrPack.recognizerKind) {
                    AsrRecognizerKind.ZIPFORMER_CTC -> SherpaSpeechRecognizerEngine(
                        modelId = asrPack.id,
                        modelDirectory = modelManager.asrDirectory(asrPack),
                        pack = asrPack,
                        clock = AndroidMonotonicClock,
                    )
                    AsrRecognizerKind.MOONSHINE_V2,
                    AsrRecognizerKind.X_ASR_OFFLINE_TRANSDUCER -> OfflineSpeechRecognizerEngine(
                        modelId = asrPack.id,
                        modelDirectory = modelManager.asrDirectory(asrPack),
                        pack = asrPack,
                        clock = AndroidMonotonicClock,
                    )
                },
                acknowledgementPlayer = WakeAcknowledgementPlayer(applicationContext),
                convertText = TraditionalChineseConverter()::convert,
                settings = settings,
                llm = llmProvider,
                musicExecutor = if (
                    settings.musicPackageName.isNotBlank() && settings.musicServiceName.isNotBlank()
                ) {
                    MusicCabinController(
                        context = applicationContext,
                        musicPackageName = settings.musicPackageName,
                        musicServiceName = settings.musicServiceName,
                        musicAppName = settings.musicAppName.ifBlank { "音樂 App" },
                    )
                } else {
                    null
                },
                reminderExecutor = AndroidReminderActionExecutor(applicationContext),
                navigationExecutor = if (settings.navPackageName.isNotBlank()) {
                    NavigationActionExecutor(
                        context = applicationContext,
                        catalog = NavigationAppCatalog(applicationContext),
                        selectedPackageName = settings.navPackageName,
                        selectedAppName = settings.navAppName.ifBlank { "地圖 App" },
                    ).also { navExecutorForService = it }
                } else {
                    null
                },
                onBackgroundNavigation = ::offerMapDestination,
                volumeExecutor = if (settings.voiceVolumeControl) {
                    AndroidVolumeActionExecutor(context = applicationContext)
                } else {
                    null
                },
                alarmExecutor = AndroidAlarmActionExecutor(applicationContext),
                transcriptCorrector = if (llmProvider != null && settings.llmCorrectionEnabled) {
                    LlmTranscriptCorrector(llmProvider)
                } else {
                    null
                },
                speechOutput = if (ttsStatus.available) ttsEngine else null,
                speechOutputFactory = { createTtsEngine(settings, hasAudioApiKey) },
                audioResumeDelayMs = settings.audioResumeDelayMs,
                endpointDetector = SpeechEndpointDetector(
                    SpeechEndpointConfig(
                        waitForSpeechMs = settings.waitForSpeechMs,
                        minSpeechMs = settings.minSpeechMs,
                        trailingSilenceMs = settings.trailingSilenceMs,
                        maxUtteranceMs = settings.maxUtteranceMs,
                    ),
                ),
            )
            if (!ttsStatus.available) ttsEngine.close()
            orchestratorReference = created
            val accepted = synchronized(lifecycleLock) {
                if (active.get()) {
                    orchestrator = created
                    audioCapture = audio
                    true
                } else {
                    false
                }
            }
            if (!accepted) {
                created.close()
                return
            }
            M1DiagnosticsRepository.setAsrModel(
                if (useSystemAsr) SystemSpeechRecognizerSupport.MODEL_ID else asrPack.id,
            )
            startHealthPublishing()
            created.start()
        } catch (error: OutOfMemoryError) {
            failAndStop(VoiceErrorCode.ASR_MODEL_OOM, error.javaClass.simpleName)
        } catch (error: SecurityException) {
            failAndStop(VoiceErrorCode.PERMISSION_MIC_DENIED, error.javaClass.simpleName)
        } catch (error: Throwable) {
            failAndStop(VoiceErrorCode.ASR_INIT_FAILED, error.javaClass.simpleName)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(): Boolean {
        val lock = getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:VoiceKws",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
        val accepted = synchronized(lifecycleLock) {
            if (active.get()) {
                wakeLock = lock
                true
            } else {
                false
            }
        }
        if (!accepted && lock.isHeld) lock.release()
        M1DiagnosticsRepository.setWakeLockHeld(accepted && lock.isHeld)
        return accepted
    }

    private fun startHealthPublishing() {
        healthJob = serviceScope.launch {
            var lastState: VoiceState? = null
            while (isActive) {
                delay(1_000)
                M1DiagnosticsRepository.publish()
                audioCapture?.frameQueue?.let {
                    M1DiagnosticsRepository.setAudioQueue(it.size(), it.highWaterMark())
                }
                val state = orchestrator?.state?.value ?: VoiceState.STOPPED
                if (state != lastState) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
                    lastState = state
                }
                if (state == VoiceState.IDLE_KWS && pendingMapIntent != null) {
                    pendingMapIntent = null
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
                }
                if (audioCapture?.isStalled(SystemClock.elapsedRealtime()) == true) {
                    orchestrator?.onAudioFailure(VoiceErrorCode.AUDIO_READ_STALLED, "no-frame-over-3000ms")
                }
            }
        }
    }

    private suspend fun createTtsEngine(settings: AssistantSettings, hasAudioApiKey: Boolean): SpeechOutputEngine? {
        val engine: SpeechOutputEngine = if (settings.cloudTtsReady(hasAudioApiKey)) {
            CloudTtsEngine(
                applicationContext,
                settings,
                OpenAiAudioSpeechClient(
                    settings = settings,
                    apiKeyProvider = AndroidKeystoreSecretStore(
                        applicationContext,
                        AndroidKeystoreSecretStore.Scope.AUDIO,
                    )::readApiKey,
                    allowHttp = BuildConfig.DEBUG,
                ),
            )
        } else {
            AndroidTtsEngine(applicationContext, settings)
        }
        val status = engine.initialize()
        M1DiagnosticsRepository.setTtsStatus(status.engine, status.voice)
        return if (status.available) engine else null.also { engine.close() }
    }

    private fun failAndStop(code: VoiceErrorCode, detail: String) {
        if (!active.compareAndSet(true, false)) return
        M1DiagnosticsRepository.recordError(code, detail)
        stopSelf()
    }

    private fun stopFromUser() {
        active.set(false)
        orchestrator?.stop()
        cleanup(removeNotification = true)
        stopSelf()
    }

    private fun cleanup(removeNotification: Boolean) {
        if (!cleanupStarted.compareAndSet(false, true)) return
        active.set(false)
        val resources = synchronized(lifecycleLock) {
            val result = orchestrator to wakeLock
            orchestrator = null
            audioCapture = null
            wakeLock = null
            navExecutorForService = null
            pendingMapIntent = null
            result
        }
        healthJob?.cancel()
        healthJob = null
        runCatching { resources.first?.close() }
        runCatching { resources.second?.let { if (it.isHeld) it.release() } }
        M1DiagnosticsRepository.setWakeLockHeld(false)
        M1DiagnosticsRepository.setStopped()
        ServiceStateRepository.setRunning(false)
        if (removeNotification) stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startAsForeground() {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(VoiceState.STARTING), foregroundType)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * 背景導航的接線：把路線 Intent 掛到常駐通知的「開啟地圖」按鈕。
     *
     * 點按鈕屬於使用者操作，系統允許開地圖；車機前景是 car launcher 時，
     * 這比另發一則通知可靠（常駐通知本來就在）。
     */
    private fun offerMapDestination(destination: String) {
        val intent = navExecutorForService?.routeIntent(destination) ?: return
        pendingMapIntent = intent
        runCatching {
            val state = orchestrator?.state?.value ?: VoiceState.EXECUTING_ACTION
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private fun buildNotification(state: VoiceState): Notification {
        val openPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, VoiceForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val primaryAction = when (state) {
            VoiceState.PAUSED -> notificationAction(ACTION_RESUME, REQUEST_RESUME, "恢復")
            VoiceState.THINKING, VoiceState.EXECUTING_ACTION, VoiceState.LISTENING,
            VoiceState.TRANSCRIBING, VoiceState.ROUTING ->
                notificationAction(ACTION_CANCEL, REQUEST_CANCEL, "取消")
            VoiceState.SPEAKING -> notificationAction(ACTION_SILENCE, REQUEST_SILENCE, "閉嘴")
            else -> notificationAction(ACTION_PAUSE, REQUEST_PAUSE, "暫停")
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title_m1))
            .setContentText(notificationText(state))
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(R.drawable.ic_notification, primaryAction.first, primaryAction.second)
            .addAction(R.drawable.ic_stop, getString(R.string.stop_service), stopPendingIntent)
        pendingMapIntent?.let { mapIntent ->
            builder.addAction(
                R.drawable.ic_notification,
                "開啟地圖",
                PendingIntent.getActivity(
                    this,
                    REQUEST_MAP,
                    mapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }

    private fun notificationAction(action: String, requestCode: Int, label: String): Pair<String, PendingIntent> =
        label to PendingIntent.getService(
            this,
            requestCode,
            Intent(this, VoiceForegroundService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationText(state: VoiceState): String = when (state) {
        VoiceState.IDLE_KWS -> "等待喚醒詞「$activeWakePhrase」"
        VoiceState.LISTENING -> "正在聆聽"
        VoiceState.EXECUTING_ACTION -> "正在執行本機操作"
        VoiceState.THINKING, VoiceState.ROUTING, VoiceState.TRANSCRIBING -> "正在取得回答"
        VoiceState.SPEAKING -> "正在回答"
        VoiceState.FOLLOW_UP -> "等待追問"
        VoiceState.PAUSED -> "已暫停"
        VoiceState.ERROR -> "需要處理；請開啟 App"
        else -> "正在準備語音助理"
    }

    companion object {
        const val ACTION_STOP = "com.va.voiceassistant.action.STOP_SERVICE"
        const val ACTION_PAUSE = "com.va.voiceassistant.action.PAUSE_SERVICE"
        const val ACTION_RESUME = "com.va.voiceassistant.action.RESUME_SERVICE"
        const val ACTION_CANCEL = "com.va.voiceassistant.action.CANCEL_SESSION"
        const val ACTION_SILENCE = "com.va.voiceassistant.action.SILENCE_SESSION"
        private const val CHANNEL_ID = "voice_service_status"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 10
        private const val REQUEST_STOP = 11
        private const val REQUEST_PAUSE = 12
        private const val REQUEST_RESUME = 13
        private const val REQUEST_CANCEL = 14
        private const val REQUEST_SILENCE = 15
        private const val REQUEST_MAP = 16
    }
}
