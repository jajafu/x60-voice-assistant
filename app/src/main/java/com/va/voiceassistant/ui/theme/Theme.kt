package com.va.voiceassistant.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.va.voiceassistant.data.AssistantSettings

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1EC),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6361),
    background = Color(0xFFF5FBF9),
    surface = Color(0xFFF5FBF9),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5D0),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504D),
    onPrimaryContainer = Color(0xFF9CF1EC),
    secondary = Color(0xFFB1CCC8),
    background = Color(0xFF0E1514),
    surface = Color(0xFF0E1514),
    error = Color(0xFFFFB4AB),
)

@Composable
fun VoiceAssistantTheme(
    themeMode: String = AssistantSettings.THEME_MODE_SYSTEM,
    content: @Composable () -> Unit,
) {
    // 跟隨系統維持原本行為；使用者指定日間／夜間時強制覆蓋，不再看系統設定。
    val darkTheme = when (themeMode) {
        AssistantSettings.THEME_MODE_LIGHT -> false
        AssistantSettings.THEME_MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

