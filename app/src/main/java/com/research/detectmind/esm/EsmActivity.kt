package com.research.detectmind.esm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.research.detectmind.ui.theme.ParticipantMonitorTheme
import com.research.detectmind.util.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EsmActivity : ComponentActivity() {

    private val viewModel: EsmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scheduleId = intent.getStringExtra(Constants.EXTRA_SCHEDULE_ID) ?: run { finish(); return }
        val triggeredAt = intent.getLongExtra(Constants.EXTRA_TRIGGERED_AT, System.currentTimeMillis())
        val responseId = intent.getLongExtra(Constants.EXTRA_RESPONSE_ID, -1L)

        viewModel.load(scheduleId, triggeredAt, responseId)

        setContent {
            ParticipantMonitorTheme {
                EsmSurveyScreen(
                    viewModel = viewModel,
                    onFinished = { finish() }
                )
            }
        }
    }
}
