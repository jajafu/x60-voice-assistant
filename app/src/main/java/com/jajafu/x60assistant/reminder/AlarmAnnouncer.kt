package com.jajafu.x60assistant.reminder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/** Called on the service's main dispatcher, including pause/resume and the speech loop. */
class AlarmAnnouncer(
    private val pauseSound: () -> Unit,
    private val resumeSound: () -> Unit,
    private val stopSpeech: () -> Unit,
) {
    private val muted = MutableStateFlow(false)

    fun reset() { muted.value = false }

    fun pauseForVoice() {
        muted.value = true
        stopSpeech()
        pauseSound()
    }

    fun resumeAfterVoice() {
        muted.value = false
        resumeSound()
    }

    suspend fun run(intervalMs: Long, speak: suspend () -> Unit) {
        while (currentCoroutineContext().isActive) {
            muted.first { !it }
            pauseSound()
            try {
                speak()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A stopped/unavailable TTS must not stop the alarm's sound fallback.
            }
            currentCoroutineContext().ensureActive()
            if (!muted.value) resumeSound()
            delay(intervalMs)
        }
    }
}
