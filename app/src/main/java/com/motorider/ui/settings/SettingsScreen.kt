package com.motorider.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motorider.ui.dashboard.DashboardViewModel
import com.motorider.ui.theme.CardBackground
import com.motorider.ui.theme.DarkBackground
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonGreen
import com.motorider.ui.theme.NeonOrange
import com.motorider.ui.theme.TextPrimary
import com.motorider.ui.theme.TextSecondary
import kotlin.math.roundToInt

import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    onBackClick: () -> Unit
) {
    val speedLimit by viewModel.speedLimit.collectAsState()
    val hapticsEnabled by viewModel.enableHapticAlerts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Speed Limit Alert removed as requested

            // Haptic Feedback Setting
            SettingCard(title = "Haptic Feedback") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, null, tint = if (hapticsEnabled) NeonGreen else TextSecondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Vibration Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (hapticsEnabled) "On" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = { viewModel.setHapticAlertsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = CardBackground,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CardBackground
                        )
                    )
                }
            }
            
            // Auto-Record Trips Setting
            val autoRecordEnabled by viewModel.autoRecordEnabled.collectAsState()
            SettingCard(title = "Auto-Record Trips") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Record when moving",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Automatically start/stop trips based on movement",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    
                    Switch(
                        checked = autoRecordEnabled,
                        onCheckedChange = { viewModel.setAutoRecordEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = CardBackground,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CardBackground
                        )
                    )
                }
            }
            
            // Units Setting
            val useMph by viewModel.useMph.collectAsState()
            SettingCard(title = "Units") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (useMph) "Miles per hour" else "Kilometers per hour",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (useMph) "Imperial (mph, mi)" else "Metric (km/h, km)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    
                    Switch(
                        checked = useMph,
                        onCheckedChange = { viewModel.setUseMph(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonOrange,
                            checkedTrackColor = CardBackground,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CardBackground
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}
