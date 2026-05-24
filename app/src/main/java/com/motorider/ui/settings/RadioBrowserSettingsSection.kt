package com.motorider.ui.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.motorider.data.repository.RadioBrowserStation
import com.motorider.service.RadioPlayerService
import com.motorider.ui.dashboard.DashboardViewModel
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.TextPrimary
import com.motorider.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun RadioBrowserSettingsSection(
    viewModel: DashboardViewModel,
    savedStationIds: Set<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val results by viewModel.radioBrowseResults.collectAsState()
    val loading by viewModel.radioBrowseLoading.collectAsState()
    val error by viewModel.radioBrowseError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadRadioBrowserUk()
    }

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) return@LaunchedEffect
        delay(450)
        if (searchQuery.trim() == q) {
            viewModel.searchRadioBrowser(q)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Browse stations (Radio Browser)",
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Thousands of free internet radio streams. Add favourites for the HUD.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search stations") },
            placeholder = { Text("e.g. capital, kiss, jazz") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TagChip(label = "UK top") { viewModel.loadRadioBrowserUk() }
            TagChip(label = "Rock") { viewModel.loadRadioBrowserByTag("rock") }
            TagChip(label = "Dance") { viewModel.loadRadioBrowserByTag("dance") }
            TagChip(label = "News") { viewModel.loadRadioBrowserByTag("news") }
            TagChip(label = "Metal") { viewModel.loadRadioBrowserByTag("metal") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    color = NeonCyan,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Loading stations…", color = TextSecondary)
            }
        }

        error?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (!loading && results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${results.size} stations",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            results.take(25).forEach { station ->
                RadioBrowserStationRow(
                    station = station,
                    isSaved = station.id in savedStationIds,
                    onPlay = {
                        val intent = Intent(context, RadioPlayerService::class.java).apply {
                            action = RadioPlayerService.ACTION_PLAY
                            putExtra(RadioPlayerService.EXTRA_STATION_NAME, station.name)
                            putExtra(RadioPlayerService.EXTRA_STATION_URL, station.url)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, intent)
                        } else {
                            context.startService(intent)
                        }
                    },
                    onAdd = {
                        viewModel.addRadioBrowserStationToFavourites(station)
                        Toast.makeText(context, "Added ${station.name}", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (results.size > 25) {
                Text(
                    text = "Refine search to see more",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TagChip(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, color = NeonCyan)
    }
}

@Composable
private fun RadioBrowserStationRow(
    station: RadioBrowserStation,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onAdd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = station.name,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = station.subtitle,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Text("Play")
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.weight(1f),
                enabled = !isSaved
            ) {
                Text(if (isSaved) "Saved" else "Add ★")
            }
        }
    }
}
