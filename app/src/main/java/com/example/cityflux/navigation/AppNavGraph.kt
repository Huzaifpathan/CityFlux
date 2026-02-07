package com.example.cityflux.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cityflux.ui.admin.AdminDashboardScreen
import com.example.cityflux.ui.citizen.MyReportsScreen
import com.example.cityflux.ui.dashboard.CitizenDashboardScreen
import com.example.cityflux.ui.login.ForgotPasswordScreen
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
                onCitizenLogin = {
                    navController.navigate(Routes.CITIZEN_DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onAdminLogin = {
                    navController.navigate(Routes.ADMIN_DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onPoliceLogin = {
                    navController.navigate(Routes.POLICE_DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onRoleSelection = {
                    navController.navigate(Routes.ROLE) {
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
                onRegisterSuccess = {
                    navController.navigate(Routes.ROLE) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
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

        // 🧑 CITIZEN DASHBOARD
        composable(Routes.CITIZEN_DASHBOARD) {
            CitizenDashboardScreen(
                onReportIssue = {
                    navController.navigate(Routes.REPORT)
                },
                onViewParking = { /* later */ },
                onViewAlerts = {
                    navController.navigate(Routes.MY_REPORTS)
                },
                onProfile = { /* later */ },
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

    }
}
