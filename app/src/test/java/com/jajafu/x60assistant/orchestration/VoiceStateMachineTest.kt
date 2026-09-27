package com.jajafu.x60assistant.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {
    @Test
    fun `complete happy path uses required states`() {
        val machine = VoiceStateMachine()
        val path = listOf(
            VoiceState.STARTING to TransitionReason.USER_START,
            VoiceState.IDLE_KWS to TransitionReason.INITIALIZED,
            VoiceState.WAKE_ACK to TransitionReason.WAKE_DETECTED,
            VoiceState.LISTENING to TransitionReason.ACK_FINISHED,
            VoiceState.TRANSCRIBING to TransitionReason.SPEECH_ENDPOINT,
            VoiceState.ROUTING to TransitionReason.TRANSCRIPT_READY,
            VoiceState.THINKING to TransitionReason.ROUTE_REMOTE,
            VoiceState.SPEAKING to TransitionReason.ANSWER_READY,
            VoiceState.FOLLOW_UP to TransitionReason.SPEECH_FINISHED,
            VoiceState.IDLE_KWS to TransitionReason.FOLLOW_UP_TIMEOUT,
            VoiceState.STOPPED to TransitionReason.USER_STOP,
        )

        path.forEach { (state, reason) -> machine.transition(state, reason) }

        assertEquals(VoiceState.STOPPED, machine.state)
    }

    @Test
    fun `invalid transition is rejected`() {
        val machine = VoiceStateMachine()
        assertFalse(machine.canTransition(VoiceState.SPEAKING))
        assertTrue(runCatching { machine.transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY) }.isFailure)
    }

    @Test
    fun `wake word may interrupt thinking or speaking`() {
        assertTrue(VoiceState.WAKE_ACK in VoiceStateMachine.allowedTargets(VoiceState.THINKING))
        assertTrue(VoiceState.WAKE_ACK in VoiceStateMachine.allowedTargets(VoiceState.SPEAKING))
    }
}
