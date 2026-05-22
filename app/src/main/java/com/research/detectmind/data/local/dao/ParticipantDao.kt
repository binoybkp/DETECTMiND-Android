package com.research.detectmind.data.local.dao

import androidx.room.*
import com.research.detectmind.data.local.entity.ParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participants ORDER BY CASE status WHEN 'active' THEN 0 ELSE 1 END LIMIT 1")
    fun observeParticipant(): Flow<ParticipantEntity?>

    @Query("SELECT * FROM participants ORDER BY CASE status WHEN 'active' THEN 0 ELSE 1 END LIMIT 1")
    suspend fun getParticipant(): ParticipantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(participant: ParticipantEntity)

    @Query("UPDATE participants SET lastSyncAt = :timestamp WHERE id = :id")
    suspend fun updateLastSync(id: String, timestamp: String)

    @Query("DELETE FROM participants")
    suspend fun clearAll()
}
