package com.jajafu.x60assistant.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeCommandRouterTest {
    private fun parse(transcript: String, wakePhrase: String = "小助手"): VolumeCommand? =
        VolumeCommandRouter.parse(transcript, wakePhrase)

    @Test
    fun `parses relative volume requests`() {
        assertEquals(VolumeCommand.Down, parse("聲音小一點"))
        assertEquals(VolumeCommand.Down, parse("小聲一點"))
        assertEquals(VolumeCommand.Down, parse("音量調小一點"))
        assertEquals(VolumeCommand.Down, parse("把聲音調小"))
        assertEquals(VolumeCommand.Up, parse("聲音大一點"))
        assertEquals(VolumeCommand.Up, parse("大聲一點"))
        assertEquals(VolumeCommand.Up, parse("音量調大一點"))
        assertEquals(VolumeCommand.Up, parse("把聲音調大"))
    }

    @Test
    fun `parses simplified chinese variants`() {
        assertEquals(VolumeCommand.Down, parse("声音小一点"))
        assertEquals(VolumeCommand.Up, parse("声音大一点"))
        assertEquals(VolumeCommand.Mute, parse("静音"))
    }

    @Test
    fun `strips wake phrase and polite prefix`() {
        assertEquals(VolumeCommand.Down, parse("小助手，聲音小一點"))
        assertEquals(VolumeCommand.Down, parse("請幫我聲音小一點"))
        assertEquals(VolumeCommand.Down, parse("小助手請幫我聲音小一點。"))
        assertEquals(VolumeCommand.SetTo(50), parse("小助手，音量調到50！"))
    }

    @Test
    fun `treats a spoken number as a percentage`() {
        assertEquals(VolumeCommand.SetTo(50), parse("音量調到50"))
        assertEquals(VolumeCommand.SetTo(50), parse("音量調整到 50%"))
        assertEquals(VolumeCommand.SetTo(80), parse("聲音設成80"))
        assertEquals(VolumeCommand.SetTo(30), parse("音量改成30"))
        // 超出 0–100 夾到邊界，而不是整句作廢後交給 LLM 亂猜。
        assertEquals(VolumeCommand.SetTo(100), parse("音量調到150"))
    }

    @Test
    fun `parses chinese numerals`() {
        assertEquals(VolumeCommand.SetTo(50), parse("音量調到五十"))
        assertEquals(VolumeCommand.SetTo(10), parse("音量調到十"))
        assertEquals(VolumeCommand.SetTo(100), parse("音量調到一百"))
        assertEquals(VolumeCommand.SetTo(0), parse("音量調到零"))
    }

    @Test
    fun `maps loudest and quietest requests onto the percent range`() {
        assertEquals(VolumeCommand.SetTo(100), parse("音量最大"))
        assertEquals(VolumeCommand.SetTo(100), parse("最大聲"))
        assertEquals(VolumeCommand.SetTo(0), parse("音量最小"))
        assertEquals(VolumeCommand.SetTo(0), parse("最小聲"))
    }

    @Test
    fun `distinguishes mute from unmute`() {
        assertEquals(VolumeCommand.Mute, parse("靜音"))
        assertEquals(VolumeCommand.Mute, parse("安靜"))
        assertEquals(VolumeCommand.Mute, parse("閉嘴"))
        assertEquals(VolumeCommand.Unmute, parse("取消靜音"))
        assertEquals(VolumeCommand.Unmute, parse("解除靜音"))
        assertEquals(VolumeCommand.Unmute, parse("恢復聲音"))
    }

    @Test
    fun `does not read a relative request as a percentage`() {
        // 「一點」是程度副詞，不是百分比 1。
        assertEquals(VolumeCommand.Down, parse("音量調小一點"))
        assertEquals(VolumeCommand.Up, parse("音量調大一點"))
        assertEquals(VolumeCommand.Down, parse("音量調小一點點"))
    }

    @Test
    fun `does not trigger on questions`() {
        assertNull(parse("什麼時候音量太大"))
        assertNull(parse("怎麼調音量"))
        assertNull(parse("為什麼要調音量"))
        assertNull(parse("音量現在多少"))
        assertNull(parse("音量可以調到100嗎"))
    }

    @Test
    fun `does not trigger on negations`() {
        assertNull(parse("不要調小音量"))
        assertNull(parse("不用調大聲音"))
        assertNull(parse("別調音量"))
        assertNull(parse("先不要靜音"))
    }

    @Test
    fun `requires a volume noun for partial utterances`() {
        // 「大一點」有列在無名詞白名單，「大聲一點」有；但「大一點點」只出現在有名詞時才認。
        assertNull(parse("這首歌"))
        assertNull(parse("導航到台北101"))
        assertNull(parse("幫我設定一個九點的提醒"))
    }

    @Test
    fun `does not trigger on unrelated requests`() {
        assertNull(parse("這是什麼歌"))
        assertNull(parse("台北101有多高"))
        assertNull(parse("幫我打開音樂"))
    }

    @Test
    fun `rejects blank input`() {
        assertNull(parse("   "))
    }

    @Test
    fun `respects a custom wake phrase`() {
        assertEquals(VolumeCommand.Down, parse("小幫手聲音小一點", wakePhrase = "小幫手"))
    }

    @Test
    fun `does not let the reminder router swallow a volume request`() {
        // 音量指令帶數字，而提醒路由排在前面，必須確認兩者不會互相誤觸。
        assertEquals(VolumeCommand.SetTo(50), parse("音量調到50"))
        assertEquals(VolumeCommand.Down, parse("音量調小一點"))
    }
}