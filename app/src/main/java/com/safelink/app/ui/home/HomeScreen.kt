package com.safelink.app.ui.home

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.BluetoothDisabled
import com.safelink.app.ui.theme.MutedRed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safelink.app.data.model.Relay
import com.safelink.app.data.model.SafeLinkDevice
import com.safelink.app.ui.components.DeviceCard
import com.safelink.app.ui.components.PulsingDot
import com.safelink.app.ui.theme.MintGreen
import com.safelink.app.ui.theme.TealAccent

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onDeviceClick: (String) -> Unit,
    onRelayClick: (SafeLinkDevice, Relay) -> Unit,
    onRelayLongClick: (SafeLinkDevice, Relay) -> Unit = { _, _ -> },
    onSetTimer: (SafeLinkDevice, Relay, Long?, Long?) -> Unit = { _, _, _, _ -> },
    onUpdateFirmware: (SafeLinkDevice) -> Unit = {},
    onRollbackFirmware: (SafeLinkDevice) -> Unit = {},
    onRefresh: () -> Unit
) {
    val renameDialogState = remember { mutableStateOf<Pair<SafeLinkDevice, Relay>?>(null) }
    val timerDialogState  = remember { mutableStateOf<Pair<SafeLinkDevice, Relay>?>(null) }

    val onlineCount = uiState.devices.count { it.isOnline }
    val totalCount  = uiState.devices.size

    // Permission launcher
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it } && !uiState.isDiscovering && uiState.devices.isEmpty()) {
            onRefresh()
        }
    }
    LaunchedEffect(Unit) { permissionLauncher.launch(permissions) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {

        // ── Connectivity warnings ──────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(12.dp))
            ConnectivityBanners()
        }

        // ── Hero summary card ──────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(4.dp))
            HomeHeroCard(
                onlineCount      = onlineCount,
                totalCount       = totalCount,
                connectionPhase  = uiState.connectionPhase,
                isDiscovering    = uiState.isDiscovering,
                scanStatusMessage= uiState.scanStatusMessage,
                onRefresh        = onRefresh
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Devices section header ─────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Devices",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedVisibility(visible = totalCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = TealAccent.copy(alpha = 0.13f)
                        ) {
                            Text(
                                text  = "$totalCount",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = TealAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ── Scanning indicator (shown above device cards if scanning AND devices exist) ─
        if (uiState.isDiscovering && totalCount > 0) {
            item {
                Surface(
                    shape  = RoundedCornerShape(12.dp),
                    color  = TealAccent.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = TealAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            uiState.scanStatusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent
                        )
                    }
                }
            }
        }

        // ── No devices + scanning: full-height spinner ─────────────────────────
        if (uiState.isDiscovering && totalCount == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = TealAccent, strokeWidth = 3.dp)
                        Text(
                            uiState.scanStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Make sure Bluetooth is ON and your phone\nis connected to the SafeLink Wi-Fi hotspot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // ── Empty, not scanning ────────────────────────────────────────────────
        if (!uiState.isDiscovering && totalCount == 0 && uiState.firstScanDone) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.DevicesOther,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            "No devices found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Make sure your ESP32 is powered on and\nconnected to the SafeLink Wi-Fi hotspot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Network")
                        }
                    }
                }
            }
        }

        // ── Device cards ───────────────────────────────────────────────────────
        items(items = uiState.devices, key = { it.deviceId }) { device ->
            DeviceCard(
                device          = device,
                connectionPhase = uiState.connectionPhase,
                modifier        = Modifier.animateItemPlacement(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessLow
                    )
                ),
                firmwareUpdate  = uiState.firmwareUpdate,
                rollbackAvailable = uiState.rollbackAvailable,
                isFlashing      = uiState.isFlashingFirmware,
                flashProgress   = uiState.flashProgress,
                onClick         = { onDeviceClick(device.ip) },
                onRelayClick    = { relay -> onRelayClick(device, relay) },
                onRelayLongClick= { relay -> renameDialogState.value = Pair(device, relay) },
                onTimerClick    = { relay -> timerDialogState.value  = Pair(device, relay) },
                onUpdateClick   = { onUpdateFirmware(device) },
                onRollbackClick = { onRollbackFirmware(device) }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    // ── Timer dialog ──────────────────────────────────────────────────────────
    timerDialogState.value?.let { (device, relay) ->
        var onDelayHours by remember { mutableStateOf("") }
        var onDelayMins  by remember { mutableStateOf("") }
        var offDelayHours by remember { mutableStateOf("") }
        var offDelayMins  by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { timerDialogState.value = null },
            title = { Text("Set Timer — ${relay.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Auto Turn ON (in hours & mins)", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = onDelayHours, onValueChange = { onDelayHours = it },
                            label = { Text("Hrs") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = onDelayMins, onValueChange = { onDelayMins = it },
                            label = { Text("Mins") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Auto Turn OFF (in hours & mins)", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = offDelayHours, onValueChange = { offDelayHours = it },
                            label = { Text("Hrs") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = offDelayMins, onValueChange = { offDelayMins = it },
                            label = { Text("Mins") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val onH  = onDelayHours.toLongOrNull() ?: 0L
                    val onM  = onDelayMins.toLongOrNull()  ?: 0L
                    val offH = offDelayHours.toLongOrNull() ?: 0L
                    val offM = offDelayMins.toLongOrNull()  ?: 0L
                    val onSecs  = (onH * 3600) + (onM * 60)
                    val offSecs = (offH * 3600) + (offM * 60)
                    onSetTimer(
                        device, relay,
                        if (onSecs > 0) onSecs else if (onDelayHours == "0" && onDelayMins == "0") 0L else null,
                        if (offSecs > 0) offSecs else if (offDelayHours == "0" && offDelayMins == "0") 0L else null
                    )
                    timerDialogState.value = null
                }) { Text("Set Timer") }
            },
            dismissButton = {
                TextButton(onClick = { timerDialogState.value = null }) { Text("Cancel") }
            }
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    renameDialogState.value?.let { (device, relay) ->
        var newName by remember { mutableStateOf(relay.name) }
        AlertDialog(
            onDismissRequest = { renameDialogState.value = null },
            title = { Text("Rename Relay") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = com.safelink.app.ui.components.relayIcon(newName),
                            contentDescription = null,
                            tint = TealAccent
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRelayLongClick(device, relay.copy(name = newName))
                    renameDialogState.value = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogState.value = null }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Hero summary card — top of home screen
// ─────────────────────────────────────────────────────────────
@Composable
fun HomeHeroCard(
    onlineCount: Int,
    totalCount: Int,
    connectionPhase: ConnectionPhase,
    isDiscovering: Boolean,
    scanStatusMessage: String,
    onRefresh: () -> Unit
) {
    val (gradientStart, gradientEnd) = when (connectionPhase) {
        ConnectionPhase.CONNECTED    -> Pair(TealAccent.copy(alpha = 0.18f), MintGreen.copy(alpha = 0.10f))
        ConnectionPhase.RECONNECTING -> Pair(Color(0xFFF59E0B).copy(alpha = 0.12f), Color(0xFFF97316).copy(alpha = 0.07f))
        ConnectionPhase.FAILED       -> Pair(MutedRed.copy(alpha = 0.10f), Color(0xFF991B1B).copy(alpha = 0.06f))
        else                         -> Pair(TealAccent.copy(alpha = 0.08f), MintGreen.copy(alpha = 0.05f))
    }

    val dotColor = when (connectionPhase) {
        ConnectionPhase.CONNECTED    -> MintGreen
        ConnectionPhase.RECONNECTING -> Color(0xFFF59E0B)
        else                         -> Color.Gray
    }
    val statusLabel = when {
        connectionPhase == ConnectionPhase.CONNECTED    ->
            if (onlineCount == 1) "1 hub online" else "$onlineCount of $totalCount online"
        connectionPhase == ConnectionPhase.RECONNECTING -> "Reconnecting…"
        totalCount == 0                                 -> "No devices"
        else                                            -> "Scanning…"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(gradientStart, gradientEnd)))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏠", fontSize = 30.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text  = "Home Network",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PulsingDot(
                            color   = dotColor,
                            pulsing = connectionPhase == ConnectionPhase.RECONNECTING || isDiscovering,
                            size    = 7
                        )
                        Text(
                            text  = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Show scan message when actively discovering (but device cards already showing)
                    AnimatedVisibility(visible = isDiscovering && totalCount > 0) {
                        Text(
                            text  = scanStatusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            IconButton(onClick = onRefresh) {
                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TealAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TealAccent
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Connectivity warning banners
// ─────────────────────────────────────────────────────────────
@Composable
fun ConnectivityBanners() {
    val context = LocalContext.current
    var isWifiEnabled      by remember { mutableStateOf(checkWifiEnabled(context)) }
    var isBluetoothEnabled by remember { mutableStateOf(checkBluetoothEnabled()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.WIFI_STATE_CHANGED_ACTION     -> isWifiEnabled      = checkWifiEnabled(context)
                    BluetoothAdapter.ACTION_STATE_CHANGED     -> isBluetoothEnabled = checkBluetoothEnabled()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    if (!isWifiEnabled) {
        ConnectivityBanner(
            title       = "Wi-Fi is Off",
            description = "Turn on Wi-Fi to connect to SafeLink hotspot.",
            icon        = Icons.Default.WifiOff,
            actionLabel = "Settings",
            onAction    = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (!isBluetoothEnabled) {
        ConnectivityBanner(
            title       = "Bluetooth is Off",
            description = "Turn on Bluetooth for proximity control and discovery.",
            icon        = Icons.Default.BluetoothDisabled,
            actionLabel = "Settings",
            onAction    = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun ConnectivityBanner(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MutedRed.copy(alpha = 0.09f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MutedRed, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MutedRed)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = MutedRed),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text(actionLabel, fontSize = 12.sp) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Utility functions
// ─────────────────────────────────────────────────────────────
private fun checkWifiEnabled(context: Context?): Boolean {
    if (context == null) return false
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    return wm?.isWifiEnabled == true
}

@SuppressLint("MissingPermission")
private fun checkBluetoothEnabled(): Boolean {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    return adapter?.isEnabled == true
}
