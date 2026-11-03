package com.va.voiceassistant.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceScheduleTimesTest {

    @Test
    fun `解析含冒號的 HH MM`() {
        assertEquals(0, VoiceScheduleTimes.parse("00:00"))
        assertEquals(8 * 60, VoiceScheduleTimes.parse("08:00"))
        assertEquals(22 * 60 + 5, VoiceScheduleTimes.parse("22:05"))
        assertEquals(23 * 60 + 59, VoiceScheduleTimes.parse("23:59"))
    }

    @Test
    fun `解析不含冒號的數字連打`() {
        assertEquals(8 * 60, VoiceScheduleTimes.parse("0800"))
        assertEquals(22 * 60, VoiceScheduleTimes.parse("2200"))
        // 3 位數解讀為 H MM（0630 → 6:30）
        assertEquals(6 * 60 + 30, VoiceScheduleTimes.parse("630"))
    }

    @Test
    fun `格式化回兩位數 HH MM`() {
        assertEquals("00:00", VoiceScheduleTimes.format(0))
        assertEquals("08:00", VoiceScheduleTimes.format(8 * 60))
        assertEquals("22:05", VoiceScheduleTimes.format(22 * 60 + 5))
        assertEquals("23:59", VoiceScheduleTimes.format(23 * 60 + 59))
    }

    @Test
    fun `格式化與解析互為反函數`() {
        for (minutes in listOf(0, 1, 59, 60, 8 * 60 + 30, 23 * 60 + 59, 24 * 60 - 1)) {
            assertEquals(minutes, VoiceScheduleTimes.parse(VoiceScheduleTimes.format(minutes)))
        }
    }

    @Test
    fun `無效輸入回 null`() {
        assertNull(VoiceScheduleTimes.parse(""))
        assertNull(VoiceScheduleTimes.parse("08"))
        assertNull(VoiceScheduleTimes.parse("24:00"))
        assertNull(VoiceScheduleTimes.parse("08:60"))
        assertNull(VoiceScheduleTimes.parse("99"))
        assertNull(VoiceScheduleTimes.parse("abc"))
        assertNull(VoiceScheduleTimes.parse("08:00:00"))
    }
}
