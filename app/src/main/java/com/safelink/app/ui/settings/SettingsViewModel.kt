package com.safelink.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safelink.app.data.repository.SettingsRepository
import com.safelink.app.data.repository.SettingsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<SettingsState> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState()
    )

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotifications(enabled)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDarkMode(enabled)
        }
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHapticFeedback(enabled)
        }
    }

    fun updatePairingKey(key: String) {
        viewModelScope.launch {
            repository.updatePairingKey(key)
        }
    }

    companion object {
        fun provideFactory(repository: SettingsRepository): androidx.lifecycle.ViewModelProvider.Factory = 
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(repository) as T
                }
            }
    }
}
