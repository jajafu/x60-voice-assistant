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
    fun `does not control volume using mute phrases`() {
        assertNull(parse("靜音"))
        assertNull(parse("静音"))
        assertNull(parse("取消靜音"))
        assertNull(parse("解除靜音"))
    }

    @Test
    fun `does not read a relative request as a percentage`() {
        // 「一點」是程度副詞，不是百分比 1。
        assertEquals(VolumeCommand.Down, parse("音量調小一點"))
        assertEquals(VolumeCommand.Up, parse("音量調大一點"))
        assertEquals(VolumeCommand.Down, parse("音量調小一點點"))
    }

    @Test
    fun `reads a level count as one step instead of a percentage`() {
        // 「一格」「兩格」數量的是格數，不是百分比；每次調整就是一格。
        assertEquals(VolumeCommand.Up, parse("音量調高一格"))
        assertEquals(VolumeCommand.Up, parse("音量調高兩格"))
        assertEquals(VolumeCommand.Down, parse("音量調低一格"))
        assertEquals(VolumeCommand.Down, parse("音量調低兩格"))
        assertEquals(VolumeCommand.Up, parse("音量大一格"))
        assertEquals(VolumeCommand.Down, parse("聲音小一格"))
        // 百分比寫法仍然照舊。
        assertEquals(VolumeCommand.SetTo(50), parse("音量調到50"))
        assertEquals(VolumeCommand.SetTo(50), parse("音量調到 50%"))
    }

    @Test
    fun `does not treat a level description as a command`() {
        // 只收要求改變的說法；描述目前狀態的句子不觸發，避免反向操作。
        assertNull(parse("音量有點大"))
        assertNull(parse("音量有點小"))
        assertNull(parse("聲音有點大"))
        assertNull(parse("聲音有點小"))
        assertNull(parse("声音有点大"))
        assertNull(parse("音量稍微有點小"))
        // 但要求改變的說法仍然照常解析。
        assertEquals(VolumeCommand.Up, parse("音量大一點"))
        assertEquals(VolumeCommand.Up, parse("聲音大一點"))
        assertEquals(VolumeCommand.Down, parse("音量稍微小聲"))
    }

    @Test
    fun `does not treat brightness as a volume request`() {
        // 語音只處理音量與聲音，亮度、螢幕亮度不納入。
        assertNull(parse("亮度調到最大"))
        assertNull(parse("亮度調高一點"))
        assertNull(parse("亮度大一點"))
        assertNull(parse("螢幕調到最大"))
        assertNull(parse("屏幕調亮一點"))
        assertNull(parse("聲音和亮度都調大"))
        // 亮度句不因為含有「音量」而誤觸媒體音量。
        assertEquals(VolumeCommand.SetTo(100), parse("音量調到最大"))
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
        assertNull(parse("先不要調音量"))
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
