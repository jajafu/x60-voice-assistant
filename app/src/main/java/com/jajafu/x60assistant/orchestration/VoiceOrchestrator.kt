package com.jajafu.x60assistant.orchestration

import com.jajafu.x60assistant.asr.AsrResult
import com.jajafu.x60assistant.asr.SpeechRecognizerEngine
import com.jajafu.x60assistant.asr.TraditionalChineseConverter
import com.jajafu.x60assistant.audio.AudioCaptureEngine
import com.jajafu.x60assistant.audio.WakeAcknowledgementPlayer
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsRepository
import com.jajafu.x60assistant.diagnostics.VoiceErrorCode
import com.jajafu.x60assistant.kws.KwsModelPack
import com.jajafu.x60assistant.kws.SherpaWakeWordEngine
import com.jajafu.x60assistant.vad.SpeechEndpointDetector
import com.jajafu.x60assistant.vad.SpeechEndpointEvent
import com.jajafu.x60assistant.vad.VadEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** The only component allowed to switch conversational state. */
class VoiceOrchestrator(
    private val scope: CoroutineScope,
    private val audio: AudioCaptureEngine,
    private val kws: SherpaWakeWordEngine,
    private val vad: VadEngine,
    private val asr: SpeechRecognizerEngine,
    private val acknowledgementPlayer: WakeAcknowledgementPlayer,
    private val endpointDetector: SpeechEndpointDetector,
    private val converter: TraditionalChineseConverter,
    private val clock: MonotonicClock = AndroidMonotonicClock,
    private val audioResumeDelayMs: Long = 300,
) : Closeable {
    private val commands = Channel<Command>(capacity = COMMAND_CAPACITY)
    private val machine = VoiceStateMachine()
    private val mutableState = MutableStateFlow(VoiceState.STOPPED)
    private val closed = AtomicBoolean(false)
    private var processorJob: Job = scope.launch { processCommands() }
    private var stateJob: Job? = null
    private var audioSource = "—"
    private var sessionId = "—"

    val state = mutableState.asStateFlow()

    fun start() = dispatch(Command.Start)
    fun pause() = dispatch(Command.Pause)
    fun resume() = dispatch(Command.Resume)
    fun cancelSession() = dispatch(Command.CancelSession)
    fun stop() = dispatch(Command.Stop)
    fun onAudioFailure(code: VoiceErrorCode, detail: String) = dispatch(Command.AudioFailure(code, detail))

    private fun dispatch(command: Command) {
        if (!closed.get() && commands.trySend(command).isFailure) {
            M1DiagnosticsRepository.onAudioError(VoiceErrorCode.QUEUE_OVERLOAD, "orchestrator-command")
        }
    }

    private suspend fun processCommands() {
        for (command in commands) {
            when (command) {
                Command.Start -> handleStart()
                Command.WakeDetected -> handleWakeDetected()
                Command.AcknowledgementFinished -> handleAcknowledgementFinished()
                Command.SpeechComplete -> handleSpeechComplete()
                is Command.SpeechRejected -> handleSpeechRejected(command.code)
                is Command.TranscriptReady -> handleTranscriptReady(command.result)
                is Command.SessionFailed -> handleSessionFailed(command.code, command.detail)
                Command.Pause -> handlePause()
                Command.Resume -> handleResume()
                Command.CancelSession -> returnToIdle(TransitionReason.USER_CANCEL)
                is Command.AudioFailure -> handleAudioFailure(command.code, command.detail)
                Command.Stop -> handleStop()
            }
        }
    }

    private suspend fun handleStart() {
        if (machine.state !in setOf(VoiceState.STOPPED, VoiceState.ERROR)) return
        transition(VoiceState.STARTING, TransitionReason.USER_START)
        stateJob = scope.launch {
            runCatching {
                audioSource = audio.start()
            }.onSuccess {
                dispatch(Command.Resume)
            }.onFailure {
                dispatch(Command.SessionFailed(VoiceErrorCode.AUDIO_INIT_FAILED, it.javaClass.simpleName))
            }
        }
    }

    private suspend fun handleResume() {
        when (machine.state) {
            VoiceState.STARTING -> enterIdle(TransitionReason.INITIALIZED, audioAlreadyRunning = true)
            VoiceState.PAUSED -> enterIdle(TransitionReason.USER_RESUME, audioAlreadyRunning = false)
            else -> Unit
        }
    }

    private suspend fun enterIdle(reason: TransitionReason, audioAlreadyRunning: Boolean) {
        if (!audioAlreadyRunning) {
            audioSource = runCatching { audio.start() }.getOrElse {
                handleAudioFailure(VoiceErrorCode.AUDIO_INIT_FAILED, it.javaClass.simpleName)
                return
            }
        }
        kws.reset()
        transition(VoiceState.IDLE_KWS, reason)
        M1DiagnosticsRepository.setIdle(audioSource)
        stateJob = scope.launch {
            try {
                while (isActive && machine.state == VoiceState.IDLE_KWS) {
                    val frame = audio.frameQueue.poll(FRAME_POLL_MS) ?: continue
                    if (kws.accept(frame)) {
                        dispatch(Command.WakeDetected)
                        return@launch
                    }
                }
            } catch (error: Throwable) {
                if (isActive) dispatch(Command.SessionFailed(VoiceErrorCode.KWS_RUNTIME_FAILED, error.javaClass.simpleName))
            }
        }
    }

    private suspend fun handleWakeDetected() {
        if (machine.state != VoiceState.IDLE_KWS) return
        sessionId = UUID.randomUUID().toString().replace("-", "").take(8)
        M1DiagnosticsRepository.onWake(KwsModelPack.KEYWORDS_SCORE)
        transition(VoiceState.WAKE_ACK, TransitionReason.WAKE_DETECTED)
        audio.stop()
        stateJob = scope.launch {
            acknowledgementPlayer.playAndAwait()
            delay(audioResumeDelayMs)
            dispatch(Command.AcknowledgementFinished)
        }
    }

    private suspend fun handleAcknowledgementFinished() {
        if (machine.state != VoiceState.WAKE_ACK) return
        runCatching {
            vad.reset()
            asr.beginSession()
            M1DiagnosticsRepository.setAsrModel(asr.modelId)
            endpointDetector.reset(clock.elapsedRealtimeMs())
            audioSource = audio.start()
        }.onFailure {
            val code = if (it is OutOfMemoryError) VoiceErrorCode.ASR_MODEL_OOM else VoiceErrorCode.ASR_INIT_FAILED
            dispatch(Command.SessionFailed(code, it.javaClass.simpleName))
            return
        }
        transition(VoiceState.LISTENING, TransitionReason.ACK_FINISHED)
        stateJob = scope.launch {
            try {
                while (isActive && machine.state == VoiceState.LISTENING) {
                    val frame = audio.frameQueue.poll(FRAME_POLL_MS) ?: continue
                    asr.accept(frame)?.let(converter::convert)?.let(M1DiagnosticsRepository::setPartialTranscript)
                    when (endpointDetector.accept(vad.accept(frame).speechDetected, clock.elapsedRealtimeMs())) {
                        SpeechEndpointEvent.None,
                        SpeechEndpointEvent.SpeechStarted,
                        -> Unit
                        SpeechEndpointEvent.Endpoint,
                        SpeechEndpointEvent.MaxDuration,
                        -> {
                            dispatch(Command.SpeechComplete)
                            return@launch
                        }
                        SpeechEndpointEvent.NoSpeechTimeout -> {
                            dispatch(Command.SpeechRejected(VoiceErrorCode.VAD_NO_SPEECH))
                            return@launch
                        }
                        SpeechEndpointEvent.SegmentTooShort -> {
                            dispatch(Command.SpeechRejected(VoiceErrorCode.VAD_SEGMENT_TOO_SHORT))
                            return@launch
                        }
                    }
                }
            } catch (error: OutOfMemoryError) {
                dispatch(Command.SessionFailed(VoiceErrorCode.ASR_MODEL_OOM, error.javaClass.simpleName))
            } catch (error: Throwable) {
                if (isActive) dispatch(Command.SessionFailed(VoiceErrorCode.ASR_RUNTIME_FAILED, error.javaClass.simpleName))
            }
        }
    }

    private suspend fun handleSpeechComplete() {
        if (machine.state != VoiceState.LISTENING) return
        transition(VoiceState.TRANSCRIBING, TransitionReason.SPEECH_ENDPOINT)
        audio.stop()
        stateJob = scope.launch {
            runCatching { asr.finish() }
                .onSuccess { dispatch(Command.TranscriptReady(it)) }
                .onFailure {
                    val code = if (it is OutOfMemoryError) VoiceErrorCode.ASR_MODEL_OOM else VoiceErrorCode.ASR_RUNTIME_FAILED
                    dispatch(Command.SessionFailed(code, it.javaClass.simpleName))
                }
        }
    }

    private suspend fun handleTranscriptReady(result: AsrResult) {
        if (machine.state != VoiceState.TRANSCRIBING) return
        if (result.text.isBlank()) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.ASR_NO_RESULT, "session=$sessionId")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        M1DiagnosticsRepository.setFinalTranscript(converter.convert(result.text), result)
        transition(VoiceState.ROUTING, TransitionReason.TRANSCRIPT_READY)
        // M2 ends locally after proving final transcription. M3 installs the conversation handler.
        returnToIdle(TransitionReason.USER_CANCEL)
    }

    private suspend fun handleSpeechRejected(code: VoiceErrorCode) {
        asr.cancel()
        M1DiagnosticsRepository.recordSessionError(code, "session=$sessionId")
        returnToIdle(TransitionReason.USER_CANCEL)
    }

    private suspend fun handlePause() {
        if (machine.state in setOf(VoiceState.STOPPED, VoiceState.ERROR, VoiceState.PAUSED)) return
        stateJob?.cancel()
        stateJob = null
        asr.cancel()
        audio.stop()
        transition(VoiceState.PAUSED, TransitionReason.USER_PAUSE)
    }

    private suspend fun returnToIdle(reason: TransitionReason) {
        if (machine.state in setOf(VoiceState.STOPPED, VoiceState.ERROR, VoiceState.STARTING)) return
        stateJob?.cancel()
        stateJob = null
        asr.cancel()
        audio.stop()
        delay(audioResumeDelayMs)
        enterIdle(reason, audioAlreadyRunning = false)
    }

    private suspend fun handleAudioFailure(code: VoiceErrorCode, detail: String) {
        M1DiagnosticsRepository.onAudioError(code, detail)
        transition(VoiceState.ERROR, TransitionReason.FATAL_ERROR)
        releaseSessionResources()
    }

    private suspend fun handleSessionFailed(code: VoiceErrorCode, detail: String) {
        M1DiagnosticsRepository.recordError(code, detail)
        transition(VoiceState.ERROR, TransitionReason.FATAL_ERROR)
        releaseSessionResources()
    }

    private suspend fun handleStop() {
        if (machine.state == VoiceState.STOPPED) return
        transition(VoiceState.STOPPED, TransitionReason.USER_STOP)
        releaseSessionResources()
        M1DiagnosticsRepository.setStopped()
    }

    private suspend fun transition(to: VoiceState, reason: TransitionReason) {
        stateJob?.cancel()
        stateJob = null
        machine.transition(to, reason)
        mutableState.value = to
        M1DiagnosticsRepository.setState(to, "$reason,session=$sessionId")
    }

    private fun releaseSessionResources() {
        stateJob?.cancel()
        stateJob = null
        asr.cancel()
        audio.stop()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        commands.trySend(Command.Stop)
        commands.close()
        processorJob.cancel()
        releaseSessionResources()
        runCatching { asr.close() }
        runCatching { vad.close() }
        runCatching { kws.close() }
        runCatching { acknowledgementPlayer.release() }
    }

    private sealed interface Command {
        data object Start : Command
        data object WakeDetected : Command
        data object AcknowledgementFinished : Command
        data object SpeechComplete : Command
        data class SpeechRejected(val code: VoiceErrorCode) : Command
        data class TranscriptReady(val result: AsrResult) : Command
        data class SessionFailed(val code: VoiceErrorCode, val detail: String) : Command
        data object Pause : Command
        data object Resume : Command
        data object CancelSession : Command
        data class AudioFailure(val code: VoiceErrorCode, val detail: String) : Command
        data object Stop : Command
    }

    companion object {
        private const val COMMAND_CAPACITY = 32
        private const val FRAME_POLL_MS = 250L
    }
}
