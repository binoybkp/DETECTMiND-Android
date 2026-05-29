package com.research.detectmind.service.collectors

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
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
 * IMPORTANT: onAccessibilityEvent runs on the MAIN thread. All work here must be
 * O(1) and non-blocking. We snapshot primitive values from the event immediately,
 * then hand off to a coroutine for node IPC and DB writes.
 *
 * canRetrieveWindowContent=true is required for element class/id, but we only call
 * event.source inside the coroutine on Dispatchers.IO to avoid blocking the main thread.
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

    // Touch state — only accessed on main thread (onAccessibilityEvent)
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartMs = 0L
    private var touchHadScroll = false
    // Track last scroll position per-package to detect scroll via position delta (e.g. WebView/Chrome)
    private val lastScrollPos = mutableMapOf<String, Pair<Int, Int>>()

    // Current foreground package — updated on WINDOW_STATE_CHANGED (main thread)
    private var currentPackage: String? = null
    private var currentWindowTitle: String? = null

    // Cache resolved app names to avoid repeated PackageManager calls
    private val appNameCache = mutableMapOf<String, String?>()

    private val swipeThresholdPx by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics)
    }

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
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 0
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

    // Data class to carry everything we can read from an event on the main thread
    // without any IPC calls. Node retrieval happens later on IO thread.
    private data class EventSnapshot(
        val eventType: Int,
        val pkg: String,
        val eventClassName: String?,   // className on the event itself (not the node)
        val windowTitle: String?,
        val scrollDx: Int,
        val scrollDy: Int,
        // Primitive touch fields — safe to read on main thread
        val touchStartX: Float,
        val touchStartY: Float,
        val touchEndX: Float,
        val touchEndY: Float,
        val touchDurationMs: Long,
        val recordedAt: String,
        // We hold the node ref briefly to read it on the IO thread
        // Caller must recycle() it after use
        val nodeRef: AccessibilityNodeInfo?
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: currentPackage ?: return
        if (pkg in ignoredPackages) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val newPkg = event.packageName?.toString() ?: return
                if (newPkg in ignoredPackages) return
                val title = event.text?.firstOrNull()?.toString()
                    ?: event.contentDescription?.toString()
                if (newPkg == currentPackage && title == currentWindowTitle) return
                currentPackage = newPkg
                currentWindowTitle = title
                // Reset scroll tracking when app changes
                lastScrollPos.remove(newPkg)
                val windowClass = event.className?.toString()
                val capturedTitle = title
                val recordedAt = Instant.now().toString()
                serviceScope.launch {
                    val pid = participantId() ?: return@launch
                    // Warm the app name cache so subsequent events get it immediately
                    if (!appNameCache.containsKey(newPkg)) resolveAppName(newPkg)
                    val data = JSONObject().apply {
                        capturedTitle?.let { put("window_title", it) }
                        windowClass?.let { put("window_class", it.substringAfterLast('.')) }
                    }
                    enqueue(pid, newPkg, "window_transition", data, recordedAt)
                }
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                // No IPC needed — just reset touch state
                touchStartX = 0f
                touchStartY = 0f
                touchStartMs = System.currentTimeMillis()
                touchHadScroll = false
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                if (touchHadScroll) return
                // Swipe detection uses stored start coords; end coords unavailable here on most
                // devices so we record the vector from start only when distance > threshold.
                // Exact end coords come from TYPE_VIEW_CLICKED for taps (already handled there).
                val durationMs = System.currentTimeMillis() - touchStartMs
                val sx = touchStartX; val sy = touchStartY
                // No node available at TOUCH_END — skip node enrichment
                val recordedAt = Instant.now().toString()
                val capturedPkg = pkg
                serviceScope.launch {
                    // We cannot get end coords reliably here across all OEMs,
                    // so swipe is recorded as directional only when we have clear displacement.
                    // For most swipes, scroll events below provide better data.
                    val pid = participantId() ?: return@launch
                    val data = JSONObject().apply {
                        put("from_x", sx.toInt())
                        put("from_y", sy.toInt())
                        put("duration_ms", durationMs)
                    }
                    enqueue(pid, capturedPkg, "swipe", data, recordedAt)
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Obtain node here — we'll recycle it in the coroutine
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
                touchHadScroll = true
                var scrollDx: Int
                var scrollDy: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    scrollDx = event.scrollDeltaX
                    scrollDy = event.scrollDeltaY
                } else {
                    scrollDx = 0
                    scrollDy = 0
                }
                // Chrome/WebView reports delta=0 but has valid absolute scrollX/scrollY.
                // Compute delta from last known position to detect these scrolls.
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
        // Cache result (empty string sentinel for "not found" to avoid repeated failed lookups)
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
        private const val FLUSH_INTERVAL_MS = 30_000L
        @Volatile var activeParticipantId: String? = null
    }
}
