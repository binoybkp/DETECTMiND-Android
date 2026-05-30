package com.research.detectmind.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StudyDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("app_description") val appDescription: String? = null,
    val status: String,
    @SerialName("sync_interval_minutes") val syncIntervalMinutes: Int = 30,
    val config: JsonElement? = null
)

@Serializable
data class ParticipantDto(
    val id: String,
    @SerialName("study_id") val studyId: String,
    @SerialName("device_id") val deviceId: String,
    val label: String? = null,
    val status: String,
    @SerialName("enrolled_at") val enrolledAt: String? = null,
    @SerialName("last_sync_at") val lastSyncAt: String? = null,
    @SerialName("device_info") val deviceInfo: JsonElement? = null,
    val permissions: JsonElement? = null
)

@Serializable
data class SensorConfigDto(
    val id: String,
    @SerialName("study_id") val studyId: String,
    @SerialName("sensor_type") val sensorType: String,
    val enabled: Boolean,
    @SerialName("interval_seconds") val intervalSeconds: Int? = null,
    val config: JsonElement? = null
)

@Serializable
data class EsmScheduleDto(
    val id: String,
    @SerialName("study_id") val studyId: String,
    val name: String,
    val description: String? = null,
    @SerialName("schedule_type") val scheduleType: String,
    @SerialName("times_of_day") val timesOfDay: List<String>? = null,
    @SerialName("random_count") val randomCount: Int? = null,
    @SerialName("random_window_start") val randomWindowStart: String? = null,
    @SerialName("random_window_end") val randomWindowEnd: String? = null,
    @SerialName("expiry_minutes") val expiryMinutes: Int,
    @SerialName("notification_title") val notificationTitle: String,
    @SerialName("notification_body") val notificationBody: String,
    val enabled: Boolean
)

@Serializable
data class EsmQuestionDto(
    val id: String,
    @SerialName("schedule_id") val scheduleId: String,
    @SerialName("question_order") val questionOrder: Int,
    @SerialName("question_type") val questionType: String,
    @SerialName("question_text") val questionText: String,
    val required: Boolean,
    val options: JsonElement? = null,
    val config: JsonElement? = null
)

@Serializable
data class ParticipantUploadDto(
    val id: String,
    @SerialName("study_id") val studyId: String,
    @SerialName("device_id") val deviceId: String,
    val label: String? = null,
    val status: String,
    @SerialName("device_info") val deviceInfo: JsonElement? = null,
    val permissions: JsonElement? = null
)

@Serializable
data class ParticipantPatchDto(
    @SerialName("last_sync_at") val lastSyncAt: String? = null,
    @SerialName("device_info") val deviceInfo: JsonElement? = null,
    val permissions: JsonElement? = null
)

@Serializable
data class ParticipantStatusPatchDto(
    val status: String
)

// Upload DTOs — match Supabase column names exactly
@Serializable
data class AppUsageUploadDto(
    @SerialName("participant_id") val participantId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class NotificationUploadDto(
    @SerialName("participant_id") val participantId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("posted_at") val postedAt: String,
    @SerialName("removed_at") val removedAt: String? = null,
    val title: String? = null,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class BatteryUploadDto(
    @SerialName("participant_id") val participantId: String,
    val level: Int,
    @SerialName("is_charging") val isCharging: Boolean,
    @SerialName("charging_type") val chargingType: String? = null,
    val temperature: Float,
    val voltage: Float,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class CallUploadDto(
    @SerialName("participant_id") val participantId: String,
    val direction: String,
    @SerialName("event_time") val eventTime: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("contact_hash") val contactHash: String,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class SmsUploadDto(
    @SerialName("participant_id") val participantId: String,
    val direction: String,
    @SerialName("event_time") val eventTime: String,
    @SerialName("contact_hash") val contactHash: String,
    @SerialName("body_hash") val bodyHash: String,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class EsmResponseUploadDto(
    @SerialName("participant_id") val participantId: String,
    @SerialName("schedule_id") val scheduleId: String,
    @SerialName("triggered_at") val triggeredAt: String,
    @SerialName("responded_at") val respondedAt: String? = null,
    val expired: Boolean,
    val responses: JsonElement? = null,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class LocationUploadDto(
    @SerialName("participant_id") val participantId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val provider: String,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class LightUploadDto(
    @SerialName("participant_id") val participantId: String,
    val lux: Float,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class ScreenStateUploadDto(
    @SerialName("participant_id") val participantId: String,
    val state: String,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class SyncLogUploadDto(
    @SerialName("participant_id") val participantId: String,
    @SerialName("synced_at") val syncedAt: String,
    @SerialName("records_synced") val recordsSynced: Int,
    val status: String,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("duration_ms") val durationMs: Long
)
