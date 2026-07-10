package com.motorider.ui.dashboard

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motorider.data.repository.SettingsRepository
import com.motorider.service.RadioPlayerService
import com.motorider.ui.dashboard.components.Speedometer
import com.motorider.ui.theme.CardBackground
import com.motorider.ui.theme.DarkBackground
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonGreen
import com.motorider.ui.theme.NeonRed
import com.motorider.ui.theme.TextPrimary
import com.motorider.ui.theme.TextSecondary

/**
 * Main dashboard screen for motorcycle riders.
 *
 * Layout optimized for:
 * - High visibility with neon colors on dark background
 * - Large touch targets for glove operation
 * - Quick glance speed reading
 * - Minimal distraction while riding
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.dashboardState.collectAsState()
    val speedLimit by viewModel.speedLimit.collectAsState()

    val camerasEnabled by viewModel.speedCamerasEnabled.collectAsState()
    val stations by viewModel.radioStations.collectAsState()
    val selectedStationId by viewModel.selectedStationId.collectAsState()

    val accentRed = NeonRed
    val textSec = TextSecondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (camerasEnabled && state.nearestCameraDistanceM != null) {
                val dist = state.nearestCameraDistanceM!!
                val infiniteTransition = rememberInfiniteTransition(label = "dashCamPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(if (dist < 100f) 400 else 800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dashCameraAlpha"
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(pulseAlpha)
                        .background(accentRed.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "⚠ SPEED CAMERA — ${dist.toInt()}m ahead",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Speedometer(
                speedKmh = state.speedKmh,
                alertState = state.alertState,
                speedLimit = speedLimit,
                roadSpeedLimit = state.roadSpeedLimit,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            DashboardRadioControls(
                stations = stations,
                selectedStationId = selectedStationId,
                onSelectStation = viewModel::setSelectedRadioStation
            )

            Spacer(modifier = Modifier.height(16.dp))

            BottomActionBar(
                onSettingsClick = onNavigateToSettings,
                accentRed = accentRed,
                textSec = textSec
            )
        }
    }
}

@Composable
private fun DashboardRadioControls(
    stations: List<SettingsRepository.RadioStation>,
    selectedStationId: String?,
    onSelectStation: (String?) -> Unit
) {
    val context = LocalContext.current
    val selectedStation = stations.firstOrNull { it.id == selectedStationId } ?: stations.firstOrNull()
    var isPlaying by remember { mutableStateOf(false) }

    fun startStation(station: SettingsRepository.RadioStation) {
        val intent = Intent(context, RadioPlayerService::class.java).apply {
            action = RadioPlayerService.ACTION_PLAY
            putExtra(RadioPlayerService.EXTRA_STATION_NAME, station.name)
            putExtra(RadioPlayerService.EXTRA_STATION_URL, station.url)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        isPlaying = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RADIO",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = selectedStation?.name ?: "No station — add one in Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPlaying) NeonGreen else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioControlButton(
                contentDescription = "Play",
                highlighted = isPlaying,
                onClick = {
                    val station = selectedStation ?: return@RadioControlButton
                    startStation(station)
                }
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_media_play),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isPlaying) NeonGreen else TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            RadioControlButton(
                contentDescription = "Pause",
                onClick = {
                    context.startService(
                        Intent(context, RadioPlayerService::class.java).apply {
                            action = RadioPlayerService.ACTION_PAUSE
                        }
                    )
                    isPlaying = false
                }
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_media_pause),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            RadioControlButton(
                contentDescription = "Next station",
                onClick = {
                    if (stations.isEmpty()) return@RadioControlButton
                    val currentIndex = stations.indexOfFirst { it.id == selectedStationId }
                    val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % stations.size
                    val nextStation = stations[nextIndex]
                    onSelectStation(nextStation.id)
                    startStation(nextStation)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun RadioControlButton(
    contentDescription: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (highlighted) NeonGreen.copy(alpha = 0.12f) else CardBackground,
        modifier = Modifier.size(64.dp),
        border = BorderStroke(
            1.dp,
            if (highlighted) NeonGreen else TextSecondary.copy(alpha = 0.5f)
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * Bottom action bar — quit and settings.
 */
@Composable
private fun BottomActionBar(
    onSettingsClick: () -> Unit,
    accentRed: androidx.compose.ui.graphics.Color = NeonRed,
    textSec: androidx.compose.ui.graphics.Color = TextSecondary
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quit/Stop All Button
        IconButton(
            onClick = {
                // Stop everything
                val radioIntent = android.content.Intent(context, com.motorider.service.RadioPlayerService::class.java).apply {
                    action = com.motorider.service.RadioPlayerService.ACTION_STOP
                }
                val overlayIntent = android.content.Intent(context, com.motorider.service.OverlayService::class.java).apply {
                    action = com.motorider.service.OverlayService.ACTION_STOP
                }
                val locIntent = android.content.Intent(context, com.motorider.service.LocationForegroundService::class.java).apply {
                    action = com.motorider.service.LocationForegroundService.ACTION_STOP
                }
                context.startService(radioIntent)
                context.startService(overlayIntent)
                context.startService(locIntent)
                (context as? android.app.Activity)?.finishAffinity()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Quit App",
                tint = accentRed,
                modifier = Modifier.size(28.dp)
            )
        }

        // Settings button
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = textSec,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

