package com.research.detectmind.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.research.detectmind.data.local.entity.StudyEntity
import com.research.detectmind.ui.screens.consent.ConsentScreen
import com.research.detectmind.ui.screens.enrollment.*
import com.research.detectmind.ui.screens.main.MainScreen
import com.research.detectmind.ui.screens.splash.SplashScreen

sealed class Route(val path: String) {
    object Splash : Route("splash")
    object Home : Route("home")
    object EnrollmentGraph : Route("enrollment_graph")
    object StudyList : Route("study_list")
    object StudyDetail : Route("study_detail")
    object Consent : Route("consent")
    object PermissionOnboarding : Route("permission_onboarding")
    object EnrollmentSuccess : Route("enrollment_success")
}

@Composable
fun AppNavHost(navController: NavHostController) {
    var selectedStudy by remember { mutableStateOf<StudyEntity?>(null) }
    var enrolledDeviceId by remember { mutableStateOf<String?>(null) }
    var enabledSensorTypes by remember { mutableStateOf<List<String>>(emptyList()) }

    NavHost(navController = navController, startDestination = Route.Splash.path) {

        composable(Route.Splash.path) {
            SplashScreen(
                onEnrolled = { navController.navigate(Route.Home.path) { popUpTo(0) } },
                onNotEnrolled = { navController.navigate(Route.EnrollmentGraph.path) { popUpTo(0) } }
            )
        }

        // ── Enrollment flow: StudyList → StudyDetail → Consent → Permissions → Success ──
        navigation(
            startDestination = Route.StudyList.path,
            route = Route.EnrollmentGraph.path
        ) {
            composable(Route.StudyList.path) {
                StudyListScreen(
                    onStudySelected = { study ->
                        selectedStudy = study
                        navController.navigate(Route.StudyDetail.path)
                    }
                )
            }

            composable(Route.StudyDetail.path) {
                val study = selectedStudy ?: return@composable
                StudyDetailScreen(
                    study = study,
                    onEnrolled = { deviceId, sensorTypes ->
                        enrolledDeviceId = deviceId
                        enabledSensorTypes = sensorTypes
                        navController.navigate(Route.Consent.path)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Route.Consent.path) {
                ConsentScreen(
                    enabledSensorTypes = enabledSensorTypes,
                    onConsented = { navController.navigate(Route.PermissionOnboarding.path) },
                    onDeclined = {
                        // Decline returns to study list so user can pick a different study
                        navController.navigate(Route.StudyList.path) {
                            popUpTo(Route.StudyList.path) { inclusive = true }
                        }
                    }
                )
            }

            composable(Route.PermissionOnboarding.path) {
                PermissionOnboardingScreen(
                    enabledSensorTypes = enabledSensorTypes,
                    onContinue = { navController.navigate(Route.EnrollmentSuccess.path) }
                )
            }

            composable(Route.EnrollmentSuccess.path) {
                val deviceId = enrolledDeviceId ?: return@composable
                EnrollmentSuccessScreen(
                    deviceId = deviceId,
                    onStartMonitoring = {
                        navController.navigate(Route.Home.path) { popUpTo(0) }
                    }
                )
            }
        }

        composable(Route.Home.path) {
            MainScreen(
                onWithdrawn = {
                    navController.navigate(Route.EnrollmentGraph.path) { popUpTo(0) }
                }
            )
        }
    }
}
