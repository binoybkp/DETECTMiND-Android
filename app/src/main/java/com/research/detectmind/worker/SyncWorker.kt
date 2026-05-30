package com.research.detectmind.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.research.detectmind.BuildConfig
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.research.detectmind.data.local.dao.*
import com.research.detectmind.data.local.entity.SyncLogEntity
import com.research.detectmind.data.remote.api.SupabaseApi
import com.research.detectmind.data.remote.dto.*
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.data.repository.*
import com.research.detectmind.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import androidx.work.workDataOf
import retrofit2.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: SupabaseApi,
    private val sensorDataDao: SensorDataDao,
    private val sensorConfigDao: SensorConfigDao,
    private val esmDao: EsmDao,
    private val participantDao: ParticipantDao,
    private val studyDao: StudyDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(Constants.NOTIFICATION_CHANNEL_SENSOR, "Sensor Collection", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        val notification = NotificationCompat.Builder(appContext, Constants.NOTIFICATION_CHANNEL_SENSOR)
            .setContentTitle("Syncing data…")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(Constants.NOTIFICATION_ID_SYNC, notification)
    }

    override suspend fun doWork(): Result {
        val prefs = dataStore.data.first()
        val participantId = prefs[EnrollmentRepository.KEY_PARTICIPANT_ID] ?: return Result.success()
        val studyId = prefs[EnrollmentRepository.KEY_STUDY_ID] ?: return Result.success()

        val startMs = System.currentTimeMillis()
        var totalSynced = 0
        var hasError = false
        val errors = mutableListOf<String>()

        // 1. Re-fetch study, sensor_configs and esm_schedules → update Room
        // Each config type is fetched independently so a transient failure on one
        // does not prevent the others from being updated.
        runCatching {
            val studyResp = api.getStudies(id = "eq.$studyId")
            val study = studyResp.body()?.firstOrNull()
            Log.d("SyncWorker", "study fetch: id=$studyId status=${study?.status} httpCode=${studyResp.code()} bodySize=${studyResp.body()?.size}")
            if (study != null) {
                studyDao.upsert(study.toEntity())
            } else if (studyResp.isSuccessful && studyResp.body()?.isEmpty() == true) {
                val local = studyDao.getStudy(studyId)
                if (local != null && local.status == "active") {
                    studyDao.upsert(local.copy(status = "paused"))
                    Log.d("SyncWorker", "study not visible via anon — marking local status as paused")
                }
            }
        }.onFailure { hasError = true; errors += "study_refresh" }

        runCatching {
            val configs = api.getSensorConfigs("eq.$studyId").body()
            if (configs != null) {
                sensorConfigDao.upsertAll(configs.map { it.toEntity() })
            }
        }.onFailure { hasError = true; errors += "sensor_config_refresh" }

        runCatching {
            val schedules = api.getEsmSchedules("eq.$studyId").body()
            if (schedules != null) {
                esmDao.upsertSchedules(schedules.map { it.toEntity() })
                for (schedule in schedules) {
                    runCatching {
                        val questions = api.getEsmQuestions("eq.${schedule.id}").body()
                        if (questions != null) {
                            esmDao.upsertQuestions(questions.map { it.toEntity() })
                        }
                    }.onFailure { Log.w("SyncWorker", "Failed to fetch questions for schedule ${schedule.id}: ${it.message}") }
                }
            }
        }.onFailure { hasError = true; errors += "esm_config_refresh" }

        // 2. Check participant status — abort if withdrawn
        val local = participantDao.getParticipant()
        if (local?.status == "withdrawn") return Result.success()

        // 3. PATCH participant: last_sync_at, permissions; then re-fetch status from server
        val now = Instant.now().toString()
        runCatching {
            val deviceInfo = buildDeviceInfoJson()
            val permissions = buildPermissionsJson()
            val patchResp = api.patchParticipant(
                id = "eq.$participantId",
                patch = ParticipantPatchDto(
                    lastSyncAt = now,
                    deviceInfo = deviceInfo,
                    permissions = permissions
                )
            )
            Log.d("SyncWorker", "patchParticipant HTTP ${patchResp.code()}: ${if (!patchResp.isSuccessful) patchResp.errorBody()?.string() else "ok"}")
            if (patchResp.isSuccessful) {
                participantDao.updateLastSync(participantId, now)
            } else {
                error("participant_patch HTTP ${patchResp.code()}: ${patchResp.errorBody()?.string()}")
            }

            // Re-fetch participant row — only update status if server says "withdrawn"
            // Never overwrite local "active" status based on network response (avoids "Monitoring Paused" on transient errors)
            val fetchResp = api.getParticipants(deviceId = null, studyId = null, id = "eq.$participantId")
            if (fetchResp.isSuccessful) {
                fetchResp.body()?.firstOrNull()?.let { dto ->
                    if (dto.status == "withdrawn" && local != null) {
                        participantDao.upsert(local.copy(status = "withdrawn", lastSyncAt = now))
                    }
                }
            }
        }.onFailure { e -> hasError = true; errors += "participant_patch(${e.message?.take(120)})" }

        // 4. Upload each data table independently — partial failures keep synced=false
        totalSynced += runTable("app_usage") { syncAppUsage(participantId) }.also { if (it < 0) { hasError = true; errors += "app_usage" } }.coerceAtLeast(0)
        totalSynced += runTable("notifications") { syncNotifications(participantId) }.also { if (it < 0) { hasError = true; errors += "notifications" } }.coerceAtLeast(0)
        totalSynced += runTable("battery") { syncBattery(participantId) }.also { if (it < 0) { hasError = true; errors += "battery" } }.coerceAtLeast(0)
        totalSynced += runTable("calls") { syncCalls(participantId) }.also { if (it < 0) { hasError = true; errors += "calls" } }.coerceAtLeast(0)
        totalSynced += runTable("sms") { syncSms(participantId) }.also { if (it < 0) { hasError = true; errors += "sms" } }.coerceAtLeast(0)
        totalSynced += runTable("location") { syncLocation(participantId) }.also { if (it < 0) { hasError = true; errors += "location" } }.coerceAtLeast(0)
        totalSynced += runTable("light") { syncLight(participantId) }.also { if (it < 0) { hasError = true; errors += "light" } }.coerceAtLeast(0)
        totalSynced += runTable("screen_state") { syncScreenState(participantId) }.also { if (it < 0) { hasError = true; errors += "screen_state" } }.coerceAtLeast(0)
totalSynced += runTable("esm_responses") { syncEsmResponses(participantId) }.also { if (it < 0) { hasError = true; errors += "esm_responses" } }.coerceAtLeast(0)

        // 4. POST sync_log
        val durationMs = System.currentTimeMillis() - startMs
        val status = when {
            !hasError -> "success"
            totalSynced > 0 -> "partial"
            else -> "error"
        }
        val errorMsg = if (errors.isEmpty()) null else errors.joinToString(",")

        sensorDataDao.insertSyncLog(
            SyncLogEntity(
                participantId = participantId,
                syncedAt = now,
                recordsSynced = totalSynced,
                status = status,
                errorMessage = errorMsg,
                durationMs = durationMs
            )
        )

        runCatching {
            api.uploadSyncLog(listOf(
                SyncLogUploadDto(
                    participantId = participantId,
                    syncedAt = now,
                    recordsSynced = totalSynced,
                    status = status,
                    errorMessage = errorMsg,
                    durationMs = durationMs
                )
            ))
        }

        // Always succeed so WorkInfo reaches SUCCEEDED (not ENQUEUED-for-retry).
        // Errors are captured in sync_log; the periodic scheduler will retry on next interval.
        return Result.success(workDataOf(KEY_SYNC_STATUS to status))
    }

    // Returns records synced (≥0) on success, -1 on failure
    private suspend fun runTable(name: String, block: suspend () -> Int): Int {
        return runCatching { block() }.getOrElse { e ->
            Log.e("SyncWorker", "Table $name failed: ${e.message}", e)
            -1
        }
    }

    private fun buildDeviceInfoJson(): JsonElement = buildJsonObject {
        put("manufacturer", Build.MANUFACTURER)
        put("model", Build.MODEL)
        put("android_version", Build.VERSION.RELEASE)
        put("sdk_int", Build.VERSION.SDK_INT.toString())
        put("android_id", Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID))
        put("app_version", BuildConfig.APP_VERSION_NAME)
        put("app_version_code", BuildConfig.APP_VERSION_CODE)
    }

    private fun buildPermissionsJson(): JsonElement = buildJsonObject {
        put("location", hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        put("background_location", hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        put("notifications", isNotificationListenerEnabled())
        put("usage_stats", isUsageStatsEnabled())
        put("call_log", hasPermission(Manifest.permission.READ_CALL_LOG))
        put("sms", hasPermission(Manifest.permission.READ_SMS))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(appContext.packageName)
    }

    private fun isUsageStatsEnabled(): Boolean {
        return runCatching {
            val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            // queryEvents returns non-empty results when permission is granted; empty list when denied
            val events = usm.queryEvents(
                System.currentTimeMillis() - 60_000L,
                System.currentTimeMillis()
            )
            events != null
        }.getOrDefault(false)
    }

    // ─── Data table sync helpers ──────────────────────────────────────────────

    private fun checkResp(resp: retrofit2.Response<*>, table: String) {
        if (!resp.isSuccessful) {
            val body = resp.errorBody()?.string()
            Log.e("SyncWorker", "Upload $table HTTP ${resp.code()}: $body")
            error("$table HTTP ${resp.code()}: $body")
        } else {
            Log.d("SyncWorker", "Upload $table HTTP ${resp.code()} ok")
        }
    }

    private suspend fun syncAppUsage(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedAppUsage(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map {
                AppUsageUploadDto(participantId, it.packageName, it.appName, it.startTime, it.endTime, it.durationSeconds, it.recordedAt)
            }
            checkResp(api.uploadAppUsage(dtos), "app_usage")
            sensorDataDao.markAppUsageSynced(batch.map { it.id })
            count += batch.size
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncNotifications(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedNotifications(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map {
                NotificationUploadDto(participantId, it.packageName, it.appName, it.postedAt, it.removedAt, it.title, it.recordedAt)
            }
            checkResp(api.uploadNotifications(dtos), "notifications")
            sensorDataDao.markNotificationsSynced(batch.map { it.id })
            count += batch.size
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncBattery(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedBattery(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map {
                BatteryUploadDto(participantId, it.level, it.isCharging, it.chargingType, it.temperature, it.voltage, it.recordedAt)
            }
            checkResp(api.uploadBattery(dtos), "battery")
            sensorDataDao.markBatterySynced(batch.map { it.id })
            count += batch.size
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncCalls(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedCalls(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map {
                CallUploadDto(participantId, it.direction, it.eventTime, it.durationSeconds, it.contactHash, it.recordedAt)
            }
            checkResp(api.uploadCalls(dtos), "calls")
            sensorDataDao.markCallsSynced(batch.map { it.id })
            count += batch.size
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncSms(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedSms(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map {
                SmsUploadDto(participantId, it.direction, it.eventTime, it.contactHash, it.bodyHash, it.recordedAt)
            }
            checkResp(api.uploadSms(dtos), "sms")
            sensorDataDao.markSmsSynced(batch.map { it.id })
            count += batch.size
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncLocation(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedLocation(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map {
                LocationUploadDto(participantId, it.latitude, it.longitude, it.altitude, it.accuracy, it.speed, it.provider, it.recordedAt)
            }
            checkResp(api.uploadLocation(dtos), "location")
            sensorDataDao.markLocationSynced(batch.map { it.id })
            count += batch.size
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncLight(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedLight(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map { LightUploadDto(participantId, it.lux, it.recordedAt) }
            checkResp(api.uploadLight(dtos), "light")
            sensorDataDao.markLightSynced(batch.map { it.id })
            count += batch.size
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncScreenState(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedScreenState(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map { ScreenStateUploadDto(participantId, it.state, it.recordedAt) }
            checkResp(api.uploadScreenState(dtos), "screen_state")
            sensorDataDao.markScreenStateSynced(batch.map { it.id })
            count += batch.size
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

private suspend fun syncEsmResponses(participantId: String): Int {
        var count = 0
        do {
            val batch = esmDao.getUnsynced(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            // Group by schedule_id so a deleted schedule doesn't block responses for valid ones
            val groups = batch.groupBy { it.scheduleId }
            for ((_, group) in groups) {
                val dtos = group.map { e ->
                    val responsesJson: JsonElement? = e.responses?.let {
                        runCatching { Json.parseToJsonElement(it) }.getOrNull()
                    }
                    EsmResponseUploadDto(participantId, e.scheduleId, e.triggeredAt, e.respondedAt, e.expired, responsesJson, e.recordedAt)
                }
                val resp = api.uploadEsmResponses(dtos)
                if (resp.isSuccessful) {
                    esmDao.markSynced(group.map { it.id })
                    count += group.size
                } else if (resp.code() == 409) {
                    // FK violation: schedule_id no longer exists on the server (schedule deleted).
                    // Mark as synced so these orphaned responses don't block future syncs.
                    Log.w("SyncWorker", "ESM responses for schedule ${group.first().scheduleId} reference a deleted schedule — discarding ${group.size} orphaned responses")
                    esmDao.markSynced(group.map { it.id })
                } else {
                    Log.e("SyncWorker", "Upload esm_responses HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
                    error("esm_responses HTTP ${resp.code()}")
                }
            }
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    companion object {
        const val KEY_SYNC_STATUS = "sync_status"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun buildRequest(intervalMinutes: Long): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .addTag(Constants.SYNC_WORK_TAG)
                .build()

        fun buildOneShot(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(networkConstraint)
                .addTag(Constants.SYNC_WORK_TAG)
                .build()
    }
}
