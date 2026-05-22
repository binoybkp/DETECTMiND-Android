package com.research.detectmind.esm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.research.detectmind.data.local.dao.EsmDao
import com.research.detectmind.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EsmExpiryReceiver : BroadcastReceiver() {

    @Inject lateinit var esmDao: EsmDao

    override fun onReceive(context: Context, intent: Intent) {
        val responseId = intent.getLongExtra(Constants.EXTRA_RESPONSE_ID, -1L)
        val scheduleId = intent.getStringExtra(Constants.EXTRA_SCHEDULE_ID) ?: return
        val notifId = scheduleId.hashCode()

        // Cancel the survey notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifId)

        // Mark the response as expired if it hasn't been answered yet
        if (responseId >= 0) {
            CoroutineScope(Dispatchers.IO).launch {
                esmDao.markExpired(responseId)
            }
        }
    }
}
