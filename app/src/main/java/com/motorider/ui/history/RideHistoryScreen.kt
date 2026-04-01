package com.motorider.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motorider.data.entity.RouteEntity
import java.text.SimpleDateFormat
import java.util.*

// Neon color palette
private val NeonCyan = Color(0xFF00FFFF)
private val NeonPurple = Color(0xFFBF00FF)
private val DarkBackground = Color(0xFF0A0A0F)
private val CardBackground = Color(0xFF151520)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(
    viewModel: RideHistoryViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val routes by viewModel.routes.collectAsState()
    val useMph by viewModel.useMph.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Past Rides",
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (routes.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "🏍️",
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No rides yet",
                        color = Color.Gray,
                        fontSize = 18.sp
                    )
                    Text(
                        "Your trips will appear here",
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(routes, key = { it.id }) { route ->
                    RideCard(
                        route = route,
                        useMph = useMph,
                        onDelete = { viewModel.deleteRoute(route.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RideCard(
    route: RouteEntity,
    useMph: Boolean,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Ride?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.1f),
                            NeonCyan.copy(alpha = 0.1f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header row with name and delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            route.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatDate(route.startTime),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        value = formatDistance(route.distanceKm, useMph),
                        label = "Distance"
                    )
                    StatItem(
                        value = route.formattedDuration,
                        label = "Duration"
                    )
                    StatItem(
                        value = formatSpeed(route.maxSpeedKmh, useMph),
                        label = "Max Speed"
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = NeonCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDistance(km: Double, useMph: Boolean): String {
    return if (useMph) {
        String.format("%.1f mi", km * 0.621371)
    } else {
        String.format("%.1f km", km)
    }
}

private fun formatSpeed(kmh: Double, useMph: Boolean): String {
    return if (useMph) {
        String.format("%.0f mph", kmh * 0.621371)
    } else {
        String.format("%.0f km/h", kmh)
    }
}
