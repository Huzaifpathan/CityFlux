package com.example.cityflux.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cityflux.ui.admin.AdminDashboardScreen
import com.example.cityflux.ui.citizen.MyReportsScreen
import com.example.cityflux.ui.dashboard.CitizenMainScreen
import com.example.cityflux.ui.login.ForgotPasswordScreen
import com.example.cityflux.ui.login.LoginScreen
import com.example.cityflux.ui.police.PoliceDashboardScreen
import com.example.cityflux.ui.register.RegisterScreen
import com.example.cityflux.ui.report.ReportIssueScreen
import com.example.cityflux.ui.splash.SplashScreen
import com.example.cityflux.ui.parking.ParkingScreen
import com.example.cityflux.ui.map.MapScreen
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
        startDestination = Routes.SPLASH,
        enterTransition = { fadeInSpec + slideInFromRight },
        exitTransition = { fadeOutSpec + slideOutToLeft },
        popEnterTransition = { fadeInSpec + slideInFromLeft },
        popExitTransition = { fadeOutSpec + slideOutToRight }
    ) {

        // 🚀 SPLASH SCREEN
        composable(
            route = Routes.SPLASH,
            enterTransition = { fadeIn(tween(0)) },
            exitTransition = { fadeOutSpec }
        ) {
            SplashScreen { userRole ->
                val destination = when (userRole) {
                    "citizen" -> Routes.CITIZEN_DASHBOARD
                    "police" -> Routes.POLICE_DASHBOARD
                    "admin" -> Routes.ADMIN_DASHBOARD
                    else -> Routes.LOGIN
                }
                navController.navigate(destination) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }

        // 🔐 LOGIN
        composable(
            route = Routes.LOGIN,
            enterTransition = { fadeInSpec },
            exitTransition = { fadeOutSpec }
        ) {
            LoginScreen(
                onCitizenLogin = {
                    navController.navigate(Routes.CITIZEN_DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onPoliceLogin = {
                    navController.navigate(Routes.POLICE_DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onRegisterClick = {
                    navController.navigate(Routes.REGISTER)
                },
                onForgotClick = {
                    navController.navigate(Routes.FORGOT_PASSWORD)
                }
            )
        }


        // 📝 REGISTER
        composable(Routes.REGISTER) {
            RegisterScreen(
                onCitizenRegistered = {
                    navController.navigate(Routes.CITIZEN_DASHBOARD) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onPoliceRegistered = {
                    navController.navigate(Routes.POLICE_DASHBOARD) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onLoginClick = {
                    navController.popBackStack()
                }
            )
        }


        // 🔑 FORGOT PASSWORD
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }


        // 🧑 CITIZEN DASHBOARD (Bottom Nav with 6 tabs)
        composable(Routes.CITIZEN_DASHBOARD) {
            CitizenMainScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CITIZEN_DASHBOARD) { inclusive = true }
                    }
                }
            )
        }


        // 👨‍💼 ADMIN DASHBOARD
        composable(Routes.ADMIN_DASHBOARD) {
            AdminDashboardScreen()
        }

        // 🚓 POLICE DASHBOARD
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
        composable(Routes.MY_REPORTS) {
            MyReportsScreen()
        }

        // 🅿️ PARKING
        composable(Routes.PARKING) {
            ParkingScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 🗺️ MAP
        composable(Routes.MAP) {
            MapScreen(
                onBack = { navController.popBackStack() }
            )
        }

    }
}
