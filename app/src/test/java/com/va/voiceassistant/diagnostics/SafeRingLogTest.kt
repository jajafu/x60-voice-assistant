package com.va.voiceassistant.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeRingLogTest {
    @Test
    fun `ring log evicts oldest and strips line breaks`() {
        val log = SafeRingLog(capacity = 2)
        log.add(SafeLogEntry(1, "first"))
        log.add(SafeLogEntry(2, "second", detail = "safe\nvalue"))
        log.add(SafeLogEntry(3, "third"))

        val entries = log.snapshot()
        assertEquals(listOf("second", "third"), entries.map { it.event })
        assertEquals("safe value", entries.first().detail)
        assertFalse(entries.first().detail!!.contains('\n'))
    }

    @Test
    fun `ring log truncates oversized fields`() {
        val log = SafeRingLog(capacity = 1)
        log.add(SafeLogEntry(1, "x".repeat(500), detail = "y".repeat(500)))

        val entry = log.snapshot().single()
        assertEquals(160, entry.event.length)
        assertEquals(160, entry.detail!!.length)
    }
}
