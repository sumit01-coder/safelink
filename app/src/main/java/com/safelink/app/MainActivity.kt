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
    }
}
