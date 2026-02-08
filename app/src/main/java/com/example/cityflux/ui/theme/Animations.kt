package com.example.cityflux.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

// ═══════════════════════════════════════════════════════════════════
// CityFlux Animation Utilities - Modern, Professional Transitions
// Smooth animations, haptic feedback, Material ripple effects
// ═══════════════════════════════════════════════════════════════════

// Animation durations - consistent across the app
object AnimationDurations {
    const val INSTANT = 100
    const val FAST = 150
    const val NORMAL = 300
    const val SLOW = 500
    const val SCREEN_TRANSITION = 350
    const val MESSAGE_FADE = 200
    const val CARD_LIFT = 150
}

// Animation easing curves
object AnimationEasing {
    val Standard = FastOutSlowInEasing
    val Decelerate = LinearOutSlowInEasing
    val Accelerate = FastOutLinearInEasing
    val Bounce = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

// ═══════════════════════════════════════════════════════════════════
// Screen Transition Animations
// ═══════════════════════════════════════════════════════════════════

// Fade in/out for activity transitions - smooth professional style
val fadeInSpec = fadeIn(
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = AnimationEasing.Standard
    )
)

val fadeOutSpec = fadeOut(
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = AnimationEasing.Standard
    )
)

// Slide animations for fragment/tab transitions (horizontal)
val slideInFromRight = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth / 4 },
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = AnimationEasing.Standard
    )
)

val slideOutToLeft = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth / 4 },
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = AnimationEasing.Standard
    )
)

val slideInFromLeft = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth / 4 },
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = AnimationEasing.Standard
    )
)

val slideOutToRight = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth / 4 },
    animationSpec = tween(
        durationMillis = AnimationDurations.SCREEN_TRANSITION,
        easing = AnimationEasing.Standard
    )
)

// Slide up animation for bottom sheets and cards
val slideUp = slideInVertically(
    initialOffsetY = { fullHeight -> fullHeight / 6 },
    animationSpec = tween(
        durationMillis = AnimationDurations.NORMAL,
        easing = AnimationEasing.Decelerate
    )
)

val slideDown = slideOutVertically(
    targetOffsetY = { fullHeight -> fullHeight / 6 },
    animationSpec = tween(
        durationMillis = AnimationDurations.NORMAL,
        easing = AnimationEasing.Accelerate
    )
)

// Scale animations for modals and popups
val scaleIn = scaleIn(
    initialScale = 0.92f,
    animationSpec = tween(
        durationMillis = AnimationDurations.NORMAL,
        easing = AnimationEasing.Decelerate
    )
)

val scaleOut = scaleOut(
    targetScale = 0.92f,
    animationSpec = tween(
        durationMillis = AnimationDurations.FAST,
        easing = AnimationEasing.Accelerate
    )
)

// ═══════════════════════════════════════════════════════════════════
// Navigation Composable with Animations
// ═══════════════════════════════════════════════════════════════════

/**
 * Navigation composable with slide + fade transition (for main screens)
 */
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

/**
 * Fade-only transition for modal screens
 */
fun NavGraphBuilder.fadeComposable(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        enterTransition = { fadeInSpec + scaleIn },
        exitTransition = { fadeOutSpec + scaleOut },
        popEnterTransition = { fadeInSpec + scaleIn },
        popExitTransition = { fadeOutSpec + scaleOut }
    ) { backStackEntry ->
        content(backStackEntry)
    }
}

/**
 * Bottom-to-top transition for bottom sheets and modals
 */
fun NavGraphBuilder.bottomSheetComposable(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        enterTransition = { slideUp + fadeInSpec },
        exitTransition = { slideDown + fadeOutSpec },
        popEnterTransition = { slideUp + fadeInSpec },
        popExitTransition = { slideDown + fadeOutSpec }
    ) { backStackEntry ->
        content(backStackEntry)
    }
}

// ═══════════════════════════════════════════════════════════════════
// Modifier Extensions for UI Animations
// ═══════════════════════════════════════════════════════════════════

/**
 * Adds a subtle scale-down animation on press with Material ripple effect
 */
fun Modifier.pressEffect(
    pressScale: Float = 0.96f,
    onClick: () -> Unit = {}
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = tween(
            durationMillis = AnimationDurations.FAST,
            easing = AnimationEasing.Standard
        ),
        label = "pressScale"
    )
    
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = rememberRipple(bounded = true),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        )
}

/**
 * Adds a lift animation on tap (card hover/tap effect)
 */
fun Modifier.liftOnPress(
    liftAmount: Float = 8f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) liftAmount else 0f,
        animationSpec = tween(
            durationMillis = AnimationDurations.CARD_LIFT,
            easing = AnimationEasing.Standard
        ),
        label = "liftElevation"
    )
    
    this.graphicsLayer {
        translationY = -elevation
        shadowElevation = elevation
    }
}

/**
 * Adds ripple effect with haptic feedback on tap
 */
fun Modifier.rippleClickable(
    bounded: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = rememberRipple(bounded = bounded),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    )
}

/**
 * Adds a slight scale bounce animation
 */
fun Modifier.bounceClick(
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale",
        finishedListener = { isPressed = false }
    )
    
    this
        .scale(scale)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = rememberRipple(bounded = true),
            onClick = {
                isPressed = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        )
}

// ═══════════════════════════════════════════════════════════════════
// Visibility Animations
// ═══════════════════════════════════════════════════════════════════

/**
 * Fade-in animation for list items and messages
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
                durationMillis = AnimationDurations.MESSAGE_FADE,
                delayMillis = delay,
                easing = AnimationEasing.Standard
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = AnimationDurations.FAST,
                easing = AnimationEasing.Standard
            )
        )
    ) {
        content()
    }
}

/**
 * Slide-up fade-in animation for cards and list items
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
                easing = AnimationEasing.Standard
            )
        ) + slideInVertically(
            initialOffsetY = { it / 8 },
            animationSpec = tween(
                durationMillis = AnimationDurations.NORMAL,
                delayMillis = delay,
                easing = AnimationEasing.Decelerate
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

/**
 * Message fade-in animation for chat bubbles
 */
@Composable
fun MessageFadeIn(
    visible: Boolean = true,
    fromEnd: Boolean = true,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDurations.MESSAGE_FADE,
                easing = AnimationEasing.Standard
            )
        ) + slideInHorizontally(
            initialOffsetX = { if (fromEnd) it / 4 else -it / 4 },
            animationSpec = tween(
                durationMillis = AnimationDurations.MESSAGE_FADE,
                easing = AnimationEasing.Decelerate
            )
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = AnimationDurations.FAST)
        )
    ) {
        content()
    }
}

/**
 * Expand/collapse animation for expandable panels
 */
@Composable
fun ExpandAnimation(
    expanded: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            animationSpec = tween(
                durationMillis = AnimationDurations.NORMAL,
                easing = AnimationEasing.Standard
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDurations.NORMAL,
                easing = AnimationEasing.Standard
            )
        ),
        exit = shrinkVertically(
            animationSpec = tween(
                durationMillis = AnimationDurations.NORMAL,
                easing = AnimationEasing.Standard
            )
        ) + fadeOut(
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
                easing = LinearEasing
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
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = AnimationEasing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    content(scale)
}

/**
 * Rotation animation for loading spinners
 */
@Composable
fun rememberRotationAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )
    return rotation
}

// ═══════════════════════════════════════════════════════════════════
// Staggered Animation Delays
// ═══════════════════════════════════════════════════════════════════

/**
 * Calculate staggered delay for list item animations
 */
fun staggeredDelay(index: Int, baseDelay: Int = 50): Int {
    return (index * baseDelay).coerceAtMost(400) // Cap at 400ms
}

/**
 * Calculate reverse staggered delay (for exit animations)
 */
fun reverseStaggeredDelay(index: Int, totalItems: Int, baseDelay: Int = 30): Int {
    return ((totalItems - 1 - index) * baseDelay).coerceAtMost(200)
}

// ═══════════════════════════════════════════════════════════════════
// Smooth Scroll Utilities
// ═══════════════════════════════════════════════════════════════════

/**
 * Fling behavior configuration for smooth scrolling
 */
val smoothScrollSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)
