package com.research.detectmind.ui.screens.consent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class ConsentDataItem(
    val sensorType: String,
    val icon: ImageVector,
    val title: String,
    val body: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(
    enabledSensorTypes: List<String>,
    onConsented: () -> Unit,
    onDeclined: () -> Unit
) {
    // All possible sensor disclosure items
    val allItems = listOf(
        ConsentDataItem("app_usage", Icons.Default.Apps, "App Usage",
            "Which apps you open, start/end times, and duration."),
        ConsentDataItem("notifications", Icons.Default.Notifications, "Notification Metadata",
            "Which app posted a notification and when. Notification content is never stored."),
        ConsentDataItem("battery", Icons.Default.BatteryFull, "Battery",
            "Battery level, charging state, temperature, and voltage."),
        ConsentDataItem("calls", Icons.Default.Call, "Call Metadata",
            "Call direction (in/out/missed), duration, and timestamp. Phone numbers are SHA-256 hashed — never stored in plain text."),
        ConsentDataItem("sms", Icons.Default.Sms, "SMS Metadata",
            "Message direction and timestamp. Contact numbers and message bodies are SHA-256 hashed — never stored in plain text."),
        ConsentDataItem("location", Icons.Default.LocationOn, "Location",
            "GPS coordinates, altitude, accuracy, and speed."),
        ConsentDataItem("light", Icons.Default.LightMode, "Ambient Light",
            "Light sensor readings in lux."),
        ConsentDataItem("screen_state", Icons.Default.PhoneAndroid, "Screen State",
            "Screen on/off/locked/unlocked events."),
        ConsentDataItem("screen_interaction", Icons.Default.TouchApp, "Screen Interaction",
            "Touch, swipe, and scroll event types and coordinates."),
        ConsentDataItem("esm_ema", Icons.AutoMirrored.Filled.Assignment, "Survey Responses",
            "Your answers to periodic experience-sampling surveys.")
    )

    // Show only the sensors enabled in this study (or all if the list is empty / not yet loaded)
    val visibleItems = if (enabledSensorTypes.isEmpty()) allItems
                       else allItems.filter { it.sensorType in enabledSensorTypes }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Research Consent") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                Text(
                    "This app collects sensor data for academic research. " +
                    "Please read carefully what data is collected before agreeing to participate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Data collected in this study", style = MaterialTheme.typography.titleMedium)

                visibleItems.forEach { item ->
                    ConsentItem(icon = item.icon, title = item.title, body = item.body)
                }

                HorizontalDivider()
                Text("Privacy protections", style = MaterialTheme.typography.titleMedium)

                PrivacyPoint("All phone numbers and SMS bodies are SHA-256 hashed before being stored. The original values are never saved anywhere.")
                PrivacyPoint("No raw data is ever displayed to you or any other participant.")
                PrivacyPoint("Data is transmitted securely to research servers.")
                PrivacyPoint("Your participation is voluntary. You may withdraw at any time by uninstalling the app.")

                Spacer(Modifier.height(8.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    "By tapping \"I Agree\" you consent to participate under the terms above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDeclined, modifier = Modifier.weight(1f)) {
                        Text("Decline")
                    }
                    Button(onClick = onConsented, modifier = Modifier.weight(1f)) {
                        Text("I Agree")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentItem(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(body, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrivacyPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Shield, contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
