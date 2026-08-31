package com.safelink.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safelink.app.data.discovery.BleDiscoveryService
import com.safelink.app.data.model.Relay
import com.safelink.app.data.model.SafeLinkDevice
import com.safelink.app.data.network.RelayApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class HomeUiState(
    val devices: List<SafeLinkDevice> = emptyList(),
    val isDiscovering: Boolean = false,
    val scanStatusMessage: String = "Scanning for nearby SafeLink devices...",
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val discoveryService = BleDiscoveryService()
    private val relayApiService  = RelayApiService()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    companion object {
        private const val BLE_SCAN_TIMEOUT_MS = 8_000L  // 8 seconds per scan attempt
        private const val RETRY_DELAY_MS      = 3_000L  // 3 seconds between retries
    }

    /**
     * Starts auto-scanning on app launch. Runs BLE scans in a loop,
     * each with an 8-second timeout, until at least one device is found.
     * Stops automatically once a device is discovered.
     */
    fun startAutoScan(pairingKey: String) {
        if (scanJob?.isActive == true) return  // Already scanning
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true, scanStatusMessage = "Looking for SafeLink hub nearby...") }
            var attempt = 0
            while (_uiState.value.devices.isEmpty()) {
                attempt++
                _uiState.update { it.copy(scanStatusMessage = "Scanning via Bluetooth... (attempt $attempt)") }

                val found = withTimeoutOrNull(BLE_SCAN_TIMEOUT_MS) {
                    var discoveredOne = false
                    discoveryService.discoverDevices(pairingKey = pairingKey).collect { bleDevice ->
                        val statusJson = relayApiService.fetchStatus(bleDevice.ip, bleDevice.port)
                        if (statusJson != null) {
                            try {
                                val jsonParser = Json { ignoreUnknownKeys = true }
                                val fullDevice = jsonParser.decodeFromString<SafeLinkDevice>(statusJson)
                                val deviceWithIds = fullDevice.copy(
                                    ip = bleDevice.ip,
                                    wifiSignal = bleDevice.wifiSignal,
                                    relays = fullDevice.relays.mapIndexed { idx, relay ->
                                        relay.copy(id = idx + 1)
                                    }
                                )
                                _uiState.update { state ->
                                    if (state.devices.none { it.deviceId == deviceWithIds.deviceId }) {
                                        state.copy(devices = state.devices + deviceWithIds)
                                    } else state
                                }
                                discoveredOne = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    discoveredOne
                }

                if (_uiState.value.devices.isNotEmpty()) break  // Found! Stop scanning.

                // Not found yet, wait and retry
                _uiState.update { it.copy(scanStatusMessage = "Hub not found. Retrying in 3s...") }
                delay(RETRY_DELAY_MS)
            }
            _uiState.update {
                it.copy(
                    isDiscovering = false,
                    scanStatusMessage = if (it.devices.isNotEmpty()) "Hub connected!" else "No devices found"
                )
            }
        }
    }

    /** Manual scan triggered by tapping Refresh — resets device list and re-scans. */
    fun discoverDevices(pairingKey: String) {
        scanJob?.cancel()
        _uiState.update { it.copy(devices = emptyList()) }
        startAutoScan(pairingKey)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
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
