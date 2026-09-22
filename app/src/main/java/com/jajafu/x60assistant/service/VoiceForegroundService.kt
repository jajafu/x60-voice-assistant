package com.jajafu.x60assistant.service

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
import com.jajafu.x60assistant.MainActivity
import com.jajafu.x60assistant.R
import com.jajafu.x60assistant.BuildConfig
import com.jajafu.x60assistant.asr.AsrModelPacks
import com.jajafu.x60assistant.asr.SherpaSpeechRecognizerEngine
import com.jajafu.x60assistant.asr.TraditionalChineseConverter
import com.jajafu.x60assistant.audio.AudioCaptureEngine
import com.jajafu.x60assistant.audio.WakeAcknowledgementPlayer
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsRepository
import com.jajafu.x60assistant.diagnostics.VoiceErrorCode
import com.jajafu.x60assistant.data.SettingsRepository
import com.jajafu.x60assistant.llm.OpenAiCompatibleLlmProvider
import com.jajafu.x60assistant.kws.KwsModelPack
import com.jajafu.x60assistant.kws.SherpaWakeWordEngine
import com.jajafu.x60assistant.model.ModelPackManager
import com.jajafu.x60assistant.orchestration.AndroidMonotonicClock
import com.jajafu.x60assistant.orchestration.VoiceOrchestrator
import com.jajafu.x60assistant.security.AndroidKeystoreSecretStore
import com.jajafu.x60assistant.tts.AndroidTtsEngine
import com.jajafu.x60assistant.tts.SpeechOutputEngine
import com.jajafu.x60assistant.data.AssistantSettings
import com.jajafu.x60assistant.orchestration.VoiceState
import com.jajafu.x60assistant.vad.SherpaVadEngine
import com.jajafu.x60assistant.vad.SpeechEndpointConfig
import com.jajafu.x60assistant.vad.SpeechEndpointDetector
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
    private var wakeLock: PowerManager.WakeLock? = null

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
            val vadStatus = modelManager.verifyVadAsset()
            if (!vadStatus.valid) {
                failAndStop(VoiceErrorCode.VAD_INIT_FAILED, vadStatus.reason.orEmpty())
                return
            }
            val asrPack = AsrModelPacks.small
            val asrStatus = modelManager.verifyAsrPack(asrPack)
            if (!asrStatus.valid) {
                failAndStop(VoiceErrorCode.ASR_MODEL_INVALID, asrStatus.reason.orEmpty())
                return
            }

            var orchestratorReference: VoiceOrchestrator? = null
            val secretStore = AndroidKeystoreSecretStore(applicationContext)
            val llmProvider = if (
                settings.connectionVerified && settings.baseUrl.isNotBlank() &&
                settings.model.isNotBlank() && secretStore.maskedApiKey() != "未設定"
            ) {
                OpenAiCompatibleLlmProvider(
                    settings = settings,
                    apiKeyProvider = secretStore::readApiKey,
                    allowHttp = BuildConfig.DEBUG,
                )
            } else {
                null
            }
            val ttsEngine = AndroidTtsEngine(applicationContext, settings)
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
                kws = SherpaWakeWordEngine(assets),
                vad = SherpaVadEngine(assets),
                asr = SherpaSpeechRecognizerEngine(
                    modelId = asrPack.id,
                    modelDirectory = modelManager.asrDirectory(asrPack),
                    pack = asrPack,
                    clock = AndroidMonotonicClock,
                ),
                acknowledgementPlayer = WakeAcknowledgementPlayer(applicationContext),
                converter = TraditionalChineseConverter(),
                settings = settings,
                llm = llmProvider,
                speechOutput = if (ttsStatus.available) ttsEngine else null,
                speechOutputFactory = { createTtsEngine(settings) },
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
                if (audioCapture?.isStalled(SystemClock.elapsedRealtime()) == true) {
                    orchestrator?.onAudioFailure(VoiceErrorCode.AUDIO_READ_STALLED, "no-frame-over-3000ms")
                }
            }
        }
    }

    private suspend fun createTtsEngine(settings: AssistantSettings): SpeechOutputEngine? {
        val engine = AndroidTtsEngine(applicationContext, settings)
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
            VoiceState.THINKING, VoiceState.LISTENING, VoiceState.TRANSCRIBING, VoiceState.ROUTING ->
                notificationAction(ACTION_CANCEL, REQUEST_CANCEL, "取消")
            VoiceState.SPEAKING -> notificationAction(ACTION_SILENCE, REQUEST_SILENCE, "閉嘴")
            else -> notificationAction(ACTION_PAUSE, REQUEST_PAUSE, "暫停")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
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
            .build()
    }

    private fun notificationAction(action: String, requestCode: Int, label: String): Pair<String, PendingIntent> =
        label to PendingIntent.getService(
            this,
            requestCode,
            Intent(this, VoiceForegroundService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationText(state: VoiceState): String = when (state) {
        VoiceState.IDLE_KWS -> "等待喚醒詞「小助手」"
        VoiceState.LISTENING -> "正在聆聽"
        VoiceState.THINKING, VoiceState.ROUTING, VoiceState.TRANSCRIBING -> "正在取得回答"
        VoiceState.SPEAKING -> "正在回答"
        VoiceState.FOLLOW_UP -> "等待追問"
        VoiceState.PAUSED -> "已暫停"
        VoiceState.ERROR -> "需要處理；請開啟 App"
        else -> "正在準備語音助理"
    }

    companion object {
        const val ACTION_STOP = "com.jajafu.x60assistant.action.STOP_SERVICE"
        const val ACTION_PAUSE = "com.jajafu.x60assistant.action.PAUSE_SERVICE"
        const val ACTION_RESUME = "com.jajafu.x60assistant.action.RESUME_SERVICE"
        const val ACTION_CANCEL = "com.jajafu.x60assistant.action.CANCEL_SESSION"
        const val ACTION_SILENCE = "com.jajafu.x60assistant.action.SILENCE_SESSION"
        private const val CHANNEL_ID = "voice_service_status"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 10
        private const val REQUEST_STOP = 11
        private const val REQUEST_PAUSE = 12
        private const val REQUEST_RESUME = 13
        private const val REQUEST_CANCEL = 14
        private const val REQUEST_SILENCE = 15
    }
}
