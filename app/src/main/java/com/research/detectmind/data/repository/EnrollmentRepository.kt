package com.research.detectmind.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.research.detectmind.data.local.dao.*
import com.research.detectmind.data.local.entity.*
import com.research.detectmind.data.remote.api.SupabaseApi
import com.research.detectmind.data.remote.dto.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnrollmentRepository @Inject constructor(
    private val api: SupabaseApi,
    private val studyDao: StudyDao,
    private val participantDao: ParticipantDao,
    private val sensorConfigDao: SensorConfigDao,
    private val sensorDataDao: SensorDataDao,
    private val esmDao: EsmDao,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_ENROLLED = booleanPreferencesKey("enrolled")
        val KEY_CONSENT_GIVEN = booleanPreferencesKey("consent_given")
        val KEY_PARTICIPANT_ID = stringPreferencesKey("participant_id")
        val KEY_STUDY_ID = stringPreferencesKey("study_id")
    }

    val isEnrolled: Flow<Boolean> = dataStore.data.map { it[KEY_ENROLLED] == true }
    val isConsentGiven: Flow<Boolean> = dataStore.data.map { it[KEY_CONSENT_GIVEN] == true }

    suspend fun getParticipantId(): String? =
        dataStore.data.first()[KEY_PARTICIPANT_ID]

    suspend fun saveConsent() {
        dataStore.edit { it[KEY_CONSENT_GIVEN] = true }
    }

    suspend fun enrollWithDeviceId(deviceId: String, studyId: String): Result<Unit> {
        return runCatching {
            // Clear any data from a previous study before writing new enrollment
            sensorDataDao.clearAll()
            participantDao.clearAll()
            sensorConfigDao.clearAll()
            studyDao.clearAll()
            esmDao.clearSchedules()
            esmDao.clearQuestions()

            val generatedId = java.util.UUID.randomUUID().toString()

            // Upsert participant — merge-duplicates returns existing row if device_id+study_id match
            val uploadDto = ParticipantUploadDto(
                id = generatedId,
                studyId = studyId,
                deviceId = deviceId,
                label = "Device $deviceId",
                status = "active"
            )
            val upsertResp = api.upsertParticipant(uploadDto)
            if (!upsertResp.isSuccessful)
                error("Failed to register participant: HTTP ${upsertResp.code()}")

            // Use the id returned by Supabase — handles returning participant whose row already exists
            val participantId = upsertResp.body()?.firstOrNull()?.id
                ?: run {
                    // Fallback: GET by device_id + study_id to recover existing participant
                    api.getParticipants(
                        deviceId = "eq.$deviceId",
                        studyId = "eq.$studyId"
                    ).body()?.firstOrNull()?.id ?: generatedId
                }

            // Fetch and persist the study row itself
            val study = api.getStudies(id = "eq.$studyId").body()?.firstOrNull()
                ?: error("Study not found")
            studyDao.upsert(study.toEntity())

            // Fetch sensor configs for the study
            val sensorConfigs = api.getSensorConfigs(studyId = "eq.$studyId").body() ?: emptyList()

            // Fetch ESM schedules and questions
            val schedules = api.getEsmSchedules(studyId = "eq.$studyId").body() ?: emptyList()
            val allQuestions = mutableListOf<EsmQuestionDto>()
            for (schedule in schedules) {
                val questions = api.getEsmQuestions(scheduleId = "eq.${schedule.id}").body() ?: emptyList()
                allQuestions.addAll(questions)
            }

            // Persist participant locally
            participantDao.upsert(
                ParticipantEntity(
                    id = participantId,
                    studyId = studyId,
                    deviceId = deviceId,
                    label = "Device $deviceId",
                    status = "active",
                    enrolledAt = Instant.now().toString(),
                    lastSyncAt = null,
                    deviceInfo = null,
                    permissions = null
                )
            )
            sensorConfigDao.upsertAll(sensorConfigs.map { it.toEntity() })
            esmDao.upsertSchedules(schedules.map { it.toEntity() })
            esmDao.upsertQuestions(allQuestions.map { it.toEntity() })

            dataStore.edit {
                it[KEY_ENROLLED] = true
                it[KEY_PARTICIPANT_ID] = participantId
                it[KEY_STUDY_ID] = studyId
            }
        }
    }

    suspend fun clearEnrollment() {
        dataStore.edit {
            it.remove(KEY_ENROLLED)
            it.remove(KEY_CONSENT_GIVEN)
            it.remove(KEY_PARTICIPANT_ID)
            it.remove(KEY_STUDY_ID)
        }
    }
}

// Mapping extensions
fun StudyDto.toEntity() = StudyEntity(
    id = id, name = name, description = description, appDescription = appDescription,
    status = status, syncIntervalMinutes = syncIntervalMinutes
)

fun ParticipantDto.toEntity() = ParticipantEntity(
    id = id, studyId = studyId, deviceId = deviceId, label = label,
    status = status, enrolledAt = enrolledAt, lastSyncAt = lastSyncAt,
    deviceInfo = deviceInfo?.toString(), permissions = permissions?.toString()
)

fun SensorConfigDto.toEntity() = SensorConfigEntity(
    id = id, studyId = studyId, sensorType = sensorType, enabled = enabled,
    intervalSeconds = intervalSeconds, config = config?.toString()
)

fun EsmScheduleDto.toEntity() = EsmScheduleEntity(
    id = id, studyId = studyId, name = name, description = description,
    scheduleType = scheduleType,
    timesOfDay = timesOfDay?.let { list ->
        kotlinx.serialization.json.buildJsonArray {
            list.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
        }.toString()
    },
    randomCount = randomCount, randomWindowStart = randomWindowStart,
    randomWindowEnd = randomWindowEnd,
    expiryMinutes = expiryMinutes, notificationTitle = notificationTitle,
    notificationBody = notificationBody, enabled = enabled
)

fun EsmQuestionDto.toEntity() = EsmQuestionEntity(
    id = id, scheduleId = scheduleId, questionOrder = questionOrder,
    questionType = questionType, questionText = questionText,
    required = required, options = options?.toString(), config = config?.toString()
)
