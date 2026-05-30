package com.research.detectmind.ui.screens.home

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.research.detectmind.data.local.dao.ParticipantDao
import com.research.detectmind.data.local.dao.SensorConfigDao
import com.research.detectmind.data.local.dao.SensorDataDao
import com.research.detectmind.data.local.dao.StudyDao
import com.research.detectmind.data.local.entity.ParticipantEntity
import com.research.detectmind.data.local.entity.SensorConfigEntity
import com.research.detectmind.data.local.entity.StudyEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.data.repository.ParticipantRepository
import com.research.detectmind.service.SensorService
import com.research.detectmind.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SensorCounts(val pending: Long, val total: Long)

data class HomeUiState(
    val study: StudyEntity? = null,
    val participant: ParticipantEntity? = null,
    val enabledSensors: List<SensorConfigEntity> = emptyList(),
    val sensorCounts: Map<String, SensorCounts> = emptyMap(),
    val lastSyncFormatted: String? = null,
    val loading: Boolean = true,
    val withdrawn: Boolean = false,
    val permissionIssues: Set<String> = emptySet(),
    val guidedPermissions: Boolean = false
)

/** Describes what kind of permission a sensor needs. */
enum class HomeSensorPermKind { RUNTIME, USAGE_STATS, NOTIFICATION_LISTENER }

/** The runtime Android permissions (if any) required for each sensor type. */
internal val SENSOR_RUNTIME_PERMS: Map<String, List<String>> = buildMap {
    put("location", listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    put("calls", listOf(Manifest.permission.READ_CALL_LOG))
    put("sms", listOf(Manifest.permission.READ_SMS))
    put(
        "esm_ema",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    )
}

/** Returns the permission kind for a given sensor type. */
internal fun sensorPermKind(sensorType: String): HomeSensorPermKind = when (sensorType) {
    "app_usage"          -> HomeSensorPermKind.USAGE_STATS
    "notifications"      -> HomeSensorPermKind.NOTIFICATION_LISTENER
    else -> HomeSensorPermKind.RUNTIME
}

/** Returns the runtime permissions to request for a sensor type (empty = none needed). */
internal fun runtimePermsForSensor(sensorType: String): List<String> =
    SENSOR_RUNTIME_PERMS[sensorType] ?: emptyList()

private val FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val studyDao: StudyDao,
    private val participantDao: ParticipantDao,
    private val sensorConfigDao: SensorConfigDao,
    private val sensorDataDao: SensorDataDao,
    private val participantRepository: ParticipantRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // All count Flows combined — Room invalidates these automatically when synced flag changes
    private val _countsFlow: Flow<Map<String, SensorCounts>> = combine(
        sensorDataDao.pendingAppUsage(), sensorDataDao.totalAppUsage(),
        sensorDataDao.pendingNotifications(), sensorDataDao.totalNotifications(),
        sensorDataDao.pendingBattery(), sensorDataDao.totalBattery()
    ) { a -> a }.combine(
        combine(
            sensorDataDao.pendingCalls(), sensorDataDao.totalCalls(),
            sensorDataDao.pendingSms(), sensorDataDao.totalSms(),
            sensorDataDao.pendingLocation(), sensorDataDao.totalLocation()
        ) { a -> a }
    ) { first, second -> first + second }.combine(
        combine(
            sensorDataDao.pendingLight(), sensorDataDao.totalLight(),
            sensorDataDao.pendingScreenState(), sensorDataDao.totalScreenState()
        ) { a -> a }
    ) { first, second ->
        val all = first + second
        // all = [pendingAppUsage, totalAppUsage, pendingNotif, totalNotif, pendingBattery, totalBattery,
        //        pendingCalls, totalCalls, pendingSms, totalSms, pendingLocation, totalLocation,
        //        pendingLight, totalLight, pendingScreenState, totalScreenState]
        mapOf(
            "app_usage"     to SensorCounts(all[0],  all[1]),
            "notifications" to SensorCounts(all[2],  all[3]),
            "battery"       to SensorCounts(all[4],  all[5]),
            "calls"         to SensorCounts(all[6],  all[7]),
            "sms"           to SensorCounts(all[8],  all[9]),
            "location"      to SensorCounts(all[10], all[11]),
            "light"         to SensorCounts(all[12], all[13]),
            "screen_state"  to SensorCounts(all[14], all[15])
        )
    }

    private val _baseState: StateFlow<HomeUiState> = combine(
        participantDao.observeParticipant(),
        dataStore.data
    ) { participant, prefs ->
        Pair(participant, prefs[EnrollmentRepository.KEY_STUDY_ID])
    }.flatMapLatest { (participant, studyId) ->
        if (studyId == null) flowOf(HomeUiState(loading = false))
        else combine(
            studyDao.observeStudy(studyId),
            sensorConfigDao.observeConfigs(studyId)
        ) { study, configs ->
            val lastSync = participant?.lastSyncAt?.let {
                runCatching { FMT.format(Instant.parse(it)) }.getOrNull()
            }
            HomeUiState(
                study = study,
                participant = participant,
                enabledSensors = configs.filter { it.enabled },
                lastSyncFormatted = lastSync,
                loading = false,
                guidedPermissions = study?.guidedPermissions ?: false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val _permissionIssues = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<HomeUiState> = combine(_baseState, _countsFlow, _permissionIssues) { base, counts, perms ->
        base.copy(sensorCounts = counts, permissionIssues = perms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val _countdown = MutableStateFlow<String?>(null)
    val countdown: StateFlow<String?> = _countdown

    // Sync button state: idle / syncing / success (briefly shown then returns to idle)
    enum class SyncState { IDLE, SYNCING, SUCCESS, PARTIAL, ERROR }
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState

    init {
        viewModelScope.launch {
            while (true) {
                updateSyncCountdown()
                delay(30_000)
            }
        }
        refreshPermissionIssues()
    }

    fun refreshPermissionIssues() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val studyId = prefs[EnrollmentRepository.KEY_STUDY_ID] ?: return@launch
            val configs = sensorConfigDao.getEnabledConfigs(studyId)
            val issues = configs.map { it.sensorType }.filter { sensorType ->
                when (sensorPermKind(sensorType)) {
                    HomeSensorPermKind.USAGE_STATS -> !isUsageStatsGranted()
                    HomeSensorPermKind.NOTIFICATION_LISTENER -> !isNotificationListenerGranted()
                    HomeSensorPermKind.RUNTIME -> {
                        val perms = runtimePermsForSensor(sensorType)
                        perms.isNotEmpty() && perms.any {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                    }
                }
            }.toSet()
            _permissionIssues.value = issues
        }
    }

    private fun isUsageStatsGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        else
            @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationListenerGranted(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(context.packageName)
    }

    private fun updateSyncCountdown() {
        val current = _baseState.value
        val participant = current.participant ?: return
        val study = current.study ?: return
        val lastSyncMs = participant.lastSyncAt?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        } ?: return
        val intervalMs = study.syncIntervalMinutes * 60_000L
        val nextMs = lastSyncMs + intervalMs
        val diffMin = ((nextMs - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        _countdown.value = if (diffMin == 0L) "Syncing soon" else "in ${diffMin}m"
    }

    fun triggerManualSync() {
        if (_syncState.value == SyncState.SYNCING) return
        val wm = WorkManager.getInstance(context)
        val request = SyncWorker.buildOneShot()
        wm.enqueueUniqueWork("manual_sync", ExistingWorkPolicy.REPLACE, request)
        _syncState.value = SyncState.SYNCING
        viewModelScope.launch {
            // Timeout: reset to IDLE after 60 s regardless of worker state
            val timeoutJob = launch {
                delay(60_000)
                if (_syncState.value == SyncState.SYNCING) _syncState.value = SyncState.IDLE
            }
            wm.getWorkInfoByIdFlow(request.id)
                .collect { info ->
                    if (info == null) return@collect
                    when (info.state) {
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            timeoutJob.cancel()
                            val status = info.outputData.getString(SyncWorker.KEY_SYNC_STATUS)
                            _syncState.value = when (status) {
                                "partial" -> SyncState.PARTIAL
                                "error"   -> SyncState.ERROR
                                else      -> SyncState.SUCCESS
                            }
                            delay(3000)
                            _syncState.value = SyncState.IDLE
                            return@collect
                        }
                        androidx.work.WorkInfo.State.FAILED,
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            timeoutJob.cancel()
                            _syncState.value = SyncState.IDLE
                            return@collect
                        }
                        else -> {} // ENQUEUED, RUNNING, BLOCKED — keep showing SYNCING
                    }
                }
        }
    }

    fun withdraw() {
        viewModelScope.launch {
            val participant = state.value.participant ?: return@launch
            context.stopService(Intent(context, SensorService::class.java))
            participantRepository.withdraw(participant.id)
                .onSuccess { enrollmentRepository.clearEnrollment() }
                .onFailure { enrollmentRepository.clearEnrollment() }
        }
    }
}
