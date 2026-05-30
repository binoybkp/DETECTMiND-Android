package com.research.detectmind.di

import android.content.Context
import androidx.room.Room
import com.research.detectmind.data.local.AppDatabase
import com.research.detectmind.data.local.MIGRATION_7_8
import com.research.detectmind.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "participant_monitor.db")
            .addMigrations(MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideStudyDao(db: AppDatabase): StudyDao = db.studyDao()
    @Provides fun provideParticipantDao(db: AppDatabase): ParticipantDao = db.participantDao()
    @Provides fun provideSensorConfigDao(db: AppDatabase): SensorConfigDao = db.sensorConfigDao()
    @Provides fun provideEsmDao(db: AppDatabase): EsmDao = db.esmDao()
    @Provides fun provideSensorDataDao(db: AppDatabase): SensorDataDao = db.sensorDataDao()
}
