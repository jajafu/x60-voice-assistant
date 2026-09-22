package com.jajafu.x60assistant.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAudioFrameQueueTest {
    @Test
    fun `full queue drops oldest frame and remains bounded`() {
        val queue = BoundedAudioFrameQueue(capacity = 2)
        queue.offerDroppingOldest(AudioFrame(shortArrayOf(1), 1))
        queue.offerDroppingOldest(AudioFrame(shortArrayOf(2), 2))

        assertTrue(queue.offerDroppingOldest(AudioFrame(shortArrayOf(3), 3)))
        assertEquals(2, queue.size())
        assertEquals(1, queue.droppedCount())
        assertArrayEquals(shortArrayOf(2), queue.poll(0)!!.samples)
        assertArrayEquals(shortArrayOf(3), queue.poll(0)!!.samples)
    }
}
