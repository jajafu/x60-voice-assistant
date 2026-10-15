package com.va.voiceassistant.audio

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class BoundedAudioFrameQueue(capacity: Int) {
    init {
        require(capacity > 0)
    }

    private val queue = ArrayBlockingQueue<AudioFrame>(capacity)
    private val dropped = AtomicLong(0)
    private val highWater = AtomicLong(0)

    fun offerDroppingOldest(frame: AudioFrame): Boolean {
        if (queue.offer(frame)) {
            highWater.accumulateAndGet(queue.size.toLong(), ::maxOf)
            return false
        }
        queue.poll()
        dropped.incrementAndGet()
        queue.offer(frame)
        highWater.accumulateAndGet(queue.size.toLong(), ::maxOf)
        return true
    }

    fun poll(timeoutMs: Long): AudioFrame? = queue.poll(timeoutMs, TimeUnit.MILLISECONDS)

    fun clear() = queue.clear()

    fun size(): Int = queue.size

    fun droppedCount(): Long = dropped.get()

    fun highWaterMark(): Int = highWater.get().toInt()
}
