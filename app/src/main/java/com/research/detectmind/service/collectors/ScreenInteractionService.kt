package com.research.detectmind.service.collectors

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.research.detectmind.data.local.entity.ScreenInteractionEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.data.repository.SensorDataRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class ScreenInteractionService : AccessibilityService() {

    @Inject lateinit var repo: SensorDataRepository
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolvedParticipantId: String? = null

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
    }

    private suspend fun participantId(): String? =
        resolvedParticipantId ?: activeParticipantId
            ?: dataStore.data.first()[EnrollmentRepository.KEY_PARTICIPANT_ID]
                .also { resolvedParticipantId = it }

    // Touch tracking for swipe detection
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartMs = 0L
    private var touchHadScroll = false  // true if a scroll event fired during this touch

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                val (ex, ey) = eventCoords(event)
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
                val (ex, ey) = eventCoords(event)
                val dx = ex - touchStartX
                val dy = ey - touchStartY
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > 50f) {
                    val durationMs = System.currentTimeMillis() - touchStartMs
                    val data = JSONObject().apply {
                        put("from_x", touchStartX)
                        put("from_y", touchStartY)
                        put("to_x", ex)
                        put("to_y", ey)
                        put("distance", dist)
                        put("direction", swipeDirection(dx, dy))
                        put("duration_ms", durationMs)
                    }
                    record(packageName, "swipe", data)
                }
                // Taps (dist <= 50) are already recorded via TYPE_VIEW_CLICKED — skip here.
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val (ex, ey) = eventCoords(event)
                val data = JSONObject().apply { put("x", ex); put("y", ey) }
                record(packageName, "touch", data)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val (ex, ey) = eventCoords(event)
                val data = JSONObject().apply { put("x", ex); put("y", ey) }
                record(packageName, "long_press", data)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                touchHadScroll = true  // suppress swipe duplicate for this touch sequence
                val (ex, ey) = eventCoords(event)
                val scrollDx = event.scrollX
                val scrollDy = event.scrollY
                val data = JSONObject().apply {
                    put("x", ex); put("y", ey)
                    put("scroll_x", scrollDx); put("scroll_y", scrollDy)
                    if (scrollDx != 0 || scrollDy != 0) {
                        put("direction", swipeDirection(scrollDx.toFloat(), scrollDy.toFloat()))
                    }
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
            repo.insertScreenInteraction(
                ScreenInteractionEntity(
                    participantId = pid,
                    interactionType = interactionType,
                    appName = appNameResolved,
                    appCategory = appCategoryResolved,
                    interactionData = interactionData.toString(),
                    recordedAt = recordedAt
                )
            )
        }
    }

    private fun resolveAppName(packageName: String): String? = runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrNull()

    private fun resolveAppCategory(packageName: String): String? = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> "game"
                ApplicationInfo.CATEGORY_AUDIO -> "audio"
                ApplicationInfo.CATEGORY_VIDEO -> "video"
                ApplicationInfo.CATEGORY_IMAGE -> "image"
                ApplicationInfo.CATEGORY_SOCIAL -> "social"
                ApplicationInfo.CATEGORY_NEWS -> "news"
                ApplicationInfo.CATEGORY_MAPS -> "maps"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
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

    private fun eventCoords(event: AccessibilityEvent): Pair<Float, Float> {
        val node = event.source ?: return Pair(0f, 0f)
        return try {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            node.recycle()
            Pair(rect.exactCenterX(), rect.exactCenterY())
        } catch (e: Exception) {
            Pair(0f, 0f)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile var activeParticipantId: String? = null
    }
}
