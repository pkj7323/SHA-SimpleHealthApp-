package com.pkj.leanbulk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF1B5E20)
private val Amber = Color(0xFFFFA000)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Amber,
    tertiary = GreenDark
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Amber,
    tertiary = GreenDark
)

@Composable
fun LeanBulkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
