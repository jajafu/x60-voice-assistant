package com.jajafu.x60assistant.orchestration

import com.jajafu.x60assistant.asr.AsrResult
import com.jajafu.x60assistant.asr.SpeechRecognizerEngine
import com.jajafu.x60assistant.asr.TraditionalChineseConverter
import com.jajafu.x60assistant.audio.AudioInputEngine
import com.jajafu.x60assistant.audio.AudioFrame
import com.jajafu.x60assistant.audio.WakeAcknowledgement
import com.jajafu.x60assistant.diagnostics.M1DiagnosticsRepository
import com.jajafu.x60assistant.diagnostics.VoiceErrorCode
import com.jajafu.x60assistant.kws.KwsModelPack
import com.jajafu.x60assistant.kws.WakeWordEngine
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
import com.jajafu.x60assistant.navigation.NavigationActionExecutor
import com.jajafu.x60assistant.navigation.NavigationActionResult
import com.jajafu.x60assistant.navigation.NavigationCommand
import com.jajafu.x60assistant.navigation.NavigationCommandRouter
import com.jajafu.x60assistant.navigation.NavigationLaunchMode
import com.jajafu.x60assistant.music.MusicRefusalRecovery
import com.jajafu.x60assistant.music.MusicRefusalRecoveryRouter
import com.jajafu.x60assistant.reminder.ReminderActionExecutor
import com.jajafu.x60assistant.reminder.ReminderActionResult
import com.jajafu.x60assistant.reminder.ReminderCommandRouter
import com.jajafu.x60assistant.reminder.AlarmActionExecutor
import com.jajafu.x60assistant.tts.SpeechOutputEngine
import com.jajafu.x60assistant.tts.StreamingSpeechPlayer
import com.jajafu.x60assistant.tts.TtsException
import com.jajafu.x60assistant.vad.SpeechEndpointDetector
import com.jajafu.x60assistant.vad.SpeechEndpointEvent
import com.jajafu.x60assistant.vad.VadEngine
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** The only component allowed to switch conversational state. */
class VoiceOrchestrator(
    scope: CoroutineScope,
    private val audio: AudioInputEngine,
    private val kws: WakeWordEngine,
    private val vad: VadEngine,
    private val asr: SpeechRecognizerEngine,
    private val acknowledgementPlayer: WakeAcknowledgement,
    private val endpointDetector: SpeechEndpointDetector,
    private val convertText: (String) -> String = TraditionalChineseConverter()::convert,
    private val settings: AssistantSettings = AssistantSettings(),
    private val llm: LlmProvider? = null,
    private val musicExecutor: MusicActionExecutor? = null,
    private val reminderExecutor: ReminderActionExecutor? = null,
    private val alarmExecutor: AlarmActionExecutor? = null,
    private val navigationExecutor: NavigationActionExecutor? = null,
    speechOutput: SpeechOutputEngine? = null,
    private val speechOutputFactory: (suspend () -> SpeechOutputEngine?)? = null,
    private val clock: MonotonicClock = AndroidMonotonicClock,
    private val audioResumeDelayMs: Long = 300,
) : Closeable {
    private val runtimeJob = SupervisorJob(scope.coroutineContext[Job])
    private val runtimeScope = CoroutineScope(scope.coroutineContext + runtimeJob)
    private val closedSignal = CompletableDeferred<Unit>()
    private val commands = Channel<Envelope>(capacity = COMMAND_CAPACITY)
    private val machine = VoiceStateMachine()
    private val mutableState = MutableStateFlow(VoiceState.STOPPED)
    private val closed = AtomicBoolean(false)
    private var generation = 0L
    private var stateJob: Job? = null
    private var answerWakeJob: Job? = null
    private var audioSource = "—"
    private var sessionId = "—"
    private val history = ConversationHistory(settings.contextTurns)
    private val recoveryPolicy = RecoveryPolicy()
    private var speechOutput: SpeechOutputEngine? = speechOutput
    private var ttsRecoveryAttempted = false
    @Volatile private var resumeMusicAfterSession = false
    @Volatile private var keepMusicPausedAfterSession = false
    @Volatile private var resumeAlarmAfterSession = false

    val state = mutableState.asStateFlow()

    init {
        runtimeScope.launch { processCommands() }
        runtimeJob.invokeOnCompletion { close() }
    }

    fun start() = dispatch(Command.Start)
    fun pause() = dispatch(Command.Pause)
    fun resume() = dispatch(Command.Resume)
    fun cancelSession() = dispatch(Command.CancelSession)
    fun stop() = dispatch(Command.Stop)
    fun onAudioFailure(code: VoiceErrorCode, detail: String) = dispatch(Command.AudioFailure(code, detail))

    private fun dispatch(command: Command, generation: Long? = null) {
        if (!closed.get() && commands.trySend(Envelope(command, generation)).isFailure) {
            M1DiagnosticsRepository.onAudioError(VoiceErrorCode.QUEUE_OVERLOAD, "orchestrator-command")
        }
    }

    private suspend fun processCommands() {
        for (envelope in commands) {
            if (envelope.generation != null && envelope.generation != generation) continue
            when (val command = envelope.command) {
                Command.Start -> handleStart()
                Command.WakeDetected -> handleWakeDetected()
                Command.AcknowledgementFinished -> handleAcknowledgementFinished()
                Command.SpeechComplete -> handleSpeechComplete()
                is Command.SpeechRejected -> handleSpeechRejected(command.code)
                is Command.TranscriptReady -> handleTranscriptReady(command.result)
                is Command.AnswerReady -> handleAnswerReady(command)
                is Command.MusicActionReady -> handleMusicActionReady(command)
                is Command.NavigationActionReady -> handleNavigationActionReady(command)
                is Command.ReminderActionReady -> handleReminderActionReady(command.result)
                is Command.ContextualCloseFinished -> handleContextualCloseFinished(command.message)
                Command.SpeechFinished -> handleSpeechFinished()
                is Command.FollowUpSpeech -> handleFollowUpSpeech(command.firstFrame)
                Command.FollowUpTimeout -> handleFollowUpTimeout()
                Command.RecoveryReady -> handleRecoveryReady()
                Command.SpeakingStarted -> {
                    if (machine.state == VoiceState.THINKING) {
                        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY, cancelWork = false)
                    }
                }
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
        stateJob = launchState { emit ->
            runCatching {
                audioSource = audio.start()
            }.onSuccess {
                emit(Command.Resume)
            }.onFailure {
                emit(Command.SessionFailed(VoiceErrorCode.AUDIO_INIT_FAILED, it.javaClass.simpleName))
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
                handleAudioStartFailure(VoiceErrorCode.AUDIO_INIT_FAILED, it.javaClass.simpleName)
                return
            }
        }
        kws.reset()
        transition(VoiceState.IDLE_KWS, reason)
        M1DiagnosticsRepository.setIdle(audioSource)
        stateJob = launchState { emit ->
            try {
                while (isActive && machine.state == VoiceState.IDLE_KWS) {
                    val frame = audio.nextFrame(FRAME_POLL_MS) ?: continue
                    if (kws.accept(frame)) {
                        emit(Command.WakeDetected)
                        return@launchState
                    }
                }
            } catch (error: Throwable) {
                if (isActive) emit(Command.SessionFailed(VoiceErrorCode.KWS_RUNTIME_FAILED, error.javaClass.simpleName))
            }
        }
    }

    private suspend fun handleWakeDetected() {
        val startingSession = machine.state == VoiceState.IDLE_KWS
        if (!startingSession && machine.state !in setOf(VoiceState.THINKING, VoiceState.SPEAKING)) return
        if (startingSession) {
            keepMusicPausedAfterSession = false
            // 喚醒詞是新話題的邊界：只有同一輪的 Follow Up 視窗才延續上下文，避免舊話題殘留。
            history.clear()
        }
        sessionId = UUID.randomUUID().toString().replace("-", "").take(8)
        M1DiagnosticsRepository.onWake(KwsModelPack.KEYWORDS_SCORE, settings.wakePhrase)
        transition(VoiceState.WAKE_ACK, TransitionReason.WAKE_DETECTED)
        stateJob = launchState { emit ->
            try {
                if (startingSession) {
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
                }
                acknowledgementPlayer.playAndAwait(speechOutput, settings.wakeAcknowledgement)
                emit(Command.AcknowledgementFinished)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(Command.SessionFailed(VoiceErrorCode.AUDIO_START_FAILED, error.javaClass.simpleName))
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
        stateJob = launchState { emit ->
            try {
                firstFrame?.let { if (processListeningFrame(it, emit)) return@launchState }
                while (isActive && machine.state == VoiceState.LISTENING) {
                    val frame = audio.nextFrame(FRAME_POLL_MS) ?: continue
                    if (processListeningFrame(frame, emit)) return@launchState
                }
            } catch (error: OutOfMemoryError) {
                emit(Command.SessionFailed(VoiceErrorCode.ASR_MODEL_OOM, error.javaClass.simpleName))
            } catch (error: Throwable) {
                if (isActive) emit(Command.SessionFailed(VoiceErrorCode.ASR_RUNTIME_FAILED, error.javaClass.simpleName))
            }
        }
    }

    private fun processListeningFrame(frame: AudioFrame, emit: (Command) -> Unit): Boolean {
        asr.accept(frame)?.let(convertText)?.let(M1DiagnosticsRepository::setPartialTranscript)
        return when (endpointDetector.accept(vad.accept(frame).speechDetected, clock.elapsedRealtimeMs())) {
            SpeechEndpointEvent.None, SpeechEndpointEvent.SpeechStarted -> false
            SpeechEndpointEvent.Endpoint -> true.also { emit(Command.SpeechComplete) }
            SpeechEndpointEvent.MaxDuration -> true.also {
                M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.VAD_MAX_DURATION, "session=$sessionId")
                emit(Command.SpeechComplete)
            }
            SpeechEndpointEvent.NoSpeechTimeout -> true.also {
                emit(Command.SpeechRejected(VoiceErrorCode.VAD_NO_SPEECH))
            }
            SpeechEndpointEvent.SegmentTooShort -> true.also {
                emit(Command.SpeechRejected(VoiceErrorCode.VAD_SEGMENT_TOO_SHORT))
            }
        }
    }

    private suspend fun handleSpeechComplete() {
        if (machine.state != VoiceState.LISTENING) return
        transition(VoiceState.TRANSCRIBING, TransitionReason.SPEECH_ENDPOINT)
        audio.stop()
        stateJob = launchState { emit ->
            runCatching { asr.finish() }
                .onSuccess { emit(Command.TranscriptReady(it)) }
                .onFailure {
                    val code = if (it is OutOfMemoryError) VoiceErrorCode.ASR_MODEL_OOM else VoiceErrorCode.ASR_RUNTIME_FAILED
                    emit(Command.SessionFailed(code, it.javaClass.simpleName))
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
        val userText = convertText(result.text)
        M1DiagnosticsRepository.setFinalTranscript(userText, result)
        transition(VoiceState.ROUTING, TransitionReason.TRANSCRIPT_READY)
        if (ContextualCloseCommandRouter.matches(userText)) {
            handleContextualClose()
            return
        }
        val reminderCommand = ReminderCommandRouter.parse(userText)
        if (reminderCommand != null && reminderExecutor != null) {
            transition(VoiceState.EXECUTING_ACTION, TransitionReason.ROUTE_LOCAL)
            stateJob = launchState { emit ->
                val response = try {
                    reminderExecutor.execute(reminderCommand)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ReminderActionResult("提醒設定失敗，請稍後再試一次。")
                }
                if (isActive) emit(Command.ReminderActionReady(response))
            }
            return
        }
        val musicCommand = MusicCommandRouter.parse(userText, settings.musicAppName)
        if (musicCommand != null && musicExecutor != null) {
            transition(VoiceState.EXECUTING_ACTION, TransitionReason.ROUTE_LOCAL)
            executeMusic(musicCommand)
            return
        }
        val navigationCommand = NavigationCommandRouter.parse(userText, settings.wakePhrase)
        if (navigationCommand != null && navigationExecutor != null) {
            transition(VoiceState.EXECUTING_ACTION, TransitionReason.ROUTE_LOCAL)
            executeNavigation(navigationCommand)
            return
        }
        val provider = llm
        if (provider == null || settings.baseUrl.isBlank() || settings.model.isBlank()) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.LLM_CONFIG_INVALID, "session=$sessionId")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        transition(VoiceState.THINKING, TransitionReason.ROUTE_REMOTE)
        startAnswerWakeMonitoring()
        stateJob = launchState { emit ->
            val started = clock.elapsedRealtimeMs()
            var firstTokenMs: Long? = null
            var providerTotalMs = 0L
            val holdPreview = musicExecutor != null && MusicRefusalRecoveryRouter.mightBePlaybackRequest(userText)
            var spoken = false
            ttsRecoveryAttempted = false
            try {
                val chunks = provider.streamChat(
                    ChatRequest(
                        messages = history.messages(settings.systemInstruction, userText),
                        maxOutputTokens = settings.maxOutputTokens,
                        temperature = settings.temperature,
                        requestId = sessionId,
                    ),
                ).map { chunk ->
                    if (firstTokenMs == null) firstTokenMs = clock.elapsedRealtimeMs() - started
                    chunk.text
                }.onCompletion { error ->
                    if (error == null) providerTotalMs = clock.elapsedRealtimeMs() - started
                }
                val speak: (suspend (String) -> Unit)? = if (holdPreview || speechOutput == null) null else { sentence ->
                    if (!spoken) {
                        spoken = true
                        emit(Command.SpeakingStarted)
                    }
                    speakWithRecovery(sentence)
                }
                val answer = StreamingSpeechPlayer().collect(
                    chunks = chunks,
                    speak = speak,
                    onText = { if (!holdPreview) M1DiagnosticsRepository.setAnswerPreview(it) },
                    onQueue = M1DiagnosticsRepository::setTtsQueue,
                    onSpeechFailure = { provider.cancel() },
                )
                emit(Command.AnswerReady(userText, answer, firstTokenMs, providerTotalMs, spoken))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                emit(Command.SpeechRejected(mapLlmError(error)))
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
        stateJob = launchState { emit ->
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
            if (isActive) emit(Command.MusicActionReady(response, recoveredFromLlm))
        }
    }

    private fun executeNavigation(command: NavigationCommand) {
        val executor = requireNotNull(navigationExecutor)
        stateJob = launchState { emit ->
            val response = try {
                executor.execute(command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                NavigationActionResult("導航操作失敗，請再試一次。", NavigationLaunchMode.FAILED)
            }
            if (isActive) emit(Command.NavigationActionReady(response))
        }
    }

    private suspend fun handleNavigationActionReady(command: Command.NavigationActionReady) {
        if (machine.state != VoiceState.EXECUTING_ACTION) return
        val message = command.result.message
        M1DiagnosticsRepository.setAnswerPreview(message)
        val output = speechOutput
        if (output == null) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "nav-action-no-output")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
        ttsRecoveryAttempted = false
        startSpeaking(message)
    }

    private suspend fun handleContextualClose() {
        val musicWasHeld = resumeMusicAfterSession
        val alarmWasHeld = resumeAlarmAfterSession
        resumeMusicAfterSession = false
        resumeAlarmAfterSession = false
        transition(VoiceState.EXECUTING_ACTION, TransitionReason.ROUTE_LOCAL)
        stateJob = launchState { emit ->
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
            if (isActive) emit(Command.ContextualCloseFinished(message))
        }
    }

    private suspend fun handleContextualCloseFinished(message: String?) {
        if (machine.state != VoiceState.EXECUTING_ACTION) return
        message?.let(M1DiagnosticsRepository::setAnswerPreview)
        returnToIdle(TransitionReason.USER_CANCEL)
    }

    private suspend fun handleAnswerReady(command: Command.AnswerReady) {
        if (machine.state !in setOf(VoiceState.THINKING, VoiceState.SPEAKING)) return
        if (command.answer.isBlank()) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.LLM_RESPONSE_INVALID, "empty-answer")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        val recovery = if (!command.spoken && musicExecutor != null) {
            MusicRefusalRecoveryRouter.recover(command.userText, command.answer)
        } else {
            null
        }
        if (recovery != null) {
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
                    if (speechOutput == null) {
                        M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "music-target-no-output")
                        returnToIdle(TransitionReason.USER_CANCEL)
                    } else {
                        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
                        ttsRecoveryAttempted = false
                        startSpeaking(message)
                    }
                }
            }
            return
        }
        history.add(command.userText, command.answer)
        M1DiagnosticsRepository.setLlmResult(command.answer, 200, command.firstTokenMs, command.totalMs)
        if (command.spoken) {
            handleSpeechFinished()
            return
        }
        val output = speechOutput
        if (output == null) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "no-output")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
        ttsRecoveryAttempted = false
        startSpeaking(command.answer)
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
        if (output == null) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "action-no-output")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
        ttsRecoveryAttempted = false
        startSpeaking(message)
    }

    private suspend fun handleReminderActionReady(result: ReminderActionResult) {
        if (machine.state != VoiceState.EXECUTING_ACTION) return
        M1DiagnosticsRepository.setAnswerPreview(result.message)
        val output = speechOutput
        if (output == null) {
            M1DiagnosticsRepository.recordSessionError(VoiceErrorCode.TTS_ENGINE_MISSING, "reminder-no-output")
            returnToIdle(TransitionReason.USER_CANCEL)
            return
        }
        transition(VoiceState.SPEAKING, TransitionReason.ANSWER_READY)
        ttsRecoveryAttempted = false
        startSpeaking(result.message)
    }

    private fun startSpeaking(text: String) {
        if (speechOutput == null) return
        startAnswerWakeMonitoring()
        stateJob = launchState { emit ->
            try {
                StreamingSpeechPlayer().collect(
                    flowOf(text), ::speakWithRecovery, onQueue = M1DiagnosticsRepository::setTtsQueue,
                )
                emit(Command.SpeechFinished)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                emit(Command.SpeechRejected(VoiceErrorCode.TTS_ENGINE_DIED))
            }
        }
    }

    private suspend fun speakWithRecovery(sentence: String) {
        try {
            requireNotNull(speechOutput).speak(sentence)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val factory = speechOutputFactory
            if (ttsRecoveryAttempted || factory == null) throw TtsException("TTS 播放失敗", error)
            ttsRecoveryAttempted = true
            speechOutput?.close()
            val replacement = factory() ?: throw TtsException("TTS 無法恢復", error)
            speechOutput = replacement
            try {
                replacement.speak(sentence)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (retryError: Exception) {
                throw TtsException("TTS 重試失敗", retryError)
            }
        }
    }

    private suspend fun handleSpeechFinished() {
        if (machine.state != VoiceState.SPEAKING) return
        if (settings.followUpSeconds <= 0) returnToIdle(TransitionReason.SPEECH_FINISHED) else enterFollowUp()
    }

    private suspend fun enterFollowUp() {
        stopAnswerWakeMonitoring()
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
            audio.frameQueue.clear()
            if (!audio.isRunning()) audioSource = audio.start()
        }.onFailure {
            dispatch(Command.AudioFailure(VoiceErrorCode.AUDIO_START_FAILED, it.javaClass.simpleName))
            return
        }
        transition(VoiceState.FOLLOW_UP, TransitionReason.SPEECH_FINISHED)
        val policy = FollowUpPolicy(settings.followUpSeconds)
        val started = clock.elapsedRealtimeMs()
        stateJob = launchState { emit ->
            while (isActive && machine.state == VoiceState.FOLLOW_UP) {
                val frame = audio.nextFrame(FRAME_POLL_MS)
                if (frame != null && vad.accept(frame).speechDetected) {
                    emit(Command.FollowUpSpeech(frame))
                    return@launchState
                }
                if (policy.expired(started, clock.elapsedRealtimeMs())) {
                    emit(Command.FollowUpTimeout)
                    return@launchState
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

    private fun mapLlmError(error: Throwable): VoiceErrorCode = when {
        error is TtsException -> VoiceErrorCode.TTS_ENGINE_DIED
        else -> when ((error as? LlmException)?.code) {
            LlmErrorCode.CONFIG_INVALID -> VoiceErrorCode.LLM_CONFIG_INVALID
            LlmErrorCode.AUTH_FAILED -> VoiceErrorCode.LLM_AUTH_FAILED
            LlmErrorCode.RATE_LIMITED -> VoiceErrorCode.LLM_RATE_LIMITED
            LlmErrorCode.NETWORK_TIMEOUT, LlmErrorCode.NETWORK_UNAVAILABLE -> VoiceErrorCode.LLM_NETWORK_TIMEOUT
            else -> VoiceErrorCode.LLM_RESPONSE_INVALID
        }
    }

    private suspend fun handleSpeechRejected(code: VoiceErrorCode) {
        M1DiagnosticsRepository.recordSessionError(code, "session=$sessionId")
        returnToIdle(TransitionReason.USER_CANCEL)
    }

    private suspend fun handlePause() {
        if (machine.state in setOf(VoiceState.STOPPED, VoiceState.ERROR, VoiceState.PAUSED)) return
        cancelStateWork()
        asr.cancel()
        audio.stop()
        transition(VoiceState.PAUSED, TransitionReason.USER_PAUSE)
        resumeHeldAlarm()
        resumeHeldMusic()
    }

    private suspend fun returnToIdle(reason: TransitionReason) {
        if (machine.state in setOf(VoiceState.STOPPED, VoiceState.ERROR, VoiceState.STARTING,
                VoiceState.IDLE_KWS, VoiceState.PAUSED, VoiceState.RECOVERING)) return
        cancelStateWork()
        asr.cancel()
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
        if (machine.state in setOf(VoiceState.STOPPED, VoiceState.ERROR, VoiceState.PAUSED, VoiceState.RECOVERING)) return
        beginAudioRecovery(code, detail)
    }

    /** A synchronous start failure belongs to the current transition, even while paused or recovering. */
    private suspend fun handleAudioStartFailure(code: VoiceErrorCode, detail: String) {
        if (machine.state in setOf(VoiceState.STOPPED, VoiceState.ERROR)) return
        beginAudioRecovery(code, detail)
    }

    /**
     * 結束這輪時必須先 releaseSessionResources()（取消 LLM 串流並停止 TTS），再恢復被暫停的
     * 鬧鐘與音樂。順序相反會讓音樂先播出、舊回答的最後一句才補上，與 returnToIdle、
     * handlePause、handleStop、handleSessionFailed 的既有順序不一致。
     */
    private suspend fun beginAudioRecovery(code: VoiceErrorCode, detail: String) {
        M1DiagnosticsRepository.onAudioError(code, detail)
        val decision = recoveryPolicy.recordFailure(clock.elapsedRealtimeMs())
        val alreadyRecovering = machine.state == VoiceState.RECOVERING
        if (!decision.recover || (!alreadyRecovering && !machine.canTransition(VoiceState.RECOVERING))) {
            transition(VoiceState.ERROR, TransitionReason.FATAL_ERROR)
            releaseSessionResources()
            resumeHeldAlarm()
            resumeHeldMusic()
            return
        }
        if (!alreadyRecovering) transition(VoiceState.RECOVERING, TransitionReason.RESOURCE_FAILURE)
        releaseSessionResources()
        resumeHeldAlarm()
        resumeHeldMusic()
        stateJob = launchState { emit ->
            delay(decision.delayMs)
            emit(Command.RecoveryReady)
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

    private suspend fun transition(to: VoiceState, reason: TransitionReason, cancelWork: Boolean = true) {
        if (cancelWork) cancelStateWork()
        machine.transition(to, reason)
        mutableState.value = to
        M1DiagnosticsRepository.setState(to, "$reason,session=$sessionId")
    }

    private fun launchState(block: suspend CoroutineScope.((Command) -> Unit) -> Unit): Job {
        val workerGeneration = generation
        return runtimeScope.launch {
            block { command -> if (isActive) dispatch(command, workerGeneration) }
        }
    }

    private fun startAnswerWakeMonitoring() {
        if (answerWakeJob?.isActive == true) return
        if (!audio.isRunning()) {
            try {
                audioSource = audio.start()
            } catch (error: Throwable) {
                dispatch(Command.AudioFailure(VoiceErrorCode.AUDIO_START_FAILED, error.javaClass.simpleName))
                return
            }
        }
        try {
            kws.reset()
        } catch (error: Throwable) {
            dispatch(Command.SessionFailed(VoiceErrorCode.KWS_RUNTIME_FAILED, error.javaClass.simpleName))
            return
        }
        val workerGeneration = generation
        answerWakeJob = runtimeScope.launch {
            try {
                while (isActive && machine.state in setOf(VoiceState.THINKING, VoiceState.SPEAKING)) {
                    val frame = audio.nextFrame(FRAME_POLL_MS) ?: continue
                    if (kws.accept(frame)) {
                        dispatch(Command.WakeDetected, workerGeneration)
                        return@launch
                    }
                }
            } catch (error: Throwable) {
                if (isActive) {
                    dispatch(
                        Command.SessionFailed(VoiceErrorCode.KWS_RUNTIME_FAILED, error.javaClass.simpleName),
                        workerGeneration,
                    )
                }
            }
        }
    }

    private suspend fun stopAnswerWakeMonitoring() {
        val job = answerWakeJob
        answerWakeJob = null
        job?.cancel()
        job?.join()
    }

    private suspend fun cancelStateWork() {
        generation++
        val job = stateJob
        val wakeJob = answerWakeJob
        stateJob = null
        answerWakeJob = null
        job?.cancel()
        wakeJob?.cancel()
        runCatching { llm?.cancel() }
        runCatching { speechOutput?.stop() }
        job?.join() // Native decode is synchronous: cancellation alone does not finish it.
        wakeJob?.join()
    }

    private suspend fun releaseSessionResources() {
        cancelStateWork()
        asr.cancel()
        audio.stop()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runtimeJob.cancel()
        commands.close()
        runCatching { llm?.cancel() }
        runCatching { speechOutput?.stop() }
        // This scope must outlive Service.onDestroy, without blocking Android's main thread.
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runtimeJob.join()
                releaseSessionResources()
                resumeHeldAlarm()
                resumeHeldMusic()
            } finally {
                runCatching { audio.stop() }
                runCatching { asr.close() }
                runCatching { vad.close() }
                runCatching { kws.close() }
                runCatching { acknowledgementPlayer.release() }
                runCatching { llm?.close() }
                runCatching { speechOutput?.close() }
                closedSignal.complete(Unit)
            }
        }
    }

    suspend fun awaitClosed() = closedSignal.await()

    private data class Envelope(val command: Command, val generation: Long?)

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
            val spoken: Boolean,
        ) : Command
        data class MusicActionReady(
            val result: MusicActionResult,
            val recoveredFromLlm: Boolean = false,
        ) : Command
        data class NavigationActionReady(val result: NavigationActionResult) : Command
        data class ReminderActionReady(val result: ReminderActionResult) : Command
        data class ContextualCloseFinished(val message: String?) : Command
        data object SpeechFinished : Command
        data class FollowUpSpeech(val firstFrame: AudioFrame) : Command
        data object FollowUpTimeout : Command
        data object RecoveryReady : Command
        data object SpeakingStarted : Command
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
