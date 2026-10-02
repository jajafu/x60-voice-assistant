package com.jajafu.x60assistant.volume

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在 [AudioManager.STREAM_MUSIC] 上執行語音音量指令。
 *
 * 只動媒體音量：鈴聲與通知音訊流不納入本輪，避免撞上 vivo 的勿擾模式。
 * 音樂與小助手的 TTS 共用這個音量級別（`docs/decision-log.md` rc27 的 vivo X60 實測），
 * 因此使用者說「小聲一點」時，小助手自己的回話也會一起變小聲，這是已知取捨。
 *
 * 本實作以 [AudioManager.adjustStreamVolume] 逐格前進；`setStreamVolume` 也是公開 SDK API，
 * 但不由此類別呼叫。每格都可能觸發系統提示音，且實際裝置行為仍待 X60 驗收；
 * 相對音量也刻意指定 `STREAM_MUSIC`，不用 `adjustSuggestedStreamVolume`，
 * 避免沒有音訊焦點時調整到其他音訊流。
 */
class AndroidVolumeActionExecutor(
    context: Context,
    private val minVolumeIndex: Int = VolumeTargetPolicy.MIN_VOLUME_INDEX,
) : VolumeActionExecutor {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    override suspend fun execute(command: VolumeCommand): VolumeActionResult =
        withContext(Dispatchers.Main.immediate) {
            val manager = audioManager
                ?: return@withContext VolumeActionResult(VolumeReply.unavailable(), applied = false)
            when (command) {
                VolumeCommand.Up -> change(manager, raise = true)
                VolumeCommand.Down -> change(manager, raise = false)
                is VolumeCommand.SetTo -> setTo(manager, command.percent)
            }
        }

    private fun change(manager: AudioManager, raise: Boolean): VolumeActionResult {
        val maxIndex = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentIndex = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = VolumeTargetPolicy.step(currentIndex, maxIndex, minVolumeIndex, raise)
            ?: return VolumeActionResult(
                message = if (raise) VolumeReply.atCeiling(maxIndex) else VolumeReply.atFloor(minVolumeIndex),
                applied = false,
            )
        applySteps(manager, VolumeTargetPolicy.adjustmentStepsTo(target, currentIndex))
        return verify(manager, target, maxIndex)
    }

    private fun setTo(manager: AudioManager, percent: Int): VolumeActionResult {
        val maxIndex = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentIndex = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = VolumeTargetPolicy.percent(percent, maxIndex, minVolumeIndex)
            ?: return VolumeActionResult(VolumeReply.unavailable(), applied = false)
        // 使用者要的比下限更低時不動作，只說明原因，不用音量 0 硬掰。
        if (target.belowFloor) {
            return VolumeActionResult(VolumeReply.belowFloor(minVolumeIndex), applied = false)
        }
        if (target.index == currentIndex) {
            return VolumeActionResult(VolumeReply.unchanged(currentIndex, maxIndex), applied = false)
        }
        applySteps(manager, VolumeTargetPolicy.adjustmentStepsTo(target.index, currentIndex))
        return verify(manager, target.index, maxIndex)
    }

    /**
     * 調整後讀回實際級別再回話。
     *
     * 系統或 OEM 可能夾限或忽略變更；只看目標值等於假稱已完成，因此以讀回值為準。
     */
    private fun verify(manager: AudioManager, targetIndex: Int, maxIndex: Int): VolumeActionResult =
        VolumeTargetPolicy.verify(
            targetIndex = targetIndex,
            actualIndex = manager.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxIndex = maxIndex,
        )

    /** 逐格前進；每一格都可能讓系統播一次音量提示音，所以路徑交給 [VolumeTargetPolicy] 產生。 */
    private fun applySteps(manager: AudioManager, steps: List<VolumeStep>) {
        for (step in steps) {
            val direction = when (step.direction) {
                VolumeStepDirection.RAISE -> AudioManager.ADJUST_RAISE
                VolumeStepDirection.LOWER -> AudioManager.ADJUST_LOWER
            }
            manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        }
    }
}
