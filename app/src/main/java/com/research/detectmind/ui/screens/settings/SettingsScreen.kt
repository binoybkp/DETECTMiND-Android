package com.research.detectmind.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.research.detectmind.ui.components.PermSheet
import com.research.detectmind.ui.components.PermSheetKind
import com.research.detectmind.ui.components.PermissionBottomSheet
import com.research.detectmind.ui.components.permSheetForKind
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onWithdrawn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var pendingSheet by remember { mutableStateOf<PermSheet?>(null) }

    pendingSheet?.let { sheet ->
        PermissionBottomSheet(sheet = sheet, onDismiss = { pendingSheet = null })
    }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermissions()
        }
    }

    LaunchedEffect(state.withdrawn) {
        if (state.withdrawn) onWithdrawn()
    }

    if (showWithdrawDialog) {
        WithdrawConfirmDialog(
            withdrawing = state.withdrawing,
            onConfirm = { viewModel.withdraw() },
            onDismiss = { showWithdrawDialog = false }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Participant ID hero ──────────────────────────────────────────────
        state.participantDeviceId?.let { deviceId ->
            item {
                val settingsScheme = MaterialTheme.colorScheme
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(settingsScheme.primary, settingsScheme.secondary)
                            ),
                            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                        )
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                ) {
                    // Decorative circles
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .offset(x = 50.dp, y = (-50).dp)
                            .align(Alignment.TopEnd)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Badge, null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Your Participant ID",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }
                        Text(
                            deviceId,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                                color = Color.White
                            )
                        )
                        Text(
                            "Share this code with your researcher if asked.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        )
                        val studyDesc = state.studyDescription
                        if (!studyDesc.isNullOrBlank()) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                studyDesc,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.75f)
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Permissions section ─────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Sensor Permissions",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                if (!state.loading && state.sensorPermissions.isNotEmpty()) {
                    val granted = state.sensorPermissions.count { it.granted || it.notRequired }
                    val total = state.sensorPermissions.size
                    val allOk = granted == total
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (allOk) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            "$granted/$total granted",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (allOk) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.sensorPermissions.isEmpty()) {
            item {
                Text(
                    "No sensors configured.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column {
                        state.sensorPermissions.forEachIndexed { index, perm ->
                            PermissionRow(
                                perm = perm,
                                onOpenSettings = {
                                    val sheetKind = when (perm.kind) {
                                        SettingsPermissionKind.USAGE_STATS -> PermSheetKind.USAGE_STATS
                                        SettingsPermissionKind.NOTIFICATION_LISTENER -> PermSheetKind.NOTIFICATION_LISTENER
                                        SettingsPermissionKind.ACCESSIBILITY -> PermSheetKind.ACCESSIBILITY
                                        SettingsPermissionKind.BACKGROUND_LOCATION -> PermSheetKind.BACKGROUND_LOCATION
                                        SettingsPermissionKind.RUNTIME -> null
                                    }
                                    if (state.guidedPermissions && sheetKind != null) {
                                        pendingSheet = permSheetForKind(sheetKind, perm.label, context.packageName)
                                    } else {
                                        val intent = when (perm.kind) {
                                            SettingsPermissionKind.USAGE_STATS ->
                                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            SettingsPermissionKind.NOTIFICATION_LISTENER ->
                                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            SettingsPermissionKind.ACCESSIBILITY ->
                                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            SettingsPermissionKind.BACKGROUND_LOCATION,
                                            SettingsPermissionKind.RUNTIME ->
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                }
                                        }
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                            )
                            if (index < state.sensorPermissions.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── App info section ────────────────────────────────────────────────
        item {
            Text(
                "App Info",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 10.dp),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow(label = "Version", value = state.appVersion)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(label = "Package", value = context.packageName)
                }
            }
        }

        // ── Danger zone ─────────────────────────────────────────────────────
        item {
            Text(
                "Study",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 10.dp),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.withdrawError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick = { showWithdrawDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Withdraw from Study", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    perm: SensorPermissionUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOk = perm.granted || perm.notRequired
    val scheme = MaterialTheme.colorScheme
    val iconBg    = if (isOk) scheme.primary.copy(alpha = 0.1f) else scheme.error.copy(alpha = 0.12f)
    val iconColor = if (isOk) scheme.primary else scheme.error

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                perm.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface
            )
            Text(
                when {
                    perm.notRequired -> "Not required"
                    perm.granted -> "Granted"
                    else -> "Tap to grant"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (!isOk) scheme.error else scheme.onSurfaceVariant
            )
        }

        if (!isOk) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.errorContainer,
                onClick = onOpenSettings
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
                        Modifier.size(11.dp), tint = scheme.onErrorContainer)
                    Text(
                        "Fix",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = scheme.onErrorContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WithdrawConfirmDialog(
    withdrawing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!withdrawing) onDismiss() },
        icon = { Icon(Icons.Default.Warning, contentDescription = null,
            tint = MaterialTheme.colorScheme.error) },
        title = { Text("Withdraw from Study?") },
        text = {
            Text(
                "This will stop all sensor collection and remove your enrollment. " +
                "Your data collected so far will remain in the study. " +
                "You cannot undo this action.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !withdrawing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                if (withdrawing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError)
                } else {
                    Text("Withdraw")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !withdrawing) {
                Text("Cancel")
            }
        }
    )
}
