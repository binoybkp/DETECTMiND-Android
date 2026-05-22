package com.research.detectmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.research.detectmind.data.local.dao.*
import com.research.detectmind.data.local.entity.*

@Database(
    entities = [
        StudyEntity::class,
        ParticipantEntity::class,
        SensorConfigEntity::class,
        EsmScheduleEntity::class,
        EsmQuestionEntity::class,
        AppUsageEntity::class,
        NotificationEntity::class,
        BatteryEntity::class,
        CallEntity::class,
        SmsEntity::class,
        EsmResponseEntity::class,
        LocationEntity::class,
        LightEntity::class,
        ScreenStateEntity::class,
        ScreenInteractionEntity::class,
        SyncLogEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao
    abstract fun participantDao(): ParticipantDao
    abstract fun sensorConfigDao(): SensorConfigDao
    abstract fun esmDao(): EsmDao
    abstract fun sensorDataDao(): SensorDataDao
}
