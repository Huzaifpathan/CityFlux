package com.example.cityflux.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cityflux.ui.admin.AdminDashboardScreen
import com.example.cityflux.ui.dashboard.CitizenDashboardScreen
import com.example.cityflux.ui.login.LoginScreen
import com.example.cityflux.ui.police.PoliceDashboardScreen
import com.example.cityflux.ui.register.RegisterScreen
import com.example.cityflux.ui.report.ReportIssueScreen
import com.example.cityflux.ui.role.RoleSelectionScreen
import com.example.cityflux.ui.theme.AnimationDurations

// Screen transition animations
private val fadeInSpec = fadeIn(animationSpec = tween(AnimationDurations.SCREEN_TRANSITION))
private val fadeOutSpec = fadeOut(animationSpec = tween(AnimationDurations.SCREEN_TRANSITION))

private val slideInFromRight = slideInHorizontally(
    initialOffsetX = { it / 3 },
    animationSpec = tween(AnimationDurations.SCREEN_TRANSITION)
)

private val slideOutToLeft = slideOutHorizontally(
    targetOffsetX = { -it / 3 },
    animationSpec = tween(AnimationDurations.SCREEN_TRANSITION)
)

private val slideInFromLeft = slideInHorizontally(
    initialOffsetX = { -it / 3 },
    animationSpec = tween(AnimationDurations.SCREEN_TRANSITION)
)

private val slideOutToRight = slideOutHorizontally(
    targetOffsetX = { it / 3 },
    animationSpec = tween(AnimationDurations.SCREEN_TRANSITION)
)

@Composable
fun AppNavGraph(navController: NavHostController) {

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
        enterTransition = { fadeInSpec + slideInFromRight },
        exitTransition = { fadeOutSpec + slideOutToLeft },
        popEnterTransition = { fadeInSpec + slideInFromLeft },
        popExitTransition = { fadeOutSpec + slideOutToRight }
    ) {

        // 🔐 LOGIN
        composable(
            route = Routes.LOGIN,
            enterTransition = { fadeInSpec },
            exitTransition = { fadeOutSpec }
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ROLE) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onRegisterClick = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        // 📝 REGISTER
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.ROLE) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                }
            )
        }

        // 👥 ROLE SELECTION
        composable(
            route = Routes.ROLE,
            enterTransition = { fadeInSpec },
            exitTransition = { fadeOutSpec + slideOutToLeft }
        ) {
            RoleSelectionScreen(
                onCitizenClick = {
                    navController.navigate(Routes.CITIZEN_DASHBOARD) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                    }
                },
                onAdminClick = {
                    navController.navigate(Routes.ADMIN_DASHBOARD) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                    }
                },
                onPoliceClick = {
                    navController.navigate(Routes.POLICE_DASHBOARD) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                    }
                }
            )
        }

        // 🧑‍💻 CITIZEN
        composable(Routes.CITIZEN_DASHBOARD) {
            CitizenDashboardScreen(
                onParkingClick = {},
                onTrafficClick = {},
                onUpdatesClick = {},
                onReportClick = {
                    navController.navigate(Routes.REPORT)
                }
            )
        }

        // 👨‍💼 ADMIN
        composable(Routes.ADMIN_DASHBOARD) {
            AdminDashboardScreen()
        }

        // 🚓 POLICE
        composable(Routes.POLICE_DASHBOARD) {
            PoliceDashboardScreen()
        }

        // 📝 REPORT ISSUE
        composable(Routes.REPORT) {
            ReportIssueScreen(
                onReportSubmitted = {
                    navController.popBackStack()
                }
            )
        }
    }
}
