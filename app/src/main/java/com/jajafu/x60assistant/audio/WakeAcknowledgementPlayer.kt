package com.jajafu.x60assistant.audio

import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.content.Context
import android.media.ToneGenerator
import com.jajafu.x60assistant.tts.SpeechOutputEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class WakeAcknowledgementPlayer(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) synchronized(toneLock) { toneGenerator.stopTone() }
    }
    private var focusRequest: AudioFocusRequest? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "x60-wake-ack")
    }

    @Volatile
    private var released = false
    private val toneLock = Any()
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME_PERCENT)

    fun play() {
        if (released) return
        executor.execute {
            synchronized(toneLock) {
                if (!released) toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, TONE_DURATION_MS)
            }
        }
    }

    suspend fun playAndAwait(speechOutput: SpeechOutputEngine?, response: String) {
        if (speechOutput != null && response.isNotBlank()) {
            try {
                speechOutput.speak(response)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the tone as a reliable fallback if TTS becomes unavailable at runtime.
            }
        }
        requestFocus()
        play()
        delay(TONE_DURATION_MS.toLong() + 30L)
        abandonFocus()
    }

    fun release() {
        released = true
        executor.shutdownNow()
        synchronized(toneLock) { toneGenerator.release() }
        abandonFocus()
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    companion object {
        private const val TONE_VOLUME_PERCENT = 100
        private const val TONE_DURATION_MS = 200
    }
}
