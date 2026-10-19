package com.va.voiceassistant.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.va.voiceassistant.audio.AudioFrame
import com.va.voiceassistant.orchestration.AndroidMonotonicClock
import com.va.voiceassistant.orchestration.MonotonicClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 系統語音辨識服務的整句引擎（探測通過才會被選用，見 `SystemSpeechRecognizerSupport`）。
 *
 * 系統服務不接受外部 PCM，只能自己開麥收音，因此 [takesOverMicrophone] 為真：
 * 進入聆聽前由 `VoiceOrchestrator` 先停掉共用 AudioRecord，再由本引擎交棒給系統；
 * 結束等待（含取消）時會**等主執行緒 destroy 完成**才返回，避免 orchestrator
 * 重新開啟共用 AudioRecord 時系統 recognizer 還在聽。
 * 沒聽到、系統逾時、網路與音訊類錯誤回傳空白結果（軟路徑，orchestrator 記
 * `ASR_NO_RESULT` 並回到待喚醒）；忙線、權限與 client 錯誤才拋出例外，
 * 由 orchestrator 記 `ASR_RUNTIME_FAILED`。
 */
class SystemSpeechRecognizerEngine(
    private val context: Context,
    /** 進入聆聽到開口之間的等待，沿用 VAD「等待說話」設定（毫秒）。 */
    private val waitForSpeechMs: Long,
    /** 開口後的整句上限，沿用 VAD「單句最長」設定（毫秒）。 */
    private val maxUtteranceMs: Long,
    /** 探測確認支援、由支援清單挑出的中文語言標籤（如 zh-TW／zh-CN）。 */
    private val language: String,
    private val clock: MonotonicClock = AndroidMonotonicClock,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : SpeechRecognizerEngine {
    init {
        require(language.isNotBlank()) { "system language must be resolved by the probe" }
    }

    override val modelId = SystemSpeechRecognizerSupport.MODEL_ID
    override val takesOverMicrophone: Boolean get() = true

    private val lock = Any()

    /** 當前整句等待；在 worker 執行緒建立，回呼在主執行緒交付一次。 */
    @Volatile private var episode: Episode? = null

    /** 只在主執行緒存取；`SpeechRecognizer` 的建立與 destroy 都必須在主執行緒。 */
    private var recognizer: SpeechRecognizer? = null

    override fun beginSession() {
        synchronized(lock) { episode = null }
    }

    /** 本引擎不收共用麥克風的音框；收音完全由系統服務負責。 */
    override fun accept(frame: AudioFrame): String? = null

    override fun finish(): AsrResult {
        val startedMs = clock.elapsedRealtimeMs()
        val current = synchronized(lock) { Episode().also { episode = it } }
        if (!startOnMainThread(current)) {
            throw IllegalStateException(current.pendingError ?: "system-asr-error:start-failed")
        }
        try {
            val text = awaitWithWatchdog(current, startedMs)
            val recognitionMs = clock.elapsedRealtimeMs() - startedMs
            return AsrResult(
                text = text.orEmpty(),
                audioDurationMs = 0,
                recognitionDurationMs = recognitionMs,
                realTimeFactor = 0.0,
                finalLatencyMs = recognitionMs,
            )
        } finally {
            // 等待結束（含取消路徑）後等主執行緒收掉系統 recognizer，麥克風才算交還共用 AudioRecord。
            synchronized(lock) { if (episode === current) episode = null }
            stopAndDestroyRecognition()
        }
    }

    /** 只取消進行中的聆聽；完成、過期或無引擎時也不會重複交付。 */
    override fun cancelActiveRequest() {
        synchronized(lock) {
            episode?.deliverText(null)
        }
        stopAndDestroyRecognition()
    }

    override fun cancel() = cancelActiveRequest()

    override fun close() = cancelActiveRequest()

    private fun startOnMainThread(current: Episode): Boolean {
        val started = CountDownLatch(1)
        var startError: Throwable? = null
        mainHandler.post {
            // 逾時／取消後才執行到這個 post 的情況（episode 已被清掉或換新）：
            // 不建立 recognizer，避免洩漏一個仍在收音、卻沒人負責收掉的服務綁定。
            if (synchronized(lock) { episode !== current }) {
                started.countDown()
                return@post
            }
            try {
                val created = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = created
                created.setRecognitionListener(buildListener(current))
                created.startListening(buildIntent())
            } catch (error: Throwable) {
                startError = error
                current.deliverError(error.message ?: error.javaClass.simpleName)
            } finally {
                started.countDown()
            }
        }
        if (!started.await(MAIN_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            current.deliverError("system-asr-error:main-thread-unavailable")
            stopAndDestroyRecognition()
            return false
        }
        startError ?: return true
        stopAndDestroyRecognition()
        return false
    }

    /**
     * 阻塞等待整句結果。預算兩段式錨點：等開口的 `waitForSpeechMs` 從 `onReadyForSpeech` 起算
     * （尚未收到 ready 時退回 finish 進入點，避免卡死的引擎無界等）、開口後的 `maxUtteranceMs`
     * 從 `onBeginningOfSpeech` 起算；錨點在等待期間翻轉時，迴圈會依新的上限繼續等。
     * 逾時回傳 `null`＝沒聽到（軟路徑）。啟動 recognizer 的冷啟動時間不計入使用者的開口預算。
     */
    private fun awaitWithWatchdog(current: Episode, startedMs: Long): String? {
        while (true) {
            val anchorMs: Long
            val budgetMs: Long
            val now = clock.elapsedRealtimeMs()
            when {
                current.speechStartMs >= 0L -> { anchorMs = current.speechStartMs; budgetMs = maxUtteranceMs }
                current.readyMs >= 0L -> { anchorMs = current.readyMs; budgetMs = waitForSpeechMs }
                else -> { anchorMs = startedMs; budgetMs = waitForSpeechMs }
            }
            val remainingMs = budgetMs - (now - anchorMs)
            if (remainingMs <= 0) return null
            if (current.latch.await(remainingMs, TimeUnit.MILLISECONDS)) {
                current.pendingError?.let { throw IllegalStateException(it) }
                return current.text
            }
            // 未在剩餘上限內完成：ready／開口可能在等待中翻轉，回算新的上限。
        }
    }

    private fun stopAndDestroyRecognition() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyNow()
            return
        }
        val done = CountDownLatch(1)
        mainHandler.post {
            runCatching { destroyNow() }
            done.countDown()
        }
        // 主執行緒突發忙碌時不無限等；destroy 已排入，最壞情況晚一點完成。
        done.await(DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private fun destroyNow() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun buildListener(current: Episode): RecognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            episode?.takeIf { it === current }?.deliverText(
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(),
            )
        }

        /** 忙線（8）、權限（9）、client（5）不可重試，交給 orchestrator 記 `ASR_RUNTIME_FAILED`。 */
        override fun onError(error: Int) {
            episode?.takeIf { it === current }?.let {
                if (error in SOFT_ERRORS) it.deliverText(null) else it.deliverError("system-asr-error:$error")
            }
        }

        override fun onBeginningOfSpeech() {
            episode?.takeIf { it === current }?.let { it.speechStartMs = clock.elapsedRealtimeMs() }
        }
        override fun onReadyForSpeech(params: Bundle?) {
            episode?.takeIf { it === current }?.let { it.readyMs = clock.elapsedRealtimeMs() }
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * 一次整句等待的結果信箱：主執行緒交付一次，worker 執行緒阻塞讀取。
     * ready／開口綁定本 Episode，舊 recognizer 的 callback 進不了新一輪的 timeout 計算。
     */
    private class Episode {
        val latch = CountDownLatch(1)

        /** watchdog 預算錨點：ready 與開口的主執行緒時間戳（-1 表示尚未發生）。 */
        @Volatile var readyMs = -1L
        @Volatile var speechStartMs = -1L
        @Volatile var text: String? = null
            private set
        @Volatile var pendingError: String? = null
            private set
        private val delivered = AtomicBoolean(false)

        /** 逾時／軟失敗／結果共用同一信箱；先到者終局，後到忽略。 */
        fun deliverText(value: String?) {
            if (!delivered.compareAndSet(false, true)) return
            text = value
            latch.countDown()
        }

        fun deliverError(detail: String) {
            if (!delivered.compareAndSet(false, true)) return
            pendingError = detail
            latch.countDown()
        }
    }

    companion object {
        /** SpeechRecognizer：1 NETWORK_TIMEOUT、2 NETWORK、3 AUDIO、4 SERVER、6 SPEECH_TIMEOUT、7 NO_MATCH。 */
        private val SOFT_ERRORS = setOf(1, 2, 3, 4, 6, 7)

        /** 主執行緒建立 recognizer 的等待上限；主執行緒被佔住即視為啟動失敗。 */
        private const val MAIN_START_TIMEOUT_MS = 3_000L

        /** 主執行緒 destroy 的等待上限；逾時不無限等，仍會返回。 */
        private const val DESTROY_TIMEOUT_MS = 2_000L
    }
}
