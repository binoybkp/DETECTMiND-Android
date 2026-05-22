package com.research.detectmind.esm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.research.detectmind.data.local.dao.EsmDao
import com.research.detectmind.data.local.entity.EsmResponseEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class EsmAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var esmDao: EsmDao
    @Inject lateinit var dataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(Constants.EXTRA_SCHEDULE_ID) ?: return
        val triggeredAt = intent.getLongExtra(Constants.EXTRA_TRIGGERED_AT, System.currentTimeMillis())
        val title = intent.getStringExtra("title") ?: "Survey"
        val body = intent.getStringExtra("body") ?: "You have a survey to complete."
        val expiryMinutes = intent.getIntExtra("expiry_minutes", 60)

        val triggerAtStr = Instant.ofEpochMilli(triggeredAt).toString()

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = dataStore.data.first()
            val participantId = prefs[EnrollmentRepository.KEY_PARTICIPANT_ID] ?: return@launch

            // Insert a pending response row to track expiry
            val responseId = esmDao.insertResponse(
                EsmResponseEntity(
                    participantId = participantId,
                    scheduleId = scheduleId,
                    triggeredAt = triggerAtStr,
                    respondedAt = null,
                    expired = false,
                    responses = null,
                    recordedAt = Instant.now().toString()
                )
            )

            // Show the notification on main thread via Handler
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                showNotification(context, scheduleId, triggeredAt, title, body, responseId)
                if (expiryMinutes > 0) {
                    scheduleExpiry(context, responseId, expiryMinutes)
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        scheduleId: String,
        triggeredAt: Long,
        title: String,
        body: String,
        responseId: Long
    ) {
        val surveyIntent = Intent(context, EsmActivity::class.java).apply {
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_TRIGGERED_AT, triggeredAt)
            putExtra(Constants.EXTRA_RESPONSE_ID, responseId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, responseId.toInt(), surveyIntent,  // unique per trigger, not per schedule
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ESM)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(responseId.toInt(), notification)  // unique per trigger — multiple schedules don't overwrite each other
    }

    private fun scheduleExpiry(
        context: Context,
        responseId: Long,
        expiryMinutes: Int
    ) {
        val expiryIntent = Intent(context, EsmExpiryReceiver::class.java).apply {
            putExtra(Constants.EXTRA_RESPONSE_ID, responseId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ("expiry_$responseId").hashCode(),  // responseId is unique per trigger
            expiryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + expiryMinutes * 60_000L
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
