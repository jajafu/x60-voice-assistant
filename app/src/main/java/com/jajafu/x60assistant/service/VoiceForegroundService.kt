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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jajafu.x60assistant.MainActivity
import com.jajafu.x60assistant.R
import com.jajafu.x60assistant.asr.AsrModelPacks
import com.jajafu.x60assistant.asr.SherpaSpeechRecognizerEngine
import com.jajafu.x60assistant.asr.TraditionalChineseConverter
import com.jajafu.x60assistant.audio.AudioCaptureEngine
import com.jajafu.x60assistant.audio.WakeAcknowledgementPlayer
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsRepository
import com.jajafu.x60assistant.diagnostics.VoiceErrorCode
import com.jajafu.x60assistant.kws.KwsModelPack
import com.jajafu.x60assistant.kws.SherpaWakeWordEngine
import com.jajafu.x60assistant.model.ModelPackManager
import com.jajafu.x60assistant.orchestration.AndroidMonotonicClock
import com.jajafu.x60assistant.orchestration.VoiceOrchestrator
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
    private var healthJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFromUser()
            return START_NOT_STICKY
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
                acknowledgementPlayer = WakeAcknowledgementPlayer(),
                endpointDetector = SpeechEndpointDetector(SpeechEndpointConfig()),
                converter = TraditionalChineseConverter(),
            )
            orchestratorReference = created
            val accepted = synchronized(lifecycleLock) {
                if (active.get()) {
                    orchestrator = created
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
            while (isActive) {
                delay(1_000)
                M1DiagnosticsRepository.publish()
            }
        }
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
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundType)
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

    private fun buildNotification(): Notification {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title_m1))
            .setContentText(getString(R.string.notification_text_m1))
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(R.drawable.ic_stop, getString(R.string.stop_service), stopPendingIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.jajafu.x60assistant.action.STOP_SERVICE"
        private const val CHANNEL_ID = "voice_service_status"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 10
        private const val REQUEST_STOP = 11
    }
}
