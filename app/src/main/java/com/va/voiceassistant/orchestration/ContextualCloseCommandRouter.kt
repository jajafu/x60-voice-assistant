package com.va.voiceassistant.orchestration

/** Recognizes only explicit close-only phrases so unrelated requests still reach their normal router. */
object ContextualCloseCommandRouter {
    private val punctuation = Regex("[，,。！？!?、]")
    private val politePrefix = Regex("^(?:(?:請|请|麻煩|麻烦)(?:你)?|幫我|帮我)\\s*")
    private val directCommands = setOf(
        "關閉", "关闭", "關掉", "关掉", "停止", "停掉",
        "關閉它", "关闭它", "關掉它", "关掉它",
        "關閉鬧鐘", "关闭闹钟", "關掉鬧鐘", "关掉闹钟", "停止鬧鐘", "停止闹钟",
        "關閉音樂", "关闭音乐", "關掉音樂", "关掉音乐", "停止音樂", "停止音乐",
        "停止播放", "關閉播放", "关闭播放",
        "把鬧鐘關掉", "把闹钟关掉", "把音樂關掉", "把音乐关掉",
    )

    fun matches(transcript: String): Boolean {
        val normalized = transcript.trim()
            .replace(punctuation, "")
            .replace(politePrefix, "")
            .replace(Regex("\\s+"), "")
        return normalized in directCommands
    }
}
