package com.research.detectmind.service.collectors

import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class NotificationSensorCollector @Inject constructor() : SensorCollector {

    override val sensorType = "notifications"

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        NotificationCollector.activeParticipantId = participantId
    }

    override fun stop() {
        NotificationCollector.activeParticipantId = null
    }
}
