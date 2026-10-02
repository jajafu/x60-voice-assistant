package com.jajafu.x60assistant.volume

/** 百分比換算後的目標音量級別。 */
data class PercentTarget(
    /** 夾在下限與上限之間的實際級別。 */
    val index: Int,
    /** 使用者要求的音量低於可調整下限；此時不執行，只提示。 */
    val belowFloor: Boolean,
)

/** 一次逐格音量調整及其方向。 */
data class VolumeStep(
    val index: Int,
    val direction: VolumeStepDirection,
)

enum class VolumeStepDirection {
    RAISE,
    LOWER,
}

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
     * 逐格路徑在 vivo X60 上已證實無效（`adjustStreamVolume` 有呼叫但音量不動），
     * 因此 `AndroidVolumeActionExecutor` 目前改用 `setStreamVolume` 一次到位，不再呼叫這裡。
     * 保留此純函式與其測試，作為逐格方案的紀錄；若 setter 同樣被限制，可據此回復。
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

    /**
     * 調整後依實際讀回的級別決定回話。
     *
     * 目標與讀回值不同時不得宣稱已調到，改報實際級別，
     * 對應 `docs/phone-control-plan.md` §4.1 不虛報成功的原則。
     */
    fun verify(targetIndex: Int, actualIndex: Int, maxIndex: Int): VolumeActionResult =
        if (actualIndex == targetIndex) {
            VolumeActionResult(VolumeReply.setTo(actualIndex, maxIndex), applied = true)
        } else {
            VolumeActionResult(VolumeReply.diverged(actualIndex, maxIndex), applied = false)
        }

    /** 以目前級別作為第一步的起點，為每一格產生正確的調整方向。 */
    fun adjustmentStepsTo(targetIndex: Int, currentIndex: Int): List<VolumeStep> {
        var previousIndex = currentIndex
        return stepsTo(targetIndex, currentIndex).map { index ->
            val direction = if (index > previousIndex) {
                VolumeStepDirection.RAISE
            } else {
                VolumeStepDirection.LOWER
            }
            previousIndex = index
            VolumeStep(index, direction)
        }
    }
}
