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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jajafu.x60assistant.MainActivity
import com.jajafu.x60assistant.R
import com.jajafu.x60assistant.audio.AudioCaptureEngine
import com.jajafu.x60assistant.audio.WakeAcknowledgementPlayer
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsRepository
import com.jajafu.x60assistant.diagnostics.VoiceErrorCode
import com.jajafu.x60assistant.kws.KwsModelPack
import com.jajafu.x60assistant.kws.SherpaWakeWordEngine
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.FileDescriptor
import java.io.PrintWriter

class VoiceForegroundService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private val cleanupStarted = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private val resourceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "x60-service-resources")
    }
    private var healthExecutor: ScheduledExecutorService? = null
    private var audioCaptureEngine: AudioCaptureEngine? = null
    private var wakeWordEngine: SherpaWakeWordEngine? = null
    private var acknowledgementPlayer: WakeAcknowledgementPlayer? = null
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
            M1DiagnosticsRepository.resetForStart(
                modelIdValue = KwsModelPack.ID,
                modelSha256Value = KwsModelPack.ARCHIVE_SHA256,
            )
            resourceExecutor.execute(::initializeResources)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanup(removeNotification = true)
        resourceExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        val snapshot = M1DiagnosticsRepository.snapshot.value
        writer.println("phase=${snapshot.phase}")
        writer.println("audio_frames=${snapshot.audioFrameCount}")
        writer.println("wake_count=${snapshot.wakeCount}")
        writer.println("configured_score=${snapshot.lastWakeScore ?: "-"}")
        writer.println("wake_timestamp_epoch_ms=${snapshot.lastWakeTimestampEpochMs ?: "-"}")
        writer.println("service_uptime_ms=${snapshot.serviceUptimeMs}")
        writer.println("audio_errors=${snapshot.audioErrorCount}")
        writer.println("dropped_frames=${snapshot.droppedFrameCount}")
        writer.println("audio_source=${snapshot.audioSource}")
        writer.println("wake_lock_held=${snapshot.wakeLockHeld}")
        writer.println("last_error=${snapshot.lastError ?: "-"}")
        writer.println("model_id=${snapshot.modelId}")
        writer.println("model_archive_sha256=${snapshot.modelSha256}")
    }

    private fun initializeResources() {
        var initializationErrorCode = VoiceErrorCode.KWS_RUNTIME_FAILED
        var pendingPlayer: WakeAcknowledgementPlayer? = null
        var pendingAudio: AudioCaptureEngine? = null
        var pendingKws: SherpaWakeWordEngine? = null
        try {
            if (!acquireWakeLock()) return
            val verification = KwsModelPack.verify(assets)
            if (!verification.valid) {
                failAndStop(VoiceErrorCode.KWS_MODEL_INVALID, verification.reason.orEmpty())
                return
            }
            if (!active.get()) return

            val player = WakeAcknowledgementPlayer()
            pendingPlayer = player
            val audio = AudioCaptureEngine(
                context = applicationContext,
                onFrame = M1DiagnosticsRepository::onAudioFrame,
                onDroppedFrame = M1DiagnosticsRepository::onDroppedFrame,
                onReadError = M1DiagnosticsRepository::onAudioError,
                onFatalError = ::failAndStop,
            )
            pendingAudio = audio
            val kws = SherpaWakeWordEngine(
                assetManager = assets,
                frameQueue = audio.frameQueue,
                onWake = { configuredScore ->
                    M1DiagnosticsRepository.onWake(configuredScore)
                    player.play()
                },
                onFatalError = ::failAndStop,
            )
            pendingKws = kws
            kws.start()
            val kwsAccepted = synchronized(lifecycleLock) {
                if (active.get()) {
                    acknowledgementPlayer = player
                    wakeWordEngine = kws
                    pendingPlayer = null
                    pendingKws = null
                    true
                } else {
                    false
                }
            }
            if (!kwsAccepted) {
                kws.stop()
                player.release()
                return
            }
            initializationErrorCode = VoiceErrorCode.AUDIO_INIT_FAILED
            val source = audio.start()
            val audioAccepted = synchronized(lifecycleLock) {
                if (active.get()) {
                    audioCaptureEngine = audio
                    pendingAudio = null
                    true
                } else {
                    false
                }
            }
            if (!audioAccepted) {
                audio.stop()
                return
            }
            val becameIdle = synchronized(lifecycleLock) {
                if (active.get()) {
                    M1DiagnosticsRepository.setIdle(source)
                    true
                } else {
                    false
                }
            }
            if (becameIdle) startHealthPublishing()
        } catch (securityError: SecurityException) {
            runCatching { pendingAudio?.stop() }
            runCatching { pendingKws?.stop() }
            runCatching { pendingPlayer?.release() }
            failAndStop(VoiceErrorCode.PERMISSION_MIC_DENIED, securityError.javaClass.simpleName)
        } catch (error: Throwable) {
            runCatching { pendingAudio?.stop() }
            runCatching { pendingKws?.stop() }
            runCatching { pendingPlayer?.release() }
            failAndStop(initializationErrorCode, error.javaClass.simpleName)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        val lock = powerManager.newWakeLock(
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
        if (!accepted) {
            if (lock.isHeld) lock.release()
            return false
        }
        M1DiagnosticsRepository.setWakeLockHeld(lock.isHeld)
        return true
    }

    private fun startHealthPublishing() {
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "x60-health-publisher")
        }
        executor.scheduleWithFixedDelay(
            M1DiagnosticsRepository::publish,
            1,
            1,
            TimeUnit.SECONDS,
        )
        val accepted = synchronized(lifecycleLock) {
            if (active.get()) {
                healthExecutor = executor
                true
            } else {
                false
            }
        }
        if (!accepted) executor.shutdownNow()
    }

    private fun failAndStop(code: VoiceErrorCode, detail: String) {
        if (!active.compareAndSet(true, false)) return
        M1DiagnosticsRepository.recordError(code, detail)
        mainHandler.post {
            cleanup(removeNotification = true)
            stopSelf()
        }
    }

    private fun stopFromUser() {
        active.set(false)
        cleanup(removeNotification = true)
        stopSelf()
    }

    private fun cleanup(removeNotification: Boolean) {
        if (!cleanupStarted.compareAndSet(false, true)) return
        active.set(false)
        val resources = synchronized(lifecycleLock) {
            val result = Resources(
                health = healthExecutor,
                audio = audioCaptureEngine,
                kws = wakeWordEngine,
                player = acknowledgementPlayer,
                lock = wakeLock,
            )
            healthExecutor = null
            audioCaptureEngine = null
            wakeWordEngine = null
            acknowledgementPlayer = null
            wakeLock = null
            result
        }
        resources.health?.shutdownNow()
        runCatching { resources.audio?.stop() }
        runCatching { resources.kws?.stop() }
        runCatching { resources.player?.release() }
        runCatching {
            resources.lock?.let { lock -> if (lock.isHeld) lock.release() }
        }
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
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            foregroundType,
        )
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

    private data class Resources(
        val health: ScheduledExecutorService?,
        val audio: AudioCaptureEngine?,
        val kws: SherpaWakeWordEngine?,
        val player: WakeAcknowledgementPlayer?,
        val lock: PowerManager.WakeLock?,
    )
}
