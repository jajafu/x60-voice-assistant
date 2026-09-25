package com.jajafu.x60assistant.music

sealed interface MusicCommand {
    data object Open : MusicCommand
    data object Play : MusicCommand
    data object Pause : MusicCommand
    data object Next : MusicCommand
    data object Previous : MusicCommand
    data object SaveCurrent : MusicCommand
    data object RemoveCurrent : MusicCommand
    data object PlayFavorites : MusicCommand
    data class SearchAndPlay(val query: String) : MusicCommand
}

/** Only explicit, low-risk requests for the selected music app are handled locally. */
object MusicCommandRouter {
    private val politePrefix = Regex("^(?:(?:(?:請|请|麻煩|麻烦)(?:你)?|幫我|帮我|給我|给我|替我)\\s*)+")
    private val punctuation = Regex("[，。！!？?、,]")
    private val songPrefixes = listOf(
        "我想要聽", "我想要听", "我想聽", "我想听", "我要聽", "我要听",
        "我想放", "我要放", "想聽", "想听", "想放",
        "放一首", "放首", "來一首", "来一首", "來首", "来首",
        "播放", "撥放", "拨放", "播", "放", "聽", "听",
    )

    fun parse(transcript: String, selectedAppName: String = "MusicCabin"): MusicCommand? {
        val phrase = transcript.trim().replace(punctuation, "").replace(politePrefix, "").trim()
        if (phrase.isBlank() || phrase.contains("不要") || phrase.contains("別") ||
            phrase.contains("别") || phrase.endsWith("嗎") || phrase.endsWith("吗")
        ) return null

        val selectedNamePattern = selectedAppName.trim().takeIf { it.isNotBlank() }?.let(Regex::escape)
        val aliases = listOfNotNull("music\\s*cabin", "音樂小屋", "音乐小屋", "音樂App", "音乐App", "音樂程式", "音乐程序", "播放器", selectedNamePattern)
            .distinct().joinToString("|")
        val appName = Regex("(?:$aliases)", RegexOption.IGNORE_CASE)
        val normalized = phrase.replace(appName, "MusicApp").trim()
        if (normalized.matches(Regex("^(打開|打开|開啟|开启|啟動|启动)\\s*MusicApp$", RegexOption.IGNORE_CASE))) {
            return MusicCommand.Open
        }
        val withoutApp = normalized.replace(Regex("(?i)(?:在\\s*)?MusicApp(?:裡|里|上)?\\s*"), "")
            .replace(politePrefix, "").trim()
        return when (withoutApp) {
            "取消收藏", "取消收藏這首歌", "取消收藏这首歌", "取消這首歌的收藏", "取消这首歌的收藏",
            "移除收藏", "把這首歌取消收藏", "把这首歌取消收藏" -> MusicCommand.RemoveCurrent
            "收藏", "收藏這首歌", "收藏这首歌", "收藏目前這首歌", "收藏目前这首歌",
            "把這首歌收藏", "把这首歌收藏", "將這首歌加入收藏", "将这首歌加入收藏" -> MusicCommand.SaveCurrent
            "播放音樂收藏", "撥放音樂收藏", "播放音乐收藏", "播放收藏", "撥放收藏",
            "播放收藏歌曲", "播放收藏的歌", "播放喜愛", "播放喜爱", "播放喜愛歌曲", "撥放喜愛歌曲",
            "播放喜歡的歌", "播放喜欢的歌" -> MusicCommand.PlayFavorites
            "播放", "撥放", "拨放", "播放音樂", "播放音乐", "撥放音樂", "拨放音乐",
            "播音樂", "播音乐", "放音樂", "放音乐", "放歌", "聽歌", "听歌",
            "聽音樂", "听音乐", "想聽音樂", "想听音乐", "我想聽音樂", "我想听音乐",
            "我要聽音樂", "我要听音乐", "放一首歌", "放首歌", "來首歌", "来首歌",
            "繼續播放", "继续播放", "恢復播放", "恢复播放" -> MusicCommand.Play
            "暫停", "暂停", "暫停音樂", "暂停音乐", "暫停播放", "暂停播放",
            "停止音樂", "停止音乐", "停止播放", "關掉音樂", "关掉音乐",
            "關閉音樂", "关闭音乐", "把音樂關掉", "把音乐关掉",
            "先暫停", "先暂停", "停一下" -> MusicCommand.Pause
            "下一首", "下一首歌", "切下一首", "切到下一首", "換下一首", "换下一首",
            "跳下一首" -> MusicCommand.Next
            "上一首", "上一首歌", "切上一首", "切到上一首", "回上一首",
            "換上一首", "换上一首" -> MusicCommand.Previous
            else -> searchCommand(withoutApp)
        }
    }

    private fun searchCommand(phrase: String): MusicCommand? {
        val prefix = songPrefixes.firstOrNull(phrase::startsWith) ?: return null
        val query = phrase.removePrefix(prefix).trim()
            .removePrefix("一下").removePrefix("一首").trim()
            .removeSuffix("這首歌").removeSuffix("这首歌").trim()
        if (query.length !in 2..100 || query.contains("怎麼") || query.contains("怎么") ||
            query.contains("為什麼") || query.contains("为什么")
        ) return null
        return MusicCommand.SearchAndPlay(query.replaceFirst("的", " ").trim())
    }
}
