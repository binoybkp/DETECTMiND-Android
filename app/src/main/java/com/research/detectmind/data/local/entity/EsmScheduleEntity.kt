package com.research.detectmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "esm_schedules")
data class EsmScheduleEntity(
    @PrimaryKey val id: String,
    val studyId: String,
    val name: String,
    val description: String?,
    val scheduleType: String,       // fixed | random
    val timesOfDay: String?,        // JSON array, fixed only
    val randomCount: Int?,
    val randomWindowStart: String?,
    val randomWindowEnd: String?,
    val expiryMinutes: Int,
    val notificationTitle: String,
    val notificationBody: String,
    val enabled: Boolean,
    val synced: Boolean = false
)
