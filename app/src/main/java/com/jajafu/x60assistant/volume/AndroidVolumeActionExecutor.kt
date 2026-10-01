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
 * Android 沒有公開的「設定音量」API（`setStreamVolume` 是 `@SystemApi`），
 * 所以絕對音量只能用 [AudioManager.adjustStreamVolume] 逐格前進；
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
                VolumeCommand.Mute -> mute(manager, mute = true)
                VolumeCommand.Unmute -> mute(manager, mute = false)
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
        applySteps(manager, VolumeTargetPolicy.stepsTo(target, currentIndex))
        return VolumeActionResult(VolumeReply.setTo(target, maxIndex), applied = true)
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
        applySteps(manager, VolumeTargetPolicy.stepsTo(target.index, currentIndex))
        return VolumeActionResult(VolumeReply.setTo(target.index, maxIndex), applied = true)
    }

    private fun mute(manager: AudioManager, mute: Boolean): VolumeActionResult {
        val maxIndex = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentIndex = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // 靜音狀態與音量級別是兩件事：isStreamMute 才是目前是否靜音。
        if (manager.isStreamMute(AudioManager.STREAM_MUSIC) == mute) {
            return VolumeActionResult(
                message = if (mute) VolumeReply.alreadyMuted() else VolumeReply.notMuted(currentIndex, maxIndex),
                applied = false,
            )
        }
        // setStreamMute 在 API 35 已棄用，改用未棄用的 ADJUST_MUTE／ADJUST_UNMUTE。
        manager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            0,
        )
        return VolumeActionResult(
            message = if (mute) VolumeReply.muted() else VolumeReply.unmuted(currentIndex, maxIndex),
            applied = true,
        )
    }

    /** 逐格前進；每一格都可能讓系統播一次音量提示音，所以路徑交給 [VolumeTargetPolicy] 產生。 */
    private fun applySteps(manager: AudioManager, steps: List<Int>) {
        var previous: Int? = null
        for (index in steps) {
            val direction = if (previous == null || index > previous) {
                AudioManager.ADJUST_RAISE
            } else {
                AudioManager.ADJUST_LOWER
            }
            manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            previous = index
        }
    }
}