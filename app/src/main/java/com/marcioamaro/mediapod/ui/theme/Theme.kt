package com.marcioamaro.mediapod.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentNeonBlue,
    onPrimary = Color.Black,
    primaryContainer = MetallicSteel,
    onPrimaryContainer = AccentNeonBlue,
    secondary = AccentElectricOrange,
    onSecondary = Color.Black,
    background = MetallicDark,
    onBackground = Color(0xFFF1F5F9),
    surface = MetallicSteel,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = MetallicBezel,
    onSurfaceVariant = Color(0xFF94A3B8)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
