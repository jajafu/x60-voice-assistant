package com.jajafu.x60assistant.volume

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在 [AudioManager.STREAM_MUSIC] 上執行語音音量指令。
 *
 * 只動媒體音量：鈴聲與通知音訊流不納入本輪，避免撞上 vivo 的勿擾模式。
 * 音樂與小助手的 TTS 共用這個音量級別（`docs/decision-log.md` rc27 的 vivo X60 實測），
 * 因此使用者說「小聲一點」時，小助手自己的回話也會一起變小聲，這是已知取捨。
 *
 * 本實作以 [AudioManager.setStreamVolume] 一次設定到目標級別。
 *
 * 探測結果（rc30 commit `3e9b742`，vivo V2046A）：逐格 [AudioManager.adjustStreamVolume]
 * 的呼叫有送達系統（`dumpsys audio` 可見 `adjustStreamVolume ... from com.jajafu.x60assistant`），
 * 但音量完全不變；同一台機以 shell 身分呼叫同一支 API 則正常。逐格方案因此無法使用。
 * 改用 `setStreamVolume` 同樣只需要 `MODIFY_AUDIO_SETTINGS`，並一次到位、不必逐格播提示音。
 * vivo／OriginOS 是否也限制本 App 的 setter 尚待實測。
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
        applyTarget(manager, target, currentIndex, maxIndex)
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
        applyTarget(manager, target.index, currentIndex, maxIndex)
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

    /**
     * 一次設定到目標級別，並記錄調整前後的實測值。
     *
     * 逐格 `adjustStreamVolume` 已證實在 vivo X60 上無效（見類別說明），因此不再使用；
     * 目標級別仍由 [VolumeTargetPolicy] 夾在下限與上限之間，這裡只負責呼叫 API。
     *
     * `setStreamVolume` 在 vivo 上可能不丟例外卻靜默失效，沒有調整前後的實測值就只能靠猜測；
     * 此日誌用於還原實際情況，確認穩定後可移除。
     */
    private fun applyTarget(
        manager: AudioManager,
        targetIndex: Int,
        currentIndex: Int,
        maxIndex: Int,
    ) {
        Log.i(DIAGNOSTIC_TAG, "before current=$currentIndex target=$targetIndex max=$maxIndex")
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        val readBack = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.i(DIAGNOSTIC_TAG, "after target=$targetIndex readBack=$readBack applied=${readBack == targetIndex}")
    }

    private companion object {
        const val DIAGNOSTIC_TAG = "VolumeDiag"
    }
}
