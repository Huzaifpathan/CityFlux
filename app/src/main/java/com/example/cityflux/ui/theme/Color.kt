package com.example.cityflux.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════
// CityFlux Modern Color Palette — Enhanced Blue Gradient System
// Clean, professional, smart-city inspired
// ═══════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────
// BLUE GRADIENT SPECTRUM (deep navy → sky blue)
// ─────────────────────────────────────────────────────────────────
val GradientNavyDark = Color(0xFF0A1628)
val GradientNavy = Color(0xFF0C2461)
val GradientRoyal = Color(0xFF1E3A8A)
val GradientMedium = Color(0xFF1E40AF)
val PrimaryBlue = Color(0xFF2563EB)
val PrimaryBlueDark = Color(0xFF1D4ED8)
val GradientBright = Color(0xFF3B82F6)
val GradientSky = Color(0xFF38BDF8)
val PrimaryBlueLight = Color(0xFF60A5FA)
val GradientPale = Color(0xFF7DD3FC)
val GradientFrost = Color(0xFF93C5FD)
val GradientIce = Color(0xFFBAE6FD)
val GradientMist = Color(0xFFDBEAFE)
val GradientCloud = Color(0xFFE0F2FE)
val GradientWhite = Color(0xFFF0F9FF)

// ─────────────────────────────────────────────────────────────────
// GRADIENT BRUSHES (reusable across the app)
// ─────────────────────────────────────────────────────────────────
val BlueGradientHorizontal = Brush.horizontalGradient(
    listOf(GradientMedium, PrimaryBlue, GradientBright)
)
val BlueGradientVertical = Brush.verticalGradient(
    listOf(GradientNavy, GradientRoyal, PrimaryBlue)
)
val BlueGradientSubtle = Brush.horizontalGradient(
    listOf(GradientMist, GradientCloud, GradientWhite)
)

// ─────────────────────────────────────────────────────────────────
// LIGHT THEME COLORS
// ─────────────────────────────────────────────────────────────────

val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF8FAFC)

val LightTextPrimary = Color(0xFF1F2933)
val LightTextSecondary = Color(0xFF6B7280)
val LightTextTertiary = Color(0xFF9CA3AF)
val LightTextAccent = Color(0xFF2563EB)

val LightCardBackground = Color(0xFFFFFFFF)
val LightCardBorder = Color(0xFFE5E7EB)

val LightInputBackground = Color(0xFFFFFFFF)
val LightInputBorder = Color(0xFFD1D5DB)
val LightInputBorderFocused = Color(0xFF2563EB)

// ─────────────────────────────────────────────────────────────────
// DARK THEME COLORS
// ─────────────────────────────────────────────────────────────────

val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF334155)

val DarkTextPrimary = Color(0xFFF9FAFB)
val DarkTextSecondary = Color(0xFF9CA3AF)
val DarkTextTertiary = Color(0xFF6B7280)
val DarkTextAccent = Color(0xFF60A5FA)

val DarkCardBackground = Color(0xFF1E293B)
val DarkCardBorder = Color(0xFF334155)

val DarkInputBackground = Color(0xFF1E293B)
val DarkInputBorder = Color(0xFF334155)
val DarkInputBorderFocused = Color(0xFF60A5FA)

// ─────────────────────────────────────────────────────────────────
// SHARED ACCENT COLORS
// ─────────────────────────────────────────────────────────────────

val AccentTraffic = Color(0xFF2563EB)
val AccentParking = Color(0xFF10B981)
val AccentIssues = Color(0xFFEF4444)
val AccentAlerts = Color(0xFFF59E0B)

val AccentGreen = Color(0xFF10B981)
val AccentOrange = Color(0xFFF59E0B)
val AccentRed = Color(0xFFEF4444)
val AccentYellow = Color(0xFFFBBF24)

// Traffic map overlay
val TrafficClear = Color(0xFF10B981)
val TrafficMedium = Color(0xFFFBBF24)
val TrafficHeavy = Color(0xFFEF4444)

// Buttons
val ButtonPrimary = Color(0xFF2563EB)
val ButtonPrimaryPressed = Color(0xFF1D4ED8)
val ButtonDisabled = Color(0xFF9CA3AF)
val ButtonSecondary = Color(0xFFFFFFFF)

// Shadows & Overlays
val CardShadow = Color(0x14000000)
val CardShadowMedium = Color(0x0A000000)
val BottomNavBlur = Color(0xE6FFFFFF)
val BottomNavBlurDark = Color(0xE60F172A)

// Divider
val Divider = Color(0xFFE5E7EB)
val DividerDark = Color(0xFF334155)

// ─────────────────────────────────────────────────────────────────
// LEGACY ALIASES (backward compatibility)
// ─────────────────────────────────────────────────────────────────

val SurfaceWhite = LightBackground
val CardBackground = LightCardBackground
val TextPrimary = LightTextPrimary
val TextSecondary = LightTextSecondary
val TextOnPrimary = Color(0xFFFFFFFF)
val InputBorder = LightInputBorder
val InputBorderFocused = LightInputBorderFocused
val InputBackground = LightInputBackground
val InputFocused = PrimaryBlue
val InputText = LightTextPrimary
val InputHint = LightTextTertiary
val CardBorder = LightCardBorder
val PrimaryPurple = Color(0xFF7C3AED)
