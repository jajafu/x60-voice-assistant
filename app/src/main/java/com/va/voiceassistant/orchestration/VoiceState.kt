package com.va.voiceassistant.orchestration

enum class VoiceState {
    STOPPED,
    STARTING,
    IDLE_KWS,
    WAKE_ACK,
    LISTENING,
    TRANSCRIBING,
    ROUTING,
    EXECUTING_ACTION,
    THINKING,
    SPEAKING,
    FOLLOW_UP,
    PAUSED,
    RECOVERING,
    ERROR,
}

enum class TransitionReason {
    USER_START,
    INITIALIZED,
    WAKE_DETECTED,
    ACK_FINISHED,
    SPEECH_STARTED,
    SPEECH_ENDPOINT,
    TRANSCRIPT_READY,
    ROUTE_REMOTE,
    ROUTE_LOCAL,
    ANSWER_READY,
    SPEECH_FINISHED,
    FOLLOW_UP_SPEECH,
    FOLLOW_UP_TIMEOUT,
    USER_PAUSE,
    USER_RESUME,
    USER_CANCEL,
    USER_STOP,
    RESOURCE_FAILURE,
    RECOVERY_SUCCEEDED,
    FATAL_ERROR,
}

data class VoiceTransition(
    val from: VoiceState,
    val to: VoiceState,
    val reason: TransitionReason,
)
