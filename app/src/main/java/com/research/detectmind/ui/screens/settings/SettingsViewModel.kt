package com.research.detectmind.ui.screens.settings

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
import com.research.detectmind.data.local.dao.ParticipantDao
import com.research.detectmind.data.local.dao.SensorConfigDao
import com.research.detectmind.data.local.dao.StudyDao
import com.research.detectmind.data.local.entity.SensorConfigEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.data.repository.ParticipantRepository
import com.research.detectmind.service.SensorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.research.detectmind.BuildConfig
import javax.inject.Inject

enum class SettingsPermissionKind { RUNTIME, USAGE_STATS, NOTIFICATION_LISTENER, BACKGROUND_LOCATION }

data class SensorPermissionUiState(
    val sensorType: String,
    val label: String,
    val granted: Boolean,
    val kind: SettingsPermissionKind = SettingsPermissionKind.RUNTIME,
    val notRequired: Boolean = false
)

data class SettingsUiState(
    val participantDeviceId: String? = null,
    val studyDescription: String? = null,
    val sensorPermissions: List<SensorPermissionUiState> = emptyList(),
    val appVersion: String = "",
    val guidedPermissions: Boolean = false,
    val withdrawing: Boolean = false,
    val withdrawError: String? = null,
    val withdrawn: Boolean = false,
    val loading: Boolean = true
)

private data class SensorPermSpec(
    val label: String,
    val kind: SettingsPermissionKind,
    val runtimePermissions: List<String> = emptyList()
)

private val SENSOR_SPECS = mapOf(
    "app_usage"          to SensorPermSpec("App Usage",           SettingsPermissionKind.USAGE_STATS),
    "notifications"      to SensorPermSpec("Notifications",       SettingsPermissionKind.NOTIFICATION_LISTENER),
    "battery"            to SensorPermSpec("Battery",             SettingsPermissionKind.RUNTIME),
    "screen_state"       to SensorPermSpec("Screen State",        SettingsPermissionKind.RUNTIME),
"light"              to SensorPermSpec("Ambient Light",       SettingsPermissionKind.RUNTIME),
    "location"           to SensorPermSpec("Location (Allow all the time)", SettingsPermissionKind.BACKGROUND_LOCATION,
                                           listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
    "calls"              to SensorPermSpec("Call Log",            SettingsPermissionKind.RUNTIME,
                                           listOf(Manifest.permission.READ_CALL_LOG)),
    "sms"                to SensorPermSpec("SMS Metadata",        SettingsPermissionKind.RUNTIME,
                                           listOf(Manifest.permission.READ_SMS)),
    "esm_ema"            to SensorPermSpec("ESM Surveys",         SettingsPermissionKind.RUNTIME,
                                           if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                               listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList())
)


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val participantDao: ParticipantDao,
    private val sensorConfigDao: SensorConfigDao,
    private val studyDao: StudyDao,
    private val participantRepository: ParticipantRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")

        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val studyId = prefs[EnrollmentRepository.KEY_STUDY_ID]
            val participant = participantDao.getParticipant()
            val configs: List<SensorConfigEntity> = if (studyId != null)
                sensorConfigDao.getEnabledConfigs(studyId) else emptyList()
            val study = if (studyId != null) studyDao.getStudy(studyId) else null

            _state.update {
                it.copy(
                    participantDeviceId = participant?.deviceId,
                    studyDescription = study?.description?.takeIf { d -> d.isNotBlank() },
                    appVersion = "${versionName ?: "1.0"} (build ${BuildConfig.APP_VERSION_CODE})",
                    sensorPermissions = buildPermissionList(configs),
                    guidedPermissions = study?.guidedPermissions ?: false,
                    loading = false
                )
            }
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val studyId = prefs[EnrollmentRepository.KEY_STUDY_ID] ?: return@launch
            val configs = sensorConfigDao.getEnabledConfigs(studyId)
            _state.update { it.copy(sensorPermissions = buildPermissionList(configs)) }
        }
    }

    private fun buildPermissionList(configs: List<SensorConfigEntity>): List<SensorPermissionUiState> =
        configs.mapNotNull { config ->
            val spec = SENSOR_SPECS[config.sensorType]
            val kind = spec?.kind ?: SettingsPermissionKind.RUNTIME
            val granted = when (kind) {
                SettingsPermissionKind.USAGE_STATS -> isUsageStatsGranted()
                SettingsPermissionKind.NOTIFICATION_LISTENER -> isNotificationListenerGranted()
                SettingsPermissionKind.BACKGROUND_LOCATION -> ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                SettingsPermissionKind.RUNTIME -> {
                    val perms = spec?.runtimePermissions ?: emptyList()
                    perms.isEmpty() || perms.all { perm ->
                        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                    }
                }
            }
            val noPermNeeded = kind == SettingsPermissionKind.RUNTIME && (spec?.runtimePermissions.isNullOrEmpty())
            if (noPermNeeded) return@mapNotNull null
            SensorPermissionUiState(
                sensorType = config.sensorType,
                label = spec?.label ?: config.sensorType.replace("_", " ").replaceFirstChar { it.uppercase() },
                granted = granted,
                kind = kind,
                notRequired = false
            )
        }

    @Suppress("DEPRECATION")
    private fun isUsageStatsGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationListenerGranted(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(context.packageName)
    }

    fun withdraw() {
        viewModelScope.launch {
            _state.update { it.copy(withdrawing = true, withdrawError = null) }
            val participant = participantDao.getParticipant()
            context.stopService(Intent(context, SensorService::class.java))
            if (participant != null) {
                participantRepository.withdraw(participant.id)
                    .onFailure { e ->
                        _state.update { it.copy(withdrawError = e.message) }
                    }
            }
            enrollmentRepository.clearEnrollment()
            _state.update { it.copy(withdrawing = false, withdrawn = true) }
        }
    }
}
