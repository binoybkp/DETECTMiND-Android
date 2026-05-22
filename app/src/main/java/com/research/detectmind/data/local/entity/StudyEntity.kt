package com.research.detectmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studies")
data class StudyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val appDescription: String?,
    val status: String,
    val syncIntervalMinutes: Int = 30,
    val synced: Boolean = false
)
