package com.research.detectmind.data.local.entity

import androidx.room.Entity

@Entity(tableName = "sensor_configs")
data class SensorConfigEntity(
    @androidx.room.PrimaryKey val id: String,
    val studyId: String,
    val sensorType: String,
    val enabled: Boolean,
    val intervalSeconds: Int?,   // null for event-based sensors
    val config: String?,
    val synced: Boolean = false
)
