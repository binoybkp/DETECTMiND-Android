package com.research.detectmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_app_usage")
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val packageName: String,
    val appName: String,
    val startTime: String,
    val endTime: String,
    val durationSeconds: Int,
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "data_notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val packageName: String,
    val appName: String,
    val postedAt: String,
    val removedAt: String?,
    val title: String?,
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "data_battery")
data class BatteryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val level: Int,
    val isCharging: Boolean,
    val chargingType: String?,
    val temperature: Float,
    val voltage: Float,
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "data_calls")
data class CallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val direction: String,
    val eventTime: String,
    val durationSeconds: Int,
    val contactHash: String,
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "data_sms")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val direction: String,
    val eventTime: String,
    val contactHash: String,
    val bodyHash: String,
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "data_esm_responses")
data class EsmResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val scheduleId: String,
    val triggeredAt: String,
    val respondedAt: String?,
    val expired: Boolean = false,
    val responses: String?,  // JSON
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "data_location")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val provider: String,
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "data_light")
data class LightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val lux: Float,
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "data_screen_state")
data class ScreenStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val state: String,
    val recordedAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: String,
    val syncedAt: String,
    val recordsSynced: Int,
    val status: String,
    val errorMessage: String?,
    val durationMs: Long
)
