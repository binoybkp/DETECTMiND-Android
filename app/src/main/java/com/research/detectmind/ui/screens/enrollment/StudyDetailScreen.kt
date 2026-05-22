package com.research.detectmind.ui.screens.enrollment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.research.detectmind.data.local.entity.StudyEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyDetailScreen(
    study: StudyEntity,
    onEnrolled: (deviceId: String, enabledSensorTypes: List<String>) -> Unit,
    onBack: () -> Unit,
    viewModel: EnrollmentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(study) { viewModel.selectStudy(study) }

    LaunchedEffect(state.enrolledDeviceId) {
        val did = state.enrolledDeviceId ?: return@LaunchedEffect
        onEnrolled(did, state.enabledSensorTypes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Study") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Study card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Science, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp).padding(top = 2.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(study.name, style = MaterialTheme.typography.titleLarge)
                        study.appDescription?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                study.status.replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your participant ID", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enter the 6-character code provided by your researcher.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Large 6-digit input field
            OutlinedTextField(
                value = state.deviceId,
                onValueChange = { raw ->
                    val filtered = raw.uppercase().filter { it.isLetterOrDigit() }
                    viewModel.onDeviceIdChange(filtered)
                },
                placeholder = {
                    Text(
                        "······",
                        style = TextStyle(
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            letterSpacing = 12.sp
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                },
                singleLine = true,
                isError = state.deviceIdError != null,
                supportingText = state.deviceIdError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii
                ),
                textStyle = TextStyle(
                    fontSize = 36.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    letterSpacing = 12.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
            )

            if (state.enrollError != null) {
                Text(
                    state.enrollError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = viewModel::enrollWithSelectedStudy,
                enabled = !state.enrolling && state.deviceId.length == 6,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.enrolling) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Confirm Enrollment", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
