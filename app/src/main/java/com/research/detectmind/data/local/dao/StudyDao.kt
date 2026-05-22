package com.research.detectmind.data.local.dao

import androidx.room.*
import com.research.detectmind.data.local.entity.StudyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM studies WHERE status = 'active'")
    fun observeActiveStudies(): Flow<List<StudyEntity>>

    @Query("SELECT * FROM studies WHERE status = 'active'")
    suspend fun getActiveStudies(): List<StudyEntity>

    @Query("SELECT * FROM studies WHERE id = :studyId LIMIT 1")
    fun observeStudy(studyId: String): Flow<StudyEntity?>

    @Query("SELECT * FROM studies WHERE id = :studyId LIMIT 1")
    suspend fun getStudy(studyId: String): StudyEntity?

    @Query("SELECT * FROM studies LIMIT 1")
    suspend fun getAnyStudy(): StudyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(study: StudyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(studies: List<StudyEntity>)

    @Query("DELETE FROM studies")
    suspend fun clearAll()
}
