package com.example.cityflux.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.R

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
    Image(
        painter = painterResource(id = R.drawable.cityfluxlogo),
        contentDescription = "CityFlux Logo",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
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
