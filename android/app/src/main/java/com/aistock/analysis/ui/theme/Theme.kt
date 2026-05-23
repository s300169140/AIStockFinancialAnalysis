package com.aistock.analysis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandGreen = Color(0xFF1FB37C)
private val BrandGreenDark = Color(0xFF0E7D52)
private val BrandNavy = Color(0xFF0B1220)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5E6),
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandNavy,
    onSecondary = Color.White,
    background = Color(0xFFFAFAFB),
    onBackground = Color(0xFF111418),
    surface = Color.White,
    onSurface = Color(0xFF111418),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF3D4651),
)

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF14523B),
    onPrimaryContainer = Color(0xFFD7F5E6),
    secondary = Color(0xFFE6EAF0),
    onSecondary = BrandNavy,
    background = BrandNavy,
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF121A2A),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1B2638),
    onSurfaceVariant = Color(0xFFB6BEC9),
)

@Composable
fun AIStockTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
