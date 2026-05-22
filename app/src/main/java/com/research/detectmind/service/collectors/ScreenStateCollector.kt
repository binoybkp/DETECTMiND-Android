package com.research.detectmind.service.collectors

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.research.detectmind.data.local.entity.ScreenStateEntity
import com.research.detectmind.data.repository.SensorDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

class ScreenStateCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SensorDataRepository
) : SensorCollector {

    override val sensorType = "screen_state"
    private var scope: CoroutineScope? = null
    private var participantId: String = ""

    private val keyguardManager: KeyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> recordState("on")
                Intent.ACTION_SCREEN_OFF -> {
                    // Record "off" immediately, then check for keyguard lock after a short delay
                    recordState("off")
                    scope?.launch {
                        delay(500)
                        if (keyguardManager.isKeyguardLocked) {
                            recordState("locked")
                        }
                    }
                }
                Intent.ACTION_USER_PRESENT -> recordState("unlocked")
            }
        }
    }

    private fun recordState(state: String) {
        scope?.launch {
            repo.insertScreenState(
                ScreenStateEntity(
                    participantId = participantId,
                    state = state,
                    recordedAt = Instant.now().toString()
                )
            )
        }
    }

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        this.scope = scope
        this.participantId = participantId
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(receiver, filter)
    }

    override fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        scope = null
    }

}
