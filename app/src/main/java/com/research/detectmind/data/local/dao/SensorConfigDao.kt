package com.research.detectmind.data.local.dao

import androidx.room.*
import com.research.detectmind.data.local.entity.SensorConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorConfigDao {
    @Query("SELECT * FROM sensor_configs WHERE studyId = :studyId")
    fun observeConfigs(studyId: String): Flow<List<SensorConfigEntity>>

    @Query("SELECT * FROM sensor_configs WHERE studyId = :studyId")
    suspend fun getConfigs(studyId: String): List<SensorConfigEntity>

    @Query("SELECT * FROM sensor_configs WHERE studyId = :studyId AND enabled = 1")
    suspend fun getEnabledConfigs(studyId: String): List<SensorConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(configs: List<SensorConfigEntity>)

    @Query("DELETE FROM sensor_configs")
    suspend fun clearAll()
}
