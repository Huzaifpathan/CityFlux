package com.example.cityflux.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
// Theme-aware with full light/dark mode support
// ═══════════════════════════════════════════════════════════════════

// Standard corner radius values
object CornerRadius {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 14.dp
    val XLarge = 16.dp
    val Round = 24.dp
}

// Standard spacing values
object Spacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 20.dp
    val XXLarge = 24.dp
    val Section = 32.dp
}

/**
 * Clean background container for all screens - theme aware
 */
@Composable
fun CleanBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        content = content
    )
}

/**
 * Subtle gradient background for login/auth screens — uses blue-tinted whites
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val gradientColors = if (colors.isDark) {
        listOf(
            Color(0xFF0F172A),
            Color(0xFF1E293B),
            Color(0xFF0F172A)
        )
    } else {
        listOf(
            GradientWhite,      // Very faint blue-tint
            Color(0xFFFFFFFF),  // Pure white center
            GradientWhite       // Faint blue-tint
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors)),
        content = content
    )
}

/**
 * Modern top app bar with logo, notification and profile icons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityFluxTopBar(
    title: String = "CityFlux",
    showBack: Boolean = false,
    showNotification: Boolean = true,
    showProfile: Boolean = true,
    onBackClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!showBack) {
                    CityFluxLogo(size = 30.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
            }
        },
        actions = {
            if (showNotification) {
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notifications",
                        tint = colors.textSecondary
                    )
                }
            }
            if (showProfile) {
                IconButton(onClick = onProfileClick) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profile",
                        tint = colors.textSecondary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = colors.textPrimary
        ),
        modifier = modifier
    )
}

/**
 * Unified screen header - theme aware
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(Spacing.XSmall))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}

/**
 * Modern card with colored left accent border (12-16px radius)
 * Blue for Traffic, Green for Parking, Red for Issues, Orange for Alerts
 */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    accentColor: Color = AccentTraffic,
    elevation: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground
        ),
        onClick = onClick ?: {}
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
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
                    .padding(Spacing.Large),
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
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.XLarge),
            content = content
        )
    }
}

/**
 * Dashboard action card with icon and colored top accent strip
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
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground
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
                // Icon container with subtle tinted background
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(CornerRadius.Medium))
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

                Spacer(modifier = Modifier.width(Spacing.Large))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(Spacing.XSmall))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Update card for the Updates feed with slide-up entrance animation
 */
@Composable
fun UpdateCard(
    title: String,
    subtitle: String,
    timestamp: String,
    category: UpdateCategory,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    val accentColor = when (category) {
        UpdateCategory.TRAFFIC -> AccentTraffic
        UpdateCategory.PARKING -> AccentParking
        UpdateCategory.ISSUES -> AccentIssues
        UpdateCategory.ALERTS -> AccentAlerts
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground
        ),
        onClick = onClick
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Large)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
                
                Spacer(modifier = Modifier.height(Spacing.Small))
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

enum class UpdateCategory {
    TRAFFIC, PARKING, ISSUES, ALERTS
}

/**
 * Primary button — gradient blue with white text, soft shadow, Material ripple
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
        modifier = modifier.height(52.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = ButtonDisabled,
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 3.dp,
            pressedElevation = 1.dp,
            focusedElevation = 4.dp,
            hoveredElevation = 4.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled && !loading) BlueGradientHorizontal
                    else Brush.horizontalGradient(listOf(ButtonDisabled, ButtonDisabled)),
                    shape = RoundedCornerShape(CornerRadius.Medium)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
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
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(CornerRadius.Medium),
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
    isError: Boolean = false,
    errorMessage: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.cityFluxColors
    
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { 
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            isError = isError,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(CornerRadius.Medium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isError) AccentRed else colors.inputBorderFocused,
                unfocusedBorderColor = if (isError) AccentRed else colors.inputBorder,
                focusedLabelColor = if (isError) AccentRed else PrimaryBlue,
                unfocusedLabelColor = if (isError) AccentRed else colors.textSecondary,
                cursorColor = PrimaryBlue,
                focusedContainerColor = colors.inputBackground,
                unfocusedContainerColor = colors.inputBackground,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                errorBorderColor = AccentRed,
                errorLabelColor = AccentRed,
                errorCursorColor = AccentRed
            )
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = AccentRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
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
        shape = RoundedCornerShape(CornerRadius.Small),
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
    val colors = MaterialTheme.cityFluxColors
    
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
        shape = RoundedCornerShape(CornerRadius.Small),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = PrimaryBlue.copy(alpha = 0.1f),
            labelColor = colors.textSecondary,
            selectedLabelColor = PrimaryBlue
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = colors.cardBorder,
            selectedBorderColor = PrimaryBlue,
            enabled = true,
            selected = selected
        ),
        modifier = modifier
    )
}

/**
 * Role selection card with modern design
 */
@Composable
fun RoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.XLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(CornerRadius.Large))
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
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(Spacing.XSmall))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Issue card for police/admin dashboards with left accent bar
 */
@Composable
fun IssueCard(
    modifier: Modifier = Modifier,
    accentColor: Color = AccentIssues,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
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
                    .padding(Spacing.Large),
                content = content
            )
        }
    }
}

/**
 * Unified app bar for screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    
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
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = colors.textPrimary
        )
    )
}

/**
 * Modern bottom navigation bar with blur background effect
 */
@Composable
fun CityFluxBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    NavigationBar(
        modifier = modifier,
        containerColor = colors.bottomNavBackground,
        tonalElevation = 0.dp
    ) {
        content()
    }
}

/**
 * Thin circular loading spinner - modern minimal style
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
 * Three-dot typing indicator animation - subtle professional style
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val delay = index * 150
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = alpha))
            )
        }
    }
}

/**
 * Minimal loading indicator (three animated dots)
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
                        PrimaryBlue.copy(alpha = alpha * (1f - (index * 0.15f)))
                    )
            )
        }
    }
}

/**
 * Chat bubble for sender (current user) - white with light blue border
 */
@Composable
fun SenderBubble(
    message: String,
    timestamp: String,
    isRead: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp
                    )
                ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp
            ),
            color = colors.cardBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                    // Read status ticks would go here
                }
            }
        }
    }
}

/**
 * Chat bubble for receiver - pure white
 */
@Composable
fun ReceiverBubble(
    message: String,
    timestamp: String,
    senderName: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                ),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = colors.cardBackground
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (senderName != null) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }
    }
}

/**
 * Circular avatar with thin accent border
 */
@Composable
fun UserAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    accentColor: Color = PrimaryBlue
) {
    val colors = MaterialTheme.cityFluxColors
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.1f))
            .border(1.5.dp, accentColor.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accentColor
        )
    }
}

/**
 * Floating action button with blur background - for maps
 */
@Composable
fun MapFloatingButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .shadow(8.dp, CircleShape),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = PrimaryBlue
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Transparent bottom sheet for parking/map info
 */
@Composable
fun InfoBottomSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.cardBackground.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.XLarge)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.divider)
                    .align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(Spacing.Large))
            
            content()
        }
    }
}

/**
 * Divider - horizontal separator
 */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = colors.divider
    )
}

/**
 * Gradient button — real blue gradient background
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val colors = MaterialTheme.cityFluxColors
    val gradientBrush = BlueGradientHorizontal

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = ButtonDisabled,
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 3.dp,
            pressedElevation = 1.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled && !loading) gradientBrush
                    else Brush.horizontalGradient(listOf(ButtonDisabled, ButtonDisabled)),
                    shape = RoundedCornerShape(CornerRadius.Medium)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Dark card variant (kept for compatibility) - now uses theme colors
 */
@Composable
fun AppCardDark(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground
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
