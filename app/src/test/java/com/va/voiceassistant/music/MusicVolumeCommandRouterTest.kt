package com.va.voiceassistant.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicVolumeCommandRouterTest {
    private fun parse(transcript: String, wakePhrase: String = "小助手"): MusicCommand? =
        MusicVolumeCommandRouter.parse(transcript, wakePhrase)

    @Test
    fun `parses relative volume requests`() {
        assertEquals(MusicCommand.VolumeDown, parse("聲音小一點"))
        assertEquals(MusicCommand.VolumeDown, parse("小聲一點"))
        assertEquals(MusicCommand.VolumeDown, parse("音量調小一點"))
        assertEquals(MusicCommand.VolumeDown, parse("把聲音調小"))
        assertEquals(MusicCommand.VolumeUp, parse("聲音大一點"))
        assertEquals(MusicCommand.VolumeUp, parse("大聲一點"))
        assertEquals(MusicCommand.VolumeUp, parse("音量調大一點"))
        assertEquals(MusicCommand.VolumeUp, parse("把聲音調大"))
    }

    @Test
    fun `parses simplified chinese variants`() {
        assertEquals(MusicCommand.VolumeDown, parse("声音小一点"))
        assertEquals(MusicCommand.VolumeUp, parse("声音大一点"))
    }

    @Test
    fun `strips wake phrase and polite prefix`() {
        assertEquals(MusicCommand.VolumeDown, parse("小助手，聲音小一點"))
        assertEquals(MusicCommand.VolumeDown, parse("請幫我聲音小一點"))
        assertEquals(MusicCommand.VolumeDown, parse("小助手請幫我聲音小一點。"))
        assertEquals(MusicCommand.VolumeSet(50), parse("小助手，音量調到50！"))
    }

    @Test
    fun `treats a spoken number as a percentage`() {
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調到50"))
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調整到 50%"))
        assertEquals(MusicCommand.VolumeSet(80), parse("聲音設成80"))
        assertEquals(MusicCommand.VolumeSet(30), parse("音量改成30"))
        // 超出 0–100 夾到邊界，而不是整句作廢後交給 LLM 亂猜。
        assertEquals(MusicCommand.VolumeSet(100), parse("音量調到150"))
    }

    @Test
    fun `accepts the full set of setting verbs used before`() {
        // 舊版（rc30）可用的說法不能在改成「要求動詞」後失效。
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調整為50"))
        assertEquals(MusicCommand.VolumeSet(30), parse("音量調整成30"))
        assertEquals(MusicCommand.VolumeSet(50), parse("音量設定為50"))
        assertEquals(MusicCommand.VolumeSet(60), parse("音量設定成60"))
        assertEquals(MusicCommand.VolumeSet(40), parse("聲音調成40"))
        assertEquals(MusicCommand.VolumeSet(70), parse("音樂音量改為70"))
        // 「至」家族的到／至同義說法。
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調至50"))
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調整至50"))
        assertEquals(MusicCommand.VolumeSet(50), parse("音量設定至50"))
    }

    @Test
    fun `does not read a level count or fraction as a percentage`() {
        // 「15格」「一半」「五成」是格數或比例說法，不是百分比，整句不處理以免設錯值。
        assertNull(parse("音量調到15格"))
        assertNull(parse("音量調到十五格"))
        assertNull(parse("音量調到一半"))
        assertNull(parse("音量調到五成"))
        // 正常百分比寫法仍照舊。
        assertEquals(MusicCommand.VolumeSet(15), parse("音量調到15"))
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調到50%"))
    }

    @Test
    fun `takes the target level not the starting level`() {
        // 「音量從 30 調到 80」要抓最後的目標 80，不是前面的起點 30。
        assertEquals(MusicCommand.VolumeSet(80), parse("音量從30調到80"))
        assertEquals(MusicCommand.VolumeSet(80), parse("音量從30調整到80"))
    }

    @Test
    fun `does not treat a plain description as a set request`() {
        // 沒有明確設定動詞的句子是描述現況，不該被當成「調到」而改變音量。
        assertNull(parse("目前音量是80"))
        assertNull(parse("音量現在是50"))
        assertNull(parse("音量80分貝"))
        assertNull(parse("音樂音量是80"))
    }

    @Test
    fun `parses chinese numerals`() {
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調到五十"))
        assertEquals(MusicCommand.VolumeSet(10), parse("音量調到十"))
        assertEquals(MusicCommand.VolumeSet(100), parse("音量調到一百"))
        assertEquals(MusicCommand.VolumeSet(0), parse("音量調到零"))
    }

    @Test
    fun `maps loudest and quietest requests onto the percent range`() {
        assertEquals(MusicCommand.VolumeSet(100), parse("音量最大"))
        assertEquals(MusicCommand.VolumeSet(100), parse("最大聲"))
        assertEquals(MusicCommand.VolumeSet(0), parse("音量最小"))
        assertEquals(MusicCommand.VolumeSet(0), parse("最小聲"))
    }

    @Test
    fun `does not control volume using mute phrases`() {
        assertNull(parse("靜音"))
        assertNull(parse("静音"))
        assertNull(parse("取消靜音"))
        assertNull(parse("解除靜音"))
    }

    @Test
    fun `parses volume queries without changing the volume`() {
        assertEquals(MusicCommand.VolumeGet, parse("現在音量多少"))
        assertEquals(MusicCommand.VolumeGet, parse("音量現在多少"))
        assertEquals(MusicCommand.VolumeGet, parse("音量是多少"))
        assertEquals(MusicCommand.VolumeGet, parse("音樂音量多少"))
        assertEquals(MusicCommand.VolumeGet, parse("小助手，現在的音樂音量是多少？"))
        assertEquals(MusicCommand.VolumeGet, parse("查音量"))
        // 尾語助詞不影響查詢句的比對。
        assertEquals(MusicCommand.VolumeGet, parse("音量是多少呢"))
        assertEquals(MusicCommand.VolumeGet, parse("現在音量多少啊"))
        assertEquals(MusicCommand.VolumeGet, parse("現在的音量多大呢"))
    }

    @Test
    fun `does not read a relative request as a percentage`() {
        // 「一點」是程度副詞，不是百分比 1。
        assertEquals(MusicCommand.VolumeDown, parse("音量調小一點"))
        assertEquals(MusicCommand.VolumeUp, parse("音量調大一點"))
        assertEquals(MusicCommand.VolumeDown, parse("音量調小一點點"))
    }

    @Test
    fun `reads a level count as one step instead of a percentage`() {
        // 「一格」「兩格」數量的是格數，不是百分比；每次調整就是一格。
        assertEquals(MusicCommand.VolumeUp, parse("音量調高一格"))
        assertEquals(MusicCommand.VolumeUp, parse("音量調高兩格"))
        assertEquals(MusicCommand.VolumeDown, parse("音量調低一格"))
        assertEquals(MusicCommand.VolumeDown, parse("音量調低兩格"))
        assertEquals(MusicCommand.VolumeUp, parse("音量大一格"))
        assertEquals(MusicCommand.VolumeDown, parse("聲音小一格"))
        // 百分比寫法仍然照舊。
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調到50"))
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調到 50%"))
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
        assertEquals(MusicCommand.VolumeUp, parse("音量大一點"))
        assertEquals(MusicCommand.VolumeUp, parse("聲音大一點"))
        assertEquals(MusicCommand.VolumeDown, parse("音量稍微小聲"))
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
        // 亮度句不因為含有「音量」而誤觸音樂音量。
        assertEquals(MusicCommand.VolumeSet(100), parse("音量調到最大"))
    }

    @Test
    fun `does not trigger on questions`() {
        assertNull(parse("什麼時候音量太大"))
        assertNull(parse("怎麼調音量"))
        assertNull(parse("為什麼要調音量"))
        assertNull(parse("音量為什麼這麼小"))
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
        assertEquals(MusicCommand.VolumeDown, parse("小幫手聲音小一點", wakePhrase = "小幫手"))
    }

    @Test
    fun `does not let the reminder router swallow a volume request`() {
        // 音量指令帶數字，而提醒路由排在前面，必須確認兩者不會互相誤觸。
        assertEquals(MusicCommand.VolumeSet(50), parse("音量調到50"))
        assertEquals(MusicCommand.VolumeDown, parse("音量調小一點"))
    }
}
