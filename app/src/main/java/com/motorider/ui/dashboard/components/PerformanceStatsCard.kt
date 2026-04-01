package com.motorider.ui.dashboard.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorider.core.performance.PerformanceStats
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonOrange
import com.motorider.ui.theme.NeonRed
import com.motorider.ui.theme.TextSecondary
import com.motorider.ui.theme.CardBackground

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PerformanceStatsCard(
    stats: PerformanceStats,
    useMph: Boolean,
    onResetTopSpeed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PERFORMANCE",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                
                if (stats.isRunActive) {
                    // Pulsing "RUNNING" indicator
                    val infiniteTransition = rememberInfiniteTransition(label = "run_pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    
                    Text(
                        text = "READY / RUNNING",
                        color = NeonRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(alpha)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Top Speed – long press to reset
                Box(
                    modifier = Modifier.combinedClickable(
                        onClick = { /* normal tap – no-op */ },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResetTopSpeed()
                            Toast.makeText(context, "Top speed reset", Toast.LENGTH_SHORT).show()
                        }
                    )
                ) {
                    StatBlock(
                        label = "TOP SPEED",
                        value = if (stats.topSpeedKmh > 0) {
                            val speed = if (useMph) stats.topSpeedKmh * 0.621371 else stats.topSpeedKmh
                            String.format("%.0f", speed)
                        } else {
                            "--"
                        },
                        subLabel = if (useMph) "mph" else "km/h"
                    )
                }
                
                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(TextSecondary.copy(alpha = 0.2f))
                )
                
                // 1/4 Mile Stats
                StatBlock(
                    label = "1/4 MILE",
                    value = formatTime(stats.lastQuarterMileTimeSeconds),
                    subLabel = if (stats.lastQuarterMileTrapSpeedKmh != null) {
                        val speed = if (useMph) stats.lastQuarterMileTrapSpeedKmh * 0.621371 else stats.lastQuarterMileTrapSpeedKmh
                        val unit = if (useMph) "mph" else "km/h"
                        "@ ${String.format("%.0f", speed)} $unit"
                    } else "seconds"
                )
            }
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    subLabel: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = value,
            color = NeonCyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = subLabel,
            color = TextSecondary.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

private fun formatTime(seconds: Double?): String {
    return if (seconds != null) {
        String.format("%.2f", seconds)
    } else {
        "--.--"
    }
}
