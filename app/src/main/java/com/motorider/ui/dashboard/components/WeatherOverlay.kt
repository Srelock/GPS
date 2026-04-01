package com.motorider.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motorider.data.model.PrecipitationType
import com.motorider.data.model.WeatherData
import com.motorider.ui.theme.CardBackground
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonOrange
import com.motorider.ui.theme.TextSecondary

/**
 * Weather overlay showing current conditions relevant to motorcycle riding.
 * 
 * Displays:
 * - Temperature and feels-like
 * - Precipitation type and intensity
 * - Visibility
 */
@Composable
fun WeatherOverlay(
    weather: WeatherData?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        when {
            isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading weather...",
                        color = TextSecondary
                    )
                }
            }
            
            weather == null -> {
                Text(
                    text = "Weather data unavailable",
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            else -> {
                WeatherContent(weather)
            }
        }
    }
}

@Composable
private fun WeatherContent(weather: WeatherData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Temperature
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Thermostat,
                contentDescription = "Temperature",
                tint = NeonCyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${weather.temperature.toInt()}°",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
            Text(
                text = "feels ${weather.feelsLike.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        // Conditions
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Conditions",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = weather.weatherDescription.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            
            // City Name
            weather.locationName?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan, 
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        // Precipitation
        if (weather.precipitationType != PrecipitationType.NONE) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = "Precipitation",
                    tint = NeonOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = weather.precipitationType.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = NeonOrange
                )
                Text(
                    text = "${weather.precipitationIntensity} mm/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        
        // Visibility
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Visibility",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatVisibility(weather.visibility),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (weather.visibility < 1000) NeonOrange else TextSecondary
            )
            Text(
                text = "visibility",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

/**
 * Format visibility in meters to a readable string.
 */
private fun formatVisibility(meters: Int): String {
    return when {
        meters >= 10000 -> ">10km"
        meters >= 1000 -> "${meters / 1000}km"
        else -> "${meters}m"
    }
}
