package com.va.voiceassistant.orchestration

import com.va.voiceassistant.asr.AsrResult
import com.va.voiceassistant.asr.SpeechRecognizerEngine
import com.va.voiceassistant.audio.*
import com.va.voiceassistant.data.AssistantSettings
import com.va.voiceassistant.kws.WakeWordEngine
import com.va.voiceassistant.llm.*
import com.va.voiceassistant.music.*
import com.va.voiceassistant.tts.*
import com.va.voiceassistant.vad.*
import com.va.voiceassistant.volume.VolumeActionExecutor
import com.va.voiceassistant.volume.VolumeActionResult
import com.va.voiceassistant.volume.VolumeCommand
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

    /** 系統辨識場景以真實時間驅動（與 verifyDecodeShutdown 同模式）：引擎 finish 會在真實執行緒阻塞，
     *  runTest 的虛擬排程接不到從其他執行緒回來的 resume，因此改用真實 scope 與狀態輪詢。 */
    @Test
    fun `system recognizer hears a transcript while our microphone is handed over`() = runBlocking {
        val engine = FakeTakeoverAsr("解釋天空的顏色", gated = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val f = Fixture(
            scope,
            chunks = flow {
                emit(LlmChunk("回答。"))
                awaitCancellation()
            },
            customAsr = engine,
        )
        try {
            f.voice.start()
            awaitState(f, VoiceState.IDLE_KWS)
            f.audio.feed(9, 0)
            awaitState(f, VoiceState.LISTENING)
            // 系統引擎聆聽中：共用麥克風已交棒給系統服務。
            assertFalse(f.audio.running)
            awaitBlocked(engine)
            engine.release()
            // 整句結果直接從 LISTENING 交逐字稿，未經本機 VAD 端點。
            awaitState(f, VoiceState.SPEAKING)
            assertTrue(f.audio.running)
            assertEquals("解釋天空的顏色", f.provider.requests.single().messages.last().content)
        } finally { disposeBlocking(f, scope) }
    }

    @Test
    fun `system recognizer without result returns to idle and can listen again`() = runBlocking {
        val engine = FakeTakeoverAsr("", gated = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val f = Fixture(scope, customAsr = engine)
        try {
            f.voice.start()
            awaitState(f, VoiceState.IDLE_KWS)
            f.audio.feed(9, 0)
            awaitState(f, VoiceState.LISTENING)
            awaitBlocked(engine)
            engine.release()
            // 空白結果視為沒聽到，回到待喚醒且麥克風恢復。
            awaitState(f, VoiceState.IDLE_KWS)
            assertTrue(f.audio.running)
            assertTrue(f.provider.requests.isEmpty())
            // 驗證真的可以再聽：第二次喚醒重新交棒、收第二輪空白後再回待喚醒。
            f.audio.feed(9, 3_000)
            awaitState(f, VoiceState.LISTENING)
            assertFalse(f.audio.running)
            awaitBlocked(engine)
            engine.release()
            awaitState(f, VoiceState.IDLE_KWS)
            assertTrue(f.audio.running)
        } finally { disposeBlocking(f, scope) }
    }

    @Test
    fun `cancel during system recognizer listening releases the episode`() = runBlocking {
        val engine = FakeTakeoverAsr("解釋天空的顏色", gated = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val f = Fixture(scope, customAsr = engine)
        try {
            f.voice.start()
            awaitState(f, VoiceState.IDLE_KWS)
            f.audio.feed(9, 0)
            awaitState(f, VoiceState.LISTENING)
            assertFalse(f.audio.running)
            awaitBlocked(engine)
            f.voice.cancelSession()
            awaitState(f, VoiceState.IDLE_KWS)
            assertTrue(f.audio.running)
            assertTrue(f.provider.requests.isEmpty())
            assertTrue(engine.cancelRequests >= 1)
        } finally { disposeBlocking(f, scope) }
    }

    @Test
    fun `system recognizer fatal error raises session error`() = runBlocking {
        val engine = FakeTakeoverAsr(
            listOf("解釋天空的顏色"),
            gated = true,
            error = IllegalStateException("system-asr-error:8"),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val f = Fixture(scope, customAsr = engine)
        try {
            f.voice.start()
            awaitState(f, VoiceState.IDLE_KWS)
            f.audio.feed(9, 0)
            awaitState(f, VoiceState.LISTENING)
            awaitBlocked(engine)
            engine.release()
            // 忙線類錯誤不可重試：服務報 ASR_RUNTIME_FAILED，與「沒聽到回到待喚醒」不同。
            awaitState(f, VoiceState.ERROR)
            assertTrue(f.provider.requests.isEmpty())
        } finally { disposeBlocking(f, scope) }
    }

    @Test
    fun `system recognizer follow up restores microphone then hands over again`() = runBlocking {
        val engine = FakeTakeoverAsr(listOf("解釋天空的顏色", "那第二首呢"), gated = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val f = Fixture(
            scope,
            chunks = flowOf(LlmChunk("回答。")),
            customAsr = engine,
            followUpSeconds = 3,
        )
        try {
            f.voice.start()
            awaitState(f, VoiceState.IDLE_KWS)
            f.audio.feed(9, 0)
            awaitState(f, VoiceState.LISTENING)
            awaitBlocked(engine)
            engine.release()
            awaitState(f, VoiceState.SPEAKING)

            // 第一輪回答播完進入 Follow Up：麥克風先回來給本機 VAD 偵測追問。
            awaitState(f, VoiceState.FOLLOW_UP)
            assertTrue(f.audio.running)
            f.audio.feed(1, 2_000)
            awaitState(f, VoiceState.LISTENING)
            // 追問命中：再次交棒給系統引擎，收第二句。
            assertFalse(f.audio.running)
            awaitBlocked(engine)
            engine.release()
            awaitState(f, VoiceState.SPEAKING)
            awaitRequests(f, 2)
            assertEquals(
                listOf("system", "user", "assistant", "user"),
                f.provider.requests[1].messages.map { it.role },
            )
            assertEquals(
                listOf("解釋天空的顏色", "回答。", "那第二首呢"),
                f.provider.requests[1].messages.drop(1).map { it.content },
            )
        } finally { disposeBlocking(f, scope) }
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

    @Test fun `a new wake word starts a new topic but a follow up keeps the last turn`() = runTest {
        val f = Fixture(backgroundScope, followUpSeconds = 10)
        try {
            // 第一輪由喚醒詞開啟。
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(VoiceState.FOLLOW_UP, f.voice.state.value)
            assertEquals(listOf("system", "user"), f.provider.requests[0].messages.map { it.role })

            // 同一輪的 Follow Up 視窗內直接追問，帶著上一輪的上下文。
            f.audio.feed(1, 3_000)
            runCurrent()
            assertEquals(VoiceState.LISTENING, f.voice.state.value)
            f.stdAsr.text = "那第二首呢"
            f.utterance(3_500)
            runCurrent()

            assertEquals(2, f.provider.requests.size)
            assertEquals(
                listOf("system", "user", "assistant", "user"),
                f.provider.requests[1].messages.map { it.role },
            )
            assertEquals(
                listOf("解釋天空的顏色", "回答。", "那第二首呢"),
                f.provider.requests[1].messages.drop(1).map { it.content },
            )

            // 新的喚醒詞是新話題的邊界，不帶入任何舊輪次。
            f.voice.cancelSession()
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)
            f.audio.feed(9, 10_000)
            runCurrent()
            f.stdAsr.text = "明天台北天氣如何"
            f.utterance(11_000)
            runCurrent()

            assertEquals(3, f.provider.requests.size)
            assertEquals(listOf("system", "user"), f.provider.requests[2].messages.map { it.role })
            assertEquals(
                listOf("明天台北天氣如何"),
                f.provider.requests[2].messages.drop(1).map { it.content },
            )
        } finally { dispose(f) }
    }

    /**
     * 朗讀到一半時發生音訊失敗，恢復被暫停的音樂必須排在停掉 TTS 之後，
     * 否則會先傳出音樂、才補上舊回答的最後一句。
     */
    @Test fun `audio failure stops speech before held music resumes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val music = FakeMusic(pauseResult = true)
        val f = Fixture(backgroundScope, chunks = flow {
            emit(LlmChunk("第一句。"))
            gate.await()
        }, music = music)
        var ttsStopsWhenMusicResumed = -1
        var stopsBeforeSpeaking = -1
        music.onResume = { ttsStopsWhenMusicResumed = f.output.stops }
        f.output.onSpeak = { stopsBeforeSpeaking = f.output.stops }
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(VoiceState.SPEAKING, f.voice.state.value)
            assertEquals(listOf("第一句。"), f.output.spoken)
            assertEquals(1, music.pauseCalls)
            assertTrue(stopsBeforeSpeaking >= 0)

            f.voice.onAudioFailure(com.va.voiceassistant.diagnostics.VoiceErrorCode.AUDIO_READ_STALLED, "test")
            runCurrent()

            assertEquals(1, music.resumeCalls)
            assertTrue(
                "恢復音樂時 TTS 尚未停止（朗讀前=$stopsBeforeSpeaking，恢復時=$ttsStopsWhenMusicResumed）",
                ttsStopsWhenMusicResumed > stopsBeforeSpeaking,
            )
        } finally {
            gate.complete(Unit)
            dispose(f)
        }
    }

    /** 音量指令必須在本機完成，不經過 LLM，也不影響音樂的暫停／恢復。 */
    @Test fun `volume commands run locally without calling the llm`() = runTest {
        val volume = FakeVolume()
        val f = Fixture(backgroundScope, text = "聲音小一點", volume = volume)
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(listOf(VolumeCommand.Down), volume.commands)
            assertEquals(listOf("音量已調到 8，共 15 格。"), f.output.spoken)
            assertTrue(f.provider.requests.isEmpty())
        } finally { dispose(f) }
    }

    /** 「音量調到50」帶數字，必須確認不會被更前面的提醒路由吞掉。 */
    @Test fun `a percentage volume request is routed locally`() = runTest {
        val volume = FakeVolume()
        val f = Fixture(backgroundScope, text = "音量調到50", volume = volume)
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(listOf(VolumeCommand.SetTo(50)), volume.commands)
            assertTrue(f.provider.requests.isEmpty())
        } finally { dispose(f) }
    }

    /** 關掉總開關後執行器不存在，音量指令應回到一般問答而不是默默執行。 */
    @Test fun `volume commands fall back to the llm when the executor is absent`() = runTest {
        val f = Fixture(backgroundScope, text = "音量調到50", volume = null)
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(1, f.provider.requests.size)
        } finally { dispose(f) }
    }

    /** 執行器丟出例外時必須明說失敗，不得把未發生的調整講成已調整。 */
    @Test fun `a failing volume executor is reported as a failure`() = runTest {
        val volume = FakeVolume(failure = SecurityException("missing MODIFY_AUDIO_SETTINGS"))
        val f = Fixture(backgroundScope, text = "音量調到50", volume = volume)
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(listOf(VolumeCommand.SetTo(50)), volume.commands)
            assertEquals(listOf("音量調整失敗，請稍後再試一次。"), f.output.spoken)
        } finally { dispose(f) }
    }

    /** 音量指令不該動到音樂的暫停狀態，因為它不是播放控制。 */
    @Test fun `volume commands do not disturb the music hold state`() = runTest {
        val music = FakeMusic(pauseResult = true)
        val volume = FakeVolume()
        val f = Fixture(backgroundScope, text = "聲音小一點", music = music, volume = volume)
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(1, music.pauseCalls)
            assertTrue(music.commands.isEmpty())
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

    @Test fun `transcript correction routes music search when direct parse misses`() = runTest {
        val f = Fixture(
            backgroundScope,
            text = "晴天很好聽嗎",
            music = FakeMusic(),
            corrector = FakeCorrector(CorrectedTranscript("播放晴天", CorrectedIntent.MUSIC_SEARCH, query = "晴天")),
        )
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(listOf(MusicCommand.SearchAndPlay("晴天")), f.music!!.commands)
            assertEquals(listOf("本機播放結果。"), f.output.spoken)
        } finally { dispose(f) }
    }

    @Test fun `transcript correction falls back to QA with corrected text`() = runTest {
        val f = Fixture(
            backgroundScope,
            text = "長根醫院在哪裡",
            corrector = FakeCorrector(CorrectedTranscript("長庚醫院在哪裡", CorrectedIntent.CHAT)),
        )
        try {
            startListening(f)
            f.utterance()
            runCurrent()
            assertEquals(1, f.provider.requests.size)
            assertEquals("長庚醫院在哪裡", f.provider.requests.single().messages.last().content)
            assertEquals(listOf("回答。"), f.output.spoken)
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

            f.voice.onAudioFailure(com.va.voiceassistant.diagnostics.VoiceErrorCode.AUDIO_READ_FAILED, "test")
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
            f.voice.onAudioFailure(com.va.voiceassistant.diagnostics.VoiceErrorCode.AUDIO_READ_FAILED, "first")
            runCurrent()
            assertEquals(VoiceState.IDLE_KWS, f.voice.state.value)

            f.voice.onAudioFailure(com.va.voiceassistant.diagnostics.VoiceErrorCode.AUDIO_READ_FAILED, "second")
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
            assertEquals(0, f.stdAsr.closes)
            assertFalse(f.stdAsr.cancelledDuringDecode)
            exit.countDown()
            withTimeout(5_000) { f.voice.awaitClosed() }
            assertEquals(1, f.stdAsr.closes)
            assertFalse(f.stdAsr.cancelledDuringDecode)
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

    /** 等系統引擎替身的 worker 真的擋上閘門（IO 執行緒內），之後 release／取消才不會錯過。 */
    private suspend fun awaitBlocked(engine: FakeTakeoverAsr) {
        withContext(Dispatchers.IO) {
            repeat(500) {
                if (engine.isAwaitingGate()) return@withContext
                Thread.sleep(10)
            }
        }
        check(engine.isAwaitingGate()) { "fake takeover engine never entered the gate" }
    }

    /** 真時間等待狀態轉換（供 runBlocking 場景使用；超時即失敗以免整個測試掛住）。 */
    private suspend fun awaitState(f: Fixture, expected: VoiceState) {
        withTimeout(10_000) { f.voice.state.first { it == expected } }
    }

    private suspend fun awaitRequests(f: Fixture, count: Int) {
        withTimeout(10_000) {
            while (f.provider.requests.size < count) delay(50)
        }
    }

    /** runBlocking 場景收尾：先解除閘門避免 worker 卡住，再關閉 orchestrator 與 scope。 */
    private suspend fun disposeBlocking(f: Fixture, scope: CoroutineScope) {
        f.voice.close()
        scope.cancel()
        withTimeout(5_000) { f.voice.awaitClosed() }
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
        val volume: FakeVolume? = null,
        decode: () -> Unit = {},
        followUpSeconds: Int = 0,
        corrector: TranscriptCorrector? = null,
        customAsr: SpeechRecognizerEngine? = null,
    ) {
        val audio = FakeAudio()
        val output = FakeOutput()
        val asr = customAsr ?: FakeAsr(text, decode)
        /** 需要操控 FakeAsr 既有欄位的測試使用；customAsr 時不可用。 */
        val stdAsr: FakeAsr
            get() = asr as FakeAsr
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
            volumeExecutor = volume,
            transcriptCorrector = corrector,
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

    private class FakeAsr(text: String, private val decode: () -> Unit) : SpeechRecognizerEngine {
        /** 可改寫，讓連續兩輪的逐字稿不同，用來驗證歷史有沒有被帶進下一次請求。 */
        var text = text
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

    /** 系統引擎替身：takesOverMicrophone 為真；每輪 finish() 可各給一筆文字、擋在閘門上或直接拋錯。 */
    private class FakeTakeoverAsr(
        texts: List<String>,
        private val gated: Boolean = false,
        private val error: Throwable? = null,
    ) : SpeechRecognizerEngine {
        constructor(text: String, gated: Boolean = false) : this(listOf(text), gated)

        override val modelId = "system/fake"
        override val takesOverMicrophone: Boolean get() = true
        private val gateLock = Any()
        private var nextGate: CountDownLatch? = null
        private val results = texts.toMutableList()

        /** finish() 真的返回時（IO 執行緒已出 blocker），讓測試等它完成再 runCurrent。 */
        var finished = CountDownLatch(1)
            private set
        @Volatile var cancelRequests = 0
            private set

        override fun beginSession() = Unit
        override fun accept(frame: AudioFrame): String? = null
        override fun finish(): AsrResult {
            val gate = synchronized(gateLock) {
                nextGate = if (gated) CountDownLatch(1) else null
                finished = CountDownLatch(1)
                nextGate
            }
            gate?.await(10, TimeUnit.SECONDS)
            val text = synchronized(gateLock) { results.removeFirstOrNull() ?: "" }
            try {
                error?.let { throw it }
                return AsrResult(text, 0, 1, 0.0, 1)
            } finally {
                finished.countDown()
            }
        }

        /** 模擬系統服務回報整句結果（只作用于當前阻塞中的那一輪）。 */
        fun release() = synchronized(gateLock) { nextGate?.countDown() }

        /** worker 已進入 finish() 且真的擋在閘門上；release／取消要先等到這一刻才不會錯過。 */
        fun isAwaitingGate(): Boolean = synchronized(gateLock) { nextGate?.count ?: 0 > 0 }

        override fun cancelActiveRequest() {
            cancelRequests += 1
            release()
        }
        override fun cancel() = cancelActiveRequest()
        override fun close() = Unit
    }

    private class FakeOutput : SpeechOutputEngine {
        val spoken = mutableListOf<String>()
        var stops = 0
        var onSpeak: () -> Unit = {}
        /** 讓 speak 停在朗讀中，用來觀察音樂恢復與 TTS 停止的先後。 */
        var speakGate: CompletableDeferred<Unit>? = null
        override suspend fun initialize() = TtsStatus(true)
        override suspend fun speak(text: String) {
            spoken += text
            onSpeak()
            speakGate?.await()
        }
        override fun stop() { stops++ }
        override fun close() = Unit
    }

    private class FakeProvider(private val chunks: Flow<LlmChunk>) : LlmProvider {
        val requests = mutableListOf<ChatRequest>()
        override suspend fun healthCheck() = ProviderHealth(true, "test", latencyMs = 0, message = "")
        override fun streamChat(request: ChatRequest): Flow<LlmChunk> { requests += request; return chunks }
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class FakeCorrector(private val result: CorrectedTranscript?) : TranscriptCorrector {
        override suspend fun correct(transcript: String, requestId: String) = result
    }

    private class FakeMusic(private val pauseResult: Boolean = false) : MusicActionExecutor {
        val commands = mutableListOf<MusicCommand>()
        var pauseCalls = 0
        var resumeCalls = 0
        /** 在恢復音樂的當下取樣，用來檢查 TTS 是否已經先被停止。 */
        var onResume: () -> Unit = {}
        override suspend fun execute(command: MusicCommand): MusicActionResult {
            commands += command
            return MusicActionResult("本機播放結果。")
        }
        override suspend fun pauseForVoice(): Boolean {
            pauseCalls++
            return pauseResult
        }
        override suspend fun resumeAfterVoice(): Boolean {
            onResume()
            resumeCalls++
            return true
        }
        override suspend fun stopForClose() = false
    }

    private class FakeVolume(
        private val result: VolumeActionResult = VolumeActionResult("音量已調到 8，共 15 格。", applied = true),
        private val failure: Throwable? = null,
    ) : VolumeActionExecutor {
        val commands = mutableListOf<VolumeCommand>()
        override suspend fun execute(command: VolumeCommand): VolumeActionResult {
            commands += command
            failure?.let { throw it }
            return result
        }
    }
}
