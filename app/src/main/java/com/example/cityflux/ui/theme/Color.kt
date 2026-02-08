package com.example.cityflux.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════
// CityFlux Modern Color Palette - Clean Professional Design
// ═══════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────
// LIGHT THEME COLORS (Primary)
// ─────────────────────────────────────────────────────────────────

// Background - Pure white for all screens
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF8FAFC)

// Text Colors - Dark charcoal spectrum
val LightTextPrimary = Color(0xFF1F2933)        // Primary text - dark charcoal
val LightTextSecondary = Color(0xFF6B7280)      // Secondary text - soft grey
val LightTextTertiary = Color(0xFF9CA3AF)       // Tertiary/hint text
val LightTextAccent = Color(0xFF2563EB)         // Accent text - deep blue

// Card Colors - White with soft shadows
val LightCardBackground = Color(0xFFFFFFFF)
val LightCardBorder = Color(0xFFE5E7EB)

// Input Colors
val LightInputBackground = Color(0xFFFFFFFF)
val LightInputBorder = Color(0xFFD1D5DB)
val LightInputBorderFocused = Color(0xFF2563EB)

// ─────────────────────────────────────────────────────────────────
// DARK THEME COLORS
// ─────────────────────────────────────────────────────────────────

// Background - Deep dark blue-grey
val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF334155)

// Text Colors - Light spectrum
val DarkTextPrimary = Color(0xFFF9FAFB)         // Primary text - almost white
val DarkTextSecondary = Color(0xFF9CA3AF)       // Secondary text - grey
val DarkTextTertiary = Color(0xFF6B7280)        // Tertiary/hint text
val DarkTextAccent = Color(0xFF60A5FA)          // Accent text - lighter blue

// Card Colors - Dark with subtle borders
val DarkCardBackground = Color(0xFF1E293B)
val DarkCardBorder = Color(0xFF334155)

// Input Colors
val DarkInputBackground = Color(0xFF1E293B)
val DarkInputBorder = Color(0xFF334155)
val DarkInputBorderFocused = Color(0xFF60A5FA)

// ─────────────────────────────────────────────────────────────────
// SHARED COLORS (Same in both themes)
// ─────────────────────────────────────────────────────────────────

// Primary Brand Color - Professional Blue
val PrimaryBlue = Color(0xFF2563EB)            // Deep professional blue
val PrimaryBlueDark = Color(0xFF1D4ED8)        // Darker blue for pressed state
val PrimaryBlueLight = Color(0xFF60A5FA)       // Lighter blue for dark theme

// Category Accent Colors (for card borders)
val AccentTraffic = Color(0xFF2563EB)          // Blue for traffic
val AccentParking = Color(0xFF10B981)          // Green for parking
val AccentIssues = Color(0xFFEF4444)           // Red for issues
val AccentAlerts = Color(0xFFF59E0B)           // Orange for alerts

// Status Colors
val AccentGreen = Color(0xFF10B981)            // Success/resolved
val AccentOrange = Color(0xFFF59E0B)           // Warning/in progress  
val AccentRed = Color(0xFFEF4444)              // Error/pending
val AccentYellow = Color(0xFFFBBF24)           // Caution

// Traffic Overlay Colors (for map)
val TrafficClear = Color(0xFF10B981)           // Green - clear
val TrafficMedium = Color(0xFFFBBF24)          // Yellow - medium
val TrafficHeavy = Color(0xFFEF4444)           // Red - heavy

// Button Colors
val ButtonPrimary = Color(0xFF2563EB)
val ButtonPrimaryPressed = Color(0xFF1D4ED8)
val ButtonDisabled = Color(0xFF9CA3AF)
val ButtonSecondary = Color(0xFFFFFFFF)

// Shadows and Overlays
val CardShadow = Color(0x14000000)             // 8% black - soft shadow
val CardShadowMedium = Color(0x0A000000)       // 4% black - very soft
val BottomNavBlur = Color(0xE6FFFFFF)          // White with blur for bottom nav
val BottomNavBlurDark = Color(0xE60F172A)      // Dark with blur for bottom nav

// Divider
val Divider = Color(0xFFE5E7EB)
val DividerDark = Color(0xFF334155)

// ─────────────────────────────────────────────────────────────────
// LEGACY ALIASES (For backward compatibility)
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
val PrimaryPurple = Color(0xFF7C3AED)          // Kept for any legacy use
