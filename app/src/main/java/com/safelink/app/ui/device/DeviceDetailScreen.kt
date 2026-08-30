package com.safelink.app.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safelink.app.data.model.Relay
import com.safelink.app.data.model.SafeLinkDevice
import com.safelink.app.ui.components.relayIcon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

val LightOrange = Color(0xFFFFA000)
val LightOrangeBg = Color(0xFFFFF8E1)
val FanBlue = Color(0xFF2979FF)
val FanBlueBg = Color(0xFFE3F2FD)

@Composable
fun DeviceDetailScreen(
    device: SafeLinkDevice,
    onRelayToggle: (Relay) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        device.relays.forEach { relay ->
            val lowerName = relay.name.lowercase()
            when {
                lowerName.contains("light") || lowerName.contains("lamp") -> {
                    LightControllerCard(relay = relay, onToggle = { onRelayToggle(relay) })
                }
                lowerName.contains("fan") || lowerName.contains("exhaust") -> {
                    FanControllerCard(relay = relay, onToggle = { onRelayToggle(relay) })
                }
                else -> {
                    GenericControllerCard(relay = relay, onToggle = { onRelayToggle(relay) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun LightControllerCard(relay: Relay, onToggle: () -> Unit) {
    var brightness by remember { mutableFloatStateOf(0.7f) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, LightOrange.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(LightOrangeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = LightOrange, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = relay.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(text = "Smart Light", style = MaterialTheme.typography.bodySmall, color = LightOrange)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (relay.state) LightOrangeBg else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onToggle
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (relay.state) "ON" else "OFF",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (relay.state) LightOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (relay.state) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(LightOrange))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Body
            Row(modifier = Modifier.fillMaxWidth()) {
                // Slider
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularSlider(
                        value = if (relay.state) brightness else 0f,
                        onValueChange = { if (relay.state) brightness = it },
                        color = LightOrange,
                        label = "Brightness",
                        enabled = relay.state
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Actions
                Column(modifier = Modifier.weight(1f)) {
                    Text("QUICK ACTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionButton(icon = Icons.Default.WbSunny, label = "Bright", color = LightOrange, active = brightness > 0.8f && relay.state, onClick = { brightness = 1f; if (!relay.state) onToggle() })
                        QuickActionButton(icon = Icons.Default.BrightnessMedium, label = "Dim", color = MaterialTheme.colorScheme.onSurfaceVariant, active = brightness in 0.3f..0.8f && relay.state, onClick = { brightness = 0.5f; if (!relay.state) onToggle() })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionButton(icon = Icons.Default.Nightlight, label = "Night", color = MaterialTheme.colorScheme.onSurfaceVariant, active = brightness < 0.3f && relay.state, onClick = { brightness = 0.1f; if (!relay.state) onToggle() })
                        QuickActionButton(icon = Icons.Default.FavoriteBorder, label = "Relax", color = MaterialTheme.colorScheme.onSurfaceVariant, active = false, onClick = { })
                    }
                }
            }
        }
    }
}

@Composable
fun FanControllerCard(relay: Relay, onToggle: () -> Unit) {
    var speed by remember { mutableFloatStateOf(0.6f) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, FanBlue.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(FanBlueBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Air, contentDescription = null, tint = FanBlue, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = relay.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(text = "Smart Fan", style = MaterialTheme.typography.bodySmall, color = FanBlue)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (relay.state) FanBlueBg else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onToggle
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (relay.state) "ON" else "OFF",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (relay.state) FanBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (relay.state) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(FanBlue))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Body
            Row(modifier = Modifier.fillMaxWidth()) {
                // Slider
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularSlider(
                        value = if (relay.state) speed else 0f,
                        onValueChange = { if (relay.state) speed = it },
                        color = FanBlue,
                        label = "Speed",
                        enabled = relay.state
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Actions
                Column(modifier = Modifier.weight(1f)) {
                    Text("QUICK ACTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionButton(icon = Icons.Default.KeyboardArrowDown, label = "Low", color = MaterialTheme.colorScheme.onSurfaceVariant, active = speed < 0.4f && relay.state, onClick = { speed = 0.3f; if (!relay.state) onToggle() })
                        QuickActionButton(icon = Icons.Default.Remove, label = "Med", color = MaterialTheme.colorScheme.onSurfaceVariant, active = speed in 0.4f..0.7f && relay.state, onClick = { speed = 0.6f; if (!relay.state) onToggle() })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionButton(icon = Icons.Default.KeyboardArrowUp, label = "High", color = FanBlue, active = speed > 0.7f && relay.state, onClick = { speed = 1f; if (!relay.state) onToggle() })
                        QuickActionButton(icon = Icons.Default.Storm, label = "Turbo", color = MaterialTheme.colorScheme.onSurfaceVariant, active = false, onClick = { })
                    }
                }
            }
        }
    }
}

@Composable
fun GenericControllerCard(relay: Relay, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(relayIcon(relay.name), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = relay.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Switch(
                checked = relay.state,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, if (active) color else Color.Transparent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
fun PowerSummaryCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem(icon = Icons.Default.Bolt, title = "120.5 W", subtitle = "Power Usage", color = LightOrange)
            VerticalDivider(modifier = Modifier.height(40.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SummaryItem(icon = Icons.Default.Schedule, title = "3h 45m", subtitle = "Total Runtime", color = FanBlue)
            VerticalDivider(modifier = Modifier.height(40.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SummaryItem(icon = Icons.Default.Eco, title = "Eco Mode", subtitle = "Active", color = Color(0xFF4CAF50))
        }
    }
}

@Composable
fun SummaryItem(icon: ImageVector, title: String, subtitle: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CircularSlider(
    value: Float, // 0f to 1f
    onValueChange: (Float) -> Unit,
    color: Color,
    label: String,
    enabled: Boolean
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val strokeWidth = 14.dp

    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = atan2(change.position.y - center.y, change.position.x - center.x)
                        // Map angle from -PI..PI to 0..1 where start is at 135 degrees (2.35 rad) and end is at 45 degrees (0.78 rad)
                        // Simplified mapping for circular slider (full circle for now)
                        var normalizedAngle = (angle + Math.PI) / (2 * Math.PI)
                        // rotate to start from bottom left
                        normalizedAngle = (normalizedAngle + 0.25f) % 1.0f
                        onValueChange(normalizedAngle.toFloat().coerceIn(0f, 1f))
                    }
                }
        ) {
            val strokeW = strokeWidth.toPx()
            val radius = (size.width - strokeW) / 2
            val centerOffset = Offset(size.width / 2, size.height / 2)

            // Draw track (270 degrees)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(strokeW / 2, strokeW / 2)
            )

            // Draw progress
            if (value > 0f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * value,
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(strokeW / 2, strokeW / 2)
                )
                
                // Thumb
                val currentAngle = (135f + 270f * value) * (Math.PI / 180f)
                val thumbX = centerOffset.x + radius * cos(currentAngle).toFloat()
                val thumbY = centerOffset.y + radius * sin(currentAngle).toFloat()
                
                drawCircle(
                    color = color,
                    radius = strokeW * 0.8f,
                    center = Offset(thumbX, thumbY)
                )
                drawCircle(
                    color = Color.White,
                    radius = strokeW * 0.5f,
                    center = Offset(thumbX, thumbY)
                )
            }
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
