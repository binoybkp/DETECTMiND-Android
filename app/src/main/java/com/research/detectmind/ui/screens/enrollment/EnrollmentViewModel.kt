package com.research.detectmind.ui.screens.enrollment

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.research.detectmind.data.local.dao.SensorConfigDao
import com.research.detectmind.data.local.dao.StudyDao
import com.research.detectmind.data.local.entity.StudyEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.data.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PermissionKind { RUNTIME, USAGE_STATS, NOTIFICATION_LISTENER, BACKGROUND_LOCATION }

data class SensorPermission(
    val sensorType: String,
    val label: String,
    val description: String,
    val permissions: List<String>,
    val kind: PermissionKind = PermissionKind.RUNTIME
)

val ALL_SENSOR_PERMISSIONS = listOf(
    SensorPermission(
        sensorType = "location",
        label = "Location (Allow all the time)",
        description = "Go to Location → select \"Allow all the time\" to enable continuous location collection.",
        permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        kind = PermissionKind.BACKGROUND_LOCATION
    ),
    SensorPermission(
        sensorType = "calls",
        label = "Call Log",
        description = "Records call direction and duration. Contact numbers are SHA-256 hashed.",
        permissions = listOf(Manifest.permission.READ_CALL_LOG)
    ),
    SensorPermission(
        sensorType = "sms",
        label = "SMS Metadata",
        description = "Records message direction and timestamps. Content and contacts are hashed.",
        permissions = listOf(Manifest.permission.READ_SMS)
    ),
    SensorPermission(
        sensorType = "esm_ema",
        label = "Notifications (ESM)",
        description = "Required to show survey prompts.",
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    ),
    SensorPermission(
        sensorType = "app_usage",
        label = "App Usage Access",
        description = "Tracks which apps are used and for how long. Grant under Special App Access → Usage access.",
        permissions = emptyList(),
        kind = PermissionKind.USAGE_STATS
    ),
    SensorPermission(
        sensorType = "notifications",
        label = "Notification Access",
        description = "Records notification metadata (title, app). Grant under Special App Access → Notification access.",
        permissions = emptyList(),
        kind = PermissionKind.NOTIFICATION_LISTENER
    ),
)

data class PermissionStatus(
    val sensorPermission: SensorPermission,
    val granted: Boolean
)

data class EnrollmentUiState(
    // Study list
    val studies: List<StudyEntity> = emptyList(),
    val studiesLoading: Boolean = false,
    val studiesError: String? = null,
    // Study detail + device ID input
    val selectedStudy: StudyEntity? = null,
    val deviceId: String = "",
    val deviceIdError: String? = null,
    // Enrollment network call
    val enrolling: Boolean = false,
    val enrollError: String? = null,
    // Permission onboarding
    val permissionStatuses: List<PermissionStatus> = emptyList(),
    // Success
    val enrolledParticipantId: String? = null,  // internal UUID, not shown to user
    val enrolledDeviceId: String? = null,        // the 6-char code the user typed
    val enabledSensorTypes: List<String> = emptyList(),
    val guidedPermissions: Boolean = false,
    val autoParticipantId: Boolean = false
)

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val sensorConfigDao: SensorConfigDao,
    private val studyDao: StudyDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(EnrollmentUiState())
    val state: StateFlow<EnrollmentUiState> = _state

    // ── Study List ──────────────────────────────────────────────────────────

    fun loadStudies() {
        viewModelScope.launch {
            _state.update { it.copy(studiesLoading = true, studiesError = null) }
            studyRepository.fetchAndStoreActiveStudies()
                .onSuccess { studies ->
                    _state.update { it.copy(studies = studies, studiesLoading = false) }
                }
                .onFailure { e ->
                    // Offline fallback — use whatever is cached in Room
                    val cached = studyRepository.getCachedActiveStudies()
                    if (cached.isNotEmpty()) {
                        _state.update { it.copy(studies = cached, studiesLoading = false) }
                    } else {
                        _state.update {
                            it.copy(studiesLoading = false, studiesError = e.message ?: "Failed to load studies")
                        }
                    }
                }
        }
    }

    // ── Study Detail ────────────────────────────────────────────────────────

    fun selectStudy(study: StudyEntity) {
        _state.update {
            it.copy(
                selectedStudy = study,
                deviceId = "",
                deviceIdError = null,
                enrollError = null,
                enrolledParticipantId = null,
                autoParticipantId = study.autoParticipantId
            )
        }
    }

    fun autoEnroll() {
        val study = _state.value.selectedStudy ?: return
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: ""
        val deviceId = androidId.take(6).uppercase().padEnd(6, '0')
        _state.update { it.copy(enrolling = true, enrollError = null) }
        viewModelScope.launch {
            enrollmentRepository.enrollWithDeviceId(deviceId = deviceId, studyId = study.id)
                .onSuccess {
                    val participantId = enrollmentRepository.getParticipantId()
                    val sensorTypes = sensorConfigDao.getEnabledConfigs(study.id).map { it.sensorType }
                    val guided = studyDao.getStudy(study.id)?.guidedPermissions ?: false
                    _state.update {
                        it.copy(
                            enrolling = false,
                            enrolledParticipantId = participantId,
                            enrolledDeviceId = deviceId,
                            enabledSensorTypes = sensorTypes,
                            guidedPermissions = guided
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(enrolling = false, enrollError = e.message ?: "Enrollment failed") }
                }
        }
    }

    fun onDeviceIdChange(value: String) {
        if (value.length <= 6) _state.update { it.copy(deviceId = value, deviceIdError = null) }
    }

    fun enrollWithSelectedStudy() {
        val id = _state.value.deviceId.trim()
        if (id.length != 6) {
            _state.update { it.copy(deviceIdError = "Must be exactly 6 characters") }
            return
        }
        val study = _state.value.selectedStudy ?: return
        _state.update { it.copy(enrolling = true, enrollError = null) }
        viewModelScope.launch {
            enrollmentRepository.enrollWithDeviceId(deviceId = id, studyId = study.id)
                .onSuccess {
                    val participantId = enrollmentRepository.getParticipantId()
                    val sensorTypes = sensorConfigDao.getEnabledConfigs(study.id).map { it.sensorType }
                    val guided = studyDao.getStudy(study.id)?.guidedPermissions ?: false
                    _state.update {
                        it.copy(
                            enrolling = false,
                            enrolledParticipantId = participantId,
                            enrolledDeviceId = id,
                            enabledSensorTypes = sensorTypes,
                            guidedPermissions = guided
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(enrolling = false, enrollError = e.message ?: "Enrollment failed") }
                }
        }
    }

    // ── Permissions ─────────────────────────────────────────────────────────

    fun refreshPermissions(enabledSensorTypes: List<String>) {
        val relevant = ALL_SENSOR_PERMISSIONS.filter { sp ->
            enabledSensorTypes.contains(sp.sensorType)
        }
        val statuses = relevant.map { sp ->
            val granted = when (sp.kind) {
                PermissionKind.RUNTIME -> {
                    if (sp.permissions.isEmpty()) true
                    else sp.permissions.all { perm ->
                        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                    }
                }
                PermissionKind.USAGE_STATS -> isUsageStatsGranted()
                PermissionKind.NOTIFICATION_LISTENER -> isNotificationListenerGranted()
                PermissionKind.BACKGROUND_LOCATION -> ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }
            PermissionStatus(sp, granted)
        }
        _state.update { it.copy(permissionStatuses = statuses) }
    }

    private fun isUsageStatsGranted(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun isNotificationListenerGranted(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    // ── Consent / monitoring start ───────────────────────────────────────────

    fun saveConsent() {
        viewModelScope.launch { enrollmentRepository.saveConsent() }
    }
}
