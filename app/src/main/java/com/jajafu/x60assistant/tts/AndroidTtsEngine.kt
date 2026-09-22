package com.jajafu.x60assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jajafu.x60assistant.data.AssistantSettings
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidTtsEngine(
    context: Context,
    private val settings: AssistantSettings,
) : SpeechOutputEngine {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val activeUtterance = AtomicReference<CancellableContinuation<Unit>?>(null)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
    }
    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var status = TtsStatus(false, message = "尚未初始化")

    override suspend fun initialize(): TtsStatus = withContext(Dispatchers.Main.immediate) {
        closeTtsOnly()
        suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(appContext, { result ->
                val instance = engine
                if (result != TextToSpeech.SUCCESS || instance == null) {
                    status = TtsStatus(false, message = "TTS Engine 初始化失敗")
                    continuation.resume(status)
                    return@TextToSpeech
                }
                tts = instance
                configureListener(instance)
                status = selectVoice(instance)
                continuation.resume(status)
            }, settings.ttsEngine.ifBlank { null })
            continuation.invokeOnCancellation { engine.shutdown() }
        }
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts ?: throw TtsException("TTS 尚未初始化")
        if (!status.available) throw TtsException(status.message)
        if (!requestAudioFocus()) throw TtsException("無法取得 Audio Focus")
        val utteranceId = "answer-${UUID.randomUUID()}"
        try {
            suspendCancellableCoroutine { continuation ->
                activeUtterance.set(continuation)
                continuation.invokeOnCancellation { engine.stop() }
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
                if (result != TextToSpeech.SUCCESS && activeUtterance.compareAndSet(continuation, null)) {
                    continuation.resumeWithException(TtsException("TTS 播放要求失敗"))
                }
            }
        } finally {
            abandonAudioFocus()
        }
    }

    private fun configureListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finishActive(null)
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) = finishActive(TtsException("TTS 播放失敗"))
            override fun onError(utteranceId: String?, errorCode: Int) =
                finishActive(TtsException("TTS 播放失敗：$errorCode"))
            override fun onStop(utteranceId: String?, interrupted: Boolean) =
                finishActive(if (interrupted) TtsException("TTS 已中止") else null)
        })
    }

    private fun finishActive(error: Throwable?) {
        val continuation = activeUtterance.getAndSet(null) ?: return
        if (!continuation.isActive) return
        if (error == null) continuation.resume(Unit) else continuation.resumeWithException(error)
    }

    private fun selectVoice(engine: TextToSpeech): TtsStatus {
        engine.setSpeechRate(settings.speechRate)
        engine.setPitch(settings.pitch)
        val voices = engine.voices.orEmpty()
        val requested = voices.firstOrNull { it.name == settings.ttsVoice }
        val selected = requested
            ?: voices.filter { !it.isNetworkConnectionRequired }.firstOrNull { it.locale.matchesLanguage("zh", "TW") }
            ?: voices.filter { !it.isNetworkConnectionRequired }.firstOrNull { it.locale.matchesLanguage("zh", "CN") }
            ?: voices.firstOrNull { it.locale.language.equals("zh", ignoreCase = true) }
        if (selected == null || engine.setVoice(selected) != TextToSpeech.SUCCESS) {
            return TtsStatus(false, engine.defaultEngine.orEmpty(), message = "沒有可用的中文 Voice")
        }
        return TtsStatus(
            available = true,
            engine = engine.defaultEngine.orEmpty(),
            voice = selected.name,
            locale = selected.locale.toLanguageTag(),
            message = if (selected.isNetworkConnectionRequired) "使用網路 Voice" else "使用離線 Voice",
        )
    }

    private fun Locale.matchesLanguage(language: String, country: String): Boolean =
        this.language.equals(language, ignoreCase = true) && this.country.equals(country, ignoreCase = true)

    private fun requestAudioFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
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
        tts?.stop()
        finishActive(TtsException("TTS 已取消"))
        abandonAudioFocus()
    }

    private fun closeTtsOnly() {
        stop()
        tts?.shutdown()
        tts = null
    }

    override fun close() = closeTtsOnly()
}
