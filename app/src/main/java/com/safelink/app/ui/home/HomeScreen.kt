package com.safelink.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
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
import com.safelink.app.ui.theme.MintGreen
import com.safelink.app.ui.theme.TealAccent

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onDeviceClick: (String) -> Unit,
    onRelayClick: (SafeLinkDevice, Relay) -> Unit,
    onRelayLongClick: (SafeLinkDevice, Relay) -> Unit = { _, _ -> },
    onRefresh: () -> Unit
) {
    val renameDialogState = remember { mutableStateOf<Pair<SafeLinkDevice, Relay>?>(null) }
    
    val onlineCount = uiState.devices.count { it.isOnline }
    val totalCount = uiState.devices.size

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            // Permissions granted, trigger scan if needed
            if (!uiState.isDiscovering) {
                onRefresh()
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Summary banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(TealAccent.copy(alpha = 0.15f), MintGreen.copy(alpha = 0.1f))
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏠", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Home Network",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (onlineCount > 0) MintGreen else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (totalCount == 0) "No devices" else "$onlineCount of $totalCount online",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TealAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Devices header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (totalCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = TealAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$totalCount",
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

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Loading indicator
        if (uiState.isDiscovering) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TealAccent)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            uiState.scanStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Make sure Bluetooth is ON and your phone\nis connected to the SafeLink Wi-Fi hotspot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Device cards
        if (!uiState.isDiscovering) {
            if (uiState.devices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 52.dp),
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                            Text(
                                "No devices found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Make sure your ESP32 is powered on\nand connected to the same WiFi network.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan Network")
                            }
                        }
                    }
                }
            } else {
                items(uiState.devices) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device.ip) },
                        onRelayClick = { relay -> onRelayClick(device, relay) },
                        onRelayLongClick = { relay -> renameDialogState.value = Pair(device, relay) }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

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
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onRelayLongClick(device, relay.copy(name = newName))
                        renameDialogState.value = null
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameDialogState.value = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

}
