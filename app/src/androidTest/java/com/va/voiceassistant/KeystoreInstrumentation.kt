package com.va.voiceassistant

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.va.voiceassistant.kws.SherpaWakeWordEngine
import com.va.voiceassistant.music.MusicCabinController
import com.va.voiceassistant.music.MusicCommand
import com.va.voiceassistant.security.AndroidKeystoreSecretStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Device-only round trip; uses a synthetic value and deletes it before finishing. */
class KeystoreInstrumentation : Instrumentation() {
    private var mode: String = "keystore"
    private var musicQuery: String = ""

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        mode = arguments?.getString("mode").orEmpty().ifBlank { "keystore" }
        musicQuery = arguments?.getString("query").orEmpty()
        start()
    }

    override fun onStart() {
        if (mode == "wake_phrase") {
            testWakePhrase()
            return
        }
        if (mode == "musiccabin_connection") {
            testMusicCabinConnection()
            return
        }
        if (mode == "musiccabin_search") {
            testMusicCabinSearch()
            return
        }
        if (mode == "musiccabin_controls") {
            testMusicCabinControls()
            return
        }
        if (mode == "musiccabin_voice_interruption") {
            testMusicCabinVoiceInterruption()
            return
        }
        val results = Bundle()
        val store = AndroidKeystoreSecretStore(targetContext)
        val audioStore = AndroidKeystoreSecretStore(
            targetContext,
            AndroidKeystoreSecretStore.Scope.AUDIO,
        )
        val expected = "sk-device-instrumentation-only-1234".toCharArray()
        val audioExpected = "audio-device-instrumentation-only-5678".toCharArray()
        try {
            store.deleteApiKey()
            audioStore.deleteApiKey()
            store.saveApiKey(expected.copyOf())
            audioStore.saveApiKey(audioExpected.copyOf())
            val actual = checkNotNull(store.readApiKey())
            val actualAudio = checkNotNull(audioStore.readApiKey())
            try {
                check(actual.contentEquals(expected))
                check(store.maskedApiKey() == "••••1234")
                check(actualAudio.contentEquals(audioExpected))
                check(audioStore.maskedApiKey() == "••••5678")
                store.deleteApiKey()
                audioStore.deleteApiKey()
                check(store.readApiKey() == null)
                check(audioStore.readApiKey() == null)
                results.putString("keystore_round_trip", "PASS")
            } finally {
                actual.fill('\u0000')
                actualAudio.fill('\u0000')
            }
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            results.putString("keystore_round_trip", "FAIL:${error.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, results)
        } finally {
            expected.fill('\u0000')
            audioExpected.fill('\u0000')
            store.deleteApiKey()
            audioStore.deleteApiKey()
        }
    }

    private fun testWakePhrase() {
        val results = Bundle()
        try {
            SherpaWakeWordEngine(targetContext.assets, "小幫手").use { it.reset() }
            results.putString("wake_phrase", "PASS")
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            results.putString("wake_phrase", "FAIL:${error.javaClass.simpleName}:${error.message}")
            finish(Activity.RESULT_CANCELED, results)
        }
    }

    private fun testMusicCabinConnection() {
        val results = Bundle()
        try {
            val result = runBlocking {
                MusicCabinController(targetContext).execute(MusicCommand.Pause)
            }
            check(!result.message.startsWith("還沒連上 MusicCabin"))
            check(!result.message.startsWith("音樂 App 拒絕"))
            results.putString("musiccabin_connection", "PASS:${result.message}")
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            results.putString("musiccabin_connection", "FAIL:${error.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, results)
        }
    }

    private fun testMusicCabinSearch() {
        val results = Bundle()
        try {
            check(musicQuery.isNotBlank())
            val controller = MusicCabinController(targetContext)
            val result = runBlocking {
                val playback = controller.execute(MusicCommand.SearchAndPlay(musicQuery))
                if (playback.deferredMessage != null) {
                    controller.execute(MusicCommand.Pause)
                }
                playback
            }
            results.putString("musiccabin_search", result.message)
            finish(
                if (result.deferredMessage != null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                results,
            )
        } catch (error: Throwable) {
            results.putString("musiccabin_search", "FAIL:${error.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, results)
        }
    }

    private fun testMusicCabinControls() {
        val results = Bundle()
        val controller = MusicCabinController(targetContext)
        try {
            val responses = runBlocking {
                try {
                    listOf(
                        controller.execute(MusicCommand.Play),
                        controller.execute(MusicCommand.Next),
                        controller.execute(MusicCommand.Previous),
                    )
                } finally {
                    controller.execute(MusicCommand.Pause)
                }
            }
            check(responses.all { it.deferredMessage != null })
            results.putString("musiccabin_controls", "PASS")
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            results.putString("musiccabin_controls", "FAIL:${error.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, results)
        }
    }

    private fun testMusicCabinVoiceInterruption() {
        val results = Bundle()
        val controller = MusicCabinController(targetContext)
        var stage = "start-playback"
        try {
            runBlocking {
                try {
                    val initialPlayback = controller.execute(MusicCommand.Play)
                    check(initialPlayback.deferredMessage != null) { initialPlayback.message }
                    stage = "pause-for-wake"
                    check(controller.pauseForVoice()) { "pauseForVoice returned false" }
                    delay(600)
                    stage = "confirm-paused"
                    val paused = controller.execute(MusicCommand.Pause)
                    check(paused.message == "音樂已經暫停了。") { paused.message }
                    stage = "avoid-pausing-twice"
                    check(!controller.pauseForVoice()) { "paused session was reported as playing" }
                    stage = "next-while-paused"
                    val next = controller.execute(MusicCommand.Next)
                    check(next.deferredMessage != null) { next.message }
                    controller.pauseForVoice()
                    delay(300)
                    val pausedAfterNext = controller.execute(MusicCommand.Pause)
                    check(pausedAfterNext.message == "音樂已經暫停了。") { pausedAfterNext.message }
                    stage = "previous-while-paused"
                    val previous = controller.execute(MusicCommand.Previous)
                    check(previous.deferredMessage != null) { previous.message }
                    controller.pauseForVoice()
                    delay(300)
                    val pausedAfterPrevious = controller.execute(MusicCommand.Pause)
                    check(pausedAfterPrevious.message == "音樂已經暫停了。") { pausedAfterPrevious.message }
                    stage = "resume-after-voice"
                    check(controller.resumeAfterVoice()) { "resumeAfterVoice returned false" }
                    delay(300)
                    stage = "confirm-resumed"
                    val resumed = controller.execute(MusicCommand.Play)
                    check(resumed.deferredMessage != null) { resumed.message }
                } finally {
                    controller.execute(MusicCommand.Pause)
                }
            }
            results.putString("musiccabin_voice_interruption", "PASS:playing-paused-resumed-paused")
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            results.putString("musiccabin_voice_interruption", "FAIL:$stage:${error.javaClass.simpleName}:${error.message}")
            finish(Activity.RESULT_CANCELED, results)
        }
    }
}
