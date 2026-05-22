package com.research.detectmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey val id: String,
    val studyId: String,
    val deviceId: String,
    val label: String?,
    val status: String,   // active | withdrawn
    val enrolledAt: String?,
    val lastSyncAt: String?,
    val deviceInfo: String?,
    val permissions: String?,
    val synced: Boolean = false
)
