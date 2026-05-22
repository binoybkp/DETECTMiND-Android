package com.research.detectmind.worker

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.service.SensorService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs every 15 minutes. If the participant is enrolled and the SensorService is not running,
 * restarts it. This guards against OS-level service kills by battery optimizers or OEM task killers.
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val enrolled = dataStore.data.first()[EnrollmentRepository.KEY_ENROLLED] == true
        if (enrolled && !SensorService.isRunning) {
            appContext.startForegroundService(Intent(appContext, SensorService::class.java))
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "service_watchdog"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
                    .build()
            )
        }
    }
}
