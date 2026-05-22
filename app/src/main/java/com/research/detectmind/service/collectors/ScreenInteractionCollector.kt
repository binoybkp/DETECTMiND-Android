package com.research.detectmind.service.collectors

import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class ScreenInteractionCollector @Inject constructor() : SensorCollector {

    override val sensorType = "screen_interaction"

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        ScreenInteractionService.activeParticipantId = participantId
    }

    override fun stop() {
        ScreenInteractionService.activeParticipantId = null
    }
}
