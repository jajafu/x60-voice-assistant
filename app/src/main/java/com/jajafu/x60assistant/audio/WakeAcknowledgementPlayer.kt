package com.jajafu.x60assistant.audio

import android.media.AudioManager
import android.media.ToneGenerator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WakeAcknowledgementPlayer {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "x60-wake-ack")
    }

    @Volatile
    private var released = false
    private val toneLock = Any()
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME_PERCENT)

    fun play() {
        if (released) return
        executor.execute {
            synchronized(toneLock) {
                if (!released) toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, TONE_DURATION_MS)
            }
        }
    }

    fun release() {
        released = true
        executor.shutdownNow()
        synchronized(toneLock) { toneGenerator.release() }
    }

    companion object {
        private const val TONE_VOLUME_PERCENT = 80
        private const val TONE_DURATION_MS = 120
    }
}
