package com.research.detectmind.esm

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.research.detectmind.data.local.entity.EsmQuestionEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Calendar

@Composable
fun EsmSurveyScreen(
    viewModel: EsmViewModel,
    onFinished: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.expired) {
        ExpiredScreen(onDismiss = onFinished)
        return
    }

    if (state.submitted) {
        SubmittedScreen(onDismiss = onFinished)
        return
    }

    val questions = state.questions
    if (questions.isEmpty()) { onFinished(); return }

    val current = questions.getOrNull(state.currentIndex) ?: run { onFinished(); return }
    val isLast = state.currentIndex == questions.lastIndex
    val canProceed = !current.required || state.answers[current.id]?.isNotBlank() == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = state.schedule?.name ?: "Survey",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        LinearProgressIndicator(
            progress = { (state.currentIndex + 1f) / questions.size },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Question ${state.currentIndex + 1} of ${questions.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = current.questionText +
                    if (current.required) " *" else "",
            style = MaterialTheme.typography.titleLarge
        )

        QuestionInput(
            question = current,
            answer = state.answers[current.id] ?: "",
            onAnswer = { viewModel.setAnswer(current.id, it) }
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.currentIndex > 0) {
                OutlinedButton(onClick = { viewModel.previousQuestion() }) { Text("Back") }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Button(
                onClick = { if (isLast) viewModel.submit() else viewModel.nextQuestion() },
                enabled = canProceed
            ) {
                Text(if (isLast) "Submit" else "Next")
            }
        }
    }
}

@Composable
private fun QuestionInput(
    question: EsmQuestionEntity,
    answer: String,
    onAnswer: (String) -> Unit
) {
    val config = question.config?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
    }
    val configMin = config?.get("min")?.jsonPrimitive?.content?.toIntOrNull()
    val configMax = config?.get("max")?.jsonPrimitive?.content?.toIntOrNull()
    val labelMin = config?.get("label_min")?.jsonPrimitive?.content
        ?.takeIf { it.isNotBlank() && it != "null" } ?: ""
    val labelMax = config?.get("label_max")?.jsonPrimitive?.content
        ?.takeIf { it.isNotBlank() && it != "null" } ?: ""

    when (question.questionType) {

        "likert" -> {
            val min = configMin ?: 1
            val max = configMax ?: 7
            val selectedVal = answer.toIntOrNull()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    for (v in min..max) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .selectable(
                                    selected = selectedVal == v,
                                    onClick = { onAnswer(v.toString()) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedVal == v,
                                onClick = { onAnswer(v.toString()) }
                            )
                            Text(
                                text = v.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(labelMin, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(labelMax, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        "slider" -> {
            val min = configMin ?: 0
            val max = configMax ?: 100
            val currentVal = answer.toFloatOrNull() ?: min.toFloat()
            LaunchedEffect(question.id) {
                if (answer.isBlank()) {
                    onAnswer(min.toString())
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Slider(
                    value = currentVal,
                    onValueChange = { onAnswer(it.toInt().toString()) },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = (max - min - 1).coerceAtLeast(0)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(labelMin.ifBlank { min.toString() }, style = MaterialTheme.typography.bodySmall)
                    Text(
                        currentVal.toInt().toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(labelMax.ifBlank { max.toString() }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        "yes_no" -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Yes", "No").forEach { option ->
                    val selected = answer == option
                    if (selected) {
                        Button(
                            onClick = { onAnswer(option) },
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) { Text(option) }
                    } else {
                        OutlinedButton(
                            onClick = { onAnswer(option) },
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) { Text(option) }
                    }
                }
            }
        }

        "single_choice" -> {
            val options = parseOptions(question.options)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = answer == option,
                                onClick = { onAnswer(option) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = answer == option, onClick = { onAnswer(option) })
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        "multi_choice" -> {
            val options = parseOptions(question.options)
            val selected = if (answer.isBlank()) emptySet() else answer.split(",").toSet()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val isChecked = option in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isChecked,
                                onClick = {
                                    val newSet = if (isChecked) selected - option else selected + option
                                    onAnswer(newSet.joinToString(","))
                                },
                                role = Role.Checkbox
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = {
                            val newSet = if (isChecked) selected - option else selected + option
                            onAnswer(newSet.joinToString(","))
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        "text" -> {
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswer,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                label = { Text("Your answer") },
                maxLines = 6
            )
        }

        "number" -> {
            var error by remember { mutableStateOf<String?>(null) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = answer,
                    onValueChange = { raw ->
                        val num = raw.toDoubleOrNull()
                        error = when {
                            raw.isBlank() -> null
                            num == null -> "Enter a valid number"
                            configMin != null && num < configMin -> "Minimum is $configMin"
                            configMax != null && num > configMax -> "Maximum is $configMax"
                            else -> null
                        }
                        val inRange = num == null || (
                            (configMin == null || num >= configMin) &&
                            (configMax == null || num <= configMax)
                        )
                        if (raw.isBlank() || (raw.all { it.isDigit() || it == '.' || it == '-' } && inRange)) {
                            onAnswer(raw)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        val range = when {
                            configMin != null && configMax != null -> "Number ($configMin–$configMax)"
                            configMin != null -> "Number (≥$configMin)"
                            configMax != null -> "Number (≤$configMax)"
                            else -> "Number"
                        }
                        Text(range)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        }

        "time" -> {
            val context = LocalContext.current
            val displayText = answer.ifBlank { "Tap to select time" }
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, h, m -> onAnswer("%02d:%02d".format(h, m)) },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(displayText)
            }
        }

        "date" -> {
            val context = LocalContext.current
            val displayText = answer.ifBlank { "Tap to select date" }
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> onAnswer("%04d-%02d-%02d".format(y, m + 1, d)) },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(displayText)
            }
        }

        else -> {
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswer,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your answer") }
            )
        }
    }
}

private fun parseOptions(optionsJson: String?): List<String> {
    if (optionsJson == null) return emptyList()
    return runCatching {
        Json.parseToJsonElement(optionsJson).jsonArray.map { it.jsonPrimitive.content }
    }.getOrDefault(emptyList())
}

@Composable
private fun ExpiredScreen(onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Survey Expired", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("This survey is no longer available.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss) { Text("Close") }
    }
}

@Composable
private fun SubmittedScreen(onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Thank You!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Your response has been recorded.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss) { Text("Close") }
    }
}
