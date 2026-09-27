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
import com.safelink.app.data.update.AppUpdateService
import com.safelink.app.data.update.FirmwareReleaseInfo
import com.safelink.app.data.update.DownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import android.content.Intent
import android.net.Uri
import com.safelink.app.SafeLinkApplication
import kotlinx.coroutines.flow.first

// ─────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────

enum class ConnectionPhase {
    IDLE,           // Nothing happening
    DISCOVERING,    // Active scan in progress
    CONNECTED,      // At least one live device
    RECONNECTING,   // Had a device (from cache), trying to re-confirm
    FAILED          // Scan ran but found nothing
}

data class HomeUiState(
    val devices: List<SafeLinkDevice> = emptyList(),
    val isDiscovering: Boolean = false,
    val connectionPhase: ConnectionPhase = ConnectionPhase.IDLE,
    val scanStatusMessage: String = "Scanning for nearby SafeLink devices...",
    val error: String? = null,
    // True after the first scan cycle finishes (even with 0 results)
    val firstScanDone: Boolean = false,
    // Firmware Update State
    val firmwareUpdate: FirmwareReleaseInfo? = null,
    val rollbackAvailable: Boolean = false,
    val isFlashingFirmware: Boolean = false,
    val flashProgress: Int = 0
)

// ─────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val bleDiscoveryService  = BleDiscoveryService()
    private val directConnectService = DirectConnectionService(application.applicationContext)
    private val relayApiService      = RelayApiService()
    private val bleRelayClient       = BleRelayClient(application.applicationContext)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val updateService = AppUpdateService(application.applicationContext)

    private var scanJob: Job? = null
    private var pollingJob: Job? = null

    private val settingsRepo = (application as SafeLinkApplication).settingsRepository
    private var currentCustomNames: Map<String, String> = emptyMap()

    companion object {
        private const val BLE_SCAN_TIMEOUT_MS   = 8_000L
        private const val RETRY_DELAY_MS         = 3_000L
        private const val POLL_INTERVAL_MS       = 5_000L   // Live status refresh rate
        private const val OFFLINE_GRACE_FAILS    = 2        // Consecutive failures before marking offline
    }

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { settingsState ->
                currentCustomNames = settingsState.customRelayNames

                // Apply new relay names to whatever is already in the list
                _uiState.update { uiState ->
                    uiState.copy(devices = uiState.devices.map { applyCustomNames(it) })
                }

                // Restore last-known device instantly on first collection
                // (before any network scan runs — gives instant UI feedback on reopen)
                val cached = settingsState.lastKnownDevice
                if (cached != null && _uiState.value.devices.isEmpty()) {
                    val namedCached = applyCustomNames(cached)
                    _uiState.update { uiState ->
                        uiState.copy(
                            devices = listOf(namedCached),
                            connectionPhase = ConnectionPhase.RECONNECTING,
                            scanStatusMessage = "Reconnecting to ${cached.deviceName}…"
                        )
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Relay name helpers
    // ─────────────────────────────────────────────────────────────

    private fun applyCustomNames(device: SafeLinkDevice): SafeLinkDevice {
        return device.copy(relays = device.relays.map { r ->
            // Key is "<deviceId>_<r.id>" — r.id is 1-based (matches SettingsRepository key)
            val customName = currentCustomNames["${device.deviceId}_${r.id}"]
            if (customName != null) r.copy(name = customName) else r
        })
    }

    // ─────────────────────────────────────────────────────────────
    // Auto scan — runs parallel strategies, stops once a device is found
    // ─────────────────────────────────────────────────────────────

    fun startAutoScan(pairingKey: String) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDiscovering = true,
                    connectionPhase = if (it.devices.isNotEmpty()) ConnectionPhase.RECONNECTING
                                      else ConnectionPhase.DISCOVERING,
                    scanStatusMessage = "Looking for SafeLink hub…"
                )
            }

            var attempt = 0
            // Keep scanning until we have a confirmed live device
            while (_uiState.value.devices.none { it.isOnline }) {
                attempt++
                _uiState.update { it.copy(scanStatusMessage = "Scanning… (attempt $attempt)") }

                // Strategy 1: Direct UDP → HTTP to 192.168.4.1
                val directJob = async {
                    val device = directConnectService.tryDirectConnect(pairingKey)
                    val err = directConnectService.lastError ?: ""
                    _uiState.update { it.copy(scanStatusMessage = "Scanning… $err") }
                    if (device != null) addOrUpdateDevice(device, isLive = true)
                }

                // Strategy 2: BLE advertisement scan → extract IP → HTTP status
                val bleJob = async {
                    withTimeoutOrNull(BLE_SCAN_TIMEOUT_MS) {
                        bleDiscoveryService.discoverDevices(pairingKey = pairingKey).collect { bleDevice ->
                            addBleDevice(bleDevice)
                        }
                    }
                }

                directJob.await()
                bleJob.await()

                if (_uiState.value.devices.none { it.isOnline }) {
                    _uiState.update { it.copy(scanStatusMessage = "Not found. Retrying…") }
                    delay(RETRY_DELAY_MS)
                }
            }

            // First live device confirmed
            _uiState.update {
                it.copy(
                    isDiscovering  = false,
                    firstScanDone  = true,
                    connectionPhase = ConnectionPhase.CONNECTED,
                    scanStatusMessage = "Hub connected!"
                )
            }

            // Persist the connected device so it can be shown on next cold launch
            _uiState.value.devices.firstOrNull { it.isOnline }?.let { live ->
                settingsRepo.saveLastKnownDevice(live)
                checkFirmwareUpdate(live.firmware)
            }

            // Start real-time polling loop
            startPolling(pairingKey)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Real-time polling — re-fetches device status every 5 s
    // ─────────────────────────────────────────────────────────────

    private fun startPolling(pairingKey: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var failCount = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val currentDevices = _uiState.value.devices
                if (currentDevices.isEmpty()) break

                val freshDevice = directConnectService.tryDirectConnect(pairingKey)
                if (freshDevice != null) {
                    failCount = 0
                    addOrUpdateDevice(freshDevice, isLive = true)
                    // Keep cache fresh with latest relay states
                    settingsRepo.saveLastKnownDevice(freshDevice)
                } else {
                    failCount++
                    if (failCount >= OFFLINE_GRACE_FAILS) {
                        // Mark all devices offline
                        _uiState.update { uiState ->
                            uiState.copy(
                                connectionPhase = ConnectionPhase.RECONNECTING,
                                devices = uiState.devices.map { it.copy(isOnline = false) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Device list management
    // ─────────────────────────────────────────────────────────────

    private fun addOrUpdateDevice(device: SafeLinkDevice, isLive: Boolean) {
        val namedDevice = applyCustomNames(device)
        val normalized = namedDevice.copy(
            isOnline = isLive,
            relays   = namedDevice.relays.mapIndexed { idx, r -> r.copy(id = idx + 1) }
        )
        _uiState.update { uiState ->
            val existingIndex = uiState.devices.indexOfFirst { it.deviceId == normalized.deviceId }
            if (existingIndex >= 0) {
                val newList = uiState.devices.toMutableList()
                newList[existingIndex] = normalized
                uiState.copy(devices = newList)
            } else {
                uiState.copy(devices = uiState.devices + normalized)
            }
        }
    }

    private suspend fun addBleDevice(bleDevice: SafeLinkDevice) {
        if (_uiState.value.devices.any { it.ip == bleDevice.ip && it.isOnline }) return
        val fullDevice = directConnectService.fetchStatus(bleDevice.ip)
        val target = fullDevice ?: bleDevice.copy(
            relays = List(bleDevice.relayCount) {
                Relay(id = it + 1, name = "Relay ${it + 1}", state = false,
                      connected = false, pinName = "?")
            }
        )
        val namedDevice = applyCustomNames(target)
        val normalized = namedDevice.copy(
            ip         = bleDevice.ip,
            wifiSignal = bleDevice.wifiSignal,
            isOnline   = true,
            relays     = namedDevice.relays.mapIndexed { idx, r -> r.copy(id = idx + 1) }
        )
        _uiState.update { uiState ->
            if (uiState.devices.none { it.deviceId == normalized.deviceId }) {
                uiState.copy(devices = uiState.devices + normalized)
            } else uiState
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Manual refresh — clears device list and re-scans
    // ─────────────────────────────────────────────────────────────

    fun discoverDevices(pairingKey: String) {
        pollingJob?.cancel()
        scanJob?.cancel()
        _uiState.update { it.copy(devices = emptyList(), firstScanDone = false) }
        startAutoScan(pairingKey)
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        scanJob?.cancel()
    }

    // ─────────────────────────────────────────────────────────────
    // Relay toggle — optimistic UI with Wi-Fi → BLE fallback
    // ─────────────────────────────────────────────────────────────

    fun toggleRelay(device: SafeLinkDevice, relay: Relay) {
        val newState = !relay.state
        applyRelayState(device.deviceId, relay.id, newState)   // Optimistic

        viewModelScope.launch {
            // Try Wi-Fi first
            var success = relayApiService.toggleRelay(
                ip         = device.ip,
                port       = device.port,
                name       = null,
                relayIndex = relay.id - 1,          // firmware expects 0-based index
                state      = newState
            )

            // Fallback: BLE GATT write
            if (!success) {
                success = bleRelayClient.toggleRelay(
                    macAddress = device.deviceId,
                    relayIndex = relay.id - 1,
                    state      = newState
                )
            }

            if (!success) {
                // Roll back optimistic update
                applyRelayState(device.deviceId, relay.id, relay.state)
                _uiState.update {
                    it.copy(error = "Failed to reach ${device.deviceName} via Wi-Fi and Bluetooth. Is it powered on?")
                }
            }
        }
    }

    private fun applyRelayState(deviceId: String, relayId: Int, newState: Boolean) {
        _uiState.update { uiState ->
            val updatedDevices = uiState.devices.map { d ->
                if (d.deviceId == deviceId) {
                    d.copy(relays = d.relays.map { r ->
                        if (r.id == relayId) r.copy(state = newState) else r
                    })
                } else d
            }
            uiState.copy(devices = updatedDevices)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Timer
    // ─────────────────────────────────────────────────────────────

    fun setTimer(
        device: SafeLinkDevice,
        relay: Relay,
        autoOnDelaySec: Long?,
        autoOffDelaySec: Long?
    ) {
        viewModelScope.launch {
            val pairingKey = settingsRepo.settingsFlow.first().pairingKey   // FIX: was using deviceName
            val success = relayApiService.setTimer(
                ip           = device.ip,
                port         = device.port,
                relayIndex   = relay.id - 1,
                autoOnDelay  = autoOnDelaySec,
                autoOffDelay = autoOffDelaySec
            )
            if (!success) {
                _uiState.update { it.copy(error = "Failed to set timer. Ensure phone is connected to SafeLink Wi-Fi.") }
            } else {
                // Re-fetch live status to reflect timer countdown
                val fresh = directConnectService.tryDirectConnect(pairingKey)
                if (fresh != null) addOrUpdateDevice(fresh, isLive = true)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Relay renaming
    // ─────────────────────────────────────────────────────────────

    fun renameRelay(device: SafeLinkDevice, relayId: Int, newName: String) {
        viewModelScope.launch {
            // FIX: pass relayId (1-based) so it matches applyCustomNames lookup key
            settingsRepo.updateCustomRelayName(device.deviceId, relayId, newName)

            // Register Google Assistant shortcut
            val shortcutId = "${device.deviceId}_$relayId"
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("safelink://toggle?light=$relayId&state=on"))
                .apply { setPackage(getApplication<Application>().packageName) }

            val shortcut = ShortcutInfoCompat.Builder(getApplication(), shortcutId)
                .setShortLabel("Turn on $newName")
                .setLongLabel("Turn on $newName on SafeLink")
                .setIntent(intent)
                .addCapabilityBinding("actions.intent.OPEN_APP_FEATURE", "feature", listOf(newName))
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(getApplication(), shortcut)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Error dismissal
    // ─────────────────────────────────────────────────────────────

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ─────────────────────────────────────────────────────────────
    // Firmware Update
    // ─────────────────────────────────────────────────────────────

    private fun checkFirmwareUpdate(currentFirmware: String) {
        viewModelScope.launch {
            val update = updateService.checkFirmwareUpdate(currentFirmware)
            if (update != null && update.isNewer) {
                _uiState.update { it.copy(firmwareUpdate = update) }
            }
        }
    }

    fun startFirmwareUpdate(device: SafeLinkDevice) {
        val fwInfo = _uiState.value.firmwareUpdate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isFlashingFirmware = true, flashProgress = 0) }
            
            // 1. Download
            var downloadSuccess = false
            updateService.downloadFirmware(fwInfo.downloadUrl).collect { state ->
                when (state) {
                    is DownloadState.Downloading -> _uiState.update { it.copy(flashProgress = state.progress / 2) }
                    is DownloadState.Done -> downloadSuccess = true
                    is DownloadState.Error -> {
                        _uiState.update { it.copy(isFlashingFirmware = false, error = "Download failed: ${state.message}") }
                    }
                    else -> {}
                }
            }

            if (!downloadSuccess) return@launch

            // 2. Flash
            updateService.flashFirmware(device.ip, usePrevious = false).collect { state ->
                when (state) {
                    is DownloadState.Downloading -> _uiState.update { it.copy(flashProgress = 50 + (state.progress / 2)) }
                    is DownloadState.Done -> {
                        _uiState.update { it.copy(
                            isFlashingFirmware = false, 
                            flashProgress = 100, 
                            firmwareUpdate = null,
                            rollbackAvailable = updateService.hasRollbackAvailable()
                        ) }
                    }
                    is DownloadState.Error -> {
                        _uiState.update { it.copy(isFlashingFirmware = false, error = "Flash failed: ${state.message}") }
                    }
                    else -> {}
                }
            }
        }
    }

    fun rollbackFirmware(device: SafeLinkDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFlashingFirmware = true, flashProgress = 0) }
            updateService.flashFirmware(device.ip, usePrevious = true).collect { state ->
                when (state) {
                    is DownloadState.Downloading -> _uiState.update { it.copy(flashProgress = state.progress) }
                    is DownloadState.Done -> {
                        _uiState.update { it.copy(isFlashingFirmware = false, flashProgress = 100) }
                    }
                    is DownloadState.Error -> {
                        _uiState.update { it.copy(isFlashingFirmware = false, error = "Rollback failed: ${state.message}") }
                    }
                    else -> {}
                }
            }
        }
    }
}
