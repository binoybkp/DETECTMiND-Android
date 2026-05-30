package com.research.detectmind.ui.screens.enrollment

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.research.detectmind.ui.components.PermSheet
import com.research.detectmind.ui.components.PermSheetKind
import com.research.detectmind.ui.components.PermissionBottomSheet
import com.research.detectmind.ui.components.permSheetForKind
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingScreen(
    enabledSensorTypes: List<String>,
    guidedPermissions: Boolean = false,
    onContinue: () -> Unit,
    viewModel: EnrollmentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var pendingSheet by remember { mutableStateOf<PermSheet?>(null) }

    pendingSheet?.let { sheet ->
        PermissionBottomSheet(sheet = sheet, onDismiss = { pendingSheet = null })
    }

    // Launcher for requesting RUNTIME permissions directly in-app
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> viewModel.refreshPermissions(enabledSensorTypes) }

    // Refresh permission statuses every time the screen resumes (user comes back from Settings)
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermissions(enabledSensorTypes)
        }
    }

    // Auto-request RUNTIME permissions once when the screen first appears.
    // BACKGROUND_LOCATION must be excluded — Android requires it to be granted separately via Settings.
    LaunchedEffect(state.permissionStatuses) {
        val runtimePerms = state.permissionStatuses
            .filter { !it.granted && it.sensorPermission.kind == PermissionKind.RUNTIME }
            .flatMap { it.sensorPermission.permissions }
            .distinct()
        if (runtimePerms.isNotEmpty()) {
            runtimeLauncher.launch(runtimePerms.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Permissions") })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "The following permissions are needed for the sensors enabled in your study. " +
                        "Tap the arrow to open the relevant Settings page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (state.permissionStatuses.isEmpty()) {
                    item {
                        Text(
                            "No permissions required for the enabled sensors.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.permissionStatuses) { status ->
                        PermissionCard(
                            status = status,
                            onGrant = {
                                when (status.sensorPermission.kind) {
                                    PermissionKind.RUNTIME -> {
                                        // Request in-app dialog
                                        val perms = status.sensorPermission.permissions
                                        if (perms.isNotEmpty()) runtimeLauncher.launch(perms.toTypedArray())
                                    }
                                    PermissionKind.USAGE_STATS ->
                                        if (guidedPermissions) pendingSheet = permSheetForKind(PermSheetKind.USAGE_STATS, status.sensorPermission.label, context.packageName)
                                        else context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    PermissionKind.NOTIFICATION_LISTENER ->
                                        if (guidedPermissions) pendingSheet = permSheetForKind(PermSheetKind.NOTIFICATION_LISTENER, status.sensorPermission.label, context.packageName)
                                        else context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    PermissionKind.BACKGROUND_LOCATION ->
                                        if (guidedPermissions) pendingSheet = permSheetForKind(PermSheetKind.BACKGROUND_LOCATION, status.sensorPermission.label, context.packageName)
                                        else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        )
                    }
                }
            }

            Surface(shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val allGranted = state.permissionStatuses.all { it.granted }
                    if (!allGranted && state.permissionStatuses.isNotEmpty()) {
                        Text(
                            "You can continue without all permissions, but some sensors will not collect data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(if (allGranted || state.permissionStatuses.isEmpty()) "Continue" else "Continue Anyway")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(status: PermissionStatus, onGrant: () -> Unit) {
    val isRuntime = status.sensorPermission.kind == PermissionKind.RUNTIME
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (status.granted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (status.granted) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(status.sensorPermission.label, style = MaterialTheme.typography.labelLarge)
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (status.granted) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            if (status.granted) "Granted" else "Required",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status.granted) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Text(
                    status.sensorPermission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!status.granted) {
                if (isRuntime) {
                    FilledTonalButton(
                        onClick = onGrant,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Allow", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    FilledTonalIconButton(onClick = onGrant) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Settings",
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
