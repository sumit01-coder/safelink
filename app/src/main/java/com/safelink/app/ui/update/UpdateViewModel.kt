package com.safelink.app.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safelink.app.data.update.AppUpdateService
import com.safelink.app.data.update.DownloadState
import com.safelink.app.data.update.ReleaseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdateUiState(
    val isChecking: Boolean = false,
    val releaseInfo: ReleaseInfo? = null,
    val showUpdateDialog: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val error: String? = null
)

class UpdateViewModel(private val context: Context) : ViewModel() {

    private val updateService = AppUpdateService(context)

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /**
     * Silently checks for updates on app launch.
     * Only shows a dialog if a newer version is found.
     */
    fun checkForUpdateSilently() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }
            val info = updateService.checkForUpdate()
            _uiState.update {
                it.copy(
                    isChecking       = false,
                    releaseInfo      = info,
                    showUpdateDialog = info?.isNewer == true
                )
            }
        }
    }

    /**
     * Manual check triggered from Settings screen.
     * Shows result or "up to date" message.
     */
    fun checkForUpdateManually() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, error = null) }
            val info = updateService.checkForUpdate()
            _uiState.update {
                it.copy(
                    isChecking       = false,
                    releaseInfo      = info,
                    showUpdateDialog = info != null,  // Show dialog regardless (even if up to date)
                    error            = if (info == null) "Could not reach GitHub. Check your internet connection." else null
                )
            }
        }
    }

    /**
     * Downloads the APK from the release URL and installs it.
     */
    fun downloadAndInstall() {
        val url = _uiState.value.releaseInfo?.apkDownloadUrl ?: return
        viewModelScope.launch {
            updateService.downloadUpdate(url).collect { state ->
                _uiState.update { it.copy(downloadState = state) }
                if (state is DownloadState.Done) {
                    updateService.installApk(state.file)
                }
                if (state is DownloadState.Error) {
                    _uiState.update { it.copy(error = state.message) }
                }
            }
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                showUpdateDialog = false,
                downloadState    = DownloadState.Idle,
                error            = null
            )
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UpdateViewModel(context.applicationContext) as T
            }
    }
}
