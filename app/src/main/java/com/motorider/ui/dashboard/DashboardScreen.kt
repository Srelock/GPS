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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material.icons.filled.Close
import com.motorider.ui.theme.DarkBackground
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
    val useMph by viewModel.useMph.collectAsState()

    // Speed cameras
    val camerasEnabled by viewModel.speedCamerasEnabled.collectAsState()

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
            // Camera warning banner (above speedometer)
            if (camerasEnabled && state.nearestCameraDistanceM != null) {
                val dist = state.nearestCameraDistanceM!!
                val infiniteTransition = rememberInfiniteTransition(label = "dashCamPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(if (dist < 150f) 400 else 800),
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

            BottomActionBar(
                onSettingsClick = onNavigateToSettings,
                accentRed = accentRed,
                textSec = textSec
            )
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

