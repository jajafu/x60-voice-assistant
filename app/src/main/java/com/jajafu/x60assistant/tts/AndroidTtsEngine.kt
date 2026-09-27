package com.jajafu.x60assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val speechAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private data class ActiveUtterance(val id: String, val continuation: CancellableContinuation<Unit>)
    private val activeUtterance = AtomicReference<ActiveUtterance?>(null)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
    }
    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var status = TtsStatus(false, message = "尚未初始化")

    override suspend fun initialize(): TtsStatus = withContext(Dispatchers.Main.immediate) {
        closeTtsOnly()
        val requestedEngine = settings.ttsEngine.trim().ifBlank { null }
        val requestedInstance = createTts(requestedEngine)

        if (requestedInstance != null) {
            val requestedStatus = activate(requestedInstance, requestedEngine)
            if (requestedStatus.available || requestedEngine == null ||
                requestedEngine.equals(requestedInstance.defaultEngine, ignoreCase = true)
            ) {
                return@withContext requestedStatus
            }

            requestedInstance.shutdown()
            tts = null
        }

        val defaultInstance = createTts(null)
        if (defaultInstance == null) {
            status = TtsStatus(false, message = "系統預設 TTS Engine 初始化失敗")
            return@withContext status
        }

        val defaultStatus = activate(defaultInstance, null)
        status = if (requestedEngine != null && defaultStatus.available) {
            defaultStatus.copy(message = "已自動改用系統預設 TTS；${defaultStatus.message}")
        } else {
            defaultStatus
        }
        status
    }

    private suspend fun createTts(enginePackage: String?): TextToSpeech? =
        suspendCancellableCoroutine { continuation ->
            var instance: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { result ->
                // Android may report an immediate failure before the constructor assignment completes.
                Handler(Looper.getMainLooper()).post {
                    val created = instance
                    if (!continuation.isActive) {
                        created?.shutdown()
                        return@post
                    }
                    if (result == TextToSpeech.SUCCESS && created != null) {
                        continuation.resume(created)
                    } else {
                        created?.shutdown()
                        continuation.resume(null)
                    }
                }
            }

            try {
                instance = if (enginePackage == null) {
                    TextToSpeech(appContext, listener)
                } else {
                    TextToSpeech(appContext, listener, enginePackage)
                }
            } catch (_: RuntimeException) {
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { instance?.shutdown() }
        }

    private fun activate(engine: TextToSpeech, requestedEngine: String?): TtsStatus {
        tts = engine
        configureListener(engine)
        engine.setAudioAttributes(speechAudioAttributes)
        status = selectVoice(engine, requestedEngine)
        return status
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts ?: throw TtsException("TTS 尚未初始化")
        if (!status.available) throw TtsException(status.message)
        if (!requestAudioFocus()) throw TtsException("無法取得 Audio Focus")
        val utteranceId = "answer-${UUID.randomUUID()}"
        try {
            suspendCancellableCoroutine { continuation ->
                val active = ActiveUtterance(utteranceId, continuation)
                activeUtterance.set(active)
                continuation.invokeOnCancellation {
                    if (activeUtterance.compareAndSet(active, null)) engine.stop()
                }
                val params = Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                }
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (result != TextToSpeech.SUCCESS && activeUtterance.compareAndSet(active, null)) {
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
            override fun onDone(utteranceId: String?) = finishActive(null, utteranceId)
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) = finishActive(TtsException("TTS 播放失敗"), utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) =
                finishActive(TtsException("TTS 播放失敗：$errorCode"), utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) =
                finishActive(if (interrupted) TtsException("TTS 已中止") else null, utteranceId)
        })
    }

    private fun finishActive(error: Throwable?, utteranceId: String? = null) {
        val active = activeUtterance.get() ?: return
        if (utteranceId != null && utteranceId != active.id) return
        if (!activeUtterance.compareAndSet(active, null)) return
        val continuation = active.continuation
        if (!continuation.isActive) return
        if (error == null) continuation.resume(Unit) else continuation.resumeWithException(error)
    }

    private fun selectVoice(engine: TextToSpeech, requestedEngine: String?): TtsStatus {
        engine.setSpeechRate(settings.speechRate)
        engine.setPitch(settings.pitch)
        val activeEngine = requestedEngine
            ?.takeIf { requested -> engine.engines.any { it.name == requested } }
            ?: engine.defaultEngine.orEmpty()
        val voices = engine.voices.orEmpty()
        val requested = voices.firstOrNull { it.name == settings.ttsVoice }
        val selected = requested
            ?: voices.filter { !it.isNetworkConnectionRequired }.firstOrNull { it.locale.matchesLanguage("zh", "TW") }
            ?: voices.filter { !it.isNetworkConnectionRequired }.firstOrNull { it.locale.matchesLanguage("zh", "CN") }
            ?: voices.firstOrNull { it.locale.language.equals("zh", ignoreCase = true) }
        if (selected == null || engine.setVoice(selected) != TextToSpeech.SUCCESS) {
            return TtsStatus(false, activeEngine, message = "沒有可用的中文 Voice")
        }
        return TtsStatus(
            available = true,
            engine = activeEngine,
            voice = selected.name,
            locale = selected.locale.toLanguageTag(),
            message = if (selected.isNetworkConnectionRequired) "使用網路 Voice" else "使用離線 Voice",
        )
    }

    private fun Locale.matchesLanguage(language: String, country: String): Boolean =
        this.language.equals(language, ignoreCase = true) && this.country.equals(country, ignoreCase = true)

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAudioAttributes)
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
