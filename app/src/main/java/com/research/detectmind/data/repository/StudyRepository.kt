package com.research.detectmind.data.repository

import com.research.detectmind.data.local.dao.StudyDao
import com.research.detectmind.data.local.entity.StudyEntity
import com.research.detectmind.data.remote.api.SupabaseApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyRepository @Inject constructor(
    private val api: SupabaseApi,
    private val studyDao: StudyDao
) {
    fun observeStudy(studyId: String): Flow<StudyEntity?> = studyDao.observeStudy(studyId)
    fun observeActiveStudies(): Flow<List<StudyEntity>> = studyDao.observeActiveStudies()

    suspend fun getStudy(studyId: String): StudyEntity? = studyDao.getStudy(studyId)
    suspend fun getAnyStudy(): StudyEntity? = studyDao.getAnyStudy()
    suspend fun getCachedActiveStudies(): List<StudyEntity> = studyDao.getActiveStudies()

    suspend fun fetchAndStoreActiveStudies(): Result<List<StudyEntity>> = runCatching {
        val response = api.getActiveStudies()
        val dtos = response.body()
            ?: error("Failed to load studies (HTTP ${response.code()})")
        if (dtos.isEmpty()) error("No active studies found")
        val entities = dtos.map { it.toEntity() }
        entities.forEach { studyDao.upsert(it) }
        entities
    }

    suspend fun fetchAndStoreActiveStudy(): Result<StudyEntity> =
        fetchAndStoreActiveStudies().map { it.first() }
}
