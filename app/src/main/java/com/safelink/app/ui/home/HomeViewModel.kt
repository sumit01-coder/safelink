package com.safelink.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safelink.app.data.discovery.UdpDiscoveryService
import com.safelink.app.data.model.Relay
import com.safelink.app.data.model.SafeLinkDevice
import com.safelink.app.data.network.RelayApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val devices: List<SafeLinkDevice> = emptyList(),
    val isDiscovering: Boolean = false,
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val discoveryService = UdpDiscoveryService()
    private val relayApiService  = RelayApiService()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // No mock data — start empty; user must scan to discover real devices.

    fun discoverDevices(pairingKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true, devices = emptyList()) }
            try {
                discoveryService.discoverDevices(pairingKey = pairingKey).collect { device ->
                    // Assign sequential IDs to relays since ESP32 JSON omits them
                    val deviceWithIds = device.copy(
                        relays = device.relays.mapIndexed { idx, relay ->
                            relay.copy(id = idx + 1)
                        }
                    )
                    _uiState.update { state ->
                        state.copy(devices = state.devices + deviceWithIds)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Discovery failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isDiscovering = false) }
            }
        }
    }

    /**
     * Toggles a relay:
     * 1. Sends HTTP POST to the real ESP32 device.
     * 2. If the call succeeds, updates the UI state optimistically.
     * 3. If the call fails (device offline, timeout), shows an error and rolls back.
     */
    fun toggleRelay(device: SafeLinkDevice, relay: Relay) {
        val newState = !relay.state

        // Optimistic UI update — apply immediately for snappy feel
        applyRelayState(device.deviceId, relay.id, newState)

        viewModelScope.launch {
            val success = relayApiService.toggleRelay(
                ip   = device.ip,
                port = device.port,
                name = relay.name,
                state = newState
            )

            if (!success) {
                // Roll back optimistic update on failure
                applyRelayState(device.deviceId, relay.id, relay.state)
                _uiState.update { it.copy(error = "Failed to reach ${device.deviceName}. Is it online?") }
            }
        }
    }

    private fun applyRelayState(deviceId: String, relayId: Int, newState: Boolean) {
        _uiState.update { state ->
            val updatedDevices = state.devices.map { d ->
                if (d.deviceId == deviceId) {
                    d.copy(
                        relays = d.relays.map { r ->
                            if (r.id == relayId) r.copy(state = newState) else r
                        }
                    )
                } else d
            }
            state.copy(devices = updatedDevices)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
