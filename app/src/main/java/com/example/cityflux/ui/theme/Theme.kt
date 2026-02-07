package com.example.cityflux.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════
// CityFlux Material 3 Theme - Modern Clean Design
// Edge-to-edge with white background
// ═══════════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    // Primary - Blue accent
    primary = Color(0xFF93C5FD),           // Light blue for dark mode
    onPrimary = Color(0xFF1E3A8A),         // Dark blue text on primary
    primaryContainer = Color(0xFF1D4ED8),  // Blue container
    onPrimaryContainer = Color(0xFFDBEAFE), // Light text on container
    
    // Secondary - Purple accent
    secondary = Color(0xFFA78BFA),          // Light purple
    onSecondary = Color(0xFF4C1D95),        // Dark purple text
    secondaryContainer = Color(0xFF5B21B6), // Purple container
    onSecondaryContainer = Color(0xFFEDE9FE), // Light text
    
    // Tertiary - Teal accent
    tertiary = Color(0xFF5EEAD4),           // Teal accent
    onTertiary = Color(0xFF134E4A),         // Dark teal text
    
    // Background & Surface - Dark theme
    background = Color(0xFF111827),         // Very dark blue-gray
    onBackground = Color(0xFFF9FAFB),       // Light text
    
    surface = NavBarBackgroundDark,         // Dark surface
    onSurface = Color(0xFFF9FAFB),          // Light text on surface
    surfaceVariant = Color(0xFF374151),     // Variant surface
    onSurfaceVariant = Color(0xFFD1D5DB),   // Muted text
    
    // Outline & Borders
    outline = Color(0xFF4B5563),            // Subtle borders
    outlineVariant = Color(0xFF374151),     // Even more subtle
    
    // Error states
    error = Color(0xFFFCA5A5),              // Light red for dark mode
    onError = Color(0xFF7F1D1D),            // Dark red text
    errorContainer = Color(0xFFDC2626),     // Red container
    onErrorContainer = Color(0xFFFEE2E2)    // Light text on error
)

private val LightColorScheme = lightColorScheme(
    // Primary - Deep professional blue
    primary = PrimaryBlue,                   // #2563EB
    onPrimary = Color(0xFFFFFFFF),           // White text on primary
    primaryContainer = Color(0xFFDBEAFE),    // Light blue container
    onPrimaryContainer = Color(0xFF1E40AF), // Dark blue text
    
    // Secondary - Purple accent
    secondary = PrimaryPurple,               // #7C3AED
    onSecondary = Color(0xFFFFFFFF),         // White text
    secondaryContainer = Color(0xFFEDE9FE),  // Light purple container
    onSecondaryContainer = Color(0xFF4C1D95), // Dark purple text
    
    // Tertiary - Teal accent
    tertiary = PrimaryTeal,                  // #0D9488
    onTertiary = Color(0xFFFFFFFF),          // White text
    
    // Background & Surface - Clean white
    background = SurfaceWhite,               // Pure white
    onBackground = TextPrimary,              // Dark charcoal #1F2933
    
    surface = SurfaceWhite,                  // White surface
    onSurface = TextPrimary,                 // Dark text on surface
    surfaceVariant = Color(0xFFF3F4F6),      // Light gray surface
    onSurfaceVariant = TextSecondary,        // Soft grey #6B7280
    
    // Outline & Borders
    outline = Color(0xFFD1D5DB),             // Subtle borders
    outlineVariant = Color(0xFFE5E7EB),      // Even lighter borders
    
    // Error states
    error = AccentRed,                       // #EF4444
    onError = Color(0xFFFFFFFF),             // White text on error
    errorContainer = Color(0xFFFEE2E2),      // Light red container
    onErrorContainer = Color(0xFF991B1B)     // Dark red text
)

@Composable
fun CityFluxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled for consistent branding
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            // Edge-to-edge design
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Status bar styling - transparent with dark/light icons
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            // Light status bar icons for dark theme, dark icons for light theme
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}