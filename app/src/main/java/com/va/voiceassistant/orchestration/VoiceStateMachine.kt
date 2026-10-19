package com.va.voiceassistant.orchestration

class VoiceStateMachine(initial: VoiceState = VoiceState.STOPPED) {
    var state: VoiceState = initial
        private set

    fun transition(to: VoiceState, reason: TransitionReason): VoiceTransition {
        require(to in allowedTargets(state)) { "Invalid voice transition: $state -> $to ($reason)" }
        return VoiceTransition(state, to, reason).also { state = to }
    }

    fun canTransition(to: VoiceState): Boolean = to in allowedTargets(state)

    companion object {
        fun allowedTargets(from: VoiceState): Set<VoiceState> = when (from) {
            VoiceState.STOPPED -> setOf(VoiceState.STARTING)
            VoiceState.STARTING -> setOf(VoiceState.IDLE_KWS, VoiceState.PAUSED, VoiceState.ERROR, VoiceState.STOPPED)
            VoiceState.IDLE_KWS -> setOf(
                VoiceState.WAKE_ACK,
                VoiceState.PAUSED,
                VoiceState.RECOVERING,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.WAKE_ACK -> setOf(
                VoiceState.LISTENING,
                VoiceState.IDLE_KWS,
                VoiceState.PAUSED,
                VoiceState.RECOVERING,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.LISTENING -> setOf(
                VoiceState.TRANSCRIBING,
                VoiceState.ROUTING, // 系統語音辨識整句完成時直接交逐字稿（見 VoiceOrchestrator.beginListening）。
                VoiceState.IDLE_KWS,
                VoiceState.PAUSED,
                VoiceState.RECOVERING,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.TRANSCRIBING -> setOf(
                VoiceState.ROUTING,
                VoiceState.IDLE_KWS,
                VoiceState.PAUSED,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.ROUTING -> setOf(
                VoiceState.EXECUTING_ACTION,
                VoiceState.THINKING,
                VoiceState.SPEAKING,
                VoiceState.IDLE_KWS,
                VoiceState.PAUSED,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.EXECUTING_ACTION -> setOf(
                VoiceState.SPEAKING,
                VoiceState.IDLE_KWS,
                VoiceState.PAUSED,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.THINKING -> setOf(
                VoiceState.WAKE_ACK,
                VoiceState.EXECUTING_ACTION,
                VoiceState.SPEAKING,
                VoiceState.FOLLOW_UP,
                VoiceState.IDLE_KWS,
                VoiceState.PAUSED,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.SPEAKING -> setOf(
                VoiceState.WAKE_ACK,
                VoiceState.FOLLOW_UP,
                VoiceState.IDLE_KWS,
                VoiceState.PAUSED,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.FOLLOW_UP -> setOf(
                VoiceState.LISTENING,
                VoiceState.IDLE_KWS,
                VoiceState.PAUSED,
                VoiceState.RECOVERING,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.PAUSED -> setOf(
                VoiceState.IDLE_KWS,
                VoiceState.RECOVERING,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.RECOVERING -> setOf(
                VoiceState.IDLE_KWS,
                VoiceState.LISTENING,
                VoiceState.PAUSED,
                VoiceState.ERROR,
                VoiceState.STOPPED,
            )
            VoiceState.ERROR -> setOf(VoiceState.STARTING, VoiceState.STOPPED)
        }
    }
}
