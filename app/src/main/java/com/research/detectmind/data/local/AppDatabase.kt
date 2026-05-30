package com.research.detectmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.research.detectmind.data.local.dao.*
import com.research.detectmind.data.local.entity.*

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS data_screen_interaction")
    }
}

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
        SyncLogEntity::class,
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao
    abstract fun participantDao(): ParticipantDao
    abstract fun sensorConfigDao(): SensorConfigDao
    abstract fun esmDao(): EsmDao
    abstract fun sensorDataDao(): SensorDataDao
}
