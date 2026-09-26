package com.jajafu.x60assistant.orchestration

import com.jajafu.x60assistant.asr.AsrResult
import com.jajafu.x60assistant.asr.SpeechRecognizerEngine
import com.jajafu.x60assistant.asr.TraditionalChineseConverter
import com.jajafu.x60assistant.audio.AudioCaptureEngine
import com.jajafu.x60assistant.audio.AudioFrame
import com.jajafu.x60assistant.audio.WakeAcknowledgementPlayer
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsRepository
import com.jajafu.x60assistant.diagnostics.VoiceErrorCode
import com.jajafu.x60assistant.kws.KwsModelPack
import com.jajafu.x60assistant.kws.SherpaWakeWordEngine
import com.jajafu.x60assistant.data.AssistantSettings
import com.jajafu.x60assistant.llm.ChatRequest
import com.jajafu.x60assistant.llm.ConversationHistory
import com.jajafu.x60assistant.llm.LlmErrorCode
import com.jajafu.x60assistant.llm.LlmException
import com.jajafu.x60assistant.llm.LlmProvider
import com.jajafu.x60assistant.music.MusicActionExecutor
import com.jajafu.x60assistant.music.MusicActionResult
import com.jajafu.x60assistant.music.MusicCommand
import com.jajafu.x60assistant.music.MusicCommandRouter
import com.jajafu.x60assistant.music.MusicRefusalRecovery
import com.jajafu.x60assistant.music.MusicRefusalRecoveryRouter
import com.jajafu.x60assistant.reminder.ReminderActionExecutor
import com.jajafu.x60assistant.reminder.ReminderActionResult
import com.jajafu.x60assistant.reminder.ReminderCommandRouter
import com.jajafu.x60assistant.reminder.AlarmActionExecutor
import com.jajafu.x60assistant.tts.BoundedTtsQueue
import com.jajafu.x60assistant.tts.SpeechOutputEngine
import com.jajafu.x60assistant.tts.SpeechSegmenter
import com.jajafu.x60assistant.vad.SpeechEndpointDetector
import com.jajafu.x60assistant.vad.SpeechEndpointEvent
import com.jajafu.x60assistant.vad.VadEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
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
    private val settings: AssistantSettings = AssistantSettings(),
    private val llm: LlmProvider? = null,
    private val musicExecutor: MusicActionExecutor? = null,
    private val reminderExecutor: ReminderActionExecutor? = null,
    private val alarmExecutor: AlarmActionExecutor? = null,
    speechOutput: SpeechOutputEngine? = null,
    private val speechOutputFactory: (suspend () -> SpeechOutputEngine?)? = null,
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
    private val history = ConversationHistory(settings.contextTurns)
    private val ttsQueue = BoundedTtsQueue()
    private val recoveryPolicy = RecoveryPolicy()
    private var speechOutput: SpeechOutputEngine? = speechOutput
    private var ttsRecoveryAttempted = false
    @Volatile private var resumeMusicAfterSession = false
    @Volatile private var keepMusicPausedAfterSession = false
    @Volatile private var resumeAlarmAfterSession = false

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
                is Command.AnswerReady -> handleAnswerReady(command)
                is Command.MusicActionReady -> handleMusicActionReady(command)
                is Command.ReminderActionReady -> handleReminderActionReady(command.result)
                is Command.ContextualCloseFinished -> handleContextualCloseFinished(command.message)
                Command.SpeechFinished -> handleSpeechFinished()
                is Command.FollowUpSpeech -> handleFollowUpSpeech(command.firstFrame)
                Command.FollowUpTimeout -> handleFollowUpTimeout()
                Command.RecoveryReady -> handleRecoveryReady()
                is Command.TtsFailed -> handleTtsFailed(command.sentence, command.detail)
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
        keepMusicPausedAfterSession = false
        sessionId = UUID.randomUUID().toString().replace("-", "").take(8)
        M1DiagnosticsRepository.onWake(KwsModelPack.KEYWORDS_SCORE, settings.wakePhrase)
        transition(VoiceState.WAKE_ACK, TransitionReason.WAKE_DETECTED)
        stateJob = scope.launch {
            try {
                withContext(NonCancellable) {
                    resumeAlarmAfterSession = try {
                        alarmExecutor?.pauseForVoice() == true
                    } catch (_: Exception) {
                        false
                    }
                    resumeMusicAfterSession = try {
                        musicExecutor?.pauseForVoice() == true
                    } catch (_: Exception) {
                        false
                    }
                }
                acknowledgementPlayer.playAndAwait(speechOutput, settings.wakeAcknowledgement)
                dispatch(Command.AcknowledgementFinished)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                dispatch(Command.SessionFailed(VoiceErrorCode.AUDIO_START_FAILED, error.javaClass.simpleName))
            }
        }
    }

    private suspend fun handleAcknowledgementFinished() {
        if (machine.state != VoiceState.WAKE_ACK) return
        // Capture stays active during the acknowledgement. Discard its echo before accepting speech.
        audio.frameQueue.clear()
        beginListening(firstFrame = null, audioAlreadyRunning = audio.isRunning())
    }

    private suspend fun beginListening(firstFrame: AudioFrame?, audioAlreadyRunning: Boolean) {
        runCatching {
            vad.reset()
            asr.beginSession()
            M1DiagnosticsRepository.setAsrModel(asr.modelId)
            endpointDetector.reset(clock.elapsedRealtimeMs())
            if (!audioAlreadyRunning) audioSource = audio.start()
        }.onFailure {
            val code = if (it is OutOfMemoryError) VoiceErrorCode.ASR_MODEL_OOM else VoiceErrorCode.ASR_INIT_FAILED
            dispatch(Command.SessionFailed(code, it.javaClass.simpleName))
            return
        }
        transition(VoiceState.LISTENING, TransitionReason.ACK_FINISHED)
        stateJob = scope.launch {
            try {
                firstFrame?.let { if (processListeningFrame(it)) return@launch }
                while (isActive && machine.state == VoiceState.LISTENING) {
                    val frame = audio.frameQueue.poll(FRAME_POLL_MS) ?: continue
                    if (processListeningFrame(frame)) return@launch
                }
            } catch (error: OutOfMemoryError) {
                dispatch(Command.SessionFailed(VoiceErrorCode.ASR_MODEL_OOM, error.javaClass.simpleName))
            } catch (error: Throwable) {
                if (isActive) dispatch(Command.SessionFailed(VoiceErrorCode.ASR_RUNTIME_FAILED, error.javaClass.simpleName))
            }
        }
    }

    private fun processListeningFrame(frame: AudioFrame): Boolean {
        asr.accept(frame)?.let(converter::convert)?.let(M1DiagnosticsRepository::setPartialTranscript)
        return when (endpointDetector.accept(vad.accept(frame).speechDetected, clock.elapsedRealtimeMs())) {
            SpeechEndpointEvent.None, SpeechEndpointEvent.SpeechStarted -> false
            SpeechEndpointEvent.Endpoint -> true.also { dispatch(Command.SpeechComplete) }
            SpeechEndpointEvent.MaxDuration -> true.also {
                M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.VAD_MAX_DURATION, "session=$sessionId")
                dispatch(Command.SpeechComplete)
            }
            SpeechEndpointEvent.NoSpeechTimeout -> true.also {
                dispatch(Command.SpeechRejected(VoiceErrorCode.VAD_NO_SPEECH))
            }
            SpeechEndpointEvent.SegmentTooShort -> true.also {
                dispatch(Command.SpeechRejected(VoiceErrorCode.VAD_SEGMENT_TOO_SHORT))
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
        val userText = converter.convert(result.text)
        M1DiagnosticsRepository.setFinalTranscript(userText, result)
        transition(VoiceState.ROUTING, TransitionReason.TRANSCRIPT_READY)
        if (ContextualCloseCommandRouter.matches(userText)) {
            handleContextualClose()
            return
        }
        val reminderCommand = ReminderCommandRouter.parse(userText)
        if (reminderCommand != null && reminderExecutor != null) {
            transition(VoiceState.EXECUTING_ACTION, TransitionReason.ROUTE_LOCAL)
            stateJob = scope.launch {
                val response = try {
                    reminderExecutor.execute(reminderCommand)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ReminderActionResult("提醒設定失敗，請稍後再試一次。")
                }
                if (isActive) dispatch(Command.ReminderActionReady(response))
            }
            return
        }
        val musicCommand = MusicCommandRouter.parse(userText, settings.musicAppName)
        if (musicCommand != null && musicExecutor != null) {
            transition(VoiceState.EXECUTING_ACTION, TransitionReason.ROUTE_LOCAL)
            executeMusic(musicCommand)
            return
        }
        val provider = llm
        if (provider == null || settings.baseUrl.isBlank() || settings.model.isBlank()) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.LLM_CONFIG_INVALID, "session=$sessionId")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        transition(VoiceState.THINKING, TransitionReason.ROUTE_REMOTE)
        stateJob = scope.launch {
            val started = clock.elapsedRealtimeMs()
            var firstTokenMs: Long? = null
            val answer = StringBuilder()
            val segmenter = SpeechSegmenter()
            val holdPreview = musicExecutor != null && MusicRefusalRecoveryRouter.mightBePlaybackRequest(userText)
            try {
                provider.streamChat(
                    ChatRequest(
                        messages = history.messages(settings.systemInstruction, userText),
                        maxOutputTokens = settings.maxOutputTokens,
                        temperature = settings.temperature,
                        requestId = sessionId,
                    ),
                ).collect { chunk ->
                    if (firstTokenMs == null) firstTokenMs = clock.elapsedRealtimeMs() - started
                    answer.append(chunk.text)
                    if (!holdPreview) M1DiagnosticsRepository.setAnswerPreview(answer.toString())
                    segmenter.accept(chunk.text).forEach { sentence ->
                        if (!ttsQueue.offer(sentence)) throw IllegalStateException("tts-queue-full")
                        M1DiagnosticsRepository.setTtsQueue(ttsQueue.size, ttsQueue.highWaterMark)
                    }
                }
                segmenter.finish(success = true).forEach { sentence ->
                    if (!ttsQueue.offer(sentence)) throw IllegalStateException("tts-queue-full")
                }
                dispatch(
                    Command.AnswerReady(
                        userText = userText,
                        answer = answer.toString(),
                        firstTokenMs = firstTokenMs,
                        totalMs = clock.elapsedRealtimeMs() - started,
                    ),
                )
            } catch (error: Throwable) {
                segmenter.finish(success = false)
                ttsQueue.clear()
                M1DiagnosticsRepository.setTtsQueue(0, ttsQueue.highWaterMark)
                if (isActive) dispatch(Command.SpeechRejected(mapLlmError(error)))
            }
        }
    }

    private fun executeMusic(command: MusicCommand, recoveredFromLlm: Boolean = false) {
        val executor = requireNotNull(musicExecutor)
        if (command == MusicCommand.Pause) {
            resumeMusicAfterSession = false
            keepMusicPausedAfterSession = true
        } else if (command == MusicCommand.Play || command == MusicCommand.PlayFavorites ||
            command == MusicCommand.Next || command == MusicCommand.Previous ||
            command is MusicCommand.SearchAndPlay
        ) {
            keepMusicPausedAfterSession = false
        }
        stateJob = scope.launch {
            val response = try {
                executor.execute(command)
            } catch (_: TimeoutCancellationException) {
                MusicActionResult("音樂操作等太久了，請再試一次。")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                MusicActionResult("音樂操作失敗，請再試一次。")
            }
            if (MusicHoldPolicy.shouldPauseAfterAction(command, resumeMusicAfterSession)) {
                try {
                    if (executor.pauseForVoice()) resumeMusicAfterSession = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The session-level resume check still runs when the conversation ends.
                }
            }
            if (isActive) dispatch(Command.MusicActionReady(response, recoveredFromLlm))
        }
    }

    private suspend fun handleContextualClose() {
        val musicWasHeld = resumeMusicAfterSession
        val alarmWasHeld = resumeAlarmAfterSession
        resumeMusicAfterSession = false
        resumeAlarmAfterSession = false
        transition(VoiceState.EXECUTING_ACTION, TransitionReason.ROUTE_LOCAL)
        stateJob = scope.launch {
            val alarmStopped = try {
                alarmExecutor?.stop() == true
            } catch (_: Exception) {
                false
            }
            if (!alarmStopped && alarmWasHeld) {
                runCatching { alarmExecutor?.resumeAfterVoice() }
            }
            val musicStopped = if (musicWasHeld) {
                try {
                    musicExecutor?.stopForClose() == true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
            val message = when {
                alarmStopped && musicStopped -> "已關閉鬧鐘與音樂。"
                alarmStopped -> "已關閉鬧鐘。"
                musicStopped -> "已停止音樂。"
                musicWasHeld -> "音樂已保持暫停，不會自動繼續播放。"
                else -> null
            }
            if (isActive) dispatch(Command.ContextualCloseFinished(message))
        }
    }

    private suspend fun handleContextualCloseFinished(message: String?) {
        if (machine.state != VoiceState.EXECUTING_ACTION) return
        message?.let(M1DiagnosticsRepository::setAnswerPreview)
        returnToIdle(TransitionReason.USER_CANCEL)
    }

    private suspend fun handleAnswerReady(command: Command.AnswerReady) {
        if (machine.state != VoiceState.THINKING) return
        if (command.answer.isBlank()) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.LLM_RESPONSE_INVALID, "empty-answer")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        val recovery = if (musicExecutor != null) {
            MusicRefusalRecoveryRouter.recover(command.userText, command.answer)
        } else {
            null
        }
        if (recovery != null) {
            ttsQueue.clear()
            M1DiagnosticsRepository.setTtsQueue(0, ttsQueue.highWaterMark)
            M1DiagnosticsRepository.setLlmResult(
                command.answer, 200, command.firstTokenMs, command.totalMs, displayAnswer = false,
            )
            when (recovery) {
                is MusicRefusalRecovery.SearchAndPlay -> {
                    M1DiagnosticsRepository.setAnswerPreview("")
                    transition(VoiceState.EXECUTING_ACTION, TransitionReason.ROUTE_LOCAL)
                    executeMusic(MusicCommand.SearchAndPlay(recovery.query), recoveredFromLlm = true)
                }
                MusicRefusalRecovery.AskForTarget -> {
                    val message = "我沒聽清要播放的歌曲或歌手，請再說一次。"
                    M1DiagnosticsRepository.setAnswerPreview(message)
                    if (speechOutput == null || !ttsQueue.offer(message)) {
                        M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "music-target-no-output")
                        returnToIdle(TransitionReason.USER_CANCEL)
                    } else {
                        M1DiagnosticsRepository.setTtsQueue(ttsQueue.size, ttsQueue.highWaterMark)
                        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
                        ttsRecoveryAttempted = false
                        startSpeaking()
                    }
                }
            }
            return
        }
        history.add(command.userText, command.answer)
        M1DiagnosticsRepository.setLlmResult(command.answer, 200, command.firstTokenMs, command.totalMs)
        val output = speechOutput
        if (output == null || ttsQueue.size == 0) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "no-output")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
        ttsRecoveryAttempted = false
        startSpeaking()
    }

    private suspend fun handleMusicActionReady(command: Command.MusicActionReady) {
        if (machine.state != VoiceState.EXECUTING_ACTION) return
        val result = command.result
        val original = if (resumeMusicAfterSession) result.deferredMessage ?: result.message else result.message
        val message = if (command.recoveredFromLlm && result.confirmedPlayback) {
            when {
                original.startsWith("好的，正在播放") -> original.replaceFirst("好的，正在播放", "好的，為你播放")
                original.startsWith("好的，準備播放") -> original.replaceFirst("好的，準備播放", "好的，準備為你播放")
                original == "好的，音樂已開始播放。" -> "好的，為你播放音樂。"
                else -> original
            }
        } else {
            original
        }
        M1DiagnosticsRepository.setAnswerPreview(message)
        val output = speechOutput
        if (output == null || !ttsQueue.offer(message)) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "action-no-output")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        M1DiagnosticsRepository.setTtsQueue(ttsQueue.size, ttsQueue.highWaterMark)
        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
        ttsRecoveryAttempted = false
        startSpeaking()
    }

    private suspend fun handleReminderActionReady(result: ReminderActionResult) {
        if (machine.state != VoiceState.EXECUTING_ACTION) return
        M1DiagnosticsRepository.setAnswerPreview(result.message)
        val output = speechOutput
        if (output == null || !ttsQueue.offer(result.message)) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "reminder-no-output")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        M1DiagnosticsRepository.setTtsQueue(ttsQueue.size, ttsQueue.highWaterMark)
        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
        ttsRecoveryAttempted = false
        startSpeaking()
    }

    private fun startSpeaking(firstSentence: String? = null) {
        val output = speechOutput ?: return
        stateJob = scope.launch {
            var currentSentence = firstSentence.orEmpty()
            try {
                if (currentSentence.isNotBlank()) output.speak(currentSentence)
                while (isActive) {
                    currentSentence = ttsQueue.poll() ?: break
                    M1DiagnosticsRepository.setTtsQueue(ttsQueue.size, ttsQueue.highWaterMark)
                    output.speak(currentSentence)
                }
                dispatch(Command.SpeechFinished)
            } catch (error: Throwable) {
                if (isActive) dispatch(Command.TtsFailed(currentSentence, error.javaClass.simpleName))
            }
        }
    }

    private suspend fun handleSpeechFinished() {
        if (machine.state != VoiceState.SPEAKING) return
        if (settings.followUpSeconds <= 0) returnToIdle(TransitionReason.SPEECH_FINISHED) else enterFollowUp()
    }

    private suspend fun enterFollowUp() {
        delay(audioResumeDelayMs)
        try {
            if (musicExecutor?.pauseForVoice() == true && !keepMusicPausedAfterSession) {
                resumeMusicAfterSession = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Continue listening even if the selected player cannot be reached.
        }
        runCatching {
            vad.reset()
            audioSource = audio.start()
        }.onFailure {
            dispatch(Command.AudioFailure(VoiceErrorCode.AUDIO_START_FAILED, it.javaClass.simpleName))
            return
        }
        transition(VoiceState.FOLLOW_UP, TransitionReason.SPEECH_FINISHED)
        val policy = FollowUpPolicy(settings.followUpSeconds)
        val started = clock.elapsedRealtimeMs()
        stateJob = scope.launch {
            while (isActive && machine.state == VoiceState.FOLLOW_UP) {
                val frame = audio.frameQueue.poll(FRAME_POLL_MS)
                if (frame != null && vad.accept(frame).speechDetected) {
                    dispatch(Command.FollowUpSpeech(frame))
                    return@launch
                }
                if (policy.expired(started, clock.elapsedRealtimeMs())) {
                    dispatch(Command.FollowUpTimeout)
                    return@launch
                }
            }
        }
    }

    private suspend fun handleFollowUpSpeech(firstFrame: AudioFrame) {
        if (machine.state != VoiceState.FOLLOW_UP) return
        sessionId = UUID.randomUUID().toString().replace("-", "").take(8)
        beginListening(firstFrame, audioAlreadyRunning = true)
    }

    private suspend fun handleFollowUpTimeout() {
        if (machine.state == VoiceState.FOLLOW_UP) returnToIdle(TransitionReason.FOLLOW_UP_TIMEOUT)
    }

    private suspend fun handleTtsFailed(sentence: String, detail: String) {
        if (machine.state != VoiceState.SPEAKING) return
        val factory = speechOutputFactory
        if (!ttsRecoveryAttempted && factory != null) {
            ttsRecoveryAttempted = true
            runCatching { speechOutput?.close() }
            val replacement = runCatching { factory() }.getOrNull()
            if (replacement != null) {
                speechOutput = replacement
                startSpeaking(sentence.takeIf { it.isNotBlank() })
                return
            }
        }
        M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_DIED, detail)
        ttsQueue.clear()
        returnToIdle(TransitionReason.USER_CANCEL)
    }

    private fun mapLlmError(error: Throwable): VoiceErrorCode = when ((error as? LlmException)?.code) {
        LlmErrorCode.CONFIG_INVALID -> VoiceErrorCode.LLM_CONFIG_INVALID
        LlmErrorCode.AUTH_FAILED -> VoiceErrorCode.LLM_AUTH_FAILED
        LlmErrorCode.RATE_LIMITED -> VoiceErrorCode.LLM_RATE_LIMITED
        LlmErrorCode.NETWORK_TIMEOUT, LlmErrorCode.NETWORK_UNAVAILABLE -> VoiceErrorCode.LLM_NETWORK_TIMEOUT
        else -> if (error.message == "tts-queue-full") VoiceErrorCode.QUEUE_OVERLOAD else VoiceErrorCode.LLM_RESPONSE_INVALID
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
        llm?.cancel()
        speechOutput?.stop()
        ttsQueue.clear()
        audio.stop()
        transition(VoiceState.PAUSED, TransitionReason.USER_PAUSE)
        resumeHeldAlarm()
        resumeHeldMusic()
    }

    private suspend fun returnToIdle(reason: TransitionReason) {
        if (machine.state in setOf(VoiceState.STOPPED, VoiceState.ERROR, VoiceState.STARTING)) return
        stateJob?.cancel()
        stateJob = null
        asr.cancel()
        llm?.cancel()
        speechOutput?.stop()
        ttsQueue.clear()
        audio.stop()
        delay(audioResumeDelayMs)
        enterIdle(reason, audioAlreadyRunning = false)
        resumeHeldAlarm()
        resumeHeldMusic()
    }

    private fun resumeHeldAlarm() {
        if (!resumeAlarmAfterSession) return
        resumeAlarmAfterSession = false
        runCatching { alarmExecutor?.resumeAfterVoice() }
    }

    private suspend fun resumeHeldMusic() {
        if (!resumeMusicAfterSession) return
        try {
            musicExecutor?.resumeAfterVoice()
            resumeMusicAfterSession = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            resumeMusicAfterSession = false
        }
    }

    private suspend fun handleAudioFailure(code: VoiceErrorCode, detail: String) {
        M1DiagnosticsRepository.onAudioError(code, detail)
        resumeHeldAlarm()
        resumeHeldMusic()
        val decision = recoveryPolicy.recordFailure(clock.elapsedRealtimeMs())
        if (!decision.recover || !machine.canTransition(VoiceState.RECOVERING)) {
            transition(VoiceState.ERROR, TransitionReason.FATAL_ERROR)
            releaseSessionResources()
            return
        }
        transition(VoiceState.RECOVERING, TransitionReason.RESOURCE_FAILURE)
        releaseSessionResources()
        stateJob = scope.launch {
            delay(decision.delayMs)
            dispatch(Command.RecoveryReady)
        }
    }

    private suspend fun handleRecoveryReady() {
        if (machine.state != VoiceState.RECOVERING) return
        enterIdle(TransitionReason.RECOVERY_SUCCEEDED, audioAlreadyRunning = false)
    }

    private suspend fun handleSessionFailed(code: VoiceErrorCode, detail: String) {
        M1DiagnosticsRepository.recordError(code, detail)
        transition(VoiceState.ERROR, TransitionReason.FATAL_ERROR)
        releaseSessionResources()
        resumeHeldAlarm()
        resumeHeldMusic()
    }

    private suspend fun handleStop() {
        if (machine.state == VoiceState.STOPPED) return
        transition(VoiceState.STOPPED, TransitionReason.USER_STOP)
        releaseSessionResources()
        resumeHeldAlarm()
        resumeHeldMusic()
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
        if (resumeAlarmAfterSession) {
            resumeAlarmAfterSession = false
            runCatching { alarmExecutor?.resumeAfterVoice() }
        }
        if (resumeMusicAfterSession) {
            resumeMusicAfterSession = false
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                runCatching { musicExecutor?.resumeAfterVoice() }
            }
        }
        runCatching { asr.close() }
        runCatching { vad.close() }
        runCatching { kws.close() }
        runCatching { acknowledgementPlayer.release() }
        runCatching { llm?.close() }
        runCatching { speechOutput?.close() }
    }

    private sealed interface Command {
        data object Start : Command
        data object WakeDetected : Command
        data object AcknowledgementFinished : Command
        data object SpeechComplete : Command
        data class SpeechRejected(val code: VoiceErrorCode) : Command
        data class TranscriptReady(val result: AsrResult) : Command
        data class AnswerReady(
            val userText: String,
            val answer: String,
            val firstTokenMs: Long?,
            val totalMs: Long,
        ) : Command
        data class MusicActionReady(
            val result: MusicActionResult,
            val recoveredFromLlm: Boolean = false,
        ) : Command
        data class ReminderActionReady(val result: ReminderActionResult) : Command
        data class ContextualCloseFinished(val message: String?) : Command
        data object SpeechFinished : Command
        data class FollowUpSpeech(val firstFrame: AudioFrame) : Command
        data object FollowUpTimeout : Command
        data object RecoveryReady : Command
        data class TtsFailed(val sentence: String, val detail: String) : Command
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
