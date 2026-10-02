package com.jajafu.x60assistant.volume

import com.jajafu.x60assistant.data.AssistantSettings

/**
 * 只在本機解析明確的音量指令，不呼叫 LLM。
 *
 * 喚醒詞與客套話的剝除、繁簡與 ASR 常見誤字、否定句與疑問句的拒絕，
 * 沿用 [com.jajafu.x60assistant.navigation.NavigationCommandRouter] 的做法，
 * 避免「什麼時候音量太大」「不要調小音量」這類句子誤觸手機操作。
 */
object VolumeCommandRouter {
    /** 喚醒詞與客套話可能疊在一起（例：「小助手，請幫我音量調小一點」），剝除最多跑幾輪。 */
    private const val MAX_STRIP_PASSES = 4

    private val politePrefix = Regex("^(?:(?:(?:請問|请问|請|请|麻煩|麻烦)(?:你)?|幫我|帮我|給我|给我|替我)\\s*)+")
    private val punctuation = Regex("[，。！!？?、,;；\\s]")

    /** 出現這些詞就不是在下指令，而是在問問題。 */
    private val questionMarkers = listOf(
        "怎麼", "怎么", "為什麼", "为什么", "嗎", "吗", "什麼", "什么", "如何", "多少",
    )

    /** 否定句不執行：目前不支援「取消調高音量」這類反向操作。 */
    private val negativeMarkers = listOf("不要", "別", "别", "不用", "不必", "先不")

    /**
     * 談的是別的東西就不碰手機音量：語音只處理音量與聲音，亮度、螢幕亮度不納入。
     * 有音量名詞也一樣拒絕，避免「聲音和亮度都調大」這種混句誤觸。
     */
    private val foreignSubjects = listOf("亮度", "螢幕", "屏幕", "背光")

    /** 必須出現其中一個名詞才算在談音量，避免「大一點」之類的半句誤觸。 */
    private val volumeNouns = listOf("音量", "声量", "聲量", "聲音", "声音")

    private val maxRequests = listOf(
        "最大聲", "最大声", "最大音量", "音量最大", "聲音最大", "声音最大", "調到最大", "调到最大",
    )

    private val minRequests = listOf(
        "最小聲", "最小声", "最小音量", "音量最小", "聲音最小", "声音最小", "調到最小", "调到最小",
    )

    /**
     * 調整方向詞。
     *
     * 只收「要求改變」的說法；「音量有點大」這類陳述目前狀態的句子不納入，
     * 避免使用者只是在描述，卻被反向操作。
     */
    private val raiseMarkers = listOf(
        "大聲", "大声", "大一點", "大一点", "大一些", "大點", "大一格",
        "音量大", "聲音大", "声音大", "稍微大", "稍微大聲", "稍微大声",
        "調大", "调大", "加大", "調高", "调高", "提高音量", "音量提高",
    )

    private val lowerMarkers = listOf(
        "小聲", "小声", "小一點", "小一点", "小一些", "小點", "小一格",
        "音量小", "聲音小", "声音小", "稍微小", "稍微小聲", "稍微小声",
        "調小", "调小", "減小", "减小", "調低", "调低", "降低音量", "音量降低",
    )

    /** 沒有名詞也允許的完整說法，例如使用者最常說的「小聲一點」。 */
    private val barePhrases = setOf(
        "大一點", "小一點", "大一格", "小一格", "大一些", "小一些", "大點", "小點",
        "大聲一點", "大聲點", "小聲一點", "小聲點", "大聲一些", "小聲一些",
        "稍微大聲一點", "稍微小聲一點", "稍微大聲", "稍微小聲", "稍微大声", "稍微小声",
    )

    /** 最大／最小同樣要求音量語意，不讓「亮度調到最大」靠字面命中。 */
    private val bareExtremes = setOf("最大聲", "最大声", "最小聲", "最小声")

    /**
     * 「音量調到 50」這類說法。
     *
     * 名詞與數字之間最多容許 4 個非數字字（涵蓋「音量調整到」「聲音設成」）。
     * 結尾的負向斷言讓「音量調大一點」不會被當成百分比 1，
     * 也讓「音量調高一格」不會被當成 1%——量詞後面跟的是格數，不是百分比。
     */
    private val setToPattern = Regex(
        "(?:音量|声量|聲量|聲音|声音)" +
            "\\D{0,4}?" +
            "(\\d{1,3}|[零〇○一二兩两三四五六七八九十百]{1,3})" +
            "(?:%|％)?(?![點点些格级級个个])"
    )

    fun parse(
        transcript: String,
        wakePhrase: String = AssistantSettings.DEFAULT_WAKE_PHRASE,
    ): VolumeCommand? {
        val phrase = transcript.trim().replace(punctuation, "").trim()
        if (phrase.isBlank()) return null

        val wakePrefix = wakePhrase.trim().takeIf { it.isNotBlank() }
            ?.let { Regex("^${Regex.escape(it)}") }
        var stripped = phrase
        repeat(MAX_STRIP_PASSES) {
            val next = stripped
                .let { current -> if (wakePrefix != null) current.replace(wakePrefix, "") else current }
                .replace(politePrefix, "")
                .trim()
            if (next == stripped) return@repeat
            stripped = next
        }
        if (stripped.isBlank()) return null
        if (questionMarkers.any { stripped.contains(it) }) return null
        if (negativeMarkers.any { stripped.contains(it) }) return null

        // 談的是亮度、螢幕亮度就整句不處理，避免順手把媒體音量也調了。
        if (foreignSubjects.any { stripped.contains(it) }) return null

        val hasNoun = volumeNouns.any { stripped.contains(it) }
        if (maxRequests.any { stripped.contains(it) } && (hasNoun || stripped in bareExtremes)) {
            return VolumeCommand.SetTo(100)
        }
        if (minRequests.any { stripped.contains(it) } && (hasNoun || stripped in bareExtremes)) {
            return VolumeCommand.SetTo(0)
        }

        if (!hasNoun && stripped !in barePhrases) return null

        if (hasNoun) {
            val token = setToPattern.find(stripped)?.groupValues?.get(1)
            if (token != null) return VolumeCommand.SetTo(parsePercent(token))
        }

        val raise = raiseMarkers.any { stripped.contains(it) }
        val lower = lowerMarkers.any { stripped.contains(it) }
        return when {
            raise && !lower -> VolumeCommand.Up
            lower && !raise -> VolumeCommand.Down
            else -> null
        }
    }

    /** 使用者說的是百分比，超出範圍時夾到 0–100 而不是整句作廢。 */
    private fun parsePercent(token: String): Int {
        token.toIntOrNull()?.let { return it.coerceIn(0, 100) }
        return (parseChineseNumber(token) ?: 0).coerceIn(0, 100)
    }

    /** 支援「音量調到五十」「音量調到一百」這類中文數字，涵蓋 0–999。 */
    private fun parseChineseNumber(token: String): Int? {
        if (token.isEmpty()) return null
        var total = 0
        var pending = 0
        var matched = false
        for (char in token) {
            val value = chineseDigit(char) ?: return null
            matched = true
            when {
                value < 10 -> pending = value
                value == 10 -> {
                    total += (if (pending == 0) 1 else pending) * 10
                    pending = 0
                }
                else -> {
                    total += (if (pending == 0) 1 else pending) * 100
                    pending = 0
                }
            }
        }
        return if (matched) total + pending else null
    }

    private fun chineseDigit(char: Char): Int? = when (char) {
        '零', '〇', '○' -> 0
        '一' -> 1
        '二', '兩', '两' -> 2
        '三' -> 3
        '四' -> 4
        '五' -> 5
        '六' -> 6
        '七' -> 7
        '八' -> 8
        '九' -> 9
        '十' -> 10
        '百' -> 100
        else -> null
    }
}
