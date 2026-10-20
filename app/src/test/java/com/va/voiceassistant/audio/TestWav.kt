package com.va.voiceassistant.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 測試用：把 16 kHz／mono／PCM16 樣本包成最小 RIFF/WAVE 位元組，供 WAV 相關測試當輸入。 */
object TestWav {
    fun pcmToWav(samples: ShortArray, sampleRate: Int = 16_000): ByteArray {
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
