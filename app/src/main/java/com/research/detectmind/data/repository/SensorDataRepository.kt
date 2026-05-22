package com.research.detectmind.data.repository

import com.research.detectmind.data.local.dao.SensorDataDao
import com.research.detectmind.data.local.entity.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorDataRepository @Inject constructor(
    private val dao: SensorDataDao
) {
    suspend fun insertAppUsage(e: AppUsageEntity) = dao.insertAppUsage(e)
    suspend fun insertNotification(e: NotificationEntity) = dao.insertNotification(e)
    suspend fun updateNotificationRemovedAt(id: Long, removedAt: String) =
        dao.updateNotificationRemovedAt(id, removedAt)
    suspend fun insertBattery(e: BatteryEntity) = dao.insertBattery(e)
    suspend fun insertCall(e: CallEntity) = dao.insertCall(e)
    suspend fun insertSms(e: SmsEntity) = dao.insertSms(e)
    suspend fun insertLocation(e: LocationEntity) = dao.insertLocation(e)
    suspend fun insertLight(e: LightEntity) = dao.insertLight(e)
    suspend fun insertScreenState(e: ScreenStateEntity) = dao.insertScreenState(e)
    suspend fun insertScreenInteraction(e: ScreenInteractionEntity) = dao.insertScreenInteraction(e)
    suspend fun insertScreenInteractions(entities: List<ScreenInteractionEntity>) = dao.insertScreenInteractions(entities)
    suspend fun insertSyncLog(e: SyncLogEntity) = dao.insertSyncLog(e)
}
