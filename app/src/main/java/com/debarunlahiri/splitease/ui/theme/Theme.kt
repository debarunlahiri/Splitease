package com.debarunlahiri.splitease.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF176B50),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC4F4D7),
        onPrimaryContainer = Color(0xFF0C3527),
        secondary = Color(0xFF536346),
        secondaryContainer = Color(0xFFE6EECB),
        tertiary = Color(0xFF7656A4),
        tertiaryContainer = Color(0xFFEEDCFF),
        background = Color(0xFFF7F8F2),
        surface = Color(0xFFF7F8F2),
        surfaceVariant = Color(0xFFE7EBE3),
        onSurface = Color(0xFF1C2822),
        outlineVariant = Color(0xFFD8DFD5),
    )
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF9EDDB5),
        primaryContainer = Color(0xFF24543E),
        secondaryContainer = Color(0xFF3F4D32),
        tertiaryContainer = Color(0xFF4B3865),
        background = Color(0xFF101A15),
        surface = Color(0xFF101A15),
        surfaceVariant = Color(0xFF28372D),
        onSurface = Color(0xFFE0EBE0),
    )

@Composable
fun SpliteaseTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        motionScheme = MotionScheme.expressive(),
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
