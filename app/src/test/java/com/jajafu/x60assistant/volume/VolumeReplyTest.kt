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
        assertEquals("已解除靜音，音量 8，共 15 格。", VolumeReply.unmuted(8, 15))
        assertEquals("目前沒有靜音，音量 8，共 15 格。", VolumeReply.notMuted(8, 15))
    }

    @Test
    fun `explains the floor instead of claiming success`() {
        assertEquals(
            "音量已經是最低的 2 格，說「靜音」可以完全關掉聲音。",
            VolumeReply.atFloor(2),
        )
        assertEquals(
            "音量最低只到 2 格，還沒調低，說「靜音」可以完全關掉聲音。",
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
            VolumeReply.alreadyMuted(),
            VolumeReply.notMuted(8, 15),
            VolumeReply.unavailable(),
            VolumeReply.failed(),
        ).forEach { message ->
            assertTrue(message, !message.contains("音量已調到"))
        }
    }

    @Test
    fun `offers mute as the way to go below the floor`() {
        assertTrue(VolumeReply.atFloor(2).contains("靜音"))
        assertTrue(VolumeReply.belowFloor(2).contains("靜音"))
    }

    @Test
    fun `has a fixed phrase for mute outcomes`() {
        assertEquals("已靜音。", VolumeReply.muted())
        assertEquals("已經是靜音狀態了。", VolumeReply.alreadyMuted())
    }
}