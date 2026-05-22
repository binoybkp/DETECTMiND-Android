package com.research.detectmind.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class PermSheet(
    val title: String,
    val description: String,
    val settingsIntent: Intent
)

fun permSheetForKind(kind: PermSheetKind, sensorLabel: String, packageName: String): PermSheet = when (kind) {
    PermSheetKind.USAGE_STATS -> PermSheet(
        title = "Allow App Usage Access",
        description = "\"$sensorLabel\" requires Usage Access permission so the study can track which apps you use and for how long.\n\nTap \"Open Settings\", find this app in the list, and turn on the toggle.",
        settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    PermSheetKind.NOTIFICATION_LISTENER -> PermSheet(
        title = "Allow Notification Access",
        description = "\"$sensorLabel\" requires Notification Listener permission so the study can record when notifications arrive.\n\nTap \"Open Settings\", find this app in the list, and enable it.",
        settingsIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    PermSheetKind.ACCESSIBILITY -> PermSheet(
        title = "Allow Accessibility Access",
        description = "\"$sensorLabel\" requires Accessibility Service permission so the study can detect screen interactions.\n\nTap \"Open Settings\", find this app under Installed Apps, and turn on the toggle.",
        settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    PermSheetKind.BACKGROUND_LOCATION -> PermSheet(
        title = "Allow Location All the Time",
        description = "\"$sensorLabel\" requires background location permission so the study can track your mobility patterns even when the app is not open.\n\nTap \"Open Settings\", go to Location, and select \"Allow all the time\".",
        settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

enum class PermSheetKind { USAGE_STATS, NOTIFICATION_LISTENER, ACCESSIBILITY, BACKGROUND_LOCATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionBottomSheet(
    sheet: PermSheet,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                sheet.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                sheet.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    onDismiss()
                    context.startActivity(sheet.settingsIntent)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Open Settings")
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Not Now")
            }
        }
    }
}
