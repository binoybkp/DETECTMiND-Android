package com.research.detectmind.service.collectors

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Accessibility service that records screen interactions.
 *
 * Two-layer gesture detection:
 *
 * Layer 1 — onMotionEvent (raw input, fires for ALL apps including React Native / Flutter):
 *   Tracks finger down/move/up to classify tap, long_press, swipe with direction and
 *   displacement. This is the universal fallback that works for every app.
 *
 * Layer 2 — onAccessibilityEvent (view-tree events, fires only for native Android View apps):
 *   TYPE_VIEW_CLICKED / LONG_CLICKED enrich the gesture with element class, id, coords.
 *   TYPE_VIEW_SCROLLED gives precise scroll deltas and element info.
 *   When a view event fires for the same gesture already tracked in Layer 1, Layer 1 is
 *   suppressed to avoid double-recording.
 *
 * IMPORTANT: onAccessibilityEvent and onMotionEvent both run on the MAIN thread.
 * All work must be O(1). Node IPC (event.source) is always deferred to IO coroutine.
 */
@AndroidEntryPoint
class ScreenInteractionService : AccessibilityService() {

    @Inject lateinit var repo: SensorDataRepository
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolvedParticipantId: String? = null

    private val eventBuffer = mutableListOf<ScreenInteractionEntity>()
    private val bufferMutex = Mutex()
    private var flushJob: Job? = null

    // ── Layer 1: raw motion tracking (main thread only) ──────────────────────
    private var motionDownX = 0f
    private var motionDownY = 0f
    private var motionDownMs = 0L
    private var motionMaxDisplace = 0f   // max displacement from down point during gesture
    private var motionLastX = 0f
    private var motionLastY = 0f
    // Set to true when a view-level event (CLICKED / LONG_CLICKED / SCROLLED) fires so
    // Layer 1 skips recording for that gesture (view event is richer).
    private var motionSuppressedByViewEvent = false

    // ── Layer 2: view-tree scroll state ──────────────────────────────────────
    private val lastScrollPos = mutableMapOf<String, Pair<Int, Int>>()

    // ── Foreground package (updated on WINDOW_STATE_CHANGED, main thread) ────
    private var currentPackage: String? = null
    private var currentWindowTitle: String? = null

    // ── Caches ────────────────────────────────────────────────────────────────
    private val appNameCache = mutableMapOf<String, String?>()

    private val ignoredPackages = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.sec.android.app.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "com.oneplus.launcher"
    )

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_END or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 0
            // Android 13+: receive raw MotionEvents without enabling touch exploration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setMotionEventSources(
                    android.view.InputDevice.SOURCE_TOUCHSCREEN
                )
            }
        }
        serviceScope.launch {
            resolvedParticipantId = activeParticipantId
                ?: dataStore.data.first()[EnrollmentRepository.KEY_PARTICIPANT_ID]
        }
        flushJob = serviceScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushBuffer()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 1: raw MotionEvent — fires for every app regardless of framework
    // ─────────────────────────────────────────────────────────────────────────

    override fun onMotionEvent(event: MotionEvent) {
        val pkg = currentPackage ?: return
        if (pkg in ignoredPackages) return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                motionDownX = event.rawX
                motionDownY = event.rawY
                motionLastX = event.rawX
                motionLastY = event.rawY
                motionDownMs = System.currentTimeMillis()
                motionMaxDisplace = 0f
                motionSuppressedByViewEvent = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - motionDownX
                val dy = event.rawY - motionDownY
                val disp = hypot(dx, dy)
                if (disp > motionMaxDisplace) motionMaxDisplace = disp
                motionLastX = event.rawX
                motionLastY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                if (motionSuppressedByViewEvent) return
                val durationMs = System.currentTimeMillis() - motionDownMs
                val dx = motionLastX - motionDownX
                val dy = motionLastY - motionDownY
                val displacement = motionMaxDisplace
                val sx = motionDownX; val sy = motionDownY
                val capturedPkg = pkg
                val recordedAt = Instant.now().toString()

                serviceScope.launch {
                    val pid = participantId() ?: return@launch
                    val (type, data) = classifyGesture(durationMs, displacement, dx, dy, sx, sy)
                    enqueue(pid, capturedPkg, type, data, recordedAt)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                motionSuppressedByViewEvent = false
            }
        }
    }

    private fun classifyGesture(
        durationMs: Long,
        displacement: Float,
        dx: Float,
        dy: Float,
        startX: Float,
        startY: Float
    ): Pair<String, JSONObject> {
        return when {
            displacement >= SWIPE_MIN_PX -> {
                val direction = swipeDirection(dx, dy)
                val data = JSONObject().apply {
                    put("direction", direction)
                    put("dx", dx.toInt())
                    put("dy", dy.toInt())
                    put("duration_ms", durationMs)
                    put("from_x", startX.toInt())
                    put("from_y", startY.toInt())
                }
                "swipe" to data
            }
            durationMs >= LONG_PRESS_MIN_MS -> {
                val data = JSONObject().apply {
                    put("duration_ms", durationMs)
                    put("x", startX.toInt())
                    put("y", startY.toInt())
                }
                "long_press" to data
            }
            else -> {
                val data = JSONObject().apply {
                    put("duration_ms", durationMs)
                    put("x", startX.toInt())
                    put("y", startY.toInt())
                }
                "touch" to data
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 2: accessibility view events — enrich with element detail
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: currentPackage ?: return
        if (pkg in ignoredPackages) return
        Log.i(TAG, "EVENT pkg=$pkg type=${AccessibilityEvent.eventTypeToString(event.eventType)}")

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val newPkg = event.packageName?.toString() ?: return
                if (newPkg in ignoredPackages) return
                val title = event.text?.firstOrNull()?.toString()
                    ?: event.contentDescription?.toString()
                if (newPkg == currentPackage && title == currentWindowTitle) return
                currentPackage = newPkg
                currentWindowTitle = title
                lastScrollPos.remove(newPkg)
                val windowClass = event.className?.toString()
                val capturedTitle = title
                val recordedAt = Instant.now().toString()
                serviceScope.launch {
                    val pid = participantId() ?: return@launch
                    if (!appNameCache.containsKey(newPkg)) resolveAppName(newPkg)
                    val data = JSONObject().apply {
                        capturedTitle?.let { put("window_title", it) }
                        windowClass?.let { put("window_class", it.substringAfterLast('.')) }
                    }
                    enqueue(pid, newPkg, "window_transition", data, recordedAt)
                }
            }

            // Pre-API33: TYPE_TOUCH_INTERACTION_START/END are the fallback for raw gestures.
            // On API33+ these are unused because onMotionEvent handles everything.
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    motionDownX = 0f; motionDownY = 0f
                    motionLastX = 0f; motionLastY = 0f
                    motionDownMs = System.currentTimeMillis()
                    motionMaxDisplace = 0f
                    motionSuppressedByViewEvent = false
                }
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !motionSuppressedByViewEvent) {
                    val durationMs = System.currentTimeMillis() - motionDownMs
                    val recordedAt = Instant.now().toString()
                    val capturedPkg = pkg
                    serviceScope.launch {
                        val pid = participantId() ?: return@launch
                        val (type, data) = classifyGesture(durationMs, motionMaxDisplace,
                            motionLastX - motionDownX, motionLastY - motionDownY,
                            motionDownX, motionDownY)
                        enqueue(pid, capturedPkg, type, data, recordedAt)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Native view tap — suppress Layer 1 record and record the richer version here
                motionSuppressedByViewEvent = true
                val node = try { event.source } catch (e: Exception) { null }
                val evtClass = event.className?.toString()
                val recordedAt = Instant.now().toString()
                val capturedPkg = pkg
                serviceScope.launch {
                    val pid = participantId() ?: return@launch
                    val data = JSONObject()
                    readNodeInfo(node, data)
                    evtClass?.substringAfterLast('.')
                        ?.let { if (!data.has("element_class")) data.put("element_class", it) }
                    enqueue(pid, capturedPkg, "touch", data, recordedAt)
                }
            }

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                motionSuppressedByViewEvent = true
                val node = try { event.source } catch (e: Exception) { null }
                val evtClass = event.className?.toString()
                val recordedAt = Instant.now().toString()
                val capturedPkg = pkg
                serviceScope.launch {
                    val pid = participantId() ?: return@launch
                    val data = JSONObject()
                    readNodeInfo(node, data)
                    evtClass?.substringAfterLast('.')
                        ?.let { if (!data.has("element_class")) data.put("element_class", it) }
                    enqueue(pid, capturedPkg, "long_press", data, recordedAt)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Scroll events from the view tree — suppress Layer 1 for this gesture
                motionSuppressedByViewEvent = true
                var scrollDx: Int
                var scrollDy: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    scrollDx = event.scrollDeltaX
                    scrollDy = event.scrollDeltaY
                } else {
                    scrollDx = 0
                    scrollDy = 0
                }
                if (scrollDx == -1 && scrollDy == -1) return
                if (scrollDx == 0 && scrollDy == 0) {
                    val absX = event.scrollX
                    val absY = event.scrollY
                    val last = lastScrollPos[pkg]
                    if (last != null) {
                        scrollDx = absX - last.first
                        scrollDy = absY - last.second
                    }
                    lastScrollPos[pkg] = absX to absY
                    if (scrollDx == 0 && scrollDy == 0) return
                }
                val node = try { event.source } catch (e: Exception) { null }
                val evtClass = event.className?.toString()
                val dx = scrollDx; val dy = scrollDy
                val recordedAt = Instant.now().toString()
                val capturedPkg = pkg
                serviceScope.launch {
                    val pid = participantId() ?: return@launch
                    val data = JSONObject().apply {
                        put("scroll_dx", dx)
                        put("scroll_dy", dy)
                        put("direction", swipeDirection(dx.toFloat(), dy.toFloat()))
                    }
                    readNodeInfo(node, data)
                    evtClass?.substringAfterLast('.')
                        ?.let { if (!data.has("element_class")) data.put("element_class", it) }
                    enqueue(pid, capturedPkg, "scroll", data, recordedAt)
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val node = try { event.source } catch (e: Exception) { null }
                val evtClass = event.className?.toString()
                val recordedAt = Instant.now().toString()
                val capturedPkg = pkg
                serviceScope.launch {
                    val pid = participantId() ?: return@launch
                    val data = JSONObject()
                    readNodeInfo(node, data)
                    evtClass?.substringAfterLast('.')
                        ?.let { if (!data.has("element_class")) data.put("element_class", it) }
                    enqueue(pid, capturedPkg, "text_input", data, recordedAt)
                }
            }
        }
    }

    // Called on IO thread — safe to do node IPC here
    private fun readNodeInfo(node: AccessibilityNodeInfo?, data: JSONObject) {
        node ?: return
        try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                data.put("x", rect.exactCenterX().toInt())
                data.put("y", rect.exactCenterY().toInt())
            }
            node.className?.toString()?.substringAfterLast('.')
                ?.let { data.put("element_class", it) }
            node.viewIdResourceName?.substringAfterLast('/')
                ?.let { data.put("element_id", it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                ?.let { data.put("content_desc", it) }
        } catch (e: Exception) {
            // Node recycled or unavailable
        } finally {
            try { node.recycle() } catch (_: Exception) {}
        }
    }

    private suspend fun enqueue(
        pid: String,
        packageName: String,
        interactionType: String,
        data: JSONObject,
        recordedAt: String
    ) {
        val appName = resolveAppName(packageName)
        val appCategory = resolveAppCategory(packageName)
        Log.i(TAG, "ENQUEUE type=$interactionType pkg=$packageName appName=$appName data=$data")
        bufferMutex.withLock {
            eventBuffer.add(
                ScreenInteractionEntity(
                    participantId = pid,
                    interactionType = interactionType,
                    appName = appName,
                    appCategory = appCategory,
                    interactionData = data.toString().takeIf { it != "{}" },
                    recordedAt = recordedAt
                )
            )
        }
    }

    private suspend fun participantId(): String? =
        resolvedParticipantId ?: activeParticipantId
            ?: dataStore.data.first()[EnrollmentRepository.KEY_PARTICIPANT_ID]
                .also { resolvedParticipantId = it }

    private fun resolveAppName(packageName: String): String? {
        appNameCache[packageName]?.let { return it.ifEmpty { null } }
        val name = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrNull()
        appNameCache[packageName] = name ?: ""
        return name
    }

    private fun resolveAppCategory(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val info = packageManager.getApplicationInfo(packageName, 0)
            when (info.category) {
                ApplicationInfo.CATEGORY_GAME         -> "game"
                ApplicationInfo.CATEGORY_AUDIO        -> "audio"
                ApplicationInfo.CATEGORY_VIDEO        -> "video"
                ApplicationInfo.CATEGORY_IMAGE        -> "image"
                ApplicationInfo.CATEGORY_SOCIAL       -> "social"
                ApplicationInfo.CATEGORY_NEWS         -> "news"
                ApplicationInfo.CATEGORY_MAPS         -> "maps"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
                ApplicationInfo.CATEGORY_UNDEFINED    -> null
                else -> "other"
            }
        } else null
    }.getOrNull()

    private fun swipeDirection(dx: Float, dy: Float): String =
        if (abs(dx) > abs(dy)) (if (dx > 0) "right" else "left")
        else (if (dy > 0) "down" else "up")

    private suspend fun flushBuffer() {
        val toInsert = bufferMutex.withLock {
            if (eventBuffer.isEmpty()) return
            val copy = eventBuffer.toList()
            eventBuffer.clear()
            copy
        }
        Log.i(TAG, "FLUSH inserting ${toInsert.size} records")
        repo.insertScreenInteractions(toInsert)
    }

    override fun onInterrupt() {
        flushJob?.cancel()
        serviceScope.launch { flushBuffer() }
    }

    override fun onDestroy() {
        super.onDestroy()
        flushJob?.cancel()
        serviceScope.launch { flushBuffer() }
    }

    companion object {
        private const val TAG = "ScreenInterSvc"
        private const val FLUSH_INTERVAL_MS = 30_000L
        private const val SWIPE_MIN_PX = 80f       // min displacement to classify as swipe
        private const val LONG_PRESS_MIN_MS = 400L  // min duration for long_press
        @Volatile var activeParticipantId: String? = null
    }
}
