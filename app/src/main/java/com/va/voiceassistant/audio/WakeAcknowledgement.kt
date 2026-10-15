package com.va.voiceassistant.audio

import com.va.voiceassistant.tts.SpeechOutputEngine

interface WakeAcknowledgement {
    suspend fun playAndAwait(speechOutput: SpeechOutputEngine?, response: String)
    fun release()
}
