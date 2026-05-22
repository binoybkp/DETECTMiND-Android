package com.research.detectmind.data.repository

import com.research.detectmind.data.local.dao.EsmDao
import com.research.detectmind.data.local.dao.ParticipantDao
import com.research.detectmind.data.local.dao.SensorDataDao
import com.research.detectmind.data.local.entity.SyncLogEntity
import com.research.detectmind.data.remote.api.SupabaseApi
import com.research.detectmind.data.remote.dto.*
import com.research.detectmind.util.Constants
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val totalSynced: Int,
    val errorMessage: String?,
    val durationMs: Long
) {
    val status: String get() = when {
        errorMessage == null -> "success"
        totalSynced > 0 -> "partial"
        else -> "error"
    }
}

@Singleton
class SyncRepository @Inject constructor(
    private val api: SupabaseApi,
    private val sensorDataDao: SensorDataDao,
    private val esmDao: EsmDao,
    private val participantDao: ParticipantDao
) {
    suspend fun syncAll(participantId: String): SyncResult {
        val startMs = System.currentTimeMillis()
        var total = 0
        val errors = mutableListOf<String>()

        suspend fun runTable(name: String, block: suspend () -> Unit) =
            runCatching { block() }.onFailure { errors += "$name(${it.message?.take(80)})" }

        runTable("app_usage") { total += syncAppUsage(participantId) }
        runTable("notifications") { total += syncNotifications(participantId) }
        runTable("battery") { total += syncBattery(participantId) }
        runTable("calls") { total += syncCalls(participantId) }
        runTable("sms") { total += syncSms(participantId) }
        runTable("location") { total += syncLocation(participantId) }
        runTable("light") { total += syncLight(participantId) }
        runTable("screen_state") { total += syncScreenState(participantId) }
        runTable("screen_interaction") { total += syncScreenInteraction(participantId) }
        runTable("esm_responses") { total += syncEsmResponses(participantId) }

        val errorMsg = if (errors.isEmpty()) null else errors.joinToString(",")
        val durationMs = System.currentTimeMillis() - startMs
        val now = Instant.now().toString()

        sensorDataDao.insertSyncLog(
            SyncLogEntity(
                participantId = participantId,
                syncedAt = now,
                recordsSynced = total,
                status = if (errorMsg == null) "success" else if (total > 0) "partial" else "error",
                errorMessage = errorMsg,
                durationMs = durationMs
            )
        )
        participantDao.updateLastSync(participantId, now)

        return SyncResult(total, errorMsg, durationMs)
    }

    private suspend fun syncAppUsage(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedAppUsage(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map {
                AppUsageUploadDto(participantId, it.packageName, it.appName, it.startTime, it.endTime, it.durationSeconds, it.recordedAt)
            }
            val resp = api.uploadAppUsage(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markAppUsageSynced(batch.map { it.id }); count += batch.size
            } else break
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
            val resp = api.uploadNotifications(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markNotificationsSynced(batch.map { it.id }); count += batch.size
            } else break
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
            val resp = api.uploadBattery(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markBatterySynced(batch.map { it.id }); count += batch.size
            } else break
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
            val resp = api.uploadCalls(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markCallsSynced(batch.map { it.id }); count += batch.size
            } else break
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
            val resp = api.uploadSms(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markSmsSynced(batch.map { it.id }); count += batch.size
            } else break
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
            val resp = api.uploadLocation(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markLocationSynced(batch.map { it.id }); count += batch.size
            } else break
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncLight(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedLight(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map { LightUploadDto(participantId, it.lux, it.recordedAt) }
            val resp = api.uploadLight(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markLightSynced(batch.map { it.id }); count += batch.size
            } else break
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncScreenState(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedScreenState(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map { ScreenStateUploadDto(participantId, it.state, it.recordedAt) }
            val resp = api.uploadScreenState(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markScreenStateSynced(batch.map { it.id }); count += batch.size
            } else break
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncScreenInteraction(participantId: String): Int {
        var count = 0
        do {
            val batch = sensorDataDao.getUnsyncedScreenInteraction(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map {
                ScreenInteractionUploadDto(
                    participantId, it.interactionType, it.appName, it.appCategory,
                    it.interactionData?.let { raw -> runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) }.getOrNull() },
                    it.recordedAt
                )
            }
            val resp = api.uploadScreenInteraction(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                sensorDataDao.markScreenInteractionSynced(batch.map { it.id }); count += batch.size
            } else break
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }

    private suspend fun syncEsmResponses(participantId: String): Int {
        var count = 0
        do {
            val batch = esmDao.getUnsynced(Constants.BATCH_SIZE)
            if (batch.isEmpty()) break
            val dtos = batch.map { e ->
                val responsesJson = e.responses?.let {
                    runCatching { Json.parseToJsonElement(it) }.getOrNull()
                }
                EsmResponseUploadDto(participantId, e.scheduleId, e.triggeredAt, e.respondedAt, e.expired, responsesJson, e.recordedAt)
            }
            val resp = api.uploadEsmResponses(dtos)
            if (resp.isSuccessful || resp.code() == 201) {
                esmDao.markSynced(batch.map { it.id }); count += batch.size
            } else break
        } while (batch.size == Constants.BATCH_SIZE)
        return count
    }
}
