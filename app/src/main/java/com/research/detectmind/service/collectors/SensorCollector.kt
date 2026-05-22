package com.research.detectmind.service.collectors

import kotlinx.coroutines.CoroutineScope

interface SensorCollector {
    val sensorType: String
    fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String? = null)
    fun stop()
}
