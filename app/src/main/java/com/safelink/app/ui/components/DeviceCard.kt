package com.safelink.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safelink.app.data.model.Relay
import com.safelink.app.data.model.SafeLinkDevice
import com.safelink.app.ui.theme.MintGreen
import com.safelink.app.ui.theme.MutedRed
import com.safelink.app.ui.theme.TealAccent

@Composable
fun DeviceCard(
    device: SafeLinkDevice,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRelayClick: (Relay) -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick ?: {}
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device avatar
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (device.isOnline) TealAccent.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                        .border(
                            1.dp,
                            if (device.isOnline) TealAccent.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = "Device",
                        tint = if (device.isOnline) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ESP32 · ${device.ip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TealAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Status row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (device.isOnline) MintGreen else MutedRed)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (device.isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (device.isOnline) MintGreen else MutedRed
                        )
                        if (device.isOnline) {
                            Spacer(modifier = Modifier.width(10.dp))
                            WifiSignalIcon(signal = device.wifiSignal)
                        }
                    }
                }

                // Relay count badge
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Text(
                            text = "${device.relayCount} Relays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (device.relays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                // Relay grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    device.relays.forEach { relay ->
                        RelayQuickControl(
                            relay = relay,
                            modifier = Modifier.weight(1f),
                            onClick = { onRelayClick(relay) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RelayQuickControl(
    relay: Relay,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (relay.state) MintGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (relay.state) MintGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.background,
        animationSpec = tween(300),
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (relay.state) MintGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "contentColor"
    )

    Card(
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = relayIcon(relay.name),
                contentDescription = relay.name,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = relay.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontSize = 10.sp
            )
            Text(
                text = if (relay.state) "ON" else "OFF",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = contentColor,
                fontSize = 11.sp
            )
        }
    }
}

/** Maps a relay name to the closest Material icon. */
fun relayIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("fan") || lower.contains("exhaust") -> Icons.Default.Air
        lower.contains("ac") || lower.contains("air con") -> Icons.Default.AcUnit
        lower.contains("tv") || lower.contains("television") -> Icons.Default.Tv
        lower.contains("lamp") -> Icons.Default.Lightbulb
        lower.contains("light") -> Icons.Default.LightMode
        lower.contains("socket") || lower.contains("plug") -> Icons.Default.PowerSettingsNew
        else -> Icons.Default.Bolt
    }
}

@Composable
private fun WifiSignalIcon(signal: Int) {
    val (icon, color) = when {
        signal >= -55 -> Icons.Default.NetworkWifi to MintGreen
        signal >= -70 -> Icons.Default.NetworkWifi2Bar to TealAccent
        else -> Icons.Default.NetworkWifi1Bar to MutedRed
    }
    Icon(
        imageVector = icon,
        contentDescription = "WiFi $signal dBm",
        tint = color,
        modifier = Modifier.size(14.dp)
    )
}
