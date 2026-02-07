package com.example.cityflux.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CityFluxDarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryPurple,
    background = DarkBackground,
    surface = CardBackground,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun CityFluxTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CityFluxDarkColorScheme,
        typography = Typography,
        content = content
    )
}

