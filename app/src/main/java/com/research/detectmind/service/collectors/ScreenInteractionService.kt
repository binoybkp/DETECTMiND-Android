package com.research.detectmind.service.collectors

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.research.detectmind.data.local.entity.ScreenInteractionEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.data.repository.SensorDataRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class ScreenInteractionService : AccessibilityService() {

    @Inject lateinit var repo: SensorDataRepository
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolvedParticipantId: String? = null

    // In-memory event buffer — flushed to Room every FLUSH_INTERVAL_MS
    private val eventBuffer = mutableListOf<ScreenInteractionEntity>()
    private val bufferMutex = Mutex()
    private var flushJob: Job? = null


    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
                    AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
            notificationTimeout = 0
        }
        serviceScope.launch {
            resolvedParticipantId = activeParticipantId
                ?: dataStore.data.first()[EnrollmentRepository.KEY_PARTICIPANT_ID]
        }
        // Start periodic flush loop
        flushJob = serviceScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushBuffer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Flush any remaining buffered events before service dies
        flushJob?.cancel()
        serviceScope.launch { flushBuffer() }
    }

    private suspend fun flushBuffer() {
        val toInsert = bufferMutex.withLock {
            if (eventBuffer.isEmpty()) return
            val copy = eventBuffer.toList()
            eventBuffer.clear()
            copy
        }
        repo.insertScreenInteractions(toInsert)
    }

    private suspend fun participantId(): String? =
        resolvedParticipantId ?: activeParticipantId
            ?: dataStore.data.first()[EnrollmentRepository.KEY_PARTICIPANT_ID]
                .also { resolvedParticipantId = it }

    // 50dp swipe threshold — converted once at runtime to raw pixels
    private val swipeThresholdPx by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50f, resources.displayMetrics)
    }

    // Touch tracking for swipe detection
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartMs = 0L
    private var touchHadScroll = false  // true if a scroll event fired during this touch

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        // Skip events with no package — lock screen transitions, unresolvable system events
        if (packageName == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                val (ex, ey) = eventCoords(event) ?: Pair(0f, 0f)
                touchStartX = ex
                touchStartY = ey
                touchStartMs = System.currentTimeMillis()
                touchHadScroll = false
                return
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                // Only record swipes here — taps are handled by TYPE_VIEW_CLICKED,
                // long-presses by TYPE_VIEW_LONG_CLICKED, scrolls by TYPE_VIEW_SCROLLED.
                // If a scroll already fired for this gesture, skip to avoid duplicates.
                if (touchHadScroll) return
                val (ex, ey) = eventCoords(event) ?: Pair(touchStartX, touchStartY)
                val dx = ex - touchStartX
                val dy = ey - touchStartY
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > swipeThresholdPx) {
                    val durationMs = System.currentTimeMillis() - touchStartMs
                    val data = JSONObject().apply {
                        put("from_x", touchStartX.toInt())
                        put("from_y", touchStartY.toInt())
                        put("to_x", ex.toInt())
                        put("to_y", ey.toInt())
                        put("distance", dist.toInt())
                        put("direction", swipeDirection(dx, dy))
                        put("duration_ms", durationMs)
                    }
                    record(packageName, "swipe", data)
                }
                // Taps (dist <= threshold) are already recorded via TYPE_VIEW_CLICKED — skip here.
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val (ex, ey) = eventCoords(event) ?: return  // skip if coords unavailable
                val data = JSONObject().apply { put("x", ex.toInt()); put("y", ey.toInt()) }
                record(packageName, "touch", data)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val (ex, ey) = eventCoords(event) ?: return  // skip if coords unavailable
                val data = JSONObject().apply { put("x", ex.toInt()); put("y", ey.toInt()) }
                record(packageName, "long_press", data)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                touchHadScroll = true  // suppress swipe duplicate for this touch sequence
                // scrollDeltaX/Y (API 28+) gives the actual pixels scrolled this event.
                // On older APIs fall back to scrollX/Y (absolute position) — less precise but usable.
                val scrollDx: Int
                val scrollDy: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    scrollDx = event.scrollDeltaX
                    scrollDy = event.scrollDeltaY
                } else {
                    scrollDx = event.scrollX
                    scrollDy = event.scrollY
                }
                // Skip scroll events with zero delta — no actual movement to record
                if (scrollDx == 0 && scrollDy == 0) return
                val (ex, ey) = eventCoords(event) ?: Pair(0f, 0f)
                val data = JSONObject().apply {
                    put("x", ex.toInt()); put("y", ey.toInt())
                    put("scroll_dx", scrollDx); put("scroll_dy", scrollDy)
                    put("direction", swipeDirection(scrollDx.toFloat(), scrollDy.toFloat()))
                }
                record(packageName, "scroll", data)
            }
            else -> return
        }
    }

    private fun record(packageName: String?, interactionType: String, interactionData: JSONObject) {
        val appNameResolved = packageName?.let { resolveAppName(it) }
        val appCategoryResolved = packageName?.let { resolveAppCategory(it) }
        val recordedAt = Instant.now().toString()

        serviceScope.launch {
            val pid = participantId() ?: return@launch
            val entity = ScreenInteractionEntity(
                participantId = pid,
                interactionType = interactionType,
                appName = appNameResolved,
                appCategory = appCategoryResolved,
                interactionData = interactionData.toString(),
                recordedAt = recordedAt
            )
            bufferMutex.withLock { eventBuffer.add(entity) }
        }
    }

    private fun resolveAppName(packageName: String): String? = runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        ).toString()
    }.getOrNull()

    private fun resolveAppCategory(packageName: String): String? = runCatching {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (info.category) {
                ApplicationInfo.CATEGORY_GAME        -> "game"
                ApplicationInfo.CATEGORY_AUDIO       -> "audio"
                ApplicationInfo.CATEGORY_VIDEO       -> "video"
                ApplicationInfo.CATEGORY_IMAGE       -> "image"
                ApplicationInfo.CATEGORY_SOCIAL      -> "social"
                ApplicationInfo.CATEGORY_NEWS        -> "news"
                ApplicationInfo.CATEGORY_MAPS        -> "maps"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
                ApplicationInfo.CATEGORY_UNDEFINED   -> null  // truly uncategorised — store null, not "other"
                else -> "other"
            }
        } else null
    }.getOrNull()

    private fun swipeDirection(dx: Float, dy: Float): String {
        return if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) "right" else "left"
        } else {
            if (dy > 0) "down" else "up"
        }
    }

    // Returns null when the source node is unavailable — callers should skip recording in that case.
    private fun eventCoords(event: AccessibilityEvent): Pair<Float, Float>? {
        val node = event.source ?: return null
        return try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            Pair(rect.exactCenterX(), rect.exactCenterY())
        } catch (e: Exception) {
            null
        } finally {
            node.recycle()
        }
    }

    override fun onInterrupt() {
        flushJob?.cancel()
        serviceScope.launch { flushBuffer() }
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 30_000L
        @Volatile var activeParticipantId: String? = null
    }
}
