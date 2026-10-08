package com.jajafu.x60assistant.asr

import com.jajafu.x60assistant.audio.AudioFrame
import com.jajafu.x60assistant.llm.AudioTranscriber
import com.jajafu.x60assistant.orchestration.AndroidMonotonicClock
import com.jajafu.x60assistant.orchestration.MonotonicClock
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 雲端 ASR spike（預設 `whisper-1`，錄完再轉）。
 *
 * VAD 與語句邊界維持本機；此引擎只在該輪收集 PCM（16 kHz／mono／PCM16），
 * 在 [finish] 時包成 WAV 一次上傳。`accept` 永遠不回傳部分結果。
 * 失敗時拋出 [com.jajafu.x60assistant.llm.LlmException]，
 * 由 `VoiceOrchestrator` 記為 `ASR_RUNTIME_FAILED`，不靜默退回本機。
 */
class CloudSpeechRecognizerEngine(
    override val modelId: String,
    private val transcriber: AudioTranscriber,
    private val clock: MonotonicClock = AndroidMonotonicClock,
    private val maxBufferSeconds: Int = MAX_BUFFER_SECONDS,
) : SpeechRecognizerEngine {
    private val lock = Any()
    private val pcm = ArrayList<Short>(SAMPLE_RATE * 10)

    override fun beginSession() {
        synchronized(lock) { pcm.clear() }
    }

    override fun accept(frame: AudioFrame): String? {
        synchronized(lock) {
            val remaining = maxBufferSeconds * SAMPLE_RATE - pcm.size
            if (remaining > 0) {
                val samples = frame.samples
                pcm.addAll(samples.take(remaining))
            }
        }
        return null
    }

    override fun finish(): AsrResult {
        val snapshot = synchronized(lock) {
            pcm.toShortArray().also { pcm.clear() }
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

    override fun cancel() {
        synchronized(lock) { pcm.clear() }
    }

    override fun close() {
        synchronized(lock) { pcm.clear() }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_BUFFER_SECONDS = 60

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
