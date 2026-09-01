package com.safelink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safelink.app.data.repository.SettingsState
import com.safelink.app.data.update.DownloadState
import com.safelink.app.ui.MainScreen
import com.safelink.app.ui.theme.SafeLinkTheme
import com.safelink.app.ui.update.UpdateDialog
import com.safelink.app.ui.update.UpdateViewModel
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.safelink.app.data.discovery.DirectConnectionService
import com.safelink.app.data.network.RelayApiService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsRepo  = (application as SafeLinkApplication).settingsRepository
            val settingsState by settingsRepo.settingsFlow.collectAsState(initial = SettingsState())

            // Update ViewModel — scoped to the activity
            val updateViewModel: UpdateViewModel = viewModel(
                factory = UpdateViewModel.provideFactory(applicationContext)
            )
            val updateState by updateViewModel.uiState.collectAsState()

            // Silently check for updates once on launch
            LaunchedEffect(Unit) {
                updateViewModel.checkForUpdateSilently()
            }

            SafeLinkTheme(darkTheme = settingsState.darkModeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(updateViewModel = updateViewModel)
                }

                // Show update dialog if a new version is found
                if (updateState.showUpdateDialog && updateState.releaseInfo != null) {
                    if (updateState.releaseInfo!!.isNewer) {
                        UpdateDialog(
                            releaseInfo   = updateState.releaseInfo!!,
                            downloadState = updateState.downloadState,
                            onDownload    = { updateViewModel.downloadAndInstall() },
                            onDismiss     = { updateViewModel.dismissDialog() }
                        )
                    } else {
                        com.safelink.app.ui.update.UpToDateDialog(
                            currentVersion = BuildConfig.VERSION_NAME,
                            onDismiss      = { updateViewModel.dismissDialog() }
                        )
                    }
                }
            }
        }
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == "safelink") {
            val uri = intent.data
            if (uri?.host == "toggle") {
                val lightStr = uri.getQueryParameter("light")
                val stateStr = uri.getQueryParameter("state")
                
                if (lightStr != null && stateStr != null) {
                    val state = stateStr == "on"
                    val relayIndex = lightStr.toIntOrNull()
                    val relayName = "Relay $lightStr" // Just for Toast
                    
                    Toast.makeText(this, "Assistant: Turning $stateStr $relayName...", Toast.LENGTH_SHORT).show()
                    
                    lifecycleScope.launch {
                        try {
                            val directConnectService = DirectConnectionService(applicationContext)
                            val settingsRepo = (application as SafeLinkApplication).settingsRepository
                            val pairingKey = settingsRepo.settingsFlow.first().pairingKey
                            
                            val device = directConnectService.tryDirectConnect(pairingKey)
                            if (device != null && relayIndex != null) {
                                val api = RelayApiService()
                                api.toggleRelay(ip = device.ip, relayIndex = relayIndex, state = state)
                                Toast.makeText(this@MainActivity, "Success: $relayName is $stateStr", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Failed: Not connected to SafeLink Wi-Fi or invalid relay", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
