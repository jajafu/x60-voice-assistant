package com.jajafu.x60assistant.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicCommandRouterTest {
    @Test fun `routes selected music app name`() {
        assertEquals(MusicCommand.Open, MusicCommandRouter.parse("打開 Spotify", "Spotify"))
    }

    @Test fun `routes direct MusicCabin controls`() {
        assertEquals(MusicCommand.Open, MusicCommandRouter.parse("請打開 MusicCabin"))
        assertEquals(MusicCommand.Play, MusicCommandRouter.parse("播放音樂"))
        assertEquals(MusicCommand.Pause, MusicCommandRouter.parse("暫停音樂"))
        assertEquals(MusicCommand.Next, MusicCommandRouter.parse("下一首"))
        assertEquals(MusicCommand.Previous, MusicCommandRouter.parse("上一首歌"))
    }

    @Test fun `extracts a song request without sending it to the LLM`() {
        assertEquals(
            MusicCommand.SearchAndPlay("周杰倫 晴天"),
            MusicCommandRouter.parse("請在 MusicCabin 播放周杰倫的晴天"),
        )
    }

    @Test fun `understands common spoken requests for a song`() {
        val requests = listOf(
            "幫我放晴天",
            "請幫我放晴天",
            "麻煩你放一下晴天",
            "給我播晴天",
            "我要聽晴天",
            "想聽晴天",
            "我想聽晴天",
            "我想聽一下晴天",
            "放一首晴天",
            "來首晴天",
            "聽晴天",
            "幫我撥放晴天",
            "請在 MusicCabin 幫我放晴天這首歌",
        )
        requests.forEach { request ->
            assertEquals(request, MusicCommand.SearchAndPlay("晴天"), MusicCommandRouter.parse(request))
        }
        assertEquals(
            MusicCommand.SearchAndPlay("周杰倫 晴天"),
            MusicCommandRouter.parse("請幫我播放一下周杰倫的晴天"),
        )
    }

    @Test fun `understands spoken media controls`() {
        assertEquals(MusicCommand.Play, MusicCommandRouter.parse("幫我放一首歌"))
        assertEquals(MusicCommand.Play, MusicCommandRouter.parse("我想聽音樂"))
        assertEquals(MusicCommand.Pause, MusicCommandRouter.parse("麻煩你停一下"))
        assertEquals(MusicCommand.Pause, MusicCommandRouter.parse("把音樂關掉"))
        assertEquals(MusicCommand.Pause, MusicCommandRouter.parse("關閉音樂"))
        assertEquals(MusicCommand.Next, MusicCommandRouter.parse("幫我換下一首"))
        assertEquals(MusicCommand.Previous, MusicCommandRouter.parse("請回上一首"))
    }

    @Test fun `routes MusicCabin favorite requests without treating them as song searches`() {
        assertEquals(MusicCommand.SaveCurrent, MusicCommandRouter.parse("收藏"))
        assertEquals(MusicCommand.SaveCurrent, MusicCommandRouter.parse("請收藏這首歌"))
        assertEquals(MusicCommand.PlayFavorites, MusicCommandRouter.parse("播放音樂收藏"))
        assertEquals(MusicCommand.PlayFavorites, MusicCommandRouter.parse("請撥放音樂收藏"))
        assertEquals(MusicCommand.PlayFavorites, MusicCommandRouter.parse("播放喜歡的歌"))
        assertEquals(MusicCommand.PlayFavorites, MusicCommandRouter.parse("撥放喜愛歌曲"))
        assertEquals(MusicCommand.RemoveCurrent, MusicCommandRouter.parse("取消收藏"))
        assertEquals(MusicCommand.RemoveCurrent, MusicCommandRouter.parse("請取消這首歌的收藏"))
    }

    @Test fun `rejects questions and negated requests`() {
        assertNull(MusicCommandRouter.parse("不要播放音樂"))
        assertNull(MusicCommandRouter.parse("為什麼要播放音樂"))
        assertNull(MusicCommandRouter.parse("可以播放音樂嗎？"))
        assertNull(MusicCommandRouter.parse("MusicCabin 是什麼？"))
    }

    @Test fun `routes current song questions to local metadata lookup`() {
        val questions = listOf(
            "這是什麼歌？", "這首什麼歌？", "這首甚麼歌？",
            "這首歌叫什麼名字？", "這首歌的歌手是誰？",
            "請問現在播放的是什麼歌？", "这首歌叫什么名字", "這首歌叫什麼名字呢",
            "MusicCabin 這首歌的歌名是什麼呀", "誰唱這首歌？",
        )
        questions.forEach { question ->
            assertEquals(question, MusicCommand.NowPlaying, MusicCommandRouter.parse(question))
        }
        assertNull(MusicCommandRouter.parse("這首歌為什麼好聽？"))
    }

    @Test fun `routes single song repeat commands without treating them as song searches`() {
        val enable = listOf(
            "單曲循環", "請幫我開啟單曲循環", "重複播放這首歌",
            "MusicCabin 把這首歌設成單曲循環", "单曲循环",
        )
        enable.forEach { request ->
            assertEquals(request, MusicCommand.RepeatOne, MusicCommandRouter.parse(request))
        }
        val disable = listOf(
            "取消單曲循環", "請不要單曲循環", "關掉單曲循環",
            "不要重複播放這首歌", "取消单曲循环",
        )
        disable.forEach { request ->
            assertEquals(request, MusicCommand.RepeatOff, MusicCommandRouter.parse(request))
        }
        assertNull(MusicCommandRouter.parse("可以單曲循環嗎？"))
    }
}
