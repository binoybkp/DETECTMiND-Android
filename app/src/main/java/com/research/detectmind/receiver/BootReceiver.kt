package com.research.detectmind.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.service.SensorService
import com.research.detectmind.worker.EsmSchedulerWorker
import com.research.detectmind.worker.ServiceWatchdogWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var dataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        CoroutineScope(Dispatchers.IO).launch {
            val enrolled = dataStore.data.first()[EnrollmentRepository.KEY_ENROLLED] == true
            if (enrolled) {
                context.startForegroundService(Intent(context, SensorService::class.java))
                ServiceWatchdogWorker.schedule(context)
                // Reboot clears all AlarmManager alarms — reschedule ESM immediately
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "esm_scheduler_boot",
                    ExistingWorkPolicy.REPLACE,
                    EsmSchedulerWorker.buildOneShot()
                )
            }
        }
    }
}
