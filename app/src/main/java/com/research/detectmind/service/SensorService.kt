package com.research.detectmind.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.research.detectmind.data.local.dao.SensorConfigDao
import com.research.detectmind.data.local.dao.StudyDao
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.service.collectors.SensorCollector
import com.research.detectmind.util.Constants
import com.research.detectmind.worker.EsmSchedulerWorker
import com.research.detectmind.worker.SyncWorker
import com.research.detectmind.worker.ServiceWatchdogWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SensorService : Service() {

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
    }

    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var studyDao: StudyDao
    @Inject lateinit var sensorConfigDao: SensorConfigDao
    // All SensorCollector implementations bound via ServiceModule @IntoSet
    @Inject lateinit var collectors: Set<@JvmSuppressWildcards SensorCollector>

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val activeCollectors = mutableListOf<SensorCollector>()
    private var configObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannels()
        startForeground(Constants.NOTIFICATION_ID_SENSOR, buildNotification())
        observeConfigAndRestart()
        ServiceWatchdogWorker.schedule(this)
    }

    private fun observeConfigAndRestart() {
        configObserverJob = serviceScope.launch {
            dataStore.data
                .flatMapLatest { prefs ->
                    val participantId = prefs[EnrollmentRepository.KEY_PARTICIPANT_ID]
                    val studyId = prefs[EnrollmentRepository.KEY_STUDY_ID]
                    if (participantId == null || studyId == null) {
                        flowOf(Triple(null, null, emptyList()))
                    } else {
                        studyDao.observeStudy(studyId).flatMapLatest { study ->
                            // Only collect when participant is active AND study is active
                            val studyActive = study?.status == "active"
                            if (!studyActive) {
                                flowOf(Triple(participantId, studyId, emptyList()))
                            } else {
                                sensorConfigDao.observeConfigs(studyId).flatMapLatest { configs ->
                                    flowOf(Triple(participantId, studyId, configs))
                                }
                            }
                        }
                    }
                }
                .distinctUntilChanged()
                .collect { (participantId, studyId, configs) ->
                    stopAllCollectors()
                    if (participantId == null || studyId == null) return@collect

                    // Always keep sync running regardless of study status
                    scheduleSyncWork()

                    // Only start collectors when configs are non-empty (study is active)
                    if (configs.isEmpty()) return@collect

                    val enabledConfigs = configs
                        .filter { it.enabled }
                        .associateBy { it.sensorType }

                    for (collector in collectors) {
                        val config = enabledConfigs[collector.sensorType] ?: continue
                        try {
                            collector.start(serviceScope, participantId, config.intervalSeconds, config.config)
                            activeCollectors.add(collector)
                        } catch (e: SecurityException) {
                            // Permission was revoked — skip this collector silently
                        } catch (e: Exception) {
                            // Any other start failure — skip this collector
                        }
                    }

                    scheduleEsmWork()
                }
        }
    }

    private fun stopAllCollectors() {
        activeCollectors.forEach { runCatching { it.stop() } }
        activeCollectors.clear()
    }

    private suspend fun scheduleSyncWork() {
        val study = studyDao.getAnyStudy() ?: return
        val intervalMinutes = study.syncIntervalMinutes.toLong().coerceAtLeast(15)
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            Constants.SYNC_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            SyncWorker.buildRequest(intervalMinutes)
        )
    }

    private fun scheduleEsmWork() {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            Constants.ESM_SCHEDULER_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            EsmSchedulerWorker.buildOneShot()
        )
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "${Constants.ESM_SCHEDULER_WORK_TAG}_daily",
            ExistingPeriodicWorkPolicy.KEEP,
            EsmSchedulerWorker.buildDailyRequest()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure isRunning is true even when Android restarts the service without calling onCreate again
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopAllCollectors()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_SENSOR)
            .setContentTitle("Participant Monitor is running")
            .setContentText("Sensor data is being recorded in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_SENSOR,
                "Sensor Collection",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ESM,
                "Survey Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}
