package com.safelink.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safelink.app.data.discovery.BleDiscoveryService
import com.safelink.app.data.discovery.DirectConnectionService
import com.safelink.app.data.model.Relay
import com.safelink.app.data.model.SafeLinkDevice
import com.safelink.app.data.network.RelayApiService
import com.safelink.app.data.network.BleRelayClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import android.content.Intent
import android.net.Uri
import com.safelink.app.SafeLinkApplication
import kotlinx.coroutines.flow.first

data class HomeUiState(
    val devices: List<SafeLinkDevice> = emptyList(),
    val isDiscovering: Boolean = false,
    val scanStatusMessage: String = "Scanning for nearby SafeLink devices...",
    val error: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val bleDiscoveryService   = BleDiscoveryService()
    private val directConnectService  = DirectConnectionService(application.applicationContext)
    private val relayApiService       = RelayApiService()
    private val bleRelayClient        = BleRelayClient(application.applicationContext)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    private val settingsRepo = (application as SafeLinkApplication).settingsRepository
    private var currentCustomNames: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { state ->
                currentCustomNames = state.customRelayNames
                // Refresh UI with new names
                _uiState.update { state ->
                    state.copy(devices = state.devices.map { applyCustomNames(it) })
                }
            }
        }
    }

    private fun applyCustomNames(device: SafeLinkDevice): SafeLinkDevice {
        return device.copy(relays = device.relays.map { r ->
            val customName = currentCustomNames["${device.deviceId}_${r.id}"]
            if (customName != null) r.copy(name = customName) else r
        })
    }

    companion object {
        private const val BLE_SCAN_TIMEOUT_MS = 8_000L  // 8 seconds per scan attempt
        private const val RETRY_DELAY_MS      = 3_000L  // 3 seconds between retries
    }

    /**
     * Starts auto-scanning on app launch.
     * Runs two discovery strategies in PARALLEL every cycle:
     *   1. Direct HTTP to 192.168.4.1 — uses Wi-Fi-bound socket, adds device directly
     *   2. BLE scan with UUID filter — uses BLE manufacturer data to get IP, then fetches
     * Stops automatically once a device is found. Retries every 3s.
     */
    fun startAutoScan(pairingKey: String) {
        if (scanJob?.isActive == true) return  // Already scanning
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true, scanStatusMessage = "Looking for SafeLink hub...") }
            var attempt = 0
            while (_uiState.value.devices.isEmpty()) {
                attempt++
                _uiState.update { it.copy(scanStatusMessage = "Scanning... (attempt $attempt)") }

                // Strategy 1: Direct HTTP to 192.168.4.1 via Wi-Fi-bound socket
                val directJob = async {
                    val hasWifi = directConnectService.hasWifiNetwork()
                    val device = directConnectService.tryDirectConnect(pairingKey)
                    val err = directConnectService.lastError ?: ""
                    _uiState.update { it.copy(scanStatusMessage = "Scanning... (wifi=$hasWifi) $err") }
                    if (device != null) addDirectDevice(device)
                }

                // Strategy 2: BLE scan — gets IP from manufacturer data, then fetches status
                val bleJob = async {
                    withTimeoutOrNull(BLE_SCAN_TIMEOUT_MS) {
                        bleDiscoveryService.discoverDevices(pairingKey = pairingKey).collect { bleDevice ->
                            addBleDevice(bleDevice)
                        }
                    }
                }

                // Wait for both (direct is fast ~1s, BLE waits up to 8s)
                directJob.await()
                bleJob.await()

                // If no devices found yet, show retrying message
                if (_uiState.value.devices.isEmpty()) {
                    _uiState.update { it.copy(scanStatusMessage = "Not found. Retrying...") }
                } else {
                    _uiState.update { it.copy(scanStatusMessage = "Hub connected!", isDiscovering = false) }
                }
                
                // Keep polling every 3 seconds to catch hot-plugged relays automatically
                delay(3000)
            }
            _uiState.update {
                it.copy(
                    isDiscovering = false,
                    scanStatusMessage = if (it.devices.isNotEmpty()) "Hub connected!" else "No devices found"
                )
            }
        }
    }

    /**
     * Adds or updates a device discovered via direct IP.
     */
    private fun addDirectDevice(device: SafeLinkDevice) {
        val namedDevice = applyCustomNames(device)
        val normalized = namedDevice.copy(
            relays = namedDevice.relays.mapIndexed { idx, r -> r.copy(id = idx + 1) }
        )
        _uiState.update { state ->
            val existingIndex = state.devices.indexOfFirst { it.deviceId == normalized.deviceId }
            if (existingIndex >= 0) {
                // Update existing device (catches hot-plugged relays)
                val newList = state.devices.toMutableList()
                newList[existingIndex] = normalized
                state.copy(devices = newList)
            } else {
                // Add new device
                state.copy(devices = state.devices + normalized)
            }
        }
    }

    /**
     * For BLE-discovered devices: fetches full status over HTTP using the
     * Wi-Fi-bound socket from DirectConnectionService.
     */
    private suspend fun addBleDevice(bleDevice: SafeLinkDevice) {
        if (_uiState.value.devices.any { it.ip == bleDevice.ip }) return
        // Re-use directConnectService which has the Wi-Fi-bound socket
        val fullDevice = directConnectService.fetchStatus(bleDevice.ip)
        val target = fullDevice ?: bleDevice.copy(
            relays = List(bleDevice.relayCount) { 
                com.safelink.app.data.model.Relay(
                    id = it + 1, 
                    name = "Relay ${it + 1}", 
                    state = false,
                    connected = false,
                    pinName = "?"
                ) 
            }
        )
        val namedDevice = applyCustomNames(target)
        val normalized = namedDevice.copy(
            ip = bleDevice.ip,
            wifiSignal = bleDevice.wifiSignal,
            relays = namedDevice.relays.mapIndexed { idx, r -> r.copy(id = idx + 1) }
        )
        _uiState.update { state ->
            if (state.devices.none { it.deviceId == normalized.deviceId }) {
                state.copy(devices = state.devices + normalized)
            } else state
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
            var success = relayApiService.toggleRelay(
                ip   = device.ip,
                port = device.port,
                name = null,
                relayIndex = relay.id - 1,
                state = newState
            )

            if (!success) {
                // HTTP Wi-Fi failed. Fallback to BLE!
                // device.deviceId is the MAC address, relay.id is 1-based but firmware expects 0-based relayIndex
                success = bleRelayClient.toggleRelay(
                    macAddress = device.deviceId,
                    relayIndex = relay.id - 1,
                    state = newState
                )
            }

            if (!success) {
                // Roll back optimistic update on failure
                applyRelayState(device.deviceId, relay.id, relay.state)
                _uiState.update { it.copy(error = "Failed to reach ${device.deviceName} via Wi-Fi and Bluetooth. Is it powered on?") }
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
    fun renameRelay(device: SafeLinkDevice, relayIndex: Int, newName: String) {
        viewModelScope.launch {
            settingsRepo.updateCustomRelayName(device.deviceId, relayIndex, newName)
            
            // Push Dynamic Shortcut to Google Assistant
            val shortcutId = "${device.deviceId}_$relayIndex"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("safelink://toggle?light=$relayIndex&state=on"))
            intent.setPackage(getApplication<Application>().packageName)
            
            val shortcut = ShortcutInfoCompat.Builder(getApplication(), shortcutId)
                .setShortLabel("Turn on $newName")
                .setLongLabel("Turn on $newName on SafeLink")
                .setIntent(intent)
                .addCapabilityBinding(
                    "actions.intent.OPEN_APP_FEATURE",
                    "feature",
                    listOf(newName)
                )
                .build()
                
            ShortcutManagerCompat.pushDynamicShortcut(getApplication(), shortcut)
        }
    }
}
