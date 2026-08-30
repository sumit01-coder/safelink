package com.safelink.app.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safelink.app.ui.theme.*

data class SceneItem(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val relayCount: Int,
    var isActive: Boolean = false
)

@Composable
fun ScenesScreen() {
    val scenes = remember {
        mutableStateListOf(
            SceneItem(
                name = "Morning",
                description = "Start your day bright",
                icon = Icons.Default.WbSunny,
                gradientColors = listOf(SunriseOrange, SunriseYellow),
                relayCount = 4,
                isActive = true
            ),
            SceneItem(
                name = "Evening",
                description = "Wind down for the night",
                icon = Icons.Default.Nightlight,
                gradientColors = listOf(EveningPurple, EveningPink),
                relayCount = 2,
                isActive = false
            ),
            SceneItem(
                name = "Away",
                description = "Secure while you're out",
                icon = Icons.Default.Security,
                gradientColors = listOf(AwayBlue, TealAccent),
                relayCount = 1,
                isActive = false
            ),
            SceneItem(
                name = "Night",
                description = "Everything off, rest well",
                icon = Icons.Default.DarkMode,
                gradientColors = listOf(NightIndigo, NightDeep),
                relayCount = 0,
                isActive = false
            ),
            SceneItem(
                name = "Movie",
                description = "Perfect ambiance for films",
                icon = Icons.Default.MovieFilter,
                gradientColors = listOf(MutedRed, EveningPurple),
                relayCount = 3,
                isActive = false
            ),
            SceneItem(
                name = "Work",
                description = "Focused and productive",
                icon = Icons.Default.Work,
                gradientColors = listOf(MintGreen, AwayBlue),
                relayCount = 3,
                isActive = false
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Scenes",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Automate your home with one tap",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Active scene banner
        val activeScene = scenes.firstOrNull { it.isActive }
        if (activeScene != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(activeScene.gradientColors))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = activeScene.icon,
                        contentDescription = activeScene.name,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Active Scene",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            activeScene.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            activeScene.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Active", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "All Scenes",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Grid of scenes
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(scenes) { scene ->
                SceneCard(
                    scene = scene,
                    onClick = {
                        val idx = scenes.indexOf(scene)
                        if (idx >= 0) {
                            // Toggle: deactivate all, activate clicked
                            val newActive = !scene.isActive
                            scenes.forEachIndexed { i, s ->
                                scenes[i] = s.copy(isActive = if (i == idx) newActive else false)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SceneCard(scene: SceneItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (scene.isActive)
                        Brush.verticalGradient(scene.gradientColors)
                    else
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (scene.isActive) Color.White.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.background
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = scene.icon,
                            contentDescription = scene.name,
                            tint = if (scene.isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (scene.isActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        )
                    }
                }
                Column {
                    Text(
                        text = scene.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (scene.isActive) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${scene.relayCount} relays",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (scene.isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
