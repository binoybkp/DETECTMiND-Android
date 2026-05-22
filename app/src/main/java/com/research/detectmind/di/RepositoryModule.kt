package com.research.detectmind.di

import com.research.detectmind.data.repository.*
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module that makes all repositories available in the dependency graph.
 * Repositories use @Singleton + @Inject constructor, so Hilt satisfies them
 * automatically — this module is the explicit declaration point required by
 * the architecture conventions so all repo bindings are traceable in one place.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
// EnrollmentRepository, StudyRepository, ParticipantRepository,
// SensorDataRepository, and SyncRepository are all @Singleton @Inject
// classes — Hilt generates their component bindings without @Provides.
