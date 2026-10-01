package com.jajafu.x60assistant.volume

/** 百分比換算後的目標音量級別。 */
data class PercentTarget(
    /** 夾在下限與上限之間的實際級別。 */
    val index: Int,
    /** 使用者要求的音量低於可調整下限；此時不執行，只提示。 */
    val belowFloor: Boolean,
)

/**
 * 決定目標音量級別的純邏輯，不碰 Android API，因此可以完整單元測試。
 *
 * 下限刻意設為 2 格：[com.jajafu.x60assistant.tts.AndroidTtsEngine] 的 TTS 與音樂
 * 共用 `STREAM_MUSIC`（見 `docs/decision-log.md` rc27 的 vivo X60 實測），
 * 壓太低會讓小助手自己的回話與喚醒提示音一起變小聲。
 */
object VolumeTargetPolicy {
    /** 語音可調整的最低音量級別。 */
    const val MIN_VOLUME_INDEX = 2

    /**
     * 相對調整一格；已在邊界或方向不成立時回傳 null，代表不動作。
     *
     * 使用者可能自己用實體音量鍵壓到下限以下，此時要求「小聲一點」不能反過來把音量抬高。
     */
    fun step(
        currentIndex: Int,
        maxIndex: Int,
        minIndex: Int = MIN_VOLUME_INDEX,
        raise: Boolean,
    ): Int? {
        if (maxIndex < minIndex) return null
        if (raise && currentIndex >= maxIndex) return null
        if (!raise && currentIndex <= minIndex) return null
        val candidate = if (raise) currentIndex + 1 else currentIndex - 1
        return candidate.coerceIn(minIndex, maxIndex)
    }

    /** 百分比換算；超出 0–100 或裝置上限不合法時回傳 null。 */
    fun percent(
        percent: Int,
        maxIndex: Int,
        minIndex: Int = MIN_VOLUME_INDEX,
    ): PercentTarget? {
        if (percent !in 0..100) return null
        if (maxIndex < minIndex) return null
        val requested = Math.round(percent / 100.0 * maxIndex).toInt()
        return PercentTarget(
            index = requested.coerceIn(minIndex, maxIndex),
            belowFloor = requested < minIndex,
        )
    }

    /**
     * 從目前級別逐格走到 [targetIndex] 的完整路徑，不含出發點。
     *
     * Android 沒有公開的設定音量的 API（`setStreamVolume` 是 `@SystemApi`），
     * 只能用 `adjustStreamVolume` 一格一格前進，所以這裡產生完整的步驟序列。
     */
    fun stepsTo(targetIndex: Int, currentIndex: Int): List<Int> {
        if (targetIndex == currentIndex) return emptyList()
        val delta = if (targetIndex > currentIndex) 1 else -1
        val steps = ArrayList<Int>(kotlin.math.abs(targetIndex - currentIndex))
        var index = currentIndex
        while (index != targetIndex) {
            index += delta
            steps += index
        }
        return steps
    }
}