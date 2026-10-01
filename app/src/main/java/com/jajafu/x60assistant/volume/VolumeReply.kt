package com.jajafu.x60assistant.volume

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

    fun unavailable(): String = "這個裝置無法調整音量。"

    fun failed(): String = "音量調整失敗，請稍後再試一次。"
}
