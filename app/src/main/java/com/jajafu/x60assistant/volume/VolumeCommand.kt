package com.jajafu.x60assistant.volume

/**
 * 封閉的手機音量動作型別。
 *
 * 只有這些動作會被執行；LLM 的一般問答文字不會、也不得轉成新的音量指令，
 * 與 [com.jajafu.x60assistant.navigation.NavigationCommand] 的設計理由相同。
 */
sealed interface VolumeCommand {
    /** 音量調高一格。 */
    data object Up : VolumeCommand

    /** 音量調低一格。 */
    data object Down : VolumeCommand

    /** 靜音。不會把音量級別設成 0，也不會記住原本的音量。 */
    data object Mute : VolumeCommand

    /** 解除靜音，沿用原本的音量級別。 */
    data object Unmute : VolumeCommand

    /**
     * 調到指定百分比。
     *
     * 使用者說「音量調到 50」時 [percent] 是 50，代表最大值的 50%；
     * 實際級別由 [VolumeTargetPolicy] 依裝置上限換算，不在解析階段假設格數。
     */
    data class SetTo(val percent: Int) : VolumeCommand
}