package com.jajafu.x60assistant.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回話文字是使用者唯一聽到的執行結果，因此鎖住「只在真的有調整時才說已調整」。
 */
class VolumeReplyTest {
    @Test
    fun `reports the resulting level and the device maximum`() {
        assertEquals("音量已調到 8，共 15 格。", VolumeReply.setTo(8, 15))
        assertEquals("音量已經是 8 格，共 15 格。", VolumeReply.unchanged(8, 15))
    }

    @Test
    fun `explains the floor instead of claiming success`() {
        assertEquals(
            "音量已經是最低的 2 格。",
            VolumeReply.atFloor(2),
        )
        assertEquals(
            "音量最低只到 2 格，還沒調低。",
            VolumeReply.belowFloor(2),
        )
        assertEquals("音量已經是最大的 15 格。", VolumeReply.atCeiling(15))
    }

    @Test
    fun `never says the volume changed when nothing changed`() {
        // 做不到時的回話不得出現「已調到」，避免假稱完成。
        listOf(
            VolumeReply.atFloor(2),
            VolumeReply.atCeiling(15),
            VolumeReply.belowFloor(2),
            VolumeReply.unchanged(8, 15),
            VolumeReply.unavailable(),
            VolumeReply.failed(),
        ).forEach { message ->
            assertTrue(message, !message.contains("音量已調到"))
        }
    }

}
