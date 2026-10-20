package com.va.voiceassistant.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiffWavTest {
    @Test
    fun `accepts a wav produced by the test helper`() {
        assertTrue(RiffWav.isWave(TestWav.pcmToWav(shortArrayOf(0, 1, -1))))
    }

    @Test
    fun `rejects mp3 json and truncated payloads`() {
        assertFalse(RiffWav.isWave(byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)))
        assertFalse(RiffWav.isWave("""{"error":"nope"}""".toByteArray()))
        assertFalse(RiffWav.isWave(ByteArray(0)))
        assertFalse(RiffWav.isWave("RIFF".toByteArray()))
        // RIFF 開頭但缺少 WAVE 標記
        assertFalse(RiffWav.isWave("RIFF____XXXX".toByteArray()))
    }

    @Test
    fun `signature reports only the header bytes in upper case hex`() {
        val signature = RiffWav.signature(byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x0a, 0xff.toByte()))

        assertEquals("49 44 33 04 00 0A FF", signature)
    }

    @Test
    fun `signature is bounded by payload size`() {
        assertEquals("49 44", RiffWav.signature(byteArrayOf(0x49, 0x44), limit = 12))
        assertEquals("", RiffWav.signature(ByteArray(0)))
    }
}
