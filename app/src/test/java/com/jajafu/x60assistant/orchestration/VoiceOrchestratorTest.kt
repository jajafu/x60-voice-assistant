package com.jajafu.x60assistant.orchestration

import com.jajafu.x60assistant.asr.AsrResult
import com.jajafu.x60assistant.asr.SpeechRecognizerEngine
import com.jajafu.x60assistant.audio.*
import com.jajafu.x60assistant.data.AssistantSettings
import com.jajafu.x60assistant.kws.WakeWordEngine
import com.jajafu.x60assistant.llm.*
import com.jajafu.x60assistant.music.*
import com.jajafu.x60assistant.tts.*
import com.jajafu.x60assistant.vad.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceOrchestratorTest {
    @Test fun `repeated cancellation is harmless and cannot resume a paused microphone`() = runTest {
        val f = Fixture(backgroundScope)
        try {
            startListening(f)
            f.voice.cancelSession()
            f.voice.cancelSession()
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            assertEquals(2, f.audio.starts)
            f.voice.cancelSession()
            runCurrent()
            assertEquals(2, f.audio.starts)
            f.voice.pause()
            f.voice.cancelSession()
            runCurrent()
            assertEquals(VoiceState.PAUSED, f.voice.state.value)
            assertFalse(f.audio.running)
            f.voice.resume()
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
        } finally { dispose(f) }
    }

    @Test fun `first sentence plays before SSE completion and long answers drain fully`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val expected = (1..30).map { "第${it}句。" }
        val f = Fixture(backgroundScope, chunks = flow {
            emit(LlmChunk(expected.first()))
            gate.await()
            expected.drop(1).forEach { emit(LlmChunk(it)) }
        })
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(VoiceState.SPEAKING, f.voice.state.value)
            assertEquals(listOf(expected.first()), f.output.spoken)
            assertTrue(f.audio.running)
            gate.complete(Unit)
            runCurrent()
            assertEquals(expected, f.output.spoken)
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
        } finally { dispose(f) }
    }

    @Test fun `cancel during streaming drops pending text and permits a fresh session`() = runTest {
        var upstreamCancelled = false
        val f = Fixture(backgroundScope, chunks = flow {
            try {
                emit(LlmChunk("第一句。"))
                awaitCancellation()
            } finally { upstreamCancelled = true }
        })
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            f.voice.cancelSession()
            f.voice.cancelSession()
            runCurrent()
            assertTrue(upstreamCancelled)
            assertEquals(listOf("第一句。"), f.output.spoken)
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            f.audio.feed(9, 3_000)
            runCurrent()
            assertEquals(VoiceState.LISTENING, f.voice.state.value)
        } finally { dispose(f) }
    }

    @Test fun `wake word during answer cancels it acknowledges and listens again`() = runTest {
        var upstreamCancelled = false
        val music = FakeMusic(pauseResult = true)
        val f = Fixture(backgroundScope, chunks = flow {
            try {
                emit(LlmChunk("第一句。"))
                awaitCancellation()
            } finally {
                upstreamCancelled = true
            }
        }, music = music)
        try {
            startListening(f)
            assertEquals(1, music.pauseCalls)
            assertEquals(listOf("我在"), f.acknowledgements)

            f.utterance()
            runCurrent()
            assertEquals(VoiceState.SPEAKING, f.voice.state.value)
            assertTrue(f.audio.running)

            f.audio.feed(9, 3_000)
            runCurrent()

            assertTrue(upstreamCancelled)
            assertEquals(VoiceState.LISTENING, f.voice.state.value)
            assertEquals(listOf("我在", "我在"), f.acknowledgements)
            assertEquals(1, music.pauseCalls)
            assertEquals(0, music.resumeCalls)

            f.voice.cancelSession()
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            assertEquals(1, music.resumeCalls)
        } finally { dispose(f) }
    }

    @Test fun `wake word while waiting for first token starts a new question`() = runTest {
        var upstreamCancelled = false
        val f = Fixture(backgroundScope, chunks = flow {
            try {
                awaitCancellation()
            } finally {
                upstreamCancelled = true
            }
        })
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(VoiceState.THINKING, f.voice.state.value)
            assertTrue(f.audio.running)

            f.audio.feed(9, 3_000)
            runCurrent()

            assertTrue(upstreamCancelled)
            assertEquals(VoiceState.LISTENING, f.voice.state.value)
            assertEquals(listOf("我在", "我在"), f.acknowledgements)
            assertTrue(f.output.spoken.isEmpty())
        } finally { dispose(f) }
    }

    @Test fun `completed answer reuses interrupt recorder for follow up`() = runTest {
        val f = Fixture(backgroundScope, followUpSeconds = 10)
        try {
            startListening(f)
            f.utterance()
            runCurrent()

            assertEquals(VoiceState.FOLLOW_UP, f.voice.state.value)
            assertTrue(f.audio.running)
            assertEquals(2, f.audio.starts)
        } finally { dispose(f) }
    }

    @Test fun `completion queued behind cancellation cannot reenter speaking or add history`() = runTest {
        val f = Fixture(backgroundScope)
        f.output.onSpeak = { f.voice.cancelSession() }
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            f.output.onSpeak = {}
            f.audio.feed(9, 3_000)
            runCurrent()
            f.utterance(4_000)
            runCurrent()
            assertEquals(2, f.provider.requests.size)
            assertEquals(listOf("system", "user"), f.provider.requests.last().messages.map { it.role })
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
        } finally { dispose(f) }
    }

    @Test fun `music refusal is buffered and only local recovery result is spoken`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(backgroundScope, text = "搜尋周杰倫的歌曲", chunks = flow {
            emit(LlmChunk("我無法播放音樂。"))
            gate.await()
            emit(LlmChunk("你可以搜尋《晴天》。"))
        }, music = FakeMusic())
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(1, f.provider.requests.size)
            assertTrue(f.output.spoken.isEmpty())
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf(MusicCommand.SearchAndPlay("晴天")), f.music!!.commands)
            assertEquals(listOf("本機播放結果。"), f.output.spoken)
        } finally { dispose(f) }
    }

    @Test fun `close waits for non cancellable native decode without blocking caller`() = runBlocking {
        verifyDecodeShutdown(cancelParent = false)
    }

    @Test fun `service scope cancellation also waits for native decode before release`() = runBlocking {
        verifyDecodeShutdown(cancelParent = true)
    }

    @Test fun `audio reopen failure schedules another recovery attempt`() = runTest {
        val f = Fixture(backgroundScope)
        try {
            f.voice.start()
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            f.audio.failStarts = 1

            f.voice.onAudioFailure(com.jajafu.x60assistant.diagnostics.VoiceErrorCode.AUDIO_READ_FAILED, "test")
            runCurrent()

            assertEquals(VoiceState.RECOVERING, f.voice.state.value)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            assertTrue(f.audio.running)
        } finally { dispose(f) }
    }

    @Test fun `pause during recovery is safe and resume restarts listening`() = runTest {
        val f = Fixture(backgroundScope)
        try {
            f.voice.start()
            runCurrent()
            f.voice.onAudioFailure(com.jajafu.x60assistant.diagnostics.VoiceErrorCode.AUDIO_READ_FAILED, "first")
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)

            f.voice.onAudioFailure(com.jajafu.x60assistant.diagnostics.VoiceErrorCode.AUDIO_READ_FAILED, "second")
            runCurrent()
            assertEquals(VoiceState.RECOVERING, f.voice.state.value)

            f.voice.pause()
            runCurrent()
            assertEquals(VoiceState.PAUSED, f.voice.state.value)
            assertFalse(f.audio.running)

            f.voice.resume()
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            assertTrue(f.audio.running)
        } finally { dispose(f) }
    }

    @Test fun `audio start failure while resuming from pause enters recovery`() = runTest {
        val f = Fixture(backgroundScope)
        try {
            f.voice.start()
            runCurrent()
            f.voice.pause()
            runCurrent()
            assertEquals(VoiceState.PAUSED, f.voice.state.value)
            f.audio.failStarts = 1

            f.voice.resume()
            runCurrent()

            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            assertTrue(f.audio.running)
            assertEquals(2, f.audio.starts)
        } finally { dispose(f) }
    }

    private suspend fun verifyDecodeShutdown(cancelParent: Boolean) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1)
        val exit = CountDownLatch(1)
        val f = Fixture(scope, decode = {
            entered.countDown()
            check(exit.await(10, TimeUnit.SECONDS))
        })
        try {
            f.voice.start()
            withTimeout(5_000) { f.voice.state.first { it == VoiceState.IDLE_KWS } }
            f.audio.feed(9, 0)
            withTimeout(5_000) { f.voice.state.first { it == VoiceState.LISTENING } }
            f.utterance()
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            if (cancelParent) scope.cancel() else f.voice.close()
            // close() has returned while the fake native call is still using its model.
            assertEquals(0, f.asr.closes)
            assertFalse(f.asr.cancelledDuringDecode)
            exit.countDown()
            withTimeout(5_000) { f.voice.awaitClosed() }
            assertEquals(1, f.asr.closes)
            assertFalse(f.asr.cancelledDuringDecode)
            assertTrue(f.provider.requests.isEmpty())
            assertFalse(f.audio.running)
        } finally {
            exit.countDown()
            f.voice.close()
            scope.cancel()
            withTimeout(5_000) { f.voice.awaitClosed() }
        }
    }

    private fun TestScope.startListening(f: Fixture) {
        f.voice.start()
        runCurrent()
        assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
        f.audio.feed(9, 0)
        runCurrent()
        assertEquals(VoiceState.LISTENING, f.voice.state.value)
    }

    private suspend fun TestScope.dispose(f: Fixture) {
        f.voice.close()
        runCurrent()
        withContext(Dispatchers.IO) { withTimeout(5_000) { f.voice.awaitClosed() } }
    }

    private class Fixture(
        scope: CoroutineScope,
        text: String = "解釋天空的顏色",
        chunks: Flow<LlmChunk> = flowOf(LlmChunk("回答。")),
        val music: FakeMusic? = null,
        decode: () -> Unit = {},
        followUpSeconds: Int = 0,
    ) {
        val audio = FakeAudio()
        val output = FakeOutput()
        val asr = FakeAsr(text, decode)
        val provider = FakeProvider(chunks)
        val acknowledgements = mutableListOf<String>()
        val voice = VoiceOrchestrator(
            scope, audio,
            kws = object : WakeWordEngine {
                override fun accept(frame: AudioFrame) = frame.samples.first() == 9.toShort()
                override fun reset() = Unit
                override fun close() = Unit
            },
            vad = object : VadEngine {
                override fun accept(frame: AudioFrame) = VadObservation(frame.samples.first() > 0)
                override fun reset() = Unit
                override fun close() = Unit
            },
            asr = asr,
            acknowledgementPlayer = object : WakeAcknowledgement {
                override suspend fun playAndAwait(speechOutput: SpeechOutputEngine?, response: String) {
                    acknowledgements += response
                }
                override fun release() = Unit
            },
            endpointDetector = SpeechEndpointDetector(SpeechEndpointConfig()),
            convertText = { it },
            settings = AssistantSettings(
                baseUrl = "https://example.invalid",
                model = "test",
                followUpSeconds = followUpSeconds,
            ),
            llm = provider,
            musicExecutor = music,
            speechOutput = output,
            clock = MonotonicClock { audio.now },
            audioResumeDelayMs = 0,
        )

        fun utterance(start: Long = 1_000) {
            audio.feed(1, start)
            audio.feed(1, start + 300)
            audio.feed(0, start + 1_200)
        }
    }

    private class FakeAudio : AudioInputEngine {
        override val frameQueue = BoundedAudioFrameQueue(50)
        private val frames = Channel<AudioFrame>(Channel.UNLIMITED)
        @Volatile var now = 0L
        @Volatile var running = false
        var starts = 0
        var failStarts = 0
        override fun start(): String {
            if (failStarts > 0) {
                failStarts--
                error("injected audio start failure")
            }
            check(!running)
            running = true
            starts++
            return "fake"
        }
        override fun stop() { running = false; while (frames.tryReceive().isSuccess) Unit }
        override fun isRunning() = running
        override suspend fun nextFrame(timeoutMs: Long): AudioFrame? = withTimeoutOrNull(timeoutMs) {
            frames.receive().also { now = it.elapsedRealtimeNanos / 1_000_000 }
        }
        fun feed(value: Int, timeMs: Long) {
            check(frames.trySend(AudioFrame(shortArrayOf(value.toShort()), timeMs * 1_000_000)).isSuccess)
        }
    }

    private class FakeAsr(private val text: String, private val decode: () -> Unit) : SpeechRecognizerEngine {
        override val modelId = "fake"
        @Volatile private var decoding = false
        @Volatile var closes = 0
        @Volatile var cancelledDuringDecode = false
        override fun beginSession() = Unit
        override fun accept(frame: AudioFrame): String? = null
        override fun finish(): AsrResult {
            decoding = true
            try { decode(); return AsrResult(text, 1_000, 1, 0.001, 1) }
            finally { decoding = false }
        }
        override fun cancel() { if (decoding) cancelledDuringDecode = true }
        override fun close() { check(!decoding); closes++ }
    }

    private class FakeOutput : SpeechOutputEngine {
        val spoken = mutableListOf<String>()
        var onSpeak: () -> Unit = {}
        override suspend fun initialize() = TtsStatus(true)
        override suspend fun speak(text: String) { spoken += text; onSpeak() }
        override fun stop() = Unit
        override fun close() = Unit
    }

    private class FakeProvider(private val chunks: Flow<LlmChunk>) : LlmProvider {
        val requests = mutableListOf<ChatRequest>()
        override suspend fun healthCheck() = ProviderHealth(true, "test", latencyMs = 0, message = "")
        override fun streamChat(request: ChatRequest): Flow<LlmChunk> { requests += request; return chunks }
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class FakeMusic(private val pauseResult: Boolean = false) : MusicActionExecutor {
        val commands = mutableListOf<MusicCommand>()
        var pauseCalls = 0
        var resumeCalls = 0
        override suspend fun execute(command: MusicCommand): MusicActionResult {
            commands += command
            return MusicActionResult("本機播放結果。")
        }
        override suspend fun pauseForVoice(): Boolean {
            pauseCalls++
            return pauseResult
        }
        override suspend fun resumeAfterVoice(): Boolean {
            resumeCalls++
            return true
        }
        override suspend fun stopForClose() = false
    }
}
