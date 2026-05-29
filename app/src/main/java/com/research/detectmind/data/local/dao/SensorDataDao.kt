package com.research.detectmind.data.local.dao

import androidx.room.*
import com.research.detectmind.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDataDao {

    // App Usage
    @Insert suspend fun insertAppUsage(e: AppUsageEntity): Long
    @Query("SELECT * FROM data_app_usage WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsyncedAppUsage(limit: Int): List<AppUsageEntity>
    @Query("UPDATE data_app_usage SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAppUsageSynced(ids: List<Long>)

    // Notifications
    @Insert suspend fun insertNotification(e: NotificationEntity): Long
    @Query("SELECT * FROM data_notifications WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsyncedNotifications(limit: Int): List<NotificationEntity>
    @Query("UPDATE data_notifications SET synced = 1 WHERE id IN (:ids)")
    suspend fun markNotificationsSynced(ids: List<Long>)
    @Query("UPDATE data_notifications SET removedAt = :removedAt WHERE id = :id")
    suspend fun updateNotificationRemovedAt(id: Long, removedAt: String)

    // Battery
    @Insert suspend fun insertBattery(e: BatteryEntity): Long
    @Query("SELECT * FROM data_battery WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsyncedBattery(limit: Int): List<BatteryEntity>
    @Query("UPDATE data_battery SET synced = 1 WHERE id IN (:ids)")
    suspend fun markBatterySynced(ids: List<Long>)

    // Calls
    @Insert suspend fun insertCall(e: CallEntity): Long
    @Query("SELECT * FROM data_calls WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsyncedCalls(limit: Int): List<CallEntity>
    @Query("UPDATE data_calls SET synced = 1 WHERE id IN (:ids)")
    suspend fun markCallsSynced(ids: List<Long>)

    // SMS
    @Insert suspend fun insertSms(e: SmsEntity): Long
    @Query("SELECT * FROM data_sms WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsyncedSms(limit: Int): List<SmsEntity>
    @Query("UPDATE data_sms SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSmsSynced(ids: List<Long>)

    // Location
    @Insert suspend fun insertLocation(e: LocationEntity): Long
    @Query("SELECT * FROM data_location WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsyncedLocation(limit: Int): List<LocationEntity>
    @Query("UPDATE data_location SET synced = 1 WHERE id IN (:ids)")
    suspend fun markLocationSynced(ids: List<Long>)

    // Light
    @Insert suspend fun insertLight(e: LightEntity): Long
    @Query("SELECT * FROM data_light WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsyncedLight(limit: Int): List<LightEntity>
    @Query("UPDATE data_light SET synced = 1 WHERE id IN (:ids)")
    suspend fun markLightSynced(ids: List<Long>)

    // Screen State
    @Insert suspend fun insertScreenState(e: ScreenStateEntity): Long
    @Query("SELECT * FROM data_screen_state WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsyncedScreenState(limit: Int): List<ScreenStateEntity>
    @Query("UPDATE data_screen_state SET synced = 1 WHERE id IN (:ids)")
    suspend fun markScreenStateSynced(ids: List<Long>)

    // Screen Interaction
    @Insert suspend fun insertScreenInteraction(e: ScreenInteractionEntity): Long
    @Insert suspend fun insertScreenInteractions(entities: List<ScreenInteractionEntity>)
    // Only fetch the 4 types accepted by the Supabase schema CHECK constraint
    @Query("SELECT * FROM data_screen_interaction WHERE synced = 0 AND interactionType IN ('touch','swipe','long_press','scroll') LIMIT :limit")
    suspend fun getUnsyncedScreenInteraction(limit: Int): List<ScreenInteractionEntity>
    @Query("UPDATE data_screen_interaction SET synced = 1 WHERE id IN (:ids)")
    suspend fun markScreenInteractionSynced(ids: List<Long>)
    // Drain local-only types (window_transition, text_input) that are not yet in the server schema
    @Query("UPDATE data_screen_interaction SET synced = 1 WHERE synced = 0 AND interactionType NOT IN ('touch','swipe','long_press','scroll')")
    suspend fun markUnsupportedInteractionTypesSynced()

    // Sync Log
    @Insert suspend fun insertSyncLog(e: SyncLogEntity)

    // Clear all sensor data (called on re-enrollment)
    @Query("DELETE FROM data_app_usage") suspend fun clearAppUsage()
    @Query("DELETE FROM data_notifications") suspend fun clearNotifications()
    @Query("DELETE FROM data_battery") suspend fun clearBattery()
    @Query("DELETE FROM data_calls") suspend fun clearCalls()
    @Query("DELETE FROM data_sms") suspend fun clearSms()
    @Query("DELETE FROM data_location") suspend fun clearLocation()
    @Query("DELETE FROM data_light") suspend fun clearLight()
    @Query("DELETE FROM data_screen_state") suspend fun clearScreenState()
    @Query("DELETE FROM data_screen_interaction") suspend fun clearScreenInteraction()
    @Query("DELETE FROM data_esm_responses") suspend fun clearEsmResponses()
    @Query("DELETE FROM sync_log") suspend fun clearSyncLog()
    suspend fun clearAll() {
        clearAppUsage(); clearNotifications(); clearBattery(); clearCalls()
        clearSms(); clearLocation(); clearLight(); clearScreenState()
        clearScreenInteraction(); clearEsmResponses(); clearSyncLog()
    }

    // Per-sensor counts for HomeScreen display — Flow so UI updates automatically after sync
    @Query("SELECT COUNT(*) FROM data_app_usage WHERE synced = 0") fun pendingAppUsage(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_app_usage") fun totalAppUsage(): Flow<Long>

    @Query("SELECT COUNT(*) FROM data_notifications WHERE synced = 0") fun pendingNotifications(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_notifications") fun totalNotifications(): Flow<Long>

    @Query("SELECT COUNT(*) FROM data_battery WHERE synced = 0") fun pendingBattery(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_battery") fun totalBattery(): Flow<Long>

    @Query("SELECT COUNT(*) FROM data_calls WHERE synced = 0") fun pendingCalls(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_calls") fun totalCalls(): Flow<Long>

    @Query("SELECT COUNT(*) FROM data_sms WHERE synced = 0") fun pendingSms(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_sms") fun totalSms(): Flow<Long>

    @Query("SELECT COUNT(*) FROM data_location WHERE synced = 0") fun pendingLocation(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_location") fun totalLocation(): Flow<Long>

    @Query("SELECT COUNT(*) FROM data_light WHERE synced = 0") fun pendingLight(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_light") fun totalLight(): Flow<Long>

    @Query("SELECT COUNT(*) FROM data_screen_state WHERE synced = 0") fun pendingScreenState(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_screen_state") fun totalScreenState(): Flow<Long>

    // Only count uploadable types so window_transition/text_input don't inflate the pending count
    @Query("SELECT COUNT(*) FROM data_screen_interaction WHERE synced = 0 AND interactionType IN ('touch','swipe','long_press','scroll')") fun pendingScreenInteraction(): Flow<Long>
    @Query("SELECT COUNT(*) FROM data_screen_interaction") fun totalScreenInteraction(): Flow<Long>
}
