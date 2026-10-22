package com.va.voiceassistant.music

import com.va.voiceassistant.data.AssistantSettings

/**
 * 只在本機解析明確的「音樂 App 音量」指令，不呼叫 LLM。
 *
 * 「聲音大一點」「小聲一點」「音量調到 80」「現在音量多少」都交給選用的音樂 App
 * （MusicCabin 的公開語音通道 `volume_up`／`volume_down`／`volume_set`／`volume_get`），
 * 調整的是 App 自己的音量，不是手機系統媒體音量。
 *
 * 喚醒詞與客套話的剝除、繁簡與 ASR 常見誤字、否定句與疑問句的拒絕、
 * 亮度等非音量主詞的排除，沿用原本手機音量解析的做法，
 * 避免「什麼時候音量太大」「不要調小音量」這類句子誤觸音量操作。
 */
object MusicVolumeCommandRouter {
    /** 喚醒詞與客套話可能疊在一起（例：「小助手，請幫我音量調小一點」），剝除最多跑幾輪。 */
    private const val MAX_STRIP_PASSES = 4

    private val politePrefix = Regex("^(?:(?:(?:請問|请问|請|请|麻煩|麻烦)(?:你)?|幫我|帮我|給我|给我|替我)\\s*)+")
    private val punctuation = Regex("[，。！!？?、,;；\\s]")

    /** 出現這些詞就不是在下指令，而是在問問題（查詢句例外，見 [getPhrases]）。 */
    private val questionMarkers = listOf(
        "怎麼", "怎么", "為什麼", "为什么", "嗎", "吗", "什麼", "什么", "如何", "多少",
    )

    /** 否定句不執行：目前不支援「取消調高音量」這類反向操作。 */
    private val negativeMarkers = listOf("不要", "別", "别", "不用", "不必", "先不")

    /**
     * 談的是別的東西就不碰音量：語音只處理音量與聲音，亮度、螢幕亮度不納入。
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
     * 查詢目前音量（不改變），回 [MusicCommand.VolumeGet]。
     *
     * 這些句子含「多少」「多大」等疑問詞，必須在疑問句拒絕之前比對，
     * 但仍以明確白名單為準，避免「音量為什麼這麼小」被當成查詢。
     */
    private val getPhrases = setOf(
        "音量多少", "音量是多少", "音量多大", "音量有多大",
        "現在音量多少", "現在的音量多少", "現在音量是多少", "現在的音量是多少",
        "目前音量多少", "目前音量是多少", "目前音量多大",
        "音量現在多少", "音量現在是多少",
        "音量現在多大", "現在的音量多大", "現在音量多大", "目前的音量多大",
        "音樂音量多少", "音樂音量是多少", "音樂的音量多少", "音樂的音量是多少",
        "音樂音量多大", "音樂的音量多大",
        "現在音樂音量多少", "現在音樂音量是多少", "現在的音樂音量多少", "現在的音樂音量是多少",
        "目前音樂音量多少", "目前的音樂音量多少", "目前的音樂音量是多少",
        "查音量", "查一下音量", "查詢音量", "音量查詢", "看一下音量",
        "現在聲音多大", "聲音多大",
    )

    /** 查詢句尾常見的語助詞；比對白名單前先去掉，讓「音量是多少呢」也認得。 */
    private val trailingParticles = listOf("呢", "啊", "呀", "吧")

    /**
     * 指定音量的動詞。要求「明確的設定動詞」才當成指定音量，
     * 這樣「目前音量是 80」「音量 80 分貝」這種描述現況的句子不會被誤當成調整要求。
     */
    private val setVerbs = listOf(
        "調到", "调到", "調至", "调至", "調成", "调成", "調為", "调为",
        "調整到", "调整到", "調整至", "调整至", "調整成", "调整成", "調整為", "调整为",
        "設到", "设到", "設至", "设至", "設成", "设成", "設為", "设为",
        "設定到", "设定到", "設定至", "设定至", "設定成", "设定成", "設定為", "设定为",
        "改成", "改到", "改至", "改為", "改为", "變更為", "变更为",
    )

    /**
     * 「音量調到 50」這類說法。
     *
     * 名詞與動詞之間最多容許 6 個字（涵蓋「音量從 30 調到 80」），動詞與數字之間
     * 最多 3 個非數字字；結尾的負向斷言讓「音量調到高一格」不會被當成百分比，
     * 也讓量詞（格／級／成／半）、小數點與相鄰數字不被讀成百分比。
     * 多個命中時取最後一個。
     */
    private val setToPattern = Regex(
        "(?:音量|声量|聲量|聲音|声音)" +
            ".{0,6}?" +
            "(?:" + setVerbs.joinToString("|") + ")" +
            "\\D{0,3}?" +
            "(\\d{1,3}|[零〇○一二兩两三四五六七八九十百]{1,3})" +
            "(?:%|％)?(?![0-9零〇○一二兩两三四五六七八九十百點点些格级級个个半成.])"
    )

    fun parse(
        transcript: String,
        wakePhrase: String = AssistantSettings.DEFAULT_WAKE_PHRASE,
    ): MusicCommand? {
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

        // 查詢句最先比對：它們含「多少」等疑問詞，但語意是查音量而不是問問題。
        // 尾語助詞（呢／啊／呀／吧）先去掉再比對，與 MusicCommandRouter 的做法一致。
        val query = trailingParticles.fold(stripped) { acc, particle -> acc.removeSuffix(particle) }
        if (query in getPhrases) return MusicCommand.VolumeGet

        if (questionMarkers.any { stripped.contains(it) }) return null
        if (negativeMarkers.any { stripped.contains(it) }) return null

        // 談的是亮度、螢幕亮度就整句不處理，避免順手把音樂音量也調了。
        if (foreignSubjects.any { stripped.contains(it) }) return null

        val hasNoun = volumeNouns.any { stripped.contains(it) }
        if (maxRequests.any { stripped.contains(it) } && (hasNoun || stripped in bareExtremes)) {
            return MusicCommand.VolumeSet(100)
        }
        if (minRequests.any { stripped.contains(it) } && (hasNoun || stripped in bareExtremes)) {
            return MusicCommand.VolumeSet(0)
        }

        if (!hasNoun && stripped !in barePhrases) return null

        if (hasNoun) {
            // 取最後一個「動詞＋數字」，讓「音量從 30 調到 80」抓到 80 而不是 30。
            val token = setToPattern.findAll(stripped).lastOrNull()?.groupValues?.get(1)
            if (token != null) return MusicCommand.VolumeSet(parsePercent(token))
        }

        val raise = raiseMarkers.any { stripped.contains(it) }
        val lower = lowerMarkers.any { stripped.contains(it) }
        return when {
            raise && !lower -> MusicCommand.VolumeUp
            lower && !raise -> MusicCommand.VolumeDown
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
