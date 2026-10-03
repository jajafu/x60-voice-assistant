package com.jajafu.x60assistant.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUrlsTest {
    @Test
    fun `allows github release hosts over https`() {
        assertEquals(
            "https://api.github.com/repos/jajafu/x60-voice-assistant/releases/latest",
            UpdateUrls.requireTrustedHttps("https://api.github.com/repos/jajafu/x60-voice-assistant/releases/latest"),
        )
        assertEquals(
            "https://github.com/jajafu/x60-voice-assistant/releases/download/v1/x.apk",
            UpdateUrls.requireTrustedHttps("https://github.com/jajafu/x60-voice-assistant/releases/download/v1/x.apk"),
        )
        assertEquals(
            "https://objects.githubusercontent.com/ab/cd",
            UpdateUrls.requireTrustedHttps("https://objects.githubusercontent.com/ab/cd"),
        )
    }

    @Test
    fun `rejects http unknown hosts and userinfo`() {
        assertTrue(runCatching { UpdateUrls.requireTrustedHttps("http://github.com/x.apk") }.isFailure)
        assertTrue(runCatching { UpdateUrls.requireTrustedHttps("https://example.com/x.apk") }.isFailure)
        assertTrue(runCatching { UpdateUrls.requireTrustedHttps("https://user@github.com/x.apk") }.isFailure)
        assertTrue(runCatching { UpdateUrls.requireTrustedHttps("file:///x.apk") }.isFailure)
        assertTrue(runCatching { UpdateUrls.requireTrustedHttps("not a url") }.isFailure)
    }
}

class UpdateInfoTest {
    @Test
    fun `compares versionCode for newness`() {
        val info = UpdateInfo(35, "1.0.0-rc31", "https://github.com/x.apk", "a".repeat(64))
        assertTrue(info.isNewerThan(34))
        assertFalse(info.isNewerThan(35))
        assertFalse(info.isNewerThan(36))
    }
}
