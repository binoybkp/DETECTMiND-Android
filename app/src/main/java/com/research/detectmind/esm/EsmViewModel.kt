package com.research.detectmind.esm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.research.detectmind.data.local.dao.EsmDao
import com.research.detectmind.data.local.entity.EsmQuestionEntity
import com.research.detectmind.data.local.entity.EsmResponseEntity
import com.research.detectmind.data.local.entity.EsmScheduleEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject

data class EsmUiState(
    val schedule: EsmScheduleEntity? = null,
    val questions: List<EsmQuestionEntity> = emptyList(),
    val answers: Map<String, String> = emptyMap(),  // keyed by question UUID
    val currentIndex: Int = 0,
    val expired: Boolean = false,
    val submitted: Boolean = false,
    val loading: Boolean = true
)

@HiltViewModel
class EsmViewModel @Inject constructor(
    private val esmDao: EsmDao,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(EsmUiState())
    val state: StateFlow<EsmUiState> = _state

    private var scheduleId: String = ""
    private var triggeredAt: Long = 0L
    private var responseId: Long = -1L
    private var participantId: String = ""

    fun load(scheduleId: String, triggeredAt: Long, responseId: Long) {
        this.scheduleId = scheduleId
        this.triggeredAt = triggeredAt
        this.responseId = responseId
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            participantId = prefs[EnrollmentRepository.KEY_PARTICIPANT_ID] ?: return@launch
            val schedule = esmDao.getSchedule(scheduleId) ?: return@launch
            val questions = esmDao.getQuestions(scheduleId)

            val isExpired = schedule.expiryMinutes > 0 &&
                    (System.currentTimeMillis() - triggeredAt) > schedule.expiryMinutes * 60_000L

            if (isExpired) {
                if (responseId >= 0) {
                    esmDao.markExpired(responseId)
                } else {
                    esmDao.insertResponse(EsmResponseEntity(
                        participantId = participantId,
                        scheduleId = scheduleId,
                        triggeredAt = Instant.ofEpochMilli(triggeredAt).toString(),
                        respondedAt = null,
                        expired = true,
                        responses = null,
                        recordedAt = Instant.now().toString()
                    ))
                }
                _state.update { it.copy(expired = true, loading = false) }
                return@launch
            }

            // If responseId was pre-created by EsmAlarmReceiver, use it; otherwise create
            if (responseId < 0) {
                this@EsmViewModel.responseId = esmDao.insertResponse(EsmResponseEntity(
                    participantId = participantId,
                    scheduleId = scheduleId,
                    triggeredAt = Instant.ofEpochMilli(triggeredAt).toString(),
                    respondedAt = null,
                    expired = false,
                    responses = null,
                    recordedAt = Instant.now().toString()
                ))
            }

            _state.update { it.copy(schedule = schedule, questions = questions, loading = false) }
        }
    }

    fun setAnswer(questionId: String, answer: String) {
        _state.update { it.copy(answers = it.answers + (questionId to answer)) }
    }

    fun nextQuestion() {
        _state.update { it.copy(currentIndex = it.currentIndex + 1) }
    }

    fun previousQuestion() {
        _state.update { it.copy(currentIndex = maxOf(0, it.currentIndex - 1)) }
    }

    fun canProceed(): Boolean {
        val s = _state.value
        val current = s.questions.getOrNull(s.currentIndex) ?: return true
        return !current.required || s.answers[current.id]?.isNotBlank() == true
    }

    fun submit() {
        viewModelScope.launch {
            val s = _state.value
            val questionTypeMap = s.questions.associate { it.id to it.questionType }
            val responsesJson = buildJsonObject {
                s.answers.forEach { (questionId, answer) ->
                    when (questionTypeMap[questionId]) {
                        "likert", "slider", "number" -> {
                            val numVal = answer.toDoubleOrNull() ?: answer.toIntOrNull()?.toDouble() ?: 0.0
                            put(questionId, numVal)
                        }
                        "yes_no" -> {
                            val boolVal = answer.equals("Yes", ignoreCase = true) ||
                                    answer.equals("true", ignoreCase = true)
                            put(questionId, boolVal)
                        }
                        "multi_choice" -> {
                            val items = if (answer.isBlank()) emptyList()
                                        else answer.split(",").map { it.trim() }
                            put(questionId, JsonArray(items.map { JsonPrimitive(it) }))
                        }
                        else -> put(questionId, answer)
                    }
                }
            }.toString()

            val respondedAt = Instant.now().toString()

            if (responseId >= 0) {
                // Update the existing pre-inserted row
                esmDao.submitResponse(responseId, respondedAt, responsesJson)
            } else {
                esmDao.insertResponse(EsmResponseEntity(
                    participantId = participantId,
                    scheduleId = scheduleId,
                    triggeredAt = Instant.ofEpochMilli(triggeredAt).toString(),
                    respondedAt = respondedAt,
                    expired = false,
                    responses = responsesJson,
                    recordedAt = respondedAt
                ))
            }

            // Cancel the expiry alarm since user responded
            cancelExpiryAlarm()

            _state.update { it.copy(submitted = true) }
        }
    }

    private fun cancelExpiryAlarm() {
        val expiryIntent = Intent(context, EsmExpiryReceiver::class.java).apply {
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_RESPONSE_ID, responseId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (scheduleId + "_expiry").hashCode(),
            expiryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
        }
    }
}
