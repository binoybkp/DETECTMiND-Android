package com.research.detectmind.service.collectors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.research.detectmind.data.local.entity.BatteryEntity
import com.research.detectmind.data.repository.SensorDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

class BatteryCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SensorDataRepository
) : SensorCollector {

    override val sensorType = "battery"
    private var job: Job? = null

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        val delayMs = (intervalSeconds ?: 60).coerceAtLeast(1) * 1000L
        job = scope.launch {
            while (true) {
                record(participantId)
                delay(delayMs)
            }
        }
    }

    override fun stop() { job?.cancel() }

    private suspend fun record(participantId: String) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> null
        }
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).toFloat()
        val pct = if (scale > 0) (level * 100 / scale) else level
        repo.insertBattery(
            BatteryEntity(
                participantId = participantId, level = pct, isCharging = isCharging,
                chargingType = chargingType, temperature = temperature, voltage = voltage,
                recordedAt = Instant.now().toString()
            )
        )
    }
}
