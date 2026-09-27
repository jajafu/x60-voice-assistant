package com.jajafu.x60assistant.reminder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jajafu.x60assistant.MainActivity
import com.jajafu.x60assistant.R
import com.jajafu.x60assistant.data.SettingsRepository
import com.jajafu.x60assistant.tts.AndroidTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReminderAlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var announcementJob: Job? = null
    private var autoStopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: AndroidTtsEngine? = null
    private val announcer = AlarmAnnouncer(::pauseAlarmSound, ::resumeAlarmSound) { tts?.stop() }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE_FOR_VOICE) {
            announcer.pauseForVoice()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESUME_AFTER_VOICE) {
            announcer.resumeAfterVoice()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val message = intent.getStringExtra(EXTRA_MESSAGE)?.trim().orEmpty().ifBlank { "提醒時間到了" }
        ReminderAlarmState.isRinging = true
        startAsForeground(message)
        restartAlarm(message)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ReminderAlarmState.isRinging = false
        releaseResources()
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("WakelockTimeout")
    private fun restartAlarm(message: String) {
        releaseResources()
        announcer.reset()
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:ReminderAlarm",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_RING_DURATION_MS)
        }
        player = createPlayer()
        if (player == null) {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100).also(::startFallbackTone)
        }
        announcementJob = scope.launch {
            delay(FIRST_ANNOUNCEMENT_DELAY_MS)
            val settings = try {
                SettingsRepository(applicationContext).current()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                com.jajafu.x60assistant.data.AssistantSettings()
            }
            val engine = AndroidTtsEngine(applicationContext, settings)
            tts = engine
            val available = try {
                engine.initialize().available
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!available) return@launch
            announcer.run(ANNOUNCEMENT_INTERVAL_MS) {
                engine.speak("提醒時間到了。$message。")
            }
        }
        autoStopJob = scope.launch {
            delay(MAX_RING_DURATION_MS)
            stopAlarm()
        }
    }

    private fun createPlayer(): MediaPlayer? {
        val candidates = listOfNotNull(
            ReminderPreferences(applicationContext).soundUri(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).distinct()
        return candidates.firstNotNullOfOrNull { uri ->
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(applicationContext, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
    }

    private fun pauseAlarmSound() {
        runCatching { if (player?.isPlaying == true) player?.pause() }
        runCatching { toneGenerator?.stopTone() }
    }

    private fun resumeAlarmSound() {
        runCatching { player?.start() }
        toneGenerator?.let(::startFallbackTone)
    }

    private fun startFallbackTone(generator: ToneGenerator) {
        runCatching {
            generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ANNOUNCEMENT_INTERVAL_MS.toInt())
        }
    }

    private fun startAsForeground(message: String) {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, ReminderAlarmService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("提醒時間到了")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "停止鬧鐘", stopIntent)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun stopAlarm() {
        ReminderAlarmState.isRinging = false
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources() {
        announcementJob?.cancel()
        announcementJob = null
        autoStopJob?.cancel()
        autoStopJob = null
        runCatching { tts?.close() }
        tts = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { toneGenerator?.stopTone() }
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.jajafu.x60assistant.action.START_REMINDER_ALARM"
        const val ACTION_STOP = "com.jajafu.x60assistant.action.STOP_REMINDER_ALARM"
        const val ACTION_PAUSE_FOR_VOICE = "com.jajafu.x60assistant.action.PAUSE_REMINDER_FOR_VOICE"
        const val ACTION_RESUME_AFTER_VOICE = "com.jajafu.x60assistant.action.RESUME_REMINDER_AFTER_VOICE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_MESSAGE = "reminder_message"
        private const val CHANNEL_ID = "reminder_alarm"
        private const val NOTIFICATION_ID = 2101
        private const val REQUEST_STOP = 212
        private const val REQUEST_OPEN = 213
        private const val FIRST_ANNOUNCEMENT_DELAY_MS = 2_500L
        private const val ANNOUNCEMENT_INTERVAL_MS = 30_000L
        private const val MAX_RING_DURATION_MS = 10 * 60_000L

        fun showFallbackNotification(context: Context, message: String) {
            createChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("提醒時間到了")
                    .setContentText(message)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .build(),
            )
        }

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "小助手鬧鐘提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "播放排程提醒的鬧鐘與語音"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
