package com.jajafu.x60assistant.tts

import com.jajafu.x60assistant.audio.RiffWav
import kotlin.math.roundToInt

data class WavGainResult(
    val bytes: ByteArray,
    val applied: Boolean,
)

/** Applies a safe 16-bit PCM gain to a RIFF/WAVE response when possible. */
object WavAudioGain {
    fun apply(bytes: ByteArray, gain: Float): WavGainResult {
        val clampedGain = gain.coerceIn(0.0f, 2.0f)
        val data = findPcm16Data(bytes) ?: return WavGainResult(bytes, applied = false)
        if (clampedGain == 1.0f) return WavGainResult(bytes, applied = true)

        val output = bytes.copyOf()
        var offset = data.first
        while (offset + 1 < data.second) {
            val unsigned = (output[offset].toInt() and 0xff) or
                ((output[offset + 1].toInt() and 0xff) shl 8)
            val sample = if (unsigned > Short.MAX_VALUE) unsigned - (1 shl 16) else unsigned
            val scaled = (sample * clampedGain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[offset] = scaled.toByte()
            output[offset + 1] = (scaled ushr 8).toByte()
            offset += 2
        }
        return WavGainResult(output, applied = true)
    }

    private fun findPcm16Data(bytes: ByteArray): Pair<Int, Int>? {
        if (!RiffWav.isWave(bytes)) return null
        var offset = 12
        var pcm16 = false
        while (offset + 8 <= bytes.size) {
            val chunkSize = littleEndianInt(bytes, offset + 4)
            if (chunkSize < 0) return null
            val contentStart = offset + 8
            val contentEnd = (contentStart.toLong() + chunkSize).coerceAtMost(bytes.size.toLong()).toInt()
            if (contentEnd < contentStart) return null
            when {
                ascii(bytes, offset, "fmt ") == true &&
                    chunkSize >= 16 && contentStart + 16 <= bytes.size -> {
                    val audioFormat = littleEndianShort(bytes, contentStart)
                    val bitsPerSample = littleEndianShort(bytes, contentStart + 14)
                    pcm16 = audioFormat == 1 && bitsPerSample == 16
                }
                ascii(bytes, offset, "data") == true && pcm16 && contentStart <= bytes.size -> {
                    return contentStart to contentEnd
                }
            }
            offset = contentEnd + (chunkSize and 1)
        }
        return null
    }

    private fun ascii(bytes: ByteArray, offset: Int, value: String): Boolean =
        offset >= 0 && offset + value.length <= bytes.size &&
            value.indices.all { bytes[offset + it].toInt() == value[it].code }

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            (bytes[offset + 3].toInt() shl 24)
}
