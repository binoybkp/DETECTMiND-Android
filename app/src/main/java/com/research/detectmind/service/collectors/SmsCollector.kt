package com.research.detectmind.service.collectors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.research.detectmind.data.local.entity.SmsEntity
import com.research.detectmind.data.repository.SensorDataRepository
import com.research.detectmind.util.CryptoUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

class SmsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SensorDataRepository
) : SensorCollector {

    override val sensorType = "sms"
    private var scope: CoroutineScope? = null
    private var participantId: String = ""
    private var lastSeenId: Long = 0L
    private var observer: ContentObserver? = null

    private val smsUri = Uri.parse("content://sms")

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED) return

        this.scope = scope
        this.participantId = participantId
        lastSeenId = queryMaxId()

        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch { drain() }
            }
        }
        observer = obs
        context.contentResolver.registerContentObserver(smsUri, true, obs)
    }

    override fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        scope = null
    }

    private fun queryMaxId(): Long {
        return runCatching {
            context.contentResolver.query(
                smsUri, arrayOf("_id"), null, null, "_id DESC LIMIT 1"
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private suspend fun drain() {
        runCatching {
            context.contentResolver.query(
                smsUri,
                arrayOf("_id", "address", "body", "date", "type"),
                "_id > ?",
                arrayOf(lastSeenId.toString()),
                "_id ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val addressIdx = cursor.getColumnIndexOrThrow("address")
                val bodyIdx = cursor.getColumnIndexOrThrow("body")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                val typeIdx = cursor.getColumnIndexOrThrow("type")

                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(idIdx)
                    // Telephony.Sms.MESSAGE_TYPE: 1=inbox, 2=sent, 3=draft, 4=outbox, 5=failed, 6=queued
                    val direction = when (cursor.getInt(typeIdx)) {
                        1 -> "incoming"
                        2 -> "outgoing"
                        else -> continue  // skip drafts/failed/queued
                    }
                    val eventTime = Instant.ofEpochMilli(cursor.getLong(dateIdx)).toString()
                    val contactHash = CryptoUtil.sha256(cursor.getString(addressIdx) ?: "")
                    val bodyHash = CryptoUtil.sha256(cursor.getString(bodyIdx) ?: "")

                    repo.insertSms(
                        SmsEntity(
                            participantId = participantId,
                            direction = direction,
                            eventTime = eventTime,
                            contactHash = contactHash,
                            bodyHash = bodyHash,
                            recordedAt = Instant.now().toString()
                        )
                    )
                    if (rowId > lastSeenId) lastSeenId = rowId
                }
            }
        }
    }
}
