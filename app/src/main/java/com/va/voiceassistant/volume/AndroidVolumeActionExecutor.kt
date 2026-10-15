package com.va.voiceassistant.volume

import android.content.Context
import android.media.AudioManager
import com.va.voiceassistant.diagnostics.M1DiagnosticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在 [AudioManager.STREAM_MUSIC] 上執行語音音量指令。
 *
 * 只動媒體音量：鈴聲與通知音訊流不納入本輪，避免撞上部分 OEM 的勿擾模式。
 * 音樂與小助手的 TTS 共用這個音量級別（`docs/decision-log.md` rc27 的測試手機實測），
 * 因此使用者說「小聲一點」時，小助手自己的回話也會一起變小聲，這是已知取捨。
 *
 * 相對調整（`Up`／`Down`）用 [AudioManager.adjustStreamVolume] 走一格；
 * 絕對音量（`SetTo`）用 [AudioManager.setStreamVolume] 一次設定到目標級別。
 * 兩者都只需要 `MODIFY_AUDIO_SETTINGS`。
 * 每次調整後讀回實際級別再回話；系統或 OEM 忽略變更時不虛報成功。
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
        val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        return verify(
            manager = manager,
            targetIndex = target,
            currentIndex = currentIndex,
            maxIndex = maxIndex,
            command = if (raise) "UP" else "DOWN",
            path = "ADJUST",
        )
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
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target.index, 0)
        return verify(
            manager = manager,
            targetIndex = target.index,
            currentIndex = currentIndex,
            maxIndex = maxIndex,
            command = "SET_$percent",
            path = "SET",
        )
    }

    /**
     * 調整後讀回實際級別再回話，並把調整前後的實測值記入診斷頁。
     *
     * 系統或 OEM 可能夾限或忽略變更；只看目標值等於假稱已完成，因此以讀回值為準。
     */
    private fun verify(
        manager: AudioManager,
        targetIndex: Int,
        currentIndex: Int,
        maxIndex: Int,
        command: String,
        path: String,
    ): VolumeActionResult {
        val readBack = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        M1DiagnosticsRepository.setVolumeAttempt(
            command = command,
            detail = "current=$currentIndex target=$targetIndex readBack=$readBack " +
                "max=$maxIndex applied=${readBack == targetIndex} fixed=${manager.isVolumeFixed} " +
                "path=$path",
        )
        return VolumeTargetPolicy.verify(
            targetIndex = targetIndex,
            actualIndex = readBack,
            maxIndex = maxIndex,
        )
    }
}
