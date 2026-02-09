package com.example.cityflux.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.ui.theme.CityFluxLogo
import com.example.cityflux.ui.theme.cityFluxColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════════
// CityFlux Splash Screen — Two-Phase Premium Launch Experience
//
// Phase 1 (≈2s): Logo fades in with subtle zoom — pure minimal
// Phase 2 (≈2s): Logo glides upward, app name + tagline fade in
//
// Background: Firebase auth state check runs concurrently.
// Navigation fires only after both animation AND init complete.
// ═══════════════════════════════════════════════════════════════════

/**
 * @param onSplashComplete receives the authenticated user's role
 *        (null = not logged in → navigate to login)
 */
@Composable
fun SplashScreen(
    onSplashComplete: (userRole: String?) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF0F172A) else Color.White
    val colors = MaterialTheme.cityFluxColors

    // Animation state
    var logoVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }

    // Background initialization + animation sequencing
    LaunchedEffect(Unit) {
        // Start auth check concurrently
        val roleDeferred = async {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val doc = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(user.uid)
                        .get()
                        .await()
                    doc.getString("role")
                } else null
            } catch (_: Exception) {
                null
            }
        }

        // ── Phase 1: Logo fade-in ──
        delay(150) // Brief pause before animation starts
        logoVisible = true
        delay(2000)

        // ── Phase 2: Text reveal ──
        textVisible = true
        delay(2000)

        // Ensure auth check is finished
        val userRole = roleDeferred.await()
        onSplashComplete(userRole)
    }

    // ── Logo animations ──
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.96f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    val logoOffsetY by animateDpAsState(
        targetValue = if (textVisible) (-36).dp else 0.dp,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "logoOffsetY"
    )

    // ── Layout ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            CityFluxLogo(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                        translationY = logoOffsetY.toPx()
                    },
                size = 150.dp,
                useDarkVariant = isDark
            )

            // ── Phase 2 Content ──
            AnimatedVisibility(
                visible = textVisible,
                enter = fadeIn(tween(900, easing = FastOutSlowInEasing)) +
                        expandVertically(tween(700, easing = FastOutSlowInEasing))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text(
                        text = "CityFlux",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Smart Mobility · Safer Roads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}
