package com.safelink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.safelink.app.ui.MainScreen
import com.safelink.app.ui.theme.SafeLinkTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.safelink.app.data.repository.SettingsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsRepo = (application as SafeLinkApplication).settingsRepository
            val settingsState by settingsRepo.settingsFlow.collectAsState(initial = SettingsState())

            SafeLinkTheme(darkTheme = settingsState.darkModeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
