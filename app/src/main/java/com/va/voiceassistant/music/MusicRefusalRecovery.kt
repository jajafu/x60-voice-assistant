package com.va.voiceassistant.music

import java.text.Normalizer
import java.util.Locale

sealed interface MusicRefusalRecovery {
    data class SearchAndPlay(val query: String) : MusicRefusalRecovery
    data object AskForTarget : MusicRefusalRecovery
}

/** Uses a music refusal as a second chance to recover the user's playback target. */
object MusicRefusalRecoveryRouter {
    private val question = Regex("[?？]|(?:嗎|吗)$|是誰|是谁|是什麼|是什么|為什麼|为什么|怎麼|怎么|如何|想知道|介紹|介绍")
    private val playbackStart = Regex(
        "^(?:請|请|麻煩|麻烦|你|可以)?(?:幫我|帮我|給我|给我|讓我|让我|我要|我想要|我想|想|要)?" +
            "(?:聽|听|播放|播|放|來首|来首)",
    )
    private val searchStart = Regex("^(?:請|请|幫我|帮我|我要|我想)?(?:找|搜尋|搜索).*(?:歌|音樂|音乐)")
    private val refusal = Regex(
        "(?:無法|无法|不能|沒辦法|没办法|沒有辦法|没有办法|不支援|不支持|不具備|不具备|未提供|做不到)" +
            ".{0,32}(?:播放|放歌|播歌|點播|点播|音樂|音乐)" +
            "|(?:沒有|没有).{0,20}(?:播放|放歌|播歌|音樂|音乐).{0,16}(?:功能|能力)" +
            "|(?:can't|cannot|unable to|don't have the ability to).{0,40}(?:play|stream).{0,20}(?:music|song)",
        RegexOption.IGNORE_CASE,
    )
    private val quotedTarget = Regex("[「『《\"“]([^」』》\"”]{2,80})[」』》\"”]")
    private val suggestedSearch = Regex("(?:搜尋|搜索|查找|找)\\s*([^，。！？!?；;]{2,80})")
    private val mentionedPlayback = Regex("(?:播放|播|放)\\s*([^，。！？!?；;]{2,80}?(?:的歌|的歌曲))")
    private val requestTarget = Regex("(?:聽|听|播放|播|放|找|搜尋|搜索)(?:一下|一首|首)?\\s*(.{2,100})")
    private val genericTargets = setOf(
        "音樂", "音乐", "歌", "歌曲", "歌手", "這首歌", "这首歌", "你喜歡的歌", "您喜歡的歌",
        "youtube", "spotify", "kkbox", "musiccabin",
    )

    fun mightBePlaybackRequest(userText: String): Boolean {
        val compact = canonical(userText).replace(Regex("\\s+"), "")
        if (compact.isBlank() || question.containsMatchIn(compact) ||
            compact.contains("不要") || compact.contains("別") || compact.contains("别")
        ) return false
        return playbackStart.containsMatchIn(compact) || searchStart.containsMatchIn(compact)
    }

    fun recover(userText: String, llmAnswer: String): MusicRefusalRecovery? {
        if (!mightBePlaybackRequest(userText)) return null
        val answer = canonical(llmAnswer)
        if (!refusal.containsMatchIn(answer)) return null

        val corrected = quotedTarget.findAll(answer)
            .mapNotNull { cleanTarget(it.groupValues[1]) }
            .firstOrNull()
            ?: suggestedSearch.findAll(answer)
                .mapNotNull { cleanTarget(it.groupValues[1]) }
                .firstOrNull()
            ?: mentionedPlayback.findAll(answer)
                .mapNotNull { cleanTarget(it.groupValues[1]) }
                .firstOrNull()
        val fromRequest = requestTarget.find(canonical(userText))
            ?.groupValues?.get(1)?.let(::cleanTarget)
        return (corrected ?: fromRequest)?.let { MusicRefusalRecovery.SearchAndPlay(it) }
            ?: MusicRefusalRecovery.AskForTarget
    }

    private fun cleanTarget(raw: String): String? {
        val value = canonical(raw)
            .trim(' ', '「', '」', '『', '』', '《', '》', '"', '“', '”', '，', '。', ',', '.', '！', '!')
            .replace(Regex("^(?:請|请|幫我|帮我|我要|我想要|我想|想)?(?:聽|听|播放|播|放|找|搜尋|搜索)(?:一下|一首|首)?\\s*"), "")
            .replace(Regex("(?:的歌|的歌曲|的音樂|的音乐|這首歌|这首歌)$"), "")
            .replace(Regex("[喔哦啊啦呢]$"), "")
            .trim(' ', '「', '」', '『', '』', '《', '》', '"', '“', '”', '，', '。', ',', '.', '！', '!')
            .replace(Regex("\\s+"), " ")
        if (value.length !in 2..80 || value.lowercase(Locale.ROOT) in genericTargets ||
            value.contains("可以去") || value.contains("你可以") || value.contains("我無法") ||
            value.contains("我无法") || value.contains("沒有辦法") || value.contains("没有办法") ||
            value.contains("功能") || value.contains("能力") ||
            question.containsMatchIn(value)
        ) return null
        return value
    }

    private fun canonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace("撥放", "播放")
        .replace("拨放", "播放")
        .trim()
}
