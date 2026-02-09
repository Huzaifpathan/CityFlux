package com.example.cityflux.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════
// CityFlux Brand Identity — Compose Canvas Logo + Brand Mark
// GPS Pin + City Skyline + Traffic Signal + Road
// Smooth gradient rendering with full dark mode support
// ═══════════════════════════════════════════════════════════════════

// Brand gradient colors
object BrandGradient {
    // Light mode pin gradient (top → bottom)
    val LightStops = listOf(
        Color(0xFF0C2461),  // Deep navy
        Color(0xFF1E3A8A),  // Royal blue
        Color(0xFF1E40AF),  // Medium blue
        Color(0xFF2563EB)   // Primary blue
    )
    // Dark mode pin gradient (brighter for contrast)
    val DarkStops = listOf(
        Color(0xFF1E40AF),
        Color(0xFF2563EB),
        Color(0xFF3B82F6),
        Color(0xFF60A5FA)
    )
    // Gradient brush for buttons, headers, accents
    val ButtonGradient = Brush.horizontalGradient(
        listOf(Color(0xFF1E40AF), Color(0xFF2563EB), Color(0xFF3B82F6))
    )
    val HeaderGradient = Brush.horizontalGradient(
        listOf(Color(0xFF0C2461), Color(0xFF1E3A8A), Color(0xFF2563EB))
    )
}

/**
 * CityFlux Logo — "Smart City Pin"
 *
 * A GPS location pin containing:
 * - Minimal city skyline (3 buildings with peaked roofs)
 * - Traffic signal indicator (3 graduated blue dots)
 * - Converging road with center dashes
 *
 * Rendered with Canvas for smooth gradient fills.
 * Design viewport: 512×512, scaled to [size].
 */
@Composable
fun CityFluxLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    useDarkVariant: Boolean? = null
) {
    val isDark = useDarkVariant ?: MaterialTheme.cityFluxColors.isDark

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width / 512f

        // ── Pin Shape ──
        val pinPath = Path().apply {
            moveTo(256f * s, 460f * s)
            cubicTo(224f * s, 395f * s, 88f * s, 298f * s, 88f * s, 186f * s)
            cubicTo(88f * s, 93f * s, 163f * s, 18f * s, 256f * s, 18f * s)
            cubicTo(349f * s, 18f * s, 424f * s, 93f * s, 424f * s, 186f * s)
            cubicTo(424f * s, 298f * s, 288f * s, 395f * s, 256f * s, 460f * s)
            close()
        }

        val gradientColors = if (isDark) BrandGradient.DarkStops else BrandGradient.LightStops
        val pinBrush = Brush.verticalGradient(
            colors = gradientColors,
            startY = 18f * s,
            endY = 460f * s
        )
        drawPath(pinPath, brush = pinBrush)

        // ── White elements inside pin ──
        val white = Color.White

        // Building Left (short)
        val bLeft = Path().apply {
            moveTo(158f * s, 248f * s)
            lineTo(158f * s, 172f * s)
            lineTo(178f * s, 150f * s)
            lineTo(198f * s, 172f * s)
            lineTo(198f * s, 248f * s)
            close()
        }
        drawPath(bLeft, color = white)

        // Building Center (tallest)
        val bCenter = Path().apply {
            moveTo(230f * s, 248f * s)
            lineTo(230f * s, 94f * s)
            lineTo(256f * s, 68f * s)
            lineTo(282f * s, 94f * s)
            lineTo(282f * s, 248f * s)
            close()
        }
        drawPath(bCenter, color = white)

        // Building Right (medium)
        val bRight = Path().apply {
            moveTo(314f * s, 248f * s)
            lineTo(314f * s, 150f * s)
            lineTo(334f * s, 128f * s)
            lineTo(354f * s, 150f * s)
            lineTo(354f * s, 248f * s)
            close()
        }
        drawPath(bRight, color = white)

        // ── Traffic Signal Dots (graduated blues, top→bottom) ──
        drawCircle(color = Color(0xFF60A5FA), radius = 11f * s, center = Offset(256f * s, 122f * s))
        drawCircle(color = Color(0xFF38BDF8), radius = 11f * s, center = Offset(256f * s, 158f * s))
        drawCircle(color = Color(0xFF7DD3FC), radius = 11f * s, center = Offset(256f * s, 194f * s))

        // ── Ground Line ──
        drawRect(
            color = white,
            topLeft = Offset(130f * s, 250f * s),
            size = Size(252f * s, 8f * s)
        )

        // ── Road (converging lanes) ──
        val road = Path().apply {
            moveTo(196f * s, 260f * s)
            lineTo(248f * s, 400f * s)
            lineTo(264f * s, 400f * s)
            lineTo(316f * s, 260f * s)
            close()
        }
        drawPath(road, color = white)

        // ── Road Center Dashes ──
        val dashColor = if (isDark) Color(0xFF1E40AF) else Color(0xFF1E3A8A)
        drawLine(dashColor, Offset(256f * s, 274f * s), Offset(256f * s, 302f * s), strokeWidth = 4.5f * s, cap = StrokeCap.Round)
        drawLine(dashColor, Offset(256f * s, 318f * s), Offset(256f * s, 346f * s), strokeWidth = 4.5f * s, cap = StrokeCap.Round)
        drawLine(dashColor, Offset(256f * s, 362f * s), Offset(256f * s, 390f * s), strokeWidth = 4f * s, cap = StrokeCap.Round)
    }
}

/**
 * Full brand mark: Logo + App Name + Tagline
 */
@Composable
fun CityFluxBrandMark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 80.dp,
    showTagline: Boolean = true,
    useDarkVariant: Boolean? = null
) {
    val colors = MaterialTheme.cityFluxColors

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CityFluxLogo(size = logoSize, useDarkVariant = useDarkVariant)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CityFlux",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        if (showTagline) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Smart Mobility · Safer Roads",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * Horizontal brand row for top bars: small logo + app name
 */
@Composable
fun CityFluxBrandRow(
    modifier: Modifier = Modifier,
    logoSize: Dp = 30.dp
) {
    val colors = MaterialTheme.cityFluxColors

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CityFluxLogo(size = logoSize)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "CityFlux",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
    }
}
