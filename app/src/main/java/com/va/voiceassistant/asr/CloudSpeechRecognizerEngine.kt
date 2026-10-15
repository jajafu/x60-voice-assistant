package com.va.voiceassistant.asr

import com.va.voiceassistant.audio.AudioFrame
import com.va.voiceassistant.llm.AudioTranscriber
import com.va.voiceassistant.orchestration.AndroidMonotonicClock
import com.va.voiceassistant.orchestration.MonotonicClock
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 雲端 ASR spike（預設 `whisper-1`，錄完再轉）。
 *
 * VAD 與語句邊界維持本機；此引擎只在該輪收集 PCM（16 kHz／mono／PCM16），
 * 在 [finish] 時包成 WAV 一次上傳。`accept` 永遠不回傳部分結果。
 * 失敗時拋出 [com.va.voiceassistant.llm.LlmException]，
 * 由 `VoiceOrchestrator` 記為 `ASR_RUNTIME_FAILED`，不靜默退回本機。
 */
class CloudSpeechRecognizerEngine(
    override val modelId: String,
    private val transcriber: AudioTranscriber,
    private val clock: MonotonicClock = AndroidMonotonicClock,
    private val maxBufferSeconds: Int = MAX_BUFFER_SECONDS,
) : SpeechRecognizerEngine {
    private val lock = Any()

    // 以 ShortArray 而非 ArrayList<Short> 保存 PCM：60 秒上限會累積 96 萬個樣本，
    // boxed Short 的暫時配置超過 20 MB，且在記憶體最緊的轉寫時刻造成 GC 壓力。
    private var pcm = ShortArray(0)

    /** 已使用的樣本數；`pcm.size` 是配置大小，兩者不可混用。 */
    private var used = 0

    override fun beginSession() {
        synchronized(lock) { reset() }
    }

    override fun accept(frame: AudioFrame): String? {
        synchronized(lock) {
            val samples = frame.samples
            val remaining = maxBufferSeconds * SAMPLE_RATE - used
            if (remaining <= 0 || samples.isEmpty()) return@synchronized
            val copied = minOf(samples.size, remaining)
            if (used + copied > pcm.size) pcm = pcm.copyOf(capacityFor(used + copied))
            samples.copyInto(pcm, destinationOffset = used, startIndex = 0, endIndex = copied)
            used += copied
        }
        return null
    }

    private fun reset() {
        pcm = ShortArray(0)
        used = 0
    }

    /** 以倍增方式取得下一個緩衝大小，避免每個 frame 都重新配置。 */
    private fun capacityFor(required: Int): Int {
        var capacity = pcm.size.coerceAtLeast(MIN_CAPACITY_SAMPLES)
        while (capacity < required) capacity *= 2
        return capacity
    }

    override fun finish(): AsrResult {
        // 交出目前的樣本並清空緩衝，呼叫端可安全持有 snapshot。
        val snapshot = synchronized(lock) {
            val data = pcm.copyOf(used)
            reset()
            data
        }
        if (snapshot.isEmpty()) throw IllegalStateException("沒有可辨識的語音")
        val audioDurationMs = snapshot.size * 1_000L / SAMPLE_RATE
        val started = clock.elapsedRealtimeMs()
        val text = transcriber.transcribe(pcmToWav(snapshot))
        val recognitionMs = clock.elapsedRealtimeMs() - started
        return AsrResult(
            text = text,
            audioDurationMs = audioDurationMs,
            recognitionDurationMs = recognitionMs,
            realTimeFactor = if (audioDurationMs > 0) recognitionMs.toDouble() / audioDurationMs else 0.0,
            finalLatencyMs = recognitionMs,
        )
    }

    override fun cancelActiveRequest() {
        transcriber.cancel()
    }

    override fun cancel() {
        synchronized(lock) { reset() }
        cancelActiveRequest()
    }

    override fun close() {
        cancel()
        (transcriber as? AutoCloseable)?.let { closeable ->
            runCatching { closeable.close() }
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_BUFFER_SECONDS = 60

        /** 初始緩衝 2 秒；不足時倍增，上限受 [MAX_BUFFER_SECONDS] 約束。 */
        private const val MIN_CAPACITY_SAMPLES = SAMPLE_RATE * 2

        internal fun pcmToWav(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
            val dataBytes = samples.size * 2
            val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(36 + dataBytes)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(1)
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 2)
            buffer.putShort(2)
            buffer.putShort(16)
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataBytes)
            samples.forEach(buffer::putShort)
            return buffer.array()
        }
    }
}
