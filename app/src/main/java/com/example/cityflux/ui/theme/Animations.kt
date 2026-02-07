package com.example.cityflux.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

// ═══════════════════════════════════════════════════════════════════
// CityFlux Animation Utilities - Modern, Professional Transitions
// ═══════════════════════════════════════════════════════════════════

// Animation durations
object AnimationDurations {
    const val FAST = 150
    const val NORMAL = 300
    const val SLOW = 500
    const val SCREEN_TRANSITION = 350
}

// ═══════════════════════════════════════════════════════════════════
// Screen Transition Animations
// ═══════════════════════════════════════════════════════════════════

// Fade in/out for activity transitions
val fadeInSpec = fadeIn(
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = FastOutSlowInEasing
    )
)

val fadeOutSpec = fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = FastOutSlowInEasing
    )
)

// Slide animations for fragment transitions (horizontal)
val slideInFromRight = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = FastOutSlowInEasing
    )
)

val slideOutToLeft = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth },
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = FastOutSlowInEasing
    )
)

val slideInFromLeft = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth },
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = FastOutSlowInEasing
    )
)

val slideOutToRight = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = FastOutSlowInEasing
    )
)

// Slide up animation for bottom sheets and cards
val slideUp = slideInVertically(
    initialOffsetY = { fullHeight -> fullHeight / 4 },
    animationSpec = tween(
        durationMillis = AnimationDurations.NORMAL,
        easing = FastOutSlowInEasing
    )
)

val slideDown = slideOutVertically(
    targetOffsetY = { fullHeight -> fullHeight / 4 },
    animationSpec = tween(
        durationMillis = AnimationDurations.NORMAL,
        easing = FastOutSlowInEasing
    )
)

// ═══════════════════════════════════════════════════════════════════
// Navigation Composable with Animations
// ═══════════════════════════════════════════════════════════════════

fun NavGraphBuilder.animatedComposable(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        enterTransition = { fadeInSpec + slideInFromRight },
        exitTransition = { fadeOutSpec + slideOutToLeft },
        popEnterTransition = { fadeInSpec + slideInFromLeft },
        popExitTransition = { fadeOutSpec + slideOutToRight }
    ) { backStackEntry ->
        content(backStackEntry)
    }
}

// Fade-only transition for modal screens
fun NavGraphBuilder.fadeComposable(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        enterTransition = { fadeInSpec },
        exitTransition = { fadeOutSpec },
        popEnterTransition = { fadeInSpec },
        popExitTransition = { fadeOutSpec }
    ) { backStackEntry ->
        content(backStackEntry)
    }
}

// ═══════════════════════════════════════════════════════════════════
// Modifier Extensions for UI Animations
// ═══════════════════════════════════════════════════════════════════

/**
 * Adds a subtle scale-down animation on press (button tap effect)
 */
fun Modifier.pressEffect(
    pressScale: Float = 0.96f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = tween(
            durationMillis = AnimationDurations.FAST,
            easing = FastOutSlowInEasing
        ),
        label = "pressScale"
    )
    
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {}
        )
}

/**
 * Adds a lift animation on tap (card hover effect)
 */
fun Modifier.liftOnPress(
    liftAmount: Float = 8f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) liftAmount else 0f,
        animationSpec = tween(
            durationMillis = AnimationDurations.FAST,
            easing = FastOutSlowInEasing
        ),
        label = "liftElevation"
    )
    
    this.graphicsLayer {
        translationY = -elevation
        shadowElevation = elevation
    }
}

/**
 * Fade-in animation for list items
 */
@Composable
fun FadeInAnimation(
    visible: Boolean = true,
    delay: Int = 0,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDurations.NORMAL,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            )
        ) + slideUp,
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = AnimationDurations.FAST,
                easing = FastOutSlowInEasing
            )
        )
    ) {
        content()
    }
}

/**
 * Slide-up fade-in animation for cards
 */
@Composable
fun SlideUpFadeIn(
    visible: Boolean = true,
    delay: Int = 0,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDurations.NORMAL,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            )
        ) + slideInVertically(
            initialOffsetY = { it / 6 },
            animationSpec = tween(
                durationMillis = AnimationDurations.NORMAL,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = AnimationDurations.FAST
            )
        )
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════════════════════════
// Shimmer Loading Effect
// ═══════════════════════════════════════════════════════════════════

@Composable
fun rememberShimmerAnimation(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return translateAnim
}

// ═══════════════════════════════════════════════════════════════════
// Loading Indicator Animations
// ═══════════════════════════════════════════════════════════════════

@Composable
fun PulsingAnimation(
    content: @Composable (Float) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    content(scale)
}

// ═══════════════════════════════════════════════════════════════════
// Staggered Animation Delays
// ═══════════════════════════════════════════════════════════════════

/**
 * Calculate staggered delay for list item animations
 */
fun staggeredDelay(index: Int, baseDelay: Int = 50): Int {
    return index * baseDelay
}
