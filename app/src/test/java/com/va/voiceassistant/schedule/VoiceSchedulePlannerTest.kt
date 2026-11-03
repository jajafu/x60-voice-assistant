package com.va.voiceassistant.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class VoiceSchedulePlannerTest {

    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun `開關關閉即無效`() {
        assertFalse(VoiceSchedulePlanner.isValid(false, 8 * 60, 22 * 60))
        assertTrue(VoiceSchedulePlanner.isValid(true, 8 * 60, 22 * 60))
    }

    @Test
    fun `啟動與停止相同時間無效`() {
        assertFalse(VoiceSchedulePlanner.isValid(true, 8 * 60, 8 * 60))
        assertTrue(VoiceSchedulePlanner.isValid(true, 8 * 60, 22 * 60))
    }

    @Test
    fun `時間超出 0 到 1439 無效`() {
        assertFalse(VoiceSchedulePlanner.isValid(true, -1, 22 * 60))
        assertFalse(VoiceSchedulePlanner.isValid(true, 24 * 60, 22 * 60))
        assertFalse(VoiceSchedulePlanner.isValid(true, 8 * 60, 24 * 60))
    }

    @Test
    fun `停止時間早於啟動時間仍有效（各自獨立排程）`() {
        assertTrue(VoiceSchedulePlanner.isValid(true, 22 * 60, 6 * 60))
    }

    @Test
    fun `當日時間未過排當天`() {
        // 2026-11-03 10:00 Taipei = 02:00 UTC
        val now = Instant.parse("2026-11-03T02:00:00Z").toEpochMilli()
        val trigger = VoiceSchedulePlanner.nextOccurrence(22 * 60, now, zone)
        assertEquals(
            Instant.parse("2026-11-03T14:00:00Z").toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun `當日時間已過排明天`() {
        val now = Instant.parse("2026-11-03T14:00:00Z").toEpochMilli()
        val trigger = VoiceSchedulePlanner.nextOccurrence(8 * 60, now, zone)
        assertEquals(
            Instant.parse("2026-11-04T00:00:00Z").toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun `現在時間恰好等於排程時間時排明天`() {
        val now = Instant.parse("2026-11-03T14:00:00Z").toEpochMilli()
        val trigger = VoiceSchedulePlanner.nextOccurrence(22 * 60, now, zone)
        assertEquals(
            Instant.parse("2026-11-04T14:00:00Z").toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun `跨午夜 00 00 的排程`() {
        val now = Instant.parse("2026-11-03T14:00:00Z").toEpochMilli()
        val trigger = VoiceSchedulePlanner.nextOccurrence(0, now, zone)
        assertEquals(
            Instant.parse("2026-11-03T16:00:00Z").toEpochMilli(),
            trigger,
        )
    }
}
