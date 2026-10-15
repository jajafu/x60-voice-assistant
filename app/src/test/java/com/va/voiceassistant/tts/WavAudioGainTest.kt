package com.va.voiceassistant.tts

import com.va.voiceassistant.asr.CloudSpeechRecognizerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavAudioGainTest {
    @Test
    fun `scales and clips signed 16 bit wav samples`() {
        val wav = CloudSpeechRecognizerEngine.pcmToWav(shortArrayOf(1_000, -1_000, 20_000, -20_000))

        val result = WavAudioGain.apply(wav, 2.0f)

        assertTrue(result.applied)
        val samples = readSamples(result.bytes)
        assertEquals(listOf(2_000, -2_000, Short.MAX_VALUE.toInt(), Short.MIN_VALUE.toInt()), samples)
    }

    @Test
    fun `leaves unsupported audio untouched for media player volume fallback`() {
        val mp3 = byteArrayOf(0x49, 0x44, 0x33, 0x04)

        val result = WavAudioGain.apply(mp3, 0.5f)

        assertFalse(result.applied)
        assertTrue(result.bytes.contentEquals(mp3))
    }

    private fun readSamples(wav: ByteArray): List<Int> {
        val start = 44
        return (start until wav.size step 2).map {
            val unsigned = (wav[it].toInt() and 0xff) or ((wav[it + 1].toInt() and 0xff) shl 8)
            if (unsigned > Short.MAX_VALUE) unsigned - (1 shl 16) else unsigned
        }
    }
}
