package com.example.cityflux.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════
// CityFlux Theme - Modern Professional Design System
// Full light/dark mode support with Material 3
// ═══════════════════════════════════════════════════════════════════

// Light Color Scheme - Clean white professional design
private val CityFluxLightColorScheme = lightColorScheme(
    // Primary colors
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    
    // Secondary colors
    secondary = AccentParking,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    
    // Tertiary colors
    tertiary = AccentAlerts,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    
    // Error colors
    error = AccentRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFB91C1C),
    
    // Background and surface
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    
    // Outline and dividers
    outline = LightCardBorder,
    outlineVariant = Color(0xFFE5E7EB),
    
    // Inverse colors (for contrast elements)
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkTextPrimary,
    inversePrimary = PrimaryBlueLight
)

// Dark Color Scheme - Elegant dark professional design
private val CityFluxDarkColorScheme = darkColorScheme(
    // Primary colors
    primary = PrimaryBlueLight,
    onPrimary = Color(0xFF1E3A8A),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),
    
    // Secondary colors
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF065F46),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    
    // Tertiary colors
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF92400E),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFEF3C7),
    
    // Error colors
    error = Color(0xFFF87171),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    
    // Background and surface
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    
    // Outline and dividers
    outline = DarkCardBorder,
    outlineVariant = Color(0xFF475569),
    
    // Inverse colors
    inverseSurface = LightSurface,
    inverseOnSurface = LightTextPrimary,
    inversePrimary = PrimaryBlue
)

// Custom theme colors accessible throughout the app
data class CityFluxColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textAccent: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val cardShadow: Color,
    val cardShadowMedium: Color,
    val surfaceVariant: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val inputBorderFocused: Color,
    val accentTraffic: Color,
    val accentParking: Color,
    val accentIssues: Color,
    val accentAlerts: Color,
    val bottomNavBackground: Color,
    val divider: Color,
    val isDark: Boolean
)

val LocalCityFluxColors = compositionLocalOf {
    CityFluxColors(
        textPrimary = LightTextPrimary,
        textSecondary = LightTextSecondary,
        textTertiary = LightTextTertiary,
        textAccent = LightTextAccent,
        cardBackground = LightCardBackground,
        cardBorder = LightCardBorder,
        cardShadow = CardShadow,
        cardShadowMedium = CardShadowMedium,
        surfaceVariant = LightSurfaceVariant,
        inputBackground = LightInputBackground,
        inputBorder = LightInputBorder,
        inputBorderFocused = LightInputBorderFocused,
        accentTraffic = AccentTraffic,
        accentParking = AccentParking,
        accentIssues = AccentIssues,
        accentAlerts = AccentAlerts,
        bottomNavBackground = BottomNavBlur,
        divider = Divider,
        isDark = false
    )
}

@Composable
fun CityFluxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to true for Android 12+ dynamic colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CityFluxDarkColorScheme
        else -> CityFluxLightColorScheme
    }
    
    // Custom CityFlux colors based on theme
    val cityFluxColors = if (darkTheme) {
        CityFluxColors(
            textPrimary = DarkTextPrimary,
            textSecondary = DarkTextSecondary,
            textTertiary = DarkTextTertiary,
            textAccent = DarkTextAccent,
            cardBackground = DarkCardBackground,
            cardBorder = DarkCardBorder,
            cardShadow = CardShadow,
            cardShadowMedium = CardShadowMedium,
            surfaceVariant = DarkSurfaceVariant,
            inputBackground = DarkInputBackground,
            inputBorder = DarkInputBorder,
            inputBorderFocused = DarkInputBorderFocused,
            accentTraffic = AccentTraffic,
            accentParking = AccentParking,
            accentIssues = AccentIssues,
            accentAlerts = AccentAlerts,
            bottomNavBackground = BottomNavBlurDark,
            divider = DividerDark,
            isDark = true
        )
    } else {
        CityFluxColors(
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textTertiary = LightTextTertiary,
            textAccent = LightTextAccent,
            cardBackground = LightCardBackground,
            cardBorder = LightCardBorder,
            cardShadow = CardShadow,
            cardShadowMedium = CardShadowMedium,
            surfaceVariant = LightSurfaceVariant,
            inputBackground = LightInputBackground,
            inputBorder = LightInputBorder,
            inputBorderFocused = LightInputBorderFocused,
            accentTraffic = AccentTraffic,
            accentParking = AccentParking,
            accentIssues = AccentIssues,
            accentAlerts = AccentAlerts,
            bottomNavBackground = BottomNavBlur,
            divider = Divider,
            isDark = false
        )
    }
    
    // Configure edge-to-edge display and status bar
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar color to transparent for edge-to-edge
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            // Configure status bar icons based on theme
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCityFluxColors provides cityFluxColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Extension property for easy access to CityFlux colors
val MaterialTheme.cityFluxColors: CityFluxColors
    @Composable
    get() = LocalCityFluxColors.current

