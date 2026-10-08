package com.jajafu.x60assistant.asr

import com.jajafu.x60assistant.audio.AudioFrame
import com.jajafu.x60assistant.llm.AudioTranscriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CloudSpeechRecognizerEngineTest {
    private val fakeClock = object {
        var now = 1_000L
    }

    @Test
    fun `buffers frames without partials and uploads wav on finish`() {
        var captured: ByteArray? = null
        val engine = CloudSpeechRecognizerEngine(
            modelId = "cloud/whisper-1",
            transcriber = AudioTranscriber { wav ->
                captured = wav
                fakeClock.now += 500L
                "測試結果"
            },
            clock = { fakeClock.now },
        )
        engine.beginSession()
        assertNull(engine.accept(AudioFrame(ShortArray(320) { 100 }, 0L)))
        assertNull(engine.accept(AudioFrame(ShortArray(320) { -100 }, 0L)))

        val result = engine.finish()

        assertEquals("測試結果", result.text)
        assertEquals(640L * 1_000L / 16000L, result.audioDurationMs)
        assertEquals(500L, result.recognitionDurationMs)
        val wav = requireNotNull(captured)
        assertTrue(wav.size == 44 + 640 * 2)
        assertEquals("RIFF", String(wav.sliceArray(0..3), Charsets.US_ASCII))
        assertEquals("WAVE", String(wav.sliceArray(8..11), Charsets.US_ASCII))
        assertEquals(16000, ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        // finish 後緩衝已清空；空會話再次 finish 應拒絕上傳。
        val second = runCatching { engine.finish() }.exceptionOrNull()
        assertTrue(second is IllegalStateException)
        engine.close()
    }

    @Test
    fun `cancel clears the buffer so nothing is uploaded`() {
        var calls = 0
        val engine = CloudSpeechRecognizerEngine(
            modelId = "cloud/whisper-1",
            transcriber = AudioTranscriber {
                calls++
                "不該出現"
            },
            clock = { fakeClock.now },
        )
        engine.beginSession()
        engine.accept(AudioFrame(ShortArray(320) { 7 }, 0L))
        engine.cancel()

        runCatching { engine.finish() }
        assertEquals(0, calls)
        engine.close()
    }

    @Test
    fun `buffer is capped at the configured maximum`() {
        var uploadedSamples = 0
        val engine = CloudSpeechRecognizerEngine(
            modelId = "cloud/whisper-1",
            transcriber = AudioTranscriber { wav ->
                uploadedSamples = (wav.size - 44) / 2
                ""
            },
            clock = { fakeClock.now },
            maxBufferSeconds = 1,
        )
        engine.beginSession()
        repeat(100) { engine.accept(AudioFrame(ShortArray(320) { 3 }, 0L)) }
        engine.finish()

        assertEquals(16000, uploadedSamples)
        engine.close()
    }
}
