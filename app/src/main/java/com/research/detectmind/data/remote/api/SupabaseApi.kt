package com.research.detectmind.data.remote.api

import com.research.detectmind.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApi {

    // Studies
    @GET("rest/v1/studies")
    @Headers("Accept: application/json")
    suspend fun getActiveStudies(
        @Query("status") status: String = "eq.active"
    ): Response<List<StudyDto>>

    @GET("rest/v1/studies")
    @Headers("Accept: application/json")
    suspend fun getStudies(@Query("id") id: String? = null): Response<List<StudyDto>>

    // Participants
    @GET("rest/v1/participants")
    @Headers("Accept: application/json")
    suspend fun getParticipants(
        @Query("device_id") deviceId: String? = null,
        @Query("study_id") studyId: String? = null,
        @Query("id") id: String? = null
    ): Response<List<ParticipantDto>>

    @POST("rest/v1/participants")
    @Headers("Accept: application/json", "Prefer: resolution=merge-duplicates,return=representation")
    suspend fun upsertParticipant(@Body participant: ParticipantUploadDto): Response<List<ParticipantDto>>

    @PATCH("rest/v1/participants")
    @Headers("Accept: application/json")
    suspend fun patchParticipant(
        @Query("id") id: String,
        @Body patch: ParticipantPatchDto
    ): Response<Unit>

    @PATCH("rest/v1/participants")
    @Headers("Accept: application/json")
    suspend fun patchParticipantStatus(
        @Query("id") id: String,
        @Body patch: ParticipantStatusPatchDto
    ): Response<Unit>

    // Sensor configs
    @GET("rest/v1/sensor_configs")
    @Headers("Accept: application/json")
    suspend fun getSensorConfigs(@Query("study_id") studyId: String): Response<List<SensorConfigDto>>

    // ESM
    @GET("rest/v1/esm_schedules")
    @Headers("Accept: application/json")
    suspend fun getEsmSchedules(@Query("study_id") studyId: String): Response<List<EsmScheduleDto>>

    @GET("rest/v1/esm_questions")
    @Headers("Accept: application/json")
    suspend fun getEsmQuestions(@Query("schedule_id") scheduleId: String): Response<List<EsmQuestionDto>>

    @POST("rest/v1/data_app_usage")
    @Headers("Prefer: return=minimal")
    suspend fun uploadAppUsage(@Body records: List<AppUsageUploadDto>): Response<Unit>

    @POST("rest/v1/data_notifications")
    @Headers("Prefer: return=minimal")
    suspend fun uploadNotifications(@Body records: List<NotificationUploadDto>): Response<Unit>

    @POST("rest/v1/data_battery")
    @Headers("Prefer: return=minimal")
    suspend fun uploadBattery(@Body records: List<BatteryUploadDto>): Response<Unit>

    @POST("rest/v1/data_calls")
    @Headers("Prefer: return=minimal")
    suspend fun uploadCalls(@Body records: List<CallUploadDto>): Response<Unit>

    @POST("rest/v1/data_sms")
    @Headers("Prefer: return=minimal")
    suspend fun uploadSms(@Body records: List<SmsUploadDto>): Response<Unit>

    @POST("rest/v1/data_esm_responses")
    @Headers("Prefer: return=minimal")
    suspend fun uploadEsmResponses(@Body records: List<EsmResponseUploadDto>): Response<Unit>

    @POST("rest/v1/data_location")
    @Headers("Prefer: return=minimal")
    suspend fun uploadLocation(@Body records: List<LocationUploadDto>): Response<Unit>

    @POST("rest/v1/data_light")
    @Headers("Prefer: return=minimal")
    suspend fun uploadLight(@Body records: List<LightUploadDto>): Response<Unit>

    @POST("rest/v1/data_screen_state")
    @Headers("Prefer: return=minimal")
    suspend fun uploadScreenState(@Body records: List<ScreenStateUploadDto>): Response<Unit>


@POST("rest/v1/sync_log")
    @Headers("Prefer: return=minimal")
    suspend fun uploadSyncLog(@Body records: List<SyncLogUploadDto>): Response<Unit>
}
