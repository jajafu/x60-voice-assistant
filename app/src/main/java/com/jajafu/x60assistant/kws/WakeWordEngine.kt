package com.jajafu.x60assistant.kws

import com.jajafu.x60assistant.audio.AudioFrame

interface WakeWordEngine : AutoCloseable {
    fun accept(frame: AudioFrame): Boolean
    fun reset()
}
