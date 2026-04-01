package com.motorider.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motorider.data.model.RelativeWind
import com.motorider.data.model.WindAlertLevel
import com.motorider.data.model.WindType
import com.motorider.ui.theme.CardBackground
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonGreen
import com.motorider.ui.theme.NeonOrange
import com.motorider.ui.theme.NeonRed
import com.motorider.ui.theme.TextSecondary

/**
 * Wind indicator component showing relative wind direction and speed.
 * 
 * Displays:
 * - Directional arrow showing wind relative to rider
 * - Wind type label (Headwind, Crosswind, etc.)
 * - Wind speed and gust speed
 * - Color-coded alert level
 */
@Composable
fun WindIndicator(
    wind: RelativeWind?,
    modifier: Modifier = Modifier
) {
    if (wind == null) {
        WindIndicatorEmpty(modifier)
        return
    }
    
    val alertColor = when (wind.alertLevel) {
        WindAlertLevel.NORMAL -> NeonCyan
        WindAlertLevel.WARNING -> NeonOrange
        WindAlertLevel.DANGER -> NeonRed
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Wind direction arrow
            WindArrow(
                relativeAngle = wind.relativeAngle.toFloat(),
                color = alertColor,
                modifier = Modifier.size(64.dp)
            )
            
            // Wind info
            Column {
                // Wind type label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (wind.alertLevel == WindAlertLevel.DANGER) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = NeonRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    Text(
                        text = when (wind.type) {
                            WindType.HEADWIND -> "HEADWIND"
                            WindType.TAILWIND -> "TAILWIND"
                            WindType.CROSSWIND_LEFT -> "CROSSWIND ←"
                            WindType.CROSSWIND_RIGHT -> "CROSSWIND →"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = alertColor
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Wind speed
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${wind.effectiveSpeed.toInt()}",
                        style = MaterialTheme.typography.displaySmall, // Larger font
                        fontWeight = FontWeight.Bold,
                        color = alertColor
                    )
                    Text(
                        text = " km/h",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    // Show gusts if present
                    wind.gustSpeed?.let { gust ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(gusts ${gust.toInt()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (gust >= 60) NeonRed else TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Directional arrow indicating wind direction relative to rider.
 */
@Composable
private fun WindArrow(
    relativeAngle: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Rotate so 0° points up (towards rider)
    val rotation by animateFloatAsState(
        targetValue = relativeAngle + 180f, // Point towards wind source
        animationSpec = tween(300),
        label = "wind_rotation"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Minimal Neon Ring
        Canvas(modifier = Modifier.size(64.dp)) {
            // Outer glow ring
            drawCircle(
                color = color.copy(alpha = 0.1f),
                radius = size.minDimension / 2
            )
            // Thin accent ring
            drawCircle(
                color = color.copy(alpha = 0.3f),
                radius = size.minDimension / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
        
        // Sharp Arrow
        Canvas(
            modifier = Modifier
                .size(36.dp)
                .rotate(rotation)
        ) {
            val path = Path().apply {
                // Sharper futuristic arrow
                moveTo(size.width / 2, 0f) // Tip
                lineTo(size.width, size.height) // Right back
                lineTo(size.width / 2, size.height * 0.7f) // Inner notch
                lineTo(0f, size.height) // Left back
                close()
            }
            
            drawPath(path, color, style = Fill)
            
            // Inner shadow/detail for 3D look
            drawPath(
                path,
                Color.Black.copy(alpha = 0.2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Empty state when no wind data is available.
 */
@Composable
private fun WindIndicatorEmpty(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Air,
                contentDescription = "Wind",
                tint = TextSecondary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "No wind data",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
