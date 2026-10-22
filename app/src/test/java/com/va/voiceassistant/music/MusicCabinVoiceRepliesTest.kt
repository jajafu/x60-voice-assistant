package com.va.voiceassistant.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCabinVoiceRepliesTest {
    private fun ok(
        status: String,
        title: String? = null,
        artist: String? = null,
        mode: String? = null,
        message: String = "",
        artistMatched: Boolean = true,
        volume: Float? = null,
    ) = VoiceControlResult(
        ok = true,
        status = status,
        title = title,
        artist = artist,
        mode = mode,
        message = message,
        artistMatched = artistMatched,
        volume = volume,
    )

    private fun failed(status: String, message: String = "") = VoiceControlResult(
        ok = false,
        status = status,
        title = null,
        artist = null,
        mode = null,
        message = message,
    )

    @Test fun `maps voice commands to channel requests`() {
        assertEquals(
            "play_query" to "周杰倫 晴天",
            MusicCabinVoiceControl.requestFor(MusicCommand.SearchAndPlay("周杰倫 晴天")),
        )
        assertEquals(
            "play_artist" to "周杰倫",
            MusicCabinVoiceControl.requestFor(MusicCommand.PlayArtist("周杰倫")),
        )
        assertEquals("play_liked" to null, MusicCabinVoiceControl.requestFor(MusicCommand.PlayFavorites))
        assertEquals("repeat_one" to null, MusicCabinVoiceControl.requestFor(MusicCommand.RepeatOne))
        assertEquals("repeat_state" to null, MusicCabinVoiceControl.requestFor(MusicCommand.RepeatState))
        assertEquals("volume_up" to null, MusicCabinVoiceControl.requestFor(MusicCommand.VolumeUp))
        assertEquals("volume_down" to null, MusicCabinVoiceControl.requestFor(MusicCommand.VolumeDown))
        assertEquals("volume_set" to "80", MusicCabinVoiceControl.requestFor(MusicCommand.VolumeSet(80)))
        assertEquals("volume_get" to null, MusicCabinVoiceControl.requestFor(MusicCommand.VolumeGet))
    }

    @Test fun `successful song playback names the track and confirms`() {
        val result = MusicCabinVoiceReplies.forCommand(
            MusicCommand.SearchAndPlay("周杰倫 晴天"),
            ok("played", title = "晴天", artist = "周杰倫"),
            "MusicCabin",
        )
        assertEquals("好的，正在播放周杰倫的《晴天》。", result.message)
        assertTrue(result.confirmedPlayback)
    }

    @Test fun `mismatched artist speaks the channel message instead of claiming success`() {
        val result = MusicCabinVoiceReplies.forCommand(
            MusicCommand.PlayArtist("周杰倫"),
            ok("played", title = "吻別", artist = "張學友", message = "沒找到周杰倫，播最相關的吻別。", artistMatched = false),
            "MusicCabin",
        )
        assertEquals("沒找到周杰倫，播最相關的吻別。", result.message)
        assertFalse(result.confirmedPlayback)
    }

    @Test fun `mismatched artist without message names the fallback track`() {
        val result = MusicCabinVoiceReplies.forCommand(
            MusicCommand.PlayArtist("周杰倫"),
            ok("played", title = "吻別", artist = "張學友", artistMatched = false),
            "MusicCabin",
        )
        assertEquals("沒找到周杰倫的歌，播放最相近的張學友的《吻別》。", result.message)
        assertFalse(result.confirmedPlayback)
    }

    @Test fun `now playing without message uses the returned title`() {
        assertEquals(
            "這首歌是周杰倫唱的晴天。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.NowPlaying,
                ok("now_playing", title = "晴天", artist = "周杰倫"),
                "MusicCabin",
            ).message,
        )
    }

    @Test fun `unknown repeat mode does not claim a specific state`() {
        assertEquals(
            "已送出全部循環設定，請到 MusicCabin 確認結果。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.RepeatAll, ok("repeat"), "MusicCabin",
            ).message,
        )
        assertEquals(
            "查不到目前的循環模式，請到 MusicCabin 確認。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.RepeatState, ok("repeat"), "MusicCabin",
            ).message,
        )
    }

    @Test fun `resume from empty queue reports the favorites fallback as favorites`() {
        val result = MusicCabinVoiceReplies.forCommand(
            MusicCommand.Play,
            ok("played", title = "晴天", artist = "周杰倫"),
            "MusicCabin",
        )
        assertEquals("好的，正在播放喜愛歌曲：周杰倫的《晴天》。", result.message)
    }

    @Test fun `pause and repeat replies use fixed wording`() {
        assertEquals(
            "好的，已暫停音樂。",
            MusicCabinVoiceReplies.forCommand(MusicCommand.Pause, ok("paused"), "MusicCabin").message,
        )
        assertEquals(
            "已開啟全部循環。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.RepeatAll, ok("repeat", mode = "all"), "MusicCabin",
            ).message,
        )
        assertEquals(
            "目前是單曲循環。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.RepeatState, ok("repeat", mode = "one"), "MusicCabin",
            ).message,
        )
        assertEquals(
            "已經收藏過了",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.SaveCurrent, ok("already_liked", title = "晴天"), "MusicCabin",
            ).message,
        )
    }

    @Test fun `old app without channel prompts an update`() {
        assertEquals(
            "MusicCabin 太舊沒有語音控制，請更新 MusicCabin 後再試。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.Play,
                failed("no_receiver"),
                "MusicCabin",
            ).message,
        )
        assertEquals(
            "MusicCabin 太舊沒有語音控制，請更新 MusicCabin 後再試。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.SearchAndPlay("晴天"),
                failed("timeout"),
                "MusicCabin",
            ).message,
        )
    }

    @Test fun `failures speak the channel message and denials guide to settings`() {
        assertEquals(
            "歌名找不到。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.SearchAndPlay("不存在的歌"),
                failed("not_found", "歌名找不到。"),
                "MusicCabin",
            ).message,
        )
        assertEquals(
            "MusicCabin 關掉了外部播放控制，請到音樂 App 的設定開啟語音控制。",
            MusicCabinVoiceReplies.forCommand(MusicCommand.Pause, failed("denied"), "MusicCabin").message,
        )
        assertEquals(
            "要改收藏請先到MusicCabin的隱私設定開啟外部收藏。",
            MusicCabinVoiceReplies.forCommand(MusicCommand.SaveCurrent, failed("denied"), "MusicCabin").message,
        )
    }

    @Test fun `volume replies speak the channel message and fall back to the returned level`() {
        // 對方給的句子已含套用後的百分比，直接念，不自己重算。
        assertEquals(
            "音量已調到 80%。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.VolumeSet(80),
                ok("volume", message = "音量已調到 80%。", volume = 0.8f),
                "MusicCabin",
            ).message,
        )
        // 對方沒給說明時用回傳的 0～1 音量自己組一句，不假稱已調到目標。
        assertEquals(
            "音樂音量現在是 55%。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.VolumeDown,
                ok("volume", volume = 0.55f),
                "MusicCabin",
            ).message,
        )
        assertEquals(
            "音樂音量現在是 100%。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.VolumeGet,
                ok("volume", volume = 1f),
                "MusicCabin",
            ).message,
        )
    }

    @Test fun `volume replies without message or level do not claim success`() {
        assertEquals(
            "已送出音量指令，請到 MusicCabin 確認結果。",
            MusicCabinVoiceReplies.forCommand(MusicCommand.VolumeUp, ok("volume"), "MusicCabin").message,
        )
        // 查詢句的兜底措辭不能說成「已送出指令」。
        assertEquals(
            "查不到目前的音樂音量，請到 MusicCabin 確認。",
            MusicCabinVoiceReplies.forCommand(MusicCommand.VolumeGet, ok("volume"), "MusicCabin").message,
        )
    }

    @Test fun `volume replies ignore out-of-range or non-finite levels`() {
        // 合約是 0～1；來了 1.5 或 NaN 就不組句子，改說已送出，不報錯誤的百分比。
        assertEquals(
            "已送出音量指令，請到 MusicCabin 確認結果。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.VolumeUp, ok("volume", volume = 1.5f), "MusicCabin",
            ).message,
        )
        assertEquals(
            "已送出音量指令，請到 MusicCabin 確認結果。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.VolumeDown, ok("volume", volume = Float.NaN), "MusicCabin",
            ).message,
        )
    }

    @Test fun `volume denials guide to the voice control setting`() {
        assertEquals(
            "MusicCabin 關掉了外部播放控制，請到音樂 App 的設定開啟語音控制。",
            MusicCabinVoiceReplies.forCommand(
                MusicCommand.VolumeUp, failed("denied"), "MusicCabin",
            ).message,
        )
    }
}
