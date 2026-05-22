package com.research.detectmind.service.collectors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.research.detectmind.data.local.entity.CallEntity
import com.research.detectmind.data.repository.SensorDataRepository
import com.research.detectmind.util.CryptoUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

class CallsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SensorDataRepository
) : SensorCollector {

    override val sensorType = "calls"
    private var scope: CoroutineScope? = null
    private var participantId: String = ""
    private var lastSeenId: Long = 0L
    private val processedIds = mutableSetOf<Long>()
    private var debounceJob: Job? = null
    private var observer: ContentObserver? = null

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED) return

        this.scope = scope
        this.participantId = participantId
        lastSeenId = queryMaxId()
        processedIds.clear()

        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Debounce: Android fires onChange multiple times per call (row created,
                // then updated with final duration). Wait 3s after the last change so we
                // always read the final settled row, never the in-progress ring-time value.
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(5_000)
                    drain()
                }
            }
        }
        observer = obs
        context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, obs)
    }

    override fun stop() {
        debounceJob?.cancel()
        debounceJob = null
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        scope = null
    }

    private fun queryMaxId(): Long {
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                null, null,
                "${CallLog.Calls._ID} DESC LIMIT 1"
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private suspend fun drain() {
        runCatching {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.NUMBER
            )
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls._ID} > ?",
                arrayOf(lastSeenId.toString()),
                "${CallLog.Calls._ID} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)

                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(idIdx)
                    if (rowId in processedIds) continue  // already recorded from a previous onChange

                    val type = cursor.getInt(typeIdx)
                    val direction = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE   -> "missed"
                        else -> continue  // skip rejected, voicemail, blocked, etc.
                    }
                    // Missed calls always get duration=0 regardless of what the log says.
                    // Answered calls get the actual duration from the log (debounce ensures final value).
                    val duration = if (type == CallLog.Calls.MISSED_TYPE) 0 else cursor.getInt(durationIdx)

                    val eventTime = Instant.ofEpochMilli(cursor.getLong(dateIdx)).toString()
                    val contactHash = CryptoUtil.sha256(cursor.getString(numberIdx) ?: "")

                    repo.insertCall(
                        CallEntity(
                            participantId = participantId,
                            direction = direction,
                            eventTime = eventTime,
                            durationSeconds = duration,
                            contactHash = contactHash,
                            recordedAt = Instant.now().toString()
                        )
                    )
                    processedIds.add(rowId)
                    if (rowId > lastSeenId) lastSeenId = rowId
                }
            }
        }
        processedIds.clear()
    }
}
