package com.va.voiceassistant.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownSpeechCleanerTest {
    @Test
    fun `removes visual markdown while retaining spoken labels`() {
        assertEquals(
            "重點 請看官方文件與連結",
            MarkdownSpeechCleaner.clean("## **重點**\n- 請看[官方文件](https://example.com)與 https://private.test?q=x"),
        )
    }

    @Test
    fun `drops fenced source code`() {
        assertEquals("前文 後文", MarkdownSpeechCleaner.clean("前文 ```kotlin\nsecret()\n``` 後文"))
    }
}
