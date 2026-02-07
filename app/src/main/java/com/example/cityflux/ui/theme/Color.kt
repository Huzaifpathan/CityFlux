package com.example.cityflux.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════
// CityFlux Modern Color Palette - Clean Professional Design
// ═══════════════════════════════════════════════════════════════════

// Primary Brand Colors
val PrimaryBlue = Color(0xFF2563EB)        // Deep Professional Blue (accent)
val PrimaryBlueDark = Color(0xFF1D4ED8)    // Darker blue for pressed states
val PrimaryBlueLight = Color(0xFF3B82F6)   // Lighter blue for hover states
val PrimaryPurple = Color(0xFF7C3AED)      // Secondary accent
val PrimaryTeal = Color(0xFF0D9488)        // Tertiary accent

// ═══════════════════════════════════════════════════════════════════
// Surface & Background Colors - Clean White Theme
// ═══════════════════════════════════════════════════════════════════
val SurfaceWhite = Color(0xFFFFFFFF)       // Pure white background
val SurfaceLight = Color(0xFFFAFAFA)       // Slightly off-white for surfaces
val SurfaceDark = Color(0xFF111827)        // Very dark for dark mode background
val CardBackground = Color(0xFFFFFFFF)     // White cards
val CardBackgroundDark = Color(0xFF1F2937) // Dark mode cards

// ═══════════════════════════════════════════════════════════════════
// Text Colors - Unified Typography Palette
// ═══════════════════════════════════════════════════════════════════
val TextPrimary = Color(0xFF1F2933)        // Dark charcoal for primary text
val TextSecondary = Color(0xFF6B7280)      // Soft grey for secondary text
val TextTertiary = Color(0xFF9CA3AF)       // Light grey for hints/labels
val TextAccent = Color(0xFF2563EB)         // Professional blue for accents
val TextOnPrimary = Color(0xFFFFFFFF)      // White text on primary buttons
val TextOnDark = Color(0xFFF9FAFB)         // Light text on dark surfaces

// ═══════════════════════════════════════════════════════════════════
// Category-Specific Accent Colors (for card borders)
// ═══════════════════════════════════════════════════════════════════
val AccentTraffic = Color(0xFF2563EB)      // Blue for Traffic
val AccentParking = Color(0xFF10B981)      // Green for Parking
val AccentIssues = Color(0xFFEF4444)       // Red for Issues
val AccentAlerts = Color(0xFFF59E0B)       // Orange for Alerts

// Status Colors
val AccentGreen = Color(0xFF10B981)        // Success/resolved
val AccentOrange = Color(0xFFF59E0B)       // Warning/in progress
val AccentRed = Color(0xFFEF4444)          // Error/pending
val AccentBlue = Color(0xFF2563EB)         // Info/links

// ═══════════════════════════════════════════════════════════════════
// Button & Interactive Elements
// ═══════════════════════════════════════════════════════════════════
val ButtonPrimary = Color(0xFF2563EB)      // Primary button background
val ButtonPrimaryPressed = Color(0xFF1D4ED8) // Pressed state
val ButtonSecondary = Color(0xFFF3F4F6)    // Secondary button background
val ButtonSecondaryPressed = Color(0xFFE5E7EB) // Pressed state
val ButtonDisabled = Color(0xFFE5E7EB)     // Disabled state

// ═══════════════════════════════════════════════════════════════════
// Card & Container Styling
// ═══════════════════════════════════════════════════════════════════
val CardShadow = Color(0x0D000000)         // Very subtle shadow (5% black)
val CardShadowMedium = Color(0x1A000000)   // Medium shadow (10% black)
val CardBorder = Color(0xFFE5E7EB)         // Light border
val Divider = Color(0xFFE5E7EB)            // Divider color

// ═══════════════════════════════════════════════════════════════════
// Input Field Colors
// ═══════════════════════════════════════════════════════════════════
val InputBackground = Color(0xFFF9FAFB)    // Light input background
val InputBorder = Color(0xFFD1D5DB)        // Input border
val InputBorderFocused = Color(0xFF2563EB) // Focused input border (blue)
val InputFocusBorder = Color(0xFF2563EB)   // Alias for focused border

// ═══════════════════════════════════════════════════════════════════
// Legacy colors for Material Theme compatibility
// ═══════════════════════════════════════════════════════════════════
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ═══════════════════════════════════════════════════════════════════
// Background - NO GRADIENTS on main layout (clean white only)
// Gradients reserved for subtle accents only
// ═══════════════════════════════════════════════════════════════════
val AppGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),   // Pure white
        Color(0xFFFAFAFA)    // Very subtle off-white
    )
)

val AppGradientSubtle = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF9FAFB)
    )
)

// Accent gradient for icons/buttons only (not backgrounds)
val AccentGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF2563EB),   // Blue
        Color(0xFF3B82F6)    // Lighter blue
    )
)

val ButtonGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF2563EB),   // Primary blue
        Color(0xFF3B82F6)    // Lighter blue
    )
)

// ═══════════════════════════════════════════════════════════════════
// Bottom Navigation Bar
// ═══════════════════════════════════════════════════════════════════
val NavBarBackground = Color(0xFFFFFFFF)   // White nav bar
val NavBarBackgroundDark = Color(0xFF1F2937) // Dark mode nav bar
val NavBarActiveColor = Color(0xFF2563EB)  // Active tab color
val NavBarInactiveColor = Color(0xFF9CA3AF) // Inactive tab color