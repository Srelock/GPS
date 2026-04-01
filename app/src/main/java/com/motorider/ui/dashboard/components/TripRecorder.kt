package com.motorider.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motorider.data.entity.ActiveTrip
import com.motorider.ui.theme.CardBackground
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonGreen
import com.motorider.ui.theme.NeonRed
import com.motorider.ui.theme.TextSecondary

/**
 * Trip recorder component showing distance, time, and start/stop controls.
 * 
 * Uses large touch targets for glove-friendly operation.
 */
@Composable
fun TripRecorder(
    activeTrip: ActiveTrip?,
    isTripActive: Boolean,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Distance
            StatItem(
                icon = Icons.Default.DirectionsBike,
                value = if (isTripActive && activeTrip != null) {
                    String.format("%.1f", activeTrip.totalDistanceMeters / 1000.0)
                } else {
                    "0.0"
                },
                unit = "km",
                label = "Distance"
            )
            
            // Duration
            StatItem(
                icon = Icons.Default.Timer,
                value = if (isTripActive && activeTrip != null) {
                    formatDuration(activeTrip.elapsedMillis)
                } else {
                    "00:00"
                },
                unit = "",
                label = "Time"
            )
            
            // Max speed
            StatItem(
                icon = null,
                value = if (isTripActive && activeTrip != null) {
                    "${activeTrip.maxSpeedKmh.toInt()}"
                } else {
                    "0"
                },
                unit = "km/h",
                label = "Max"
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Start/Stop button - large touch target
        if (isTripActive) {
            Button(
                onClick = onStopTrip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonRed
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STOP RECORDING",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onStartTrip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "START TRIP",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CardBackground
                )
            }
        }
    }
}

/**
 * Individual stat display item.
 */
@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    value: String,
    unit: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                )
            }
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
            
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/**
 * Format duration milliseconds as MM:SS or HH:MM:SS.
 */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
