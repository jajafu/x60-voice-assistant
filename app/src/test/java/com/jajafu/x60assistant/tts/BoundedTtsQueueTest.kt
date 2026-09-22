package com.jajafu.x60assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedTtsQueueTest {
    @Test
    fun `queue remains bounded and preserves accepted order`() {
        val queue = BoundedTtsQueue(2)
        assertTrue(queue.offer("一"))
        assertTrue(queue.offer("二"))
        assertFalse(queue.offer("三"))
        assertEquals(2, queue.highWaterMark)
        assertEquals(1L, queue.droppedCount)
        assertEquals("一", queue.poll())
        assertEquals("二", queue.poll())
    }
}
