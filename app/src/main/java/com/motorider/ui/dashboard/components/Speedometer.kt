package com.motorider.ui.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorider.core.alert.SpeedAlertState
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonGreen
import com.motorider.ui.theme.NeonOrange
import com.motorider.ui.theme.NeonRed
import com.motorider.ui.theme.TextPrimary

/**
 * High-Tech 7-Segment Speedometer.
 * Draws custom segments for a 100% reliable digital look.
 */
@Composable
fun Speedometer(
    speedKmh: Double,
    alertState: SpeedAlertState,
    speedLimit: Double,
    roadSpeedLimit: Double? = null,
    maxSpeedInMph: Double = 80.0,
    modifier: Modifier = Modifier
) {
    // Current app architecture uses km/h internally, but we now only show MPH
    val displaySpeedMph = speedKmh * 0.621371
    val speedInt = displaySpeedMph.toInt()

    // Calculate max speed bar in display units
    val maxDisplaySpeed = maxSpeedInMph
    
    // Alert color logic
    val baseColor = when (alertState) {
        SpeedAlertState.NORMAL -> NeonCyan
        SpeedAlertState.APPROACHING -> NeonGreen
        SpeedAlertState.WARNING -> NeonOrange
        SpeedAlertState.CRITICAL -> NeonRed
    }
    
    val hudColor by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(500),
        label = "hudColor"
    )
    
    // Fraction of max speed (0.0 to 1.0)
    val speedFraction = (displaySpeedMph / maxDisplaySpeed).coerceIn(0.0, 1.0).toFloat()
    
    // Animate the bar expansion
    val animatedFraction by animateFloatAsState(
        targetValue = speedFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "barProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            
            // Digital Speed Reading
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Show real road speed limit if available (LEFT)
                roadSpeedLimit?.let { limitKmh ->
                    val displayLimit = limitKmh * 0.621371
                    
                    Surface(
                        modifier = Modifier.size(75.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = androidx.compose.ui.graphics.Color.White,
                        border = androidx.compose.foundation.BorderStroke(4.dp, androidx.compose.ui.graphics.Color.Red),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${Math.round(displayLimit).toInt()}",
                                color = androidx.compose.ui.graphics.Color.Black,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                }

                // 2. Main Speed Digits (RIGHT)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val speedStr = speedInt.toString()
                    val displayStr = if (speedStr.length < 2) speedStr.padStart(2, ' ') else speedStr

                    displayStr.forEach { char ->
                        if (char == ' ') {
                            Spacer(modifier = Modifier.size(width = 60.dp, height = 100.dp))
                        } else {
                            SevenSegmentDigit(
                                digit = char.digitToInt(),
                                color = TextPrimary,
                                modifier = Modifier.size(width = 60.dp, height = 100.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Center-Out Linear Bar
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val totalWidth = size.width
                val barHeight = size.height
                
                // Track Background
                drawRect(
                    color = hudColor.copy(alpha = 0.05f),
                    size = size
                )
                
                // Active Expanding Bar
                val currentBarWidth = totalWidth * animatedFraction
                val startX = center.x - (currentBarWidth / 2)
                
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            hudColor.copy(alpha = 0.3f),
                            hudColor,
                            hudColor.copy(alpha = 0.3f)
                        ),
                        startX = startX,
                        endX = startX + currentBarWidth
                    ),
                    topLeft = Offset(startX, 0f),
                    size = Size(currentBarWidth, barHeight)
                )
                
                // Frame border
                drawRect(
                    color = hudColor.copy(alpha = 0.2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                
                // Center line
                drawLine(
                    color = TextPrimary.copy(alpha = 0.8f),
                    start = Offset(center.x, -5f),
                    end = Offset(center.x, barHeight + 5f),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

/**
 * Draws a single digital 7-segment digit.
 */
@Composable
fun SevenSegmentDigit(
    digit: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val thickness = w * 0.14f
        val gap = 2f
        
        // Segments: a, b, c, d, e, f, g
        val segments = when (digit) {
            0 -> listOf(true, true, true, true, true, true, false)
            1 -> listOf(false, true, true, false, false, false, false)
            2 -> listOf(true, true, false, true, true, false, true)
            3 -> listOf(true, true, true, true, false, false, true)
            4 -> listOf(false, true, true, false, false, true, true)
            5 -> listOf(true, false, true, true, false, true, true)
            6 -> listOf(true, false, true, true, true, true, true)
            7 -> listOf(true, true, true, false, false, false, false)
            8 -> listOf(true, true, true, true, true, true, true)
            9 -> listOf(true, true, true, true, false, true, true)
            else -> listOf(false, false, false, false, false, false, false)
        }

        // Slight slant for that technical look
        drawContext.canvas.skew(-0.06f, 0f)

        // a (top)
        drawSegment(Offset(thickness, 0f), Size(w - 2 * thickness, thickness), segments[0], color)
        // b (top right)
        drawSegment(Offset(w - thickness, thickness), Size(thickness, h / 2 - thickness - gap), segments[1], color)
        // c (bottom right)
        drawSegment(Offset(w - thickness, h / 2 + gap), Size(thickness, h / 2 - thickness - gap), segments[2], color)
        // d (bottom)
        drawSegment(Offset(thickness, h - thickness), Size(w - 2 * thickness, thickness), segments[3], color)
        // e (bottom left)
        drawSegment(Offset(0f, h / 2 + gap), Size(thickness, h / 2 - thickness - gap), segments[4], color)
        // f (top left)
        drawSegment(Offset(0f, thickness), Size(thickness, h / 2 - thickness - gap), segments[5], color)
        // g (middle)
        drawSegment(Offset(thickness, h / 2 - thickness / 2), Size(w - 2 * thickness, thickness), segments[6], color)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegment(
    offset: Offset,
    size: Size,
    isActive: Boolean,
    color: Color
) {
    // Subdued background segments (ghost effect)
    val alpha = if (isActive) 1f else 0.02f // Subtler ghost segments
    
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = offset,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
    )
}
