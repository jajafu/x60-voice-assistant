package com.va.voiceassistant.reminder

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmAnnouncerTest {
    @Test fun `muting before initialization blocks announcements until resumed`() = runTest {
        var spoken = 0
        val announcer = AlarmAnnouncer({}, {}, {})
        announcer.pauseForVoice()
        val job = launch { announcer.run(30_000) { spoken++ } }
        runCurrent()
        advanceTimeBy(90_000)
        assertEquals(0, spoken)
        announcer.resumeAfterVoice()
        runCurrent()
        assertEquals(1, spoken)
        job.cancelAndJoin()
    }

    @Test fun `muting interrupts current speech and prevents later speech or sound`() = runTest {
        var spoken = 0
        var resumes = 0
        var stops = 0
        val speech = CompletableDeferred<Unit>()
        val announcer = AlarmAnnouncer({}, { resumes++ }, {
            stops++
            speech.completeExceptionally(IllegalStateException("stopped"))
        })
        val job = launch { announcer.run(30_000) { spoken++; speech.await() } }
        runCurrent()
        assertEquals(1, spoken)
        announcer.pauseForVoice()
        runCurrent()
        advanceTimeBy(90_000)
        runCurrent()
        assertEquals(1, stops)
        assertEquals(1, spoken)
        assertEquals(0, resumes)
        announcer.resumeAfterVoice()
        runCurrent()
        assertEquals(2, spoken)
        job.cancelAndJoin()
    }

    @Test fun `service cancellation does not resume alarm sound`() = runTest {
        var resumes = 0
        val announcer = AlarmAnnouncer({}, { resumes++ }, {})
        val job = launch { announcer.run(30_000) { CompletableDeferred<Unit>().await() } }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(0, resumes)
    }
}
