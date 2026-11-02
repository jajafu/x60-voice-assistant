package com.va.voiceassistant.ui

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUiModeTest {

    @Test
    fun `uiMode 回報電視即為電視`() {
        assertTrue(
            DeviceUiMode.isTv(Configuration.UI_MODE_TYPE_TELEVISION, hasLeanbackFeature = false),
        )
    }

    @Test
    fun `宣告 leanback 特徵即為電視`() {
        assertTrue(
            DeviceUiMode.isTv(Configuration.UI_MODE_TYPE_NORMAL, hasLeanbackFeature = true),
        )
    }

    @Test
    fun `一般模式且無 leanback 為手機`() {
        assertFalse(
            DeviceUiMode.isTv(Configuration.UI_MODE_TYPE_NORMAL, hasLeanbackFeature = false),
        )
    }

    @Test
    fun `形態未定義且無 leanback 為手機`() {
        assertFalse(
            DeviceUiMode.isTv(Configuration.UI_MODE_TYPE_UNDEFINED, hasLeanbackFeature = false),
        )
    }

    @Test
    fun `形態未定義但有 leanback 仍視為電視`() {
        assertTrue(
            DeviceUiMode.isTv(Configuration.UI_MODE_TYPE_UNDEFINED, hasLeanbackFeature = true),
        )
    }
}
