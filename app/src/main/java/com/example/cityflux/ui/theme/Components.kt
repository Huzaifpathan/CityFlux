package com.example.cityflux.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════
// CityFlux Modern UI Components - Clean Professional Design
// White backgrounds, subtle shadows, colored accents
// ═══════════════════════════════════════════════════════════════════

/**
 * Clean white background container for all screens
 */
@Composable
fun CleanBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceWhite),
        content = content
    )
}

/**
 * Gradient background for splash/login screens only
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8FAFC),
                        Color(0xFFFFFFFF)
                    )
                )
            ),
        content = content
    )
}

/**
 * Modern top app bar with notification and profile icons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityFluxTopBar(
    title: String = "CityFlux",
    showNotification: Boolean = true,
    showProfile: Boolean = true,
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        actions = {
            if (showNotification) {
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notifications",
                        tint = TextSecondary
                    )
                }
            }
            if (showProfile) {
                IconButton(onClick = onProfileClick) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profile",
                        tint = TextSecondary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SurfaceWhite,
            titleContentColor = TextPrimary
        ),
        modifier = modifier
    )
}

/**
 * Unified screen header on white background
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

/**
 * Modern card with colored left accent border
 */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    accentColor: Color = AccentTraffic,
    elevation: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(14.dp),
                ambientColor = CardShadow,
                spotColor = CardShadowMedium
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        onClick = onClick ?: {}
    ) {
        Row {
            // Colored accent bar on left
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * Modern white card with soft shadow (12-16px radius)
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(14.dp),
                ambientColor = CardShadow,
                spotColor = CardShadowMedium
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

/**
 * Dashboard action card with icon and colored top accent
 */
@Composable
fun DashboardActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = AccentTraffic
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = CardShadow,
                spotColor = CardShadowMedium
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        onClick = onClick
    ) {
        Column {
            // Top accent strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accentColor)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon container with subtle background
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Primary button - solid blue with white text
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(52.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonPrimary,
            contentColor = TextOnPrimary,
            disabledContainerColor = ButtonDisabled,
            disabledContentColor = TextSecondary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
            focusedElevation = 4.dp,
            hoveredElevation = 4.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = TextOnPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Secondary button - outlined style
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PrimaryBlue
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (enabled) PrimaryBlue else ButtonDisabled
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Modern text field with floating label and blue focus border
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { 
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            ) 
        },
        modifier = modifier.fillMaxWidth(),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = InputBorderFocused,
            unfocusedBorderColor = InputBorder,
            focusedLabelColor = PrimaryBlue,
            unfocusedLabelColor = TextSecondary,
            cursorColor = PrimaryBlue,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}

/**
 * Status chip with category-appropriate colors
 */
@Composable
fun StatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (status.lowercase()) {
        "resolved" -> AccentGreen.copy(alpha = 0.12f) to AccentGreen
        "in progress" -> AccentOrange.copy(alpha = 0.12f) to AccentOrange
        "pending" -> AccentRed.copy(alpha = 0.12f) to AccentRed
        else -> PrimaryBlue.copy(alpha = 0.12f) to PrimaryBlue
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

/**
 * Category chip for filtering
 */
@Composable
fun CategoryChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = PrimaryBlue.copy(alpha = 0.1f),
            labelColor = TextSecondary,
            selectedLabelColor = PrimaryBlue
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = CardBorder,
            selectedBorderColor = PrimaryBlue,
            enabled = true,
            selected = selected
        ),
        modifier = modifier
    )
}

/**
 * Role selection card
 */
@Composable
fun RoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = CardShadow,
                spotColor = CardShadowMedium
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimaryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Issue card for police/admin dashboards
 */
@Composable
fun IssueCard(
    modifier: Modifier = Modifier,
    accentColor: Color = AccentIssues,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = CardShadow,
                spotColor = CardShadowMedium
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {
        Row {
            // Colored accent bar on left
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * Unified app bar for screens with TopAppBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SurfaceWhite,
            titleContentColor = TextPrimary
        )
    )
}

/**
 * Dark card variant (kept for compatibility)
 */
@Composable
fun AppCardDark(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = CardShadow,
                spotColor = CardShadowMedium
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Thin circular loading spinner
 */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = PrimaryBlue
) {
    CircularProgressIndicator(
        modifier = modifier.size(32.dp),
        color = color,
        strokeWidth = 2.dp
    )
}

/**
 * Minimal loading indicator
 */
@Composable
fun MinimalLoader(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loaderAlpha"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        PrimaryBlue.copy(
                            alpha = alpha * (1f - (index * 0.15f))
                        )
                    )
            )
        }
    }
}

/**
 * Gradient button (kept for compatibility)
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonPrimary,
            disabledContainerColor = ButtonDisabled
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = TextOnPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextOnPrimary
            )
        }
    }
}
