package com.example.cityflux.navigation

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

@Composable
fun AppNavGraph(navController: NavHostController) {

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {

        // 🔐 LOGIN
        composable(Routes.LOGIN) {
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
        composable(Routes.ROLE) {
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
