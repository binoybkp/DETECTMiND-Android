package com.research.detectmind.data.repository

import com.research.detectmind.data.local.dao.ParticipantDao
import com.research.detectmind.data.local.entity.ParticipantEntity
import com.research.detectmind.data.remote.api.SupabaseApi
import com.research.detectmind.data.remote.dto.ParticipantPatchDto
import com.research.detectmind.data.remote.dto.ParticipantStatusPatchDto
import com.research.detectmind.data.remote.dto.ParticipantUploadDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParticipantRepository @Inject constructor(
    private val api: SupabaseApi,
    private val participantDao: ParticipantDao
) {
    fun observeParticipant(): Flow<ParticipantEntity?> = participantDao.observeParticipant()

    suspend fun getParticipant(): ParticipantEntity? = participantDao.getParticipant()

    suspend fun upsertToRemote(entity: ParticipantEntity): Result<Unit> = runCatching {
        val dto = ParticipantUploadDto(
            id = entity.id,
            studyId = entity.studyId,
            deviceId = entity.deviceId,
            label = entity.label,
            status = entity.status
        )
        val response = api.upsertParticipant(dto)
        if (!response.isSuccessful)
            error("Upsert participant failed: HTTP ${response.code()}")
    }

    suspend fun patchLastSync(participantId: String, timestamp: String): Result<Unit> = runCatching {
        val response = api.patchParticipant(
            id = "eq.$participantId",
            patch = ParticipantPatchDto(lastSyncAt = timestamp)
        )
        if (!response.isSuccessful) error("Patch participant failed: HTTP ${response.code()}")
        participantDao.updateLastSync(participantId, timestamp)
    }

    suspend fun patchDeviceInfo(participantId: String, deviceInfoJson: String): Result<Unit> = runCatching {
        val deviceInfo = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(deviceInfoJson)
        }.getOrNull() ?: JsonNull
        val response = api.patchParticipant(
            id = "eq.$participantId",
            patch = ParticipantPatchDto(deviceInfo = deviceInfo)
        )
        if (!response.isSuccessful) error("Patch device info failed: HTTP ${response.code()}")
    }

    suspend fun patchPermissions(participantId: String, permissionsJson: String): Result<Unit> = runCatching {
        val permissions = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(permissionsJson)
        }.getOrNull() ?: JsonNull
        val response = api.patchParticipant(
            id = "eq.$participantId",
            patch = ParticipantPatchDto(permissions = permissions)
        )
        if (!response.isSuccessful) error("Patch permissions failed: HTTP ${response.code()}")
    }

    suspend fun withdraw(participantId: String): Result<Unit> = runCatching {
        val response = api.patchParticipantStatus(
            id = "eq.$participantId",
            patch = ParticipantStatusPatchDto(status = "withdrawn")
        )
        if (!response.isSuccessful) error("Withdraw failed: HTTP ${response.code()}")
        // Update local Room record too
        val current = participantDao.getParticipant()
        if (current != null) participantDao.upsert(current.copy(status = "withdrawn"))
    }
}
