package com.jajafu.x60assistant.navigation

import com.jajafu.x60assistant.data.AssistantSettings

sealed interface NavigationCommand {
    /** 開啟地圖到指定目的地的路線預覽頁。 */
    data class Navigate(val destination: String) : NavigationCommand
}

/**
 * 只處理明確帶有目的地的本機導航指令，不呼叫 LLM。
 *
 * 觸發詞必須後面接著目的地；單純說「導航」不算，避免喚醒後誤開地圖。
 * 繁簡、ASR 常見誤字與否定句的處理沿用 [com.jajafu.x60assistant.music.MusicCommandRouter] 的做法。
 */
object NavigationCommandRouter {
    /** 喚醒詞與客套話可能疊在一起（例：「小助手，請幫我導航到…」），剝除最多跑幾輪。 */
    private const val MAX_STRIP_PASSES = 4

    private val politePrefix = Regex("^(?:(?:(?:請問|请问|請|请|麻煩|麻烦)(?:你)?|幫我|帮我|給我|给我|替我)\\s*)+")
    private val punctuation = Regex("[，。！!？?、,;；]")

    private val navigatePrefixes = listOf(
        "導航到", "导航到", "導航去", "导航去", "導航至", "导航至",
        "帶我去", "带我去", "帶我到", "带我到", "帶我去到", "带我去到",
        "開地圖到", "开地图到", "開導航到", "开导航到",
        "我要去", "我要到", "想去", "要到", "前往", "去到",
        "導航", "导航",
    )

    private val appAliases = listOf("google\\s*maps", "google\\s*地圖", "google\\s*地图", "谷歌地圖", "谷歌地图")
        .distinct()

    /** 允許使用者指定要開哪個地圖 App，例如「用Google地圖導航到台北101」。 */
    private val appNamePattern = Regex(
        "(?:(?:用|使用|打開|打开|開啟|开启)\\s*)?" +
            "(?:" + appAliases.joinToString("|") + ")" +
            "(?:地圖|地图)?",
        RegexOption.IGNORE_CASE,
    )

    private val questionMarkers = listOf("怎麼", "怎么", "為什麼", "为什么", "嗎", "吗", "什麼", "什么")
    private val negativeMarkers = listOf("不要", "別", "别", "不用")

    fun parse(
        transcript: String,
        wakePhrase: String = AssistantSettings.DEFAULT_WAKE_PHRASE,
    ): NavigationCommand? {
        val phrase = transcript.trim().replace(punctuation, "").trim()
        if (phrase.isBlank()) return null

        // 喚醒詞可能仍留在逐字稿裡（使用者說「小助手，導航到台北101」），先把它與客套話剝掉。
        val wakePrefix = wakePhrase.trim().takeIf { it.isNotBlank() }
            ?.let { Regex("^${Regex.escape(it)}\\s*") }
        var stripped = phrase
        repeat(MAX_STRIP_PASSES) {
            val next = stripped
                .let { current -> if (wakePrefix != null) current.replace(wakePrefix, "") else current }
                .replace(politePrefix, "")
                .trim()
            if (next == stripped) return@repeat
            stripped = next
        }
        val normalized = stripped
            .replace(appNamePattern, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return null
        if (questionMarkers.any { normalized.contains(it) }) return null
        if (negativeMarkers.any { normalized.contains(it) }) return null

        val prefix = navigatePrefixes.firstOrNull(normalized::startsWith) ?: return null
        val destination = normalized.removePrefix(prefix).trim()
            .removePrefix("到").removePrefix("去").trim()

        // 目的地太短多半是喚醒後的半句或 ASR 誤字，不啟動地圖。
        if (destination.length !in 2..60) return null

        return NavigationCommand.Navigate(destination)
    }
}
