package com.va.voiceassistant.volume

/**
 * 回話文字集中在這裡，讓「只報實際結果、做不到就明說」的規則可以被單元測試。
 *
 * 依 `docs/phone-control-plan.md` §4.1，音量調整完成後才播報結果；
 * 不可用或不支援時不得假稱已經調好。
 */
object VolumeReply {
    fun setTo(index: Int, maxIndex: Int): String = "音量已調到 $index，共 $maxIndex 格。"

    fun unchanged(index: Int, maxIndex: Int): String = "音量已經是 $index 格，共 $maxIndex 格。"

    fun atFloor(minIndex: Int): String = "音量已經是最低的 $minIndex 格。"

    fun atCeiling(maxIndex: Int): String = "音量已經是最大的 $maxIndex 格。"

    fun belowFloor(minIndex: Int): String = "音量最低只到 $minIndex 格，還沒調低。"

    /**
     * 調整後讀回的級別與目標不同。
     *
     * 系統或 OEM 可能夾限或忽略變更，因此回報實際讀到的值而不是原本要求的值。
     */
    fun diverged(actual: Int, maxIndex: Int): String =
        "音量現在是 $actual 格，共 $maxIndex 格，跟剛才要求的不一樣。"

    fun unavailable(): String = "這個裝置無法調整音量。"

    fun failed(): String = "音量調整失敗，請稍後再試一次。"
}
