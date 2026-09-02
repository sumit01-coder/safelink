package com.safelink.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.shadow
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
    onRelayClick: (Relay) -> Unit,
    onRelayLongClick: ((Relay) -> Unit)? = null,
    onTimerClick: ((Relay) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick ?: {}
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

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
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ESP32 · ${device.ip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Status row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (device.isOnline) MintGreen else MutedRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (device.isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (device.isOnline) MintGreen else MutedRed
                        )
                        if (device.isOnline) {
                            Spacer(modifier = Modifier.width(12.dp))
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
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 1.5.dp)
                Spacer(modifier = Modifier.height(20.dp))

                // Relay grid (2 columns)
                val chunkedRelays = device.relays.chunked(2)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    chunkedRelays.forEach { rowRelays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowRelays.forEach { relay ->
                                RelayQuickControl(
                                    relay = relay,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onRelayClick(relay) },
                                    onLongClick = { onRelayLongClick?.invoke(relay) },
                                    onTimerClick = { onTimerClick?.invoke(relay) }
                                )
                            }
                            // If row is incomplete, add an empty spacer with same weight to maintain grid alignment
                            if (rowRelays.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelayQuickControl(
    relay: Relay,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTimerClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val isMainAction = relay.state

    val springSpec = spring<Color>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val floatSpringSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    // Colors matching the dark premium UI
    val cardBg = if (isMainAction) Color(0xFF1E293B) else Color(0xFF0F172A)
    val ringColorStart = if (isMainAction) MintGreen else Color(0xFFF59E0B)
    val ringColorEnd = if (isMainAction) Color(0xFF0D9488) else Color(0xFFE11D48)
    
    val bgColor by animateColorAsState(targetValue = cardBg, animationSpec = springSpec, label = "bg")
    val contentColor by animateColorAsState(
        targetValue = if (isMainAction) Color.White else Color.White.copy(alpha = 0.8f),
        animationSpec = springSpec, label = "content"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .height(180.dp)
            .shadow(if (isMainAction) 16.dp else 4.dp, RoundedCornerShape(20.dp), ambientColor = ringColorStart)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick?.invoke()
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top Row: Pin Name & Connection Dot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = relay.pinName ?: "",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (relay.connected) MintGreen else MutedRed)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon in Glowing Ring
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(listOf(ringColorStart.copy(alpha = 0.8f), ringColorEnd.copy(alpha = 0.2f))),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = relayIcon(relay.name),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Name & State
                Text(
                    text = relay.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                val timerText = if (relay.autoOnLeft > 0) {
                    val m = relay.autoOnLeft / 60
                    val s = relay.autoOnLeft % 60
                    " (ON in ${m}m ${s}s)"
                } else if (relay.autoOffLeft > 0) {
                    val m = relay.autoOffLeft / 60
                    val s = relay.autoOffLeft % 60
                    " (OFF in ${m}m ${s}s)"
                } else ""

                Text(
                    text = if (isMainAction) "ON$timerText" else "OFF$timerText",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (timerText.isNotEmpty()) MintGreen else Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Custom Bottom Switch
                val switchOffset by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isMainAction) 24.dp else 4.dp,
                    animationSpec = spring<androidx.compose.ui.unit.Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "switchOffset"
                )
                val switchBgColor by animateColorAsState(
                    targetValue = if (isMainAction) MintGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                    animationSpec = springSpec,
                    label = "switchBg"
                )
                
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(switchBgColor)
                        .padding(horizontal = 0.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = switchOffset)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isMainAction) MintGreen else Color.White.copy(alpha = 0.9f))
                    )
                }
            }

            // Timer Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (relay.autoOnLeft > 0 || relay.autoOffLeft > 0) MintGreen.copy(alpha=0.2f) else Color.White.copy(alpha = 0.1f))
                    .clickable { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTimerClick?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "Timer",
                    tint = if (relay.autoOnLeft > 0 || relay.autoOffLeft > 0) MintGreen else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
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
