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
        if (responseId < 0) return

        // Cancel the survey notification (ID matches responseId set in EsmAlarmReceiver)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(responseId.toInt())

        // Mark the response as expired if it hasn't been answered yet
        CoroutineScope(Dispatchers.IO).launch {
            esmDao.markExpired(responseId)
        }
    }
}
