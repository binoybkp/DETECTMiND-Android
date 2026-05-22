package com.research.detectmind.service.collectors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.research.detectmind.data.local.entity.LightEntity
import com.research.detectmind.data.repository.SensorDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

class LightCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SensorDataRepository
) : SensorCollector, SensorEventListener {

    override val sensorType = "light"
    private var job: Job? = null
    private val luxChannel = Channel<Float>(Channel.CONFLATED)
    private var sensorManager: SensorManager? = null

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return  // device has no light sensor
        sensorManager = sm
        sm.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        val delayMs = (intervalSeconds ?: 60).coerceAtLeast(1) * 1000L
        job = scope.launch {
            while (true) {
                delay(delayMs)
                val lux = luxChannel.tryReceive().getOrNull() ?: continue
                repo.insertLight(
                    LightEntity(participantId = participantId, lux = lux, recordedAt = Instant.now().toString())
                )
            }
        }
    }

    override fun stop() {
        sensorManager?.unregisterListener(this)
        job?.cancel()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            luxChannel.trySend(event.values[0])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
