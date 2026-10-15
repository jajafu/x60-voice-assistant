package com.va.voiceassistant.diagnostics

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import com.va.voiceassistant.asr.AsrResult
import com.va.voiceassistant.orchestration.VoiceState
import com.va.voiceassistant.tts.MarkdownSpeechCleaner

data class M1DiagnosticsSnapshot(
    val phase: VoiceState = VoiceState.STOPPED,
    val audioFrameCount: Long = 0,
    val wakeCount: Long = 0,
    val lastWakeScore: Float? = null,
    val lastWakeTimestampEpochMs: Long? = null,
    val serviceUptimeMs: Long = 0,
    val audioErrorCount: Long = 0,
    val droppedFrameCount: Long = 0,
    val lastError: VoiceErrorCode? = null,
    val audioSource: String = "—",
    val wakeLockHeld: Boolean = false,
    val modelId: String = "—",
    val modelSha256: String = "—",
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val transcriptLength: Int = 0,
    val transcriptHash: String = "—",
    val asrModelId: String = "—",
    val asrRtf: Double? = null,
    val asrFinalLatencyMs: Long? = null,
    val answerPreview: String = "",
    val answerLength: Int = 0,
    val answerHash: String = "—",
    val llmHttpStatus: Int? = null,
    val llmFirstTokenLatencyMs: Long? = null,
    val llmTotalLatencyMs: Long? = null,
    val ttsQueueDepth: Int = 0,
    val ttsQueueHighWaterMark: Int = 0,
    val ttsEngine: String = "—",
    val ttsVoice: String = "—",
    val audioQueueDepth: Int = 0,
    val audioQueueHighWaterMark: Int = 0,
    val serviceRestartCount: Long = 0,
    val volumeLastCommand: String = "—",
    val volumeLastDetail: String = "—",
)

object M1DiagnosticsRepository {
    private val audioFrames = AtomicLong(0)
    private val wakeCount = AtomicLong(0)
    private val audioErrors = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private val startedAtElapsedMs = AtomicLong(0)
    private val phase = AtomicReference(VoiceState.STOPPED)
    private val lastError = AtomicReference<VoiceErrorCode?>(null)
    private val audioSource = AtomicReference("—")
    private val wakeLockHeld = AtomicReference(false)
    private val lastWakeScore = AtomicReference<Float?>(null)
    private val lastWakeEpochMs = AtomicReference<Long?>(null)
    private val modelId = AtomicReference("—")
    private val modelSha256 = AtomicReference("—")
    private val partialTranscript = AtomicReference("")
    private val finalTranscript = AtomicReference("")
    private val transcriptHash = AtomicReference("—")
    private val asrModelId = AtomicReference("—")
    private val asrRtf = AtomicReference<Double?>(null)
    private val asrFinalLatencyMs = AtomicReference<Long?>(null)
    private val answerPreview = AtomicReference("")
    private val answerHash = AtomicReference("—")
    private val llmHttpStatus = AtomicReference<Int?>(null)
    private val llmFirstTokenLatencyMs = AtomicReference<Long?>(null)
    private val llmTotalLatencyMs = AtomicReference<Long?>(null)
    private val ttsQueueDepth = AtomicLong(0)
    private val ttsQueueHighWaterMark = AtomicLong(0)
    private val ttsEngine = AtomicReference("—")
    private val ttsVoice = AtomicReference("—")
    private val audioQueueDepth = AtomicLong(0)
    private val audioQueueHighWaterMark = AtomicLong(0)
    private val serviceRestartCount = AtomicLong(0)
    private val volumeLastCommand = AtomicReference("—")
    private val volumeLastDetail = AtomicReference("—")
    private val mutableSnapshot = MutableStateFlow(M1DiagnosticsSnapshot())

    val snapshot = mutableSnapshot.asStateFlow()
    val ringLog = SafeRingLog()

    fun resetForStart(modelIdValue: String, modelSha256Value: String) {
        serviceRestartCount.incrementAndGet()
        audioFrames.set(0)
        wakeCount.set(0)
        audioErrors.set(0)
        droppedFrames.set(0)
        startedAtElapsedMs.set(SystemClock.elapsedRealtime())
        phase.set(VoiceState.STARTING)
        lastError.set(null)
        audioSource.set("—")
        wakeLockHeld.set(false)
        lastWakeScore.set(null)
        lastWakeEpochMs.set(null)
        modelId.set(modelIdValue)
        modelSha256.set(modelSha256Value)
        partialTranscript.set("")
        finalTranscript.set("")
        transcriptHash.set("—")
        asrModelId.set("—")
        asrRtf.set(null)
        asrFinalLatencyMs.set(null)
        answerPreview.set("")
        answerHash.set("—")
        llmHttpStatus.set(null)
        llmFirstTokenLatencyMs.set(null)
        llmTotalLatencyMs.set(null)
        ttsQueueDepth.set(0)
        ttsQueueHighWaterMark.set(0)
        ttsEngine.set("—")
        ttsVoice.set("—")
        ringLog.clear()
        log("SERVICE_STARTING")
        publish()
    }

    fun onAudioFrame() {
        audioFrames.incrementAndGet()
    }

    fun onDroppedFrame() {
        droppedFrames.incrementAndGet()
    }

    fun onAudioError(code: VoiceErrorCode, detail: String? = null) {
        audioErrors.incrementAndGet()
        recordError(code, detail)
    }

    fun onWake(configuredScore: Float, wakePhrase: String) {
        wakeCount.incrementAndGet()
        lastWakeScore.set(configuredScore)
        lastWakeEpochMs.set(System.currentTimeMillis())
        partialTranscript.set("")
        finalTranscript.set("")
        answerPreview.set("")
        log("WAKE_DETECTED", detail = "keyword=$wakePhrase")
        publish()
    }

    fun setIdle(audioSourceValue: String) {
        audioSource.set(audioSourceValue)
        phase.set(VoiceState.IDLE_KWS)
        log("IDLE_KWS", detail = "source=$audioSourceValue")
        publish()
    }

    fun setState(state: VoiceState, reason: String) {
        phase.set(state)
        log("STATE_$state", detail = "reason=$reason")
        publish()
    }

    fun setPartialTranscript(text: String) {
        partialTranscript.set(text.take(MAX_VISIBLE_TRANSCRIPT))
        publish()
    }

    fun setFinalTranscript(displayText: String, result: AsrResult) {
        partialTranscript.set("")
        finalTranscript.set(displayText.take(MAX_VISIBLE_TRANSCRIPT))
        transcriptHash.set(shortHash(result.text))
        asrRtf.set(result.realTimeFactor)
        asrFinalLatencyMs.set(result.finalLatencyMs)
        log(
            "ASR_FINAL",
            detail = "length=${result.text.length},hash=${transcriptHash.get()},rtf=${"%.3f".format(result.realTimeFactor)}",
        )
        publish()
    }

    fun setAsrModel(id: String) {
        asrModelId.set(id)
        publish()
    }

    fun setLlmResult(answer: String, httpStatus: Int?, firstTokenMs: Long?, totalMs: Long, displayAnswer: Boolean = true) {
        if (displayAnswer) answerPreview.set(MarkdownSpeechCleaner.clean(answer).take(MAX_VISIBLE_TRANSCRIPT))
        answerHash.set(shortHash(answer))
        llmHttpStatus.set(httpStatus)
        llmFirstTokenLatencyMs.set(firstTokenMs)
        llmTotalLatencyMs.set(totalMs)
        log("LLM_COMPLETE", detail = "status=${httpStatus ?: "-"},length=${answer.length},hash=${answerHash.get()}")
        publish()
    }

    fun setAnswerPreview(answer: String) {
        answerPreview.set(MarkdownSpeechCleaner.clean(answer).take(MAX_VISIBLE_TRANSCRIPT))
        publish()
    }

    fun setTtsStatus(engine: String, voice: String) {
        ttsEngine.set(engine)
        ttsVoice.set(voice)
        publish()
    }

    fun setTtsQueue(depth: Int, highWaterMark: Int) {
        ttsQueueDepth.set(depth.toLong())
        ttsQueueHighWaterMark.set(highWaterMark.toLong())
        publish()
    }

    /**
     * 記錄一次語音音量操作的前後實測值。
     *
     * vivo／OriginOS 上 `setStreamVolume` 可能不丟例外卻靜默失效，只看回話無法分辨
     * 是寫入被丟棄還是讀回讀錯對象，因此把調整前、目標與讀回值一併留下。
     * 同時寫入 ring log，會隨診斷匯出一起帶出。
     */
    fun setVolumeAttempt(command: String, detail: String) {
        volumeLastCommand.set(command)
        volumeLastDetail.set(detail)
        log("VOLUME_$command", detail = detail)
        publish()
    }

    fun setAudioQueue(depth: Int, highWaterMark: Int) {
        audioQueueDepth.set(depth.toLong())
        audioQueueHighWaterMark.set(highWaterMark.toLong())
    }

    fun clearDiagnostics() {
        ringLog.clear()
        partialTranscript.set("")
        finalTranscript.set("")
        transcriptHash.set("—")
        answerPreview.set("")
        answerHash.set("—")
        lastError.set(null)
        publish()
    }

    fun setWakeLockHeld(held: Boolean) {
        wakeLockHeld.set(held)
        log(if (held) "WAKE_LOCK_ACQUIRED" else "WAKE_LOCK_RELEASED")
        publish()
    }

    fun recordError(code: VoiceErrorCode, detail: String? = null) {
        lastError.set(code)
        phase.set(VoiceState.ERROR)
        log("ERROR", code, detail)
        publish()
    }

    fun recordSessionError(code: VoiceErrorCode, detail: String? = null) {
        lastError.set(code)
        log("SESSION_ERROR", code, detail)
        publish()
    }

    fun setStopped() {
        phase.set(VoiceState.STOPPED)
        wakeLockHeld.set(false)
        log("SERVICE_STOPPED")
        publish()
    }

    fun publish() {
        val started = startedAtElapsedMs.get()
        val currentPhase = phase.get()
        mutableSnapshot.value = M1DiagnosticsSnapshot(
            phase = currentPhase,
            audioFrameCount = audioFrames.get(),
            wakeCount = wakeCount.get(),
            lastWakeScore = lastWakeScore.get(),
            lastWakeTimestampEpochMs = lastWakeEpochMs.get(),
            serviceUptimeMs = if (started == 0L || currentPhase == VoiceState.STOPPED) {
                0
            } else {
                (SystemClock.elapsedRealtime() - started).coerceAtLeast(0)
            },
            audioErrorCount = audioErrors.get(),
            droppedFrameCount = droppedFrames.get(),
            lastError = lastError.get(),
            audioSource = audioSource.get(),
            wakeLockHeld = wakeLockHeld.get(),
            modelId = modelId.get(),
            modelSha256 = modelSha256.get(),
            partialTranscript = partialTranscript.get(),
            finalTranscript = finalTranscript.get(),
            transcriptLength = finalTranscript.get().length,
            transcriptHash = transcriptHash.get(),
            asrModelId = asrModelId.get(),
            asrRtf = asrRtf.get(),
            asrFinalLatencyMs = asrFinalLatencyMs.get(),
            answerPreview = answerPreview.get(),
            answerLength = answerPreview.get().length,
            answerHash = answerHash.get(),
            llmHttpStatus = llmHttpStatus.get(),
            llmFirstTokenLatencyMs = llmFirstTokenLatencyMs.get(),
            llmTotalLatencyMs = llmTotalLatencyMs.get(),
            ttsQueueDepth = ttsQueueDepth.get().toInt(),
            ttsQueueHighWaterMark = ttsQueueHighWaterMark.get().toInt(),
            ttsEngine = ttsEngine.get(),
            ttsVoice = ttsVoice.get(),
            audioQueueDepth = audioQueueDepth.get().toInt(),
            audioQueueHighWaterMark = audioQueueHighWaterMark.get().toInt(),
            serviceRestartCount = serviceRestartCount.get(),
            volumeLastCommand = volumeLastCommand.get(),
            volumeLastDetail = volumeLastDetail.get(),
        )
    }

    private fun log(
        event: String,
        errorCode: VoiceErrorCode? = null,
        detail: String? = null,
    ) {
        ringLog.add(
            SafeLogEntry(
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                event = event,
                errorCode = errorCode,
                detail = detail,
            ),
        )
    }

    private fun shortHash(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private const val MAX_VISIBLE_TRANSCRIPT = 2_000
}
