package com.motorider.ui.dashboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.motorider.ui.theme.NeonRed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motorider.ui.dashboard.components.Speedometer
import com.motorider.ui.dashboard.components.PerformanceStatsCard
import com.motorider.ui.dashboard.components.WeatherOverlay
import com.motorider.ui.dashboard.components.WindIndicator
import com.motorider.ui.theme.DarkBackground
import com.motorider.ui.theme.NeonCyan
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
    val useMph by viewModel.useMph.collectAsState()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsState()
    val relativeWind by viewModel.relativeWind.collectAsState()
    
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
            // Top action bar
            TopActionBar(
                isRecording = state.isTripActive,
                recordingDuration = state.tripDurationFormatted,
                onHistoryClick = onNavigateToHistory,
                onSettingsClick = onNavigateToSettings,
                onAnnounceClick = { viewModel.announceSpeed() }
            )
            
            // Main speedometer - takes available space and centers itself
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Speedometer(
                    speedKmh = state.speedKmh,
                    alertState = state.alertState,
                    speedLimit = speedLimit,
                    useMph = useMph,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Bottom Cards Section - scrollable if content overflows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Performance Stats
                PerformanceStatsCard(
                    stats = state.performanceStats,
                    useMph = useMph,
                    onResetTopSpeed = { viewModel.resetTopSpeed() },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Wind indicator
                WindIndicator(
                    wind = relativeWind,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Weather overlay
                WeatherOverlay(
                    weather = state.weather,
                    isLoading = isWeatherLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Top action bar with navigation and quick actions.
 */
@Composable
private fun TopActionBar(
    isRecording: Boolean,
    recordingDuration: String,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAnnounceClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // History button
        IconButton(
            onClick = onHistoryClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "Trip History",
                tint = TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Center Area (Announce + REC status)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onAnnounceClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Announce Speed",
                    tint = NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            if (isRecording) {
                val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                
                Text(
                    text = "● REC $recordingDuration",
                    color = NeonRed,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(alpha)
                )
            }
        }
        
        // Settings button
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
