package com.safelink.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.safelink.app.ui.home.ConnectionPhase
import com.safelink.app.ui.theme.MintGreen
import com.safelink.app.ui.theme.MutedRed
import com.safelink.app.ui.theme.TealAccent

// ─────────────────────────────────────────────────────────────
// Animated pulsing dot — shows real-time connection status
// ─────────────────────────────────────────────────────────────
@Composable
fun PulsingDot(
    color: Color,
    pulsing: Boolean = true,
    size: Int = 9
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (pulsing) 0.3f else 1f,
        targetValue  = if (pulsing) 1.0f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = if (pulsing) 0.85f else 1f,
        targetValue  = if (pulsing) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(if (pulsing) scale else 1f)
            .clip(CircleShape)
            .background(color.copy(alpha = if (pulsing) alpha else 1f))
    )
}

// ─────────────────────────────────────────────────────────────
// Connection status chip — shown inside the device card header
// ─────────────────────────────────────────────────────────────
@Composable
fun ConnectionStatusChip(
    phase: ConnectionPhase,
    signal: Int,
    isOnline: Boolean
) {
    val (dotColor, label, isPulsing) = when {
        isOnline && phase == ConnectionPhase.CONNECTED    ->
            Triple(MintGreen, "Live", false)
        phase == ConnectionPhase.RECONNECTING             ->
            Triple(Color(0xFFF59E0B), "Reconnecting…", true)
        !isOnline                                        ->
            Triple(MutedRed, "Offline", false)
        else                                             ->
            Triple(Color.Gray, "Unknown", false)
    }

    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = dotColor.copy(alpha = 0.12f),
        modifier = Modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            PulsingDot(color = dotColor, pulsing = isPulsing, size = 7)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = dotColor,
                fontSize = 11.sp
            )
            if (isOnline) {
                Spacer(modifier = Modifier.width(8.dp))
                WifiSignalIcon(signal = signal)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Device Card
// ─────────────────────────────────────────────────────────────
@Composable
fun DeviceCard(
    device: SafeLinkDevice,
    connectionPhase: ConnectionPhase = ConnectionPhase.IDLE,
    modifier: Modifier = Modifier,
    firmwareUpdate: com.safelink.app.data.update.FirmwareReleaseInfo? = null,
    rollbackAvailable: Boolean = false,
    isFlashing: Boolean = false,
    flashProgress: Int = 0,
    onClick: (() -> Unit)? = null,
    onRelayClick: (Relay) -> Unit,
    onRelayLongClick: ((Relay) -> Unit)? = null,
    onTimerClick: ((Relay) -> Unit)? = null,
    onUpdateClick: (() -> Unit)? = null,
    onRollbackClick: (() -> Unit)? = null
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            device.isOnline && connectionPhase == ConnectionPhase.CONNECTED -> TealAccent.copy(alpha = 0.4f)
            connectionPhase == ConnectionPhase.RECONNECTING -> Color(0xFFF59E0B).copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        },
        animationSpec = tween(600),
        label = "border"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.5.dp, borderColor, RoundedCornerShape(24.dp)),
        shape    = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (device.isOnline) 6.dp else 2.dp,
            pressedElevation = 0.dp
        ),
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick  = onClick ?: {}
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device avatar with live ring
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (device.isOnline) TealAccent.copy(alpha = 0.13f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                        )
                        .border(
                            1.5.dp,
                            if (device.isOnline) TealAccent.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = "Device",
                        tint = if (device.isOnline) TealAccent
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = device.deviceName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text  = "ESP32 · ${device.ip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    ConnectionStatusChip(
                        phase    = connectionPhase,
                        signal   = device.wifiSignal,
                        isOnline = device.isOnline
                    )
                }

                // Right side: relay count + chevron
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TealAccent.copy(alpha = if (device.isOnline) 0.13f else 0.05f)
                    ) {
                        Text(
                            text  = "${device.relayCount} Relays",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (device.isOnline) TealAccent
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── Offline / Reconnecting banner ─────────────────────────────────
            if (!device.isOnline) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (connectionPhase == ConnectionPhase.RECONNECTING)
                                Color(0xFFF59E0B).copy(alpha = 0.08f)
                            else MutedRed.copy(alpha = 0.07f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (connectionPhase == ConnectionPhase.RECONNECTING) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(14.dp),
                                color     = Color(0xFFF59E0B),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Reconnecting — make sure the SafeLink hotspot is on.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF59E0B)
                            )
                        } else {
                            Icon(
                                Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MutedRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Device unreachable. Last seen relay states are shown.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedRed
                            )
                        }
                    }
                }
            }

            // ── Firmware Update Banner ────────────────────────────────────────
            if (device.isOnline && (firmwareUpdate != null || rollbackAvailable || isFlashing)) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TealAccent.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isFlashing) "Flashing Firmware..." else if (firmwareUpdate != null) "Update Available: v${firmwareUpdate.firmwareVersion}" else "Rollback Available",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = TealAccent
                            )
                        }
                        
                        if (isFlashing) {
                            LinearProgressIndicator(
                                progress = { flashProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = TealAccent,
                                trackColor = TealAccent.copy(alpha = 0.2f)
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (firmwareUpdate != null) {
                                    Button(
                                        onClick = { onUpdateClick?.invoke() },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Update Now", fontSize = 12.sp)
                                    }
                                }
                                if (rollbackAvailable) {
                                    OutlinedButton(
                                        onClick = { onRollbackClick?.invoke() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Rollback", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Relay grid ────────────────────────────────────────────────────
            if (device.relays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(18.dp))

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
                                    relay         = relay,
                                    deviceOnline  = device.isOnline,
                                    modifier      = Modifier.weight(1f),
                                    onClick       = { onRelayClick(relay) },
                                    onLongClick   = { onRelayLongClick?.invoke(relay) },
                                    onTimerClick  = { onTimerClick?.invoke(relay) }
                                )
                            }
                            if (rowRelays.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Relay quick-control tile
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelayQuickControl(
    relay: Relay,
    deviceOnline: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTimerClick: (() -> Unit)? = null
) {
    val haptic       = LocalHapticFeedback.current
    val isOn         = relay.state
    val dimmedByOffline = !deviceOnline

    val springSpec      = spring<Color>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val cardBg          = if (isOn) Color(0xFF1E293B) else Color(0xFF0F172A)
    val ringColorStart  = if (isOn) MintGreen else Color(0xFFF59E0B)
    val ringColorEnd    = if (isOn) Color(0xFF0D9488) else Color(0xFFE11D48)

    val bgColor      by animateColorAsState(targetValue = cardBg, animationSpec = springSpec, label = "bg")
    val contentColor by animateColorAsState(
        targetValue    = if (isOn && !dimmedByOffline) Color.White else Color.White.copy(alpha = 0.5f),
        animationSpec  = springSpec,
        label          = "content"
    )

    Card(
        shape   = RoundedCornerShape(20.dp),
        colors  = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOn && deviceOnline) 8.dp else 2.dp),
        modifier = modifier
            .height(180.dp)
            .shadow(
                elevation   = if (isOn && deviceOnline) 14.dp else 2.dp,
                shape       = RoundedCornerShape(20.dp),
                ambientColor = ringColorStart.copy(alpha = if (deviceOnline) 0.5f else 0.1f)
            )
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {
                    if (deviceOnline) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick?.invoke()
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Top row: Pin name + connection dot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = relay.pinName ?: "",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
                // Relay physical connection dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !relay.connected -> MutedRed
                                !deviceOnline    -> Color(0xFFF59E0B)
                                else             -> MintGreen
                            }
                        )
                )
            }

            // Centre: icon + name + state + switch
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glowing ring + icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    ringColorStart.copy(alpha = if (deviceOnline) 0.8f else 0.25f),
                                    ringColorEnd.copy(alpha = if (deviceOnline) 0.2f else 0.05f)
                                )
                            ),
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text     = relay.name,
                    style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color    = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))

                // Timer / state label
                val timerText = buildString {
                    when {
                        relay.autoOnLeft > 0L  -> {
                            val m = relay.autoOnLeft / 60; val s = relay.autoOnLeft % 60
                            append("ON in ${m}m ${s}s")
                        }
                        relay.autoOffLeft > 0L -> {
                            val m = relay.autoOffLeft / 60; val s = relay.autoOffLeft % 60
                            append("OFF in ${m}m ${s}s")
                        }
                        else -> append(if (isOn) "ON" else "OFF")
                    }
                }
                Text(
                    text  = timerText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = when {
                        !deviceOnline -> Color.White.copy(alpha = 0.3f)
                        relay.autoOnLeft > 0L || relay.autoOffLeft > 0L -> MintGreen
                        isOn -> MintGreen.copy(alpha = 0.9f)
                        else -> Color.White.copy(alpha = 0.4f)
                    },
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Custom switch + timer button
                val switchOffset by animateDpAsState(
                    targetValue    = if (isOn) 24.dp else 4.dp,
                    animationSpec  = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "switchOffset"
                )
                val switchBgColor by animateColorAsState(
                    targetValue   = if (isOn && deviceOnline) MintGreen.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                    animationSpec = springSpec,
                    label = "switchBg"
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(switchBgColor),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = switchOffset)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isOn && deviceOnline) MintGreen
                                    else Color.White.copy(alpha = 0.4f)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (relay.autoOnLeft > 0L || relay.autoOffLeft > 0L)
                                    MintGreen.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTimerClick?.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Timer",
                            tint = if (relay.autoOnLeft > 0L || relay.autoOffLeft > 0L)
                                       MintGreen
                                   else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────

/** Maps a relay name keyword to the closest Material icon. */
fun relayIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("fan") || lower.contains("exhaust") -> Icons.Default.Air
        lower.contains("ac") || lower.contains("air con")  -> Icons.Default.AcUnit
        lower.contains("tv") || lower.contains("television")-> Icons.Default.Tv
        lower.contains("lamp")                             -> Icons.Default.Lightbulb
        lower.contains("light")                            -> Icons.Default.LightMode
        lower.contains("socket") || lower.contains("plug") -> Icons.Default.PowerSettingsNew
        lower.contains("pump") || lower.contains("water")  -> Icons.Default.Water
        lower.contains("heat") || lower.contains("heater") -> Icons.Default.Thermostat
        else                                               -> Icons.Default.Bolt
    }
}

@Composable
fun WifiSignalIcon(signal: Int) {
    val (icon, color) = when {
        signal >= -55 -> Icons.Default.NetworkWifi    to MintGreen
        signal >= -70 -> Icons.Default.NetworkWifi2Bar to TealAccent
        else          -> Icons.Default.NetworkWifi1Bar to MutedRed
    }
    Icon(
        imageVector = icon,
        contentDescription = "WiFi $signal dBm",
        tint = color,
        modifier = Modifier.size(13.dp)
    )
}
