package com.research.detectmind.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.research.detectmind.data.local.entity.SensorConfigEntity

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onRequestPermission: (sensorType: String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermissionIssues()
        }
    }

    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Study + sync card (edge-to-edge, no horizontal padding)
        state.study?.let { study ->
            StudyCard(
                studyName = study.name,
                studyDescription = study.appDescription,
                lastSyncFormatted = state.lastSyncFormatted,
                countdown = countdown,
                syncState = syncState,
                onSyncNow = viewModel::triggerManualSync
            )
        }

        Spacer(Modifier.height(20.dp))

        // Sensor list
        if (state.enabledSensors.isNotEmpty()) {
            SensorSection(
                modifier = Modifier.padding(horizontal = 16.dp),
                sensors = state.enabledSensors,
                sensorCounts = state.sensorCounts,
                permissionIssues = state.permissionIssues,
                onRequestPermission = onRequestPermission
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Status chip (also used in MainScreen) ────────────────────────────────────

@Composable
fun StatusChip(isActive: Boolean) { // kept for any legacy callers
    StatusChip(studyStatus = if (isActive) "active" else "paused", participantActive = true)
}

@Composable
fun StatusChip(studyStatus: String?, participantActive: Boolean) {
    // Determine display state
    val effectiveStatus = when {
        !participantActive       -> "paused"
        studyStatus == "active"  -> "active"
        studyStatus == "completed" -> "completed"
        else                     -> "paused"   // draft, null, or paused
    }

    val dotColor = when (effectiveStatus) {
        "active"    -> Color(0xFF5EC987) // green
        "completed" -> Color(0xFF90CAF9) // light blue
        else        -> Color(0xFFFFB74D) // amber for paused
    }
    val label = when (effectiveStatus) {
        "active"    -> "Live"
        "completed" -> "Completed"
        else        -> "Paused"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.16f),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
        }
    }
}

// ── Study card ────────────────────────────────────────────────────────────────

@Composable
private fun StudyCard(
    studyName: String,
    studyDescription: String?,
    lastSyncFormatted: String?,
    countdown: String?,
    syncState: HomeViewModel.SyncState,
    onSyncNow: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val gradientStart = scheme.primary
    val gradientEnd   = scheme.secondary
    val onHero        = scheme.onPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(gradientStart, gradientEnd)),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
    ) {
        // Subtle decorative circle top-right
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(x = 60.dp, y = (-60).dp)
                .align(Alignment.TopEnd)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(x = 20.dp, y = (-20).dp)
                .align(Alignment.TopEnd)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Study name + description
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    studyName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = onHero
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!studyDescription.isNullOrBlank()) {
                    Text(
                        studyDescription,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = onHero.copy(alpha = 0.75f)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Sync info row — glassmorphism card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                when (syncState) {
                                    HomeViewModel.SyncState.ERROR   -> Icons.Default.CloudOff
                                    HomeViewModel.SyncState.PARTIAL -> Icons.Default.CloudQueue
                                    else                            -> Icons.Default.CloudDone
                                },
                                contentDescription = null,
                                tint = onHero.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                if (lastSyncFormatted != null) "Last synced $lastSyncFormatted" else "Not yet synced",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = onHero.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        if (countdown != null) {
                            val countdownText = if (countdown == "Syncing soon") "Syncing soon…"
                                               else "Next sync $countdown"
                            Text(
                                countdownText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = onHero.copy(alpha = 0.55f)
                                ),
                                modifier = Modifier.padding(start = 20.dp)
                            )
                        }
                    }
                    SyncButton(syncState = syncState, onSyncNow = onSyncNow, onHero = onHero)
                }
            }
        }
    }
}

// ── Sync button ───────────────────────────────────────────────────────────────

@Composable
private fun SyncButton(
    syncState: HomeViewModel.SyncState,
    onSyncNow: () -> Unit,
    onHero: Color = Color.White
) {
    val isSyncing = syncState == HomeViewModel.SyncState.SYNCING
    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )
    val btnBg = when (syncState) {
        HomeViewModel.SyncState.ERROR   -> Color(0xFFFFCDD2).copy(alpha = 0.35f)
        HomeViewModel.SyncState.PARTIAL -> Color(0xFFFFF9C4).copy(alpha = 0.35f)
        HomeViewModel.SyncState.SUCCESS -> Color.White.copy(alpha = 0.25f)
        else                            -> Color.White.copy(alpha = 0.18f)
    }
    val btnContent = onHero

    FilledTonalButton(
        onClick = onSyncNow,
        enabled = !isSyncing,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = btnBg,
            contentColor = btnContent,
            disabledContainerColor = btnBg.copy(alpha = 0.5f),
            disabledContentColor = btnContent.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        AnimatedContent(
            targetState = syncState,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "syncBtn"
        ) { s ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (s) {
                    HomeViewModel.SyncState.SYNCING -> {
                        Icon(Icons.Default.Sync, null, Modifier.size(15.dp).rotate(rotation))
                        Text("Syncing…", style = MaterialTheme.typography.labelMedium)
                    }
                    HomeViewModel.SyncState.SUCCESS -> {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(15.dp))
                        Text("Synced!", style = MaterialTheme.typography.labelMedium)
                    }
                    HomeViewModel.SyncState.PARTIAL -> {
                        Icon(Icons.Default.Warning, null, Modifier.size(15.dp))
                        Text("Partial", style = MaterialTheme.typography.labelMedium)
                    }
                    HomeViewModel.SyncState.ERROR -> {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(15.dp))
                        Text("Failed", style = MaterialTheme.typography.labelMedium)
                    }
                    HomeViewModel.SyncState.IDLE -> {
                        Icon(Icons.Default.Sync, null, Modifier.size(15.dp))
                        Text("Sync Now", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ── Sensor section ────────────────────────────────────────────────────────────

@Composable
private fun SensorSection(
    sensors: List<SensorConfigEntity>,
    sensorCounts: Map<String, SensorCounts>,
    permissionIssues: Set<String>,
    onRequestPermission: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Data Collection",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
            )
            Spacer(Modifier.weight(1f))
            if (permissionIssues.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = scheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, Modifier.size(11.dp), tint = scheme.onErrorContainer)
                        Text(
                            "${permissionIssues.size} issue${if (permissionIssues.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = scheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            } else {
                Text(
                    "${sensors.size} active",
                    style = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
                )
            }
        }

        // List card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                sensors.forEachIndexed { index, sensor ->
                    SensorRow(
                        sensor = sensor,
                        counts = sensorCounts[sensor.sensorType],
                        hasIssue = sensor.sensorType in permissionIssues,
                        onFix = { onRequestPermission(sensor.sensorType) }
                    )
                    if (index < sensors.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                            color = scheme.outlineVariant.copy(alpha = 0.6f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

// ── Sensor row (list item) ────────────────────────────────────────────────────

@Composable
private fun SensorRow(
    sensor: SensorConfigEntity,
    counts: SensorCounts?,
    hasIssue: Boolean,
    onFix: () -> Unit
) {
    val (icon, fullName) = sensorMeta(sensor.sensorType)
    val scheme  = MaterialTheme.colorScheme
    val pending = counts?.pending ?: 0L

    val iconBg    = if (hasIssue) scheme.errorContainer else scheme.primaryContainer
    val iconColor = if (hasIssue) scheme.error else scheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasIssue) Modifier.clickable(onClick = onFix) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = fullName, tint = iconColor, modifier = Modifier.size(19.dp))
        }

        // Name
        Text(
            fullName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Trailing: error pill / count pill / check icon
        when {
            hasIssue -> Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(11.dp), tint = scheme.onErrorContainer)
                    Text(
                        "Fix",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = scheme.onErrorContainer
                        )
                    )
                }
            }
            pending > 0L -> Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.secondaryContainer
            ) {
                Text(
                    "${if (pending > 999) "999+" else pending}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = scheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            else -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Sensor metadata ───────────────────────────────────────────────────────────

fun sensorMeta(sensorType: String): Pair<ImageVector, String> =
    when (sensorType) {
        "app_usage"          -> Icons.Default.Apps                    to "App Usage"
        "notifications"      -> Icons.Default.Notifications           to "Notifications"
        "battery"            -> Icons.Default.BatteryFull             to "Battery"
        "calls"              -> Icons.Default.Call                    to "Call Log"
        "sms"                -> Icons.Default.Sms                    to "SMS"
        "location"           -> Icons.Default.LocationOn              to "Location"
        "light"              -> Icons.Default.LightMode               to "Light"
        "screen_state"       -> Icons.Default.PhoneAndroid            to "Screen State"
"esm_ema"            -> Icons.AutoMirrored.Filled.Assignment  to "ESM Surveys"
        else -> Icons.Default.Sensors to
                sensorType.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
