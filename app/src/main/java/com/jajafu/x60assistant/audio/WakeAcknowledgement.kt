package com.jajafu.x60assistant.audio

import com.jajafu.x60assistant.tts.SpeechOutputEngine

interface WakeAcknowledgement {
    suspend fun playAndAwait(speechOutput: SpeechOutputEngine?, response: String)
    fun release()
}
