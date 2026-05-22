package com.research.detectmind.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.research.detectmind.ui.navigation.AppNavHost
import com.research.detectmind.ui.theme.ParticipantMonitorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParticipantMonitorTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
