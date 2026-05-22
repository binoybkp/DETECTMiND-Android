package com.research.detectmind.service.collectors

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.research.detectmind.data.local.entity.NotificationEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.data.repository.SensorDataRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class NotificationCollector : NotificationListenerService() {

    @Inject lateinit var repo: SensorDataRepository
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolvedParticipantId: String? = null
    // Track notification keys already recorded — onNotificationPosted fires on post AND update
    private val seenKeys = mutableSetOf<String>()
    // Map from sbn.key → Room row ID so removedAt can be updated when a notification is dismissed
    private val keyToRowId = mutableMapOf<String, Long>()

    override fun onListenerConnected() {
        seenKeys.clear()
        keyToRowId.clear()
        serviceScope.launch {
            resolvedParticipantId = activeParticipantId
                ?: dataStore.data.first()[EnrollmentRepository.KEY_PARTICIPANT_ID]
        }
    }

    private suspend fun participantId(): String? =
        resolvedParticipantId ?: activeParticipantId
            ?: dataStore.data.first()[EnrollmentRepository.KEY_PARTICIPANT_ID]
                .also { resolvedParticipantId = it }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // sbn.key is stable across updates to the same notification — skip duplicates
        if (!seenKeys.add(sbn.key)) return
        // Skip notifications from this app itself
        if (sbn.packageName == packageName) return
        // Skip notifications with no title — transient state notifications (e.g. dialer transitions)
        val title = sbn.notification.extras.getString("android.title")
        if (title.isNullOrBlank()) return
        val sbnKey = sbn.key
        serviceScope.launch {
            val pid = participantId() ?: return@launch
            val appName = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(sbn.packageName, 0)
                ).toString()
            }.getOrDefault(sbn.packageName)
            val rowId = repo.insertNotification(
                NotificationEntity(
                    participantId = pid,
                    packageName = sbn.packageName,
                    appName = appName,
                    postedAt = Instant.ofEpochMilli(sbn.postTime).toString(),
                    removedAt = null,
                    title = title,
                    recordedAt = Instant.now().toString()
                )
            )
            keyToRowId[sbnKey] = rowId
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seenKeys.remove(sbn.key)
        val rowId = keyToRowId.remove(sbn.key) ?: return
        serviceScope.launch {
            repo.updateNotificationRemovedAt(rowId, Instant.now().toString())
        }
    }

    companion object {
        @Volatile var activeParticipantId: String? = null
    }
}
