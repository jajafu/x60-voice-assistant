package com.jajafu.x60assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.PlaybackParams
import android.media.MediaPlayer
import android.os.Looper
import com.jajafu.x60assistant.data.AssistantSettings
import com.jajafu.x60assistant.llm.SpeechSynthesizer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 雲端 TTS spike（預設 `gpt-4o-mini-tts`）。
 *
 * 向 Provider 下載完整 WAV 音訊後以 `MediaPlayer` 經 `STREAM_MUSIC` 播放，
 * 在播放前套用 PCM 增益、語速與音調；與音樂共用同一個系統音量級別。
 * 失敗時拋出 [TtsException]，由 `VoiceOrchestrator` 的既有恢復路徑處理。
 */
class CloudTtsEngine(
    context: Context,
    private val settings: AssistantSettings,
    private val synthesizer: SpeechSynthesizer,
) : SpeechOutputEngine {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val playerAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private data class ActivePlayback(val id: String, val file: File)
    private val activePlayback = AtomicReference<ActivePlayback?>(null)
    private val activeContinuation = AtomicReference<CancellableContinuation<Unit>?>(null)
    private val activePlayer = AtomicReference<MediaPlayer?>(null)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
    }
    private var focusRequest: AudioFocusRequest? = null

    override suspend fun initialize(): TtsStatus = TtsStatus(
        available = true,
        engine = "cloud/${settings.cloudTtsModel}",
        voice = settings.cloudTtsVoice,
        locale = "雲端",
        message = "雲端 TTS（spike），需網路",
    )

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        if (!requestAudioFocus()) throw TtsException("無法取得 Audio Focus")
        // MediaPlayer 回呼需要 Looper；強制在主執行緒建立與播放。
        withContext(Dispatchers.Main.immediate) {
            check(Looper.myLooper() != null) { "雲端 TTS 需要主執行緒 Looper" }
            try {
                val audio = synthesizer.synthesize(text)
                val playbackId = "cloud-${UUID.randomUUID()}"
                val file = File(appContext.cacheDir, "$playbackId.wav")
                try {
                    val gainApplied = withContext(Dispatchers.IO) {
                        val result = WavAudioGain.apply(audio, settings.ttsVolume)
                        file.writeBytes(result.bytes)
                        result.applied
                    }
                    val active = ActivePlayback(playbackId, file)
                    activePlayback.set(active)
                    try {
                        suspendCancellableCoroutine { continuation ->
                            val player = MediaPlayer()
                            activePlayer.set(player)
                            activeContinuation.set(continuation)
                            continuation.invokeOnCancellation {
                                activeContinuation.compareAndSet(continuation, null)
                                if (activePlayback.compareAndSet(active, null)) {
                                    runCatching { player.stop() }
                                    runCatching { player.release() }
                                    activePlayer.compareAndSet(player, null)
                                    runCatching { file.delete() }
                                }
                            }
                            player.setAudioAttributes(playerAudioAttributes)
                            player.setOnPreparedListener { prepared ->
                                if (activePlayback.get() != active || !continuation.isActive) return@setOnPreparedListener
                                try {
                                    val playerVolume = if (gainApplied) 1.0f else settings.ttsVolume.coerceIn(0.0f, 1.0f)
                                    prepared.setVolume(playerVolume, playerVolume)
                                    prepared.setPlaybackParams(
                                        PlaybackParams()
                                            .setSpeed(settings.speechRate)
                                            .setPitch(settings.pitch),
                                    )
                                    prepared.start()
                                } catch (error: Exception) {
                                    if (activePlayback.compareAndSet(active, null)) {
                                        runCatching { prepared.release() }
                                        activePlayer.compareAndSet(player, null)
                                        runCatching { file.delete() }
                                        if (activeContinuation.compareAndSet(continuation, null) && continuation.isActive) {
                                            continuation.resumeWithException(TtsException("雲端 TTS 啟動失敗", error))
                                        }
                                    }
                                }
                            }
                            player.setOnCompletionListener {
                                if (activePlayback.compareAndSet(active, null)) {
                                    runCatching { it.release() }
                                    activePlayer.compareAndSet(player, null)
                                    runCatching { file.delete() }
                                    if (activeContinuation.compareAndSet(continuation, null) && continuation.isActive) {
                                        continuation.resume(Unit)
                                    }
                                }
                            }
                            player.setOnErrorListener { _, what, extra ->
                                if (activePlayback.compareAndSet(active, null)) {
                                    runCatching { player.release() }
                                    activePlayer.compareAndSet(player, null)
                                    runCatching { file.delete() }
                                    if (activeContinuation.compareAndSet(continuation, null) && continuation.isActive) {
                                        continuation.resumeWithException(TtsException("雲端 TTS 播放失敗：$what/$extra"))
                                    }
                                }
                                true
                            }
                            try {
                                player.setDataSource(file.absolutePath)
                                player.prepareAsync()
                            } catch (error: Exception) {
                                if (activePlayback.compareAndSet(active, null)) {
                                    runCatching { player.release() }
                                    activePlayer.compareAndSet(player, null)
                                    runCatching { file.delete() }
                                    if (activeContinuation.compareAndSet(continuation, null) && continuation.isActive) {
                                        continuation.resumeWithException(error)
                                    }
                                }
                            }
                        }
                    } finally {
                        activePlayback.compareAndSet(active, null)
                        runCatching { file.delete() }
                    }
                } finally {
                    runCatching { file.delete() }
                }
            } finally {
                abandonAudioFocus()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(playerAudioAttributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    override fun stop() {
        synthesizer.cancel()
        val continuation = activeContinuation.getAndSet(null)
        activePlayer.getAndSet(null)?.let { player ->
            // onCompletion 不會因 stop() 觸發；先清 active 再停，避免回呼重複結算。
            activePlayback.getAndSet(null)?.let { runCatching { it.file.delete() } }
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        if (continuation != null && continuation.isActive) {
            continuation.resumeWithException(TtsException("TTS 已中止"))
        }
        abandonAudioFocus()
    }

    override fun close() {
        stop()
        (synthesizer as? AutoCloseable)?.let { closeable ->
            runCatching { closeable.close() }
        }
    }
}
