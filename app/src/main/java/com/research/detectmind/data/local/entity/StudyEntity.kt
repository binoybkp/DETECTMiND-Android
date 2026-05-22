package com.research.detectmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity(tableName = "studies")
data class StudyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val appDescription: String?,
    val status: String,
    val syncIntervalMinutes: Int = 30,
    val config: String? = null,
    val synced: Boolean = false
) {
    val guidedPermissions: Boolean
        get() = runCatching {
            config?.let { JSONObject(it).optBoolean("guided_permissions", false) } ?: false
        }.getOrDefault(false)

    val autoParticipantId: Boolean
        get() = runCatching {
            config?.let { JSONObject(it).optBoolean("auto_participant_id", false) } ?: false
        }.getOrDefault(false)
}
