package com.research.detectmind.ui.screens.main

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.research.detectmind.ui.components.PermSheet
import com.research.detectmind.ui.components.PermSheetKind
import com.research.detectmind.ui.components.PermissionBottomSheet
import com.research.detectmind.ui.components.permSheetForKind
import com.research.detectmind.ui.screens.home.HomeScreen
import com.research.detectmind.ui.screens.home.HomeViewModel
import com.research.detectmind.ui.screens.home.HomeSensorPermKind
import com.research.detectmind.ui.screens.home.StatusChip
import com.research.detectmind.ui.screens.home.runtimePermsForSensor
import com.research.detectmind.ui.screens.home.sensorPermKind
import com.research.detectmind.ui.screens.settings.SettingsScreen

private enum class MainTab { Home, Settings }

private fun permSheetFor(kind: HomeSensorPermKind, sensorLabel: String, packageName: String): PermSheet? = when (kind) {
    HomeSensorPermKind.USAGE_STATS -> permSheetForKind(PermSheetKind.USAGE_STATS, sensorLabel, packageName)
    HomeSensorPermKind.NOTIFICATION_LISTENER -> permSheetForKind(PermSheetKind.NOTIFICATION_LISTENER, sensorLabel, packageName)
    HomeSensorPermKind.ACCESSIBILITY -> permSheetForKind(PermSheetKind.ACCESSIBILITY, sensorLabel, packageName)
    HomeSensorPermKind.RUNTIME -> null
}

private fun sensorLabel(sensorType: String): String = when (sensorType) {
    "app_usage"          -> "App Usage"
    "notifications"      -> "Notifications"
    "screen_interaction" -> "Screen Touch"
    "location"           -> "Location"
    "calls"              -> "Call Log"
    "sms"                -> "SMS Metadata"
    "light"              -> "Ambient Light"
    "battery"            -> "Battery"
    "screen_state"       -> "Screen State"
    "esm_ema"            -> "ESM Surveys"
    else                 -> sensorType.replace("_", " ").replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onWithdrawn: () -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = hiltViewModel()

    var pendingSheet by remember { mutableStateOf<PermSheet?>(null) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> homeViewModel.refreshPermissionIssues() }

    val activity = context as? androidx.activity.ComponentActivity

    val onRequestPermission: (String) -> Unit = { sensorType ->
        val kind = sensorPermKind(sensorType)
        when (kind) {
            HomeSensorPermKind.RUNTIME -> {
                val perms = runtimePermsForSensor(sensorType)
                if (perms.isNotEmpty()) {
                // Check if any permission is permanently denied (don't show rationale + not granted)
                val permanentlyDenied = perms.any { perm ->
                    ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED &&
                    activity?.shouldShowRequestPermissionRationale(perm) == false
                }
                if (permanentlyDenied) {
                    // Can't show system dialog — send to app settings
                    pendingSheet = PermSheet(
                        title = "Permission Required",
                        description = "This permission was previously denied. Please open app settings and grant it manually.",
                        settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    permissionsLauncher.launch(perms.toTypedArray())
                }
                } // end if (perms.isNotEmpty())
            }
            else -> {
                val sheet = permSheetFor(kind, sensorLabel(sensorType), context.packageName)
                if (homeViewModel.state.value.guidedPermissions && sheet != null) {
                    pendingSheet = sheet
                } else {
                    val intent = when (kind) {
                        HomeSensorPermKind.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        HomeSensorPermKind.NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        HomeSensorPermKind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        HomeSensorPermKind.RUNTIME -> null
                    }
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
                }
            }
        }
    }

    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val studyStatus = homeState.study?.status
    val participantActive = homeState.participant?.status == "active"

    pendingSheet?.let { sheet ->
        PermissionBottomSheet(sheet = sheet, onDismiss = { pendingSheet = null })
    }

    Scaffold(
        topBar = {
            when (selectedTab) {
                MainTab.Home -> TopAppBar(
                    title = {
                        Text(
                            "DETECTMiND",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    },
                    actions = {
                        if (!homeState.loading) {
                            StatusChip(studyStatus = studyStatus, participantActive = participantActive)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                MainTab.Settings -> TopAppBar(
                    title = {
                        Text(
                            "DETECTMiND",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(tonalElevation = 4.dp) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Home,
                    onClick = { selectedTab = MainTab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", style = MaterialTheme.typography.labelMedium) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Settings,
                    onClick = { selectedTab = MainTab.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            MainTab.Home -> HomeScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onRequestPermission = onRequestPermission,
                viewModel = homeViewModel
            )
            MainTab.Settings -> SettingsScreen(
                onWithdrawn = onWithdrawn,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}
