package com.va.voiceassistant.kws

import com.va.voiceassistant.audio.AudioFrame

interface WakeWordEngine : AutoCloseable {
    fun accept(frame: AudioFrame): Boolean
    fun reset()
}
