package com.phoenix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFFB388FF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1F1B24),
    surface = Color(0xFF15121B),
    background = Color(0xFF100D16),
    secondary = Color(0xFF80CBC4),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A3DE8),
    secondary = Color(0xFF00897B),
)

@Composable
fun PhoenixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PhoenixTypography,
        content = content,
    )
}
