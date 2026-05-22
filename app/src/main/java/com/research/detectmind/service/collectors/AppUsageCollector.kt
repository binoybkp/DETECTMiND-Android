package com.research.detectmind.service.collectors

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.research.detectmind.data.local.entity.AppUsageEntity
import com.research.detectmind.data.repository.SensorDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

class AppUsageCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SensorDataRepository
) : SensorCollector {

    override val sensorType = "app_usage"
    private var job: Job? = null

    // Tracks foreground start time per package for open sessions
    private val sessionStart = mutableMapOf<String, Long>()
    private var lastQueryMs = System.currentTimeMillis()

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        sessionStart.clear()
        val delayMs = (intervalSeconds ?: 60).coerceAtLeast(1) * 1000L
        lastQueryMs = System.currentTimeMillis()
        job = scope.launch {
            while (true) {
                delay(delayMs)
                record(participantId)
            }
        }
    }

    override fun stop() { job?.cancel() }

    private suspend fun record(participantId: String) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQueryMs, now) ?: return
        lastQueryMs = now

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    sessionStart[pkg] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val startMs = sessionStart.remove(pkg) ?: continue
                    val durationMs = event.timeStamp - startMs
                    if (durationMs <= 0) continue
                    val appName = runCatching {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(pkg, 0)
                        ).toString()
                    }.getOrDefault(pkg)
                    repo.insertAppUsage(
                        AppUsageEntity(
                            participantId = participantId,
                            packageName = pkg,
                            appName = appName,
                            startTime = Instant.ofEpochMilli(startMs).toString(),
                            endTime = Instant.ofEpochMilli(event.timeStamp).toString(),
                            durationSeconds = ((durationMs + 500) / 1000).toInt(),
                            recordedAt = Instant.now().toString()
                        )
                    )
                }
            }
        }
    }
}
