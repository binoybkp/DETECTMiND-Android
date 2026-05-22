package com.research.detectmind.data.local.dao

import androidx.room.*
import com.research.detectmind.data.local.entity.EsmQuestionEntity
import com.research.detectmind.data.local.entity.EsmResponseEntity
import com.research.detectmind.data.local.entity.EsmScheduleEntity

@Dao
interface EsmDao {
    @Query("SELECT * FROM esm_schedules WHERE studyId = :studyId AND enabled = 1")
    suspend fun getEnabledSchedules(studyId: String): List<EsmScheduleEntity>

    @Query("SELECT * FROM esm_schedules WHERE id = :scheduleId")
    suspend fun getSchedule(scheduleId: String): EsmScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedules(schedules: List<EsmScheduleEntity>)

    @Query("SELECT * FROM esm_questions WHERE scheduleId = :scheduleId ORDER BY questionOrder ASC")
    suspend fun getQuestions(scheduleId: String): List<EsmQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(questions: List<EsmQuestionEntity>)

    @Insert
    suspend fun insertResponse(response: EsmResponseEntity): Long

    @Query("UPDATE data_esm_responses SET expired = 1 WHERE id = :id AND respondedAt IS NULL")
    suspend fun markExpired(id: Long)

    @Query("UPDATE data_esm_responses SET respondedAt = :respondedAt, responses = :responses, expired = 0 WHERE id = :id")
    suspend fun submitResponse(id: Long, respondedAt: String, responses: String)

    @Query("SELECT * FROM data_esm_responses WHERE synced = 0 AND (respondedAt IS NOT NULL OR expired = 1) LIMIT :limit")
    suspend fun getUnsynced(limit: Int): List<EsmResponseEntity>

    @Query("UPDATE data_esm_responses SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM esm_schedules")
    suspend fun clearSchedules()

    @Query("DELETE FROM esm_questions")
    suspend fun clearQuestions()
}
