package com.motorider

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.composable
import com.motorider.data.repository.SettingsRepository
import com.motorider.service.LocationForegroundService
import com.motorider.service.OverlayService
import com.motorider.ui.dashboard.DashboardScreen
import com.motorider.ui.dashboard.DashboardViewModel
import com.motorider.ui.theme.MotoRiderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (fineLocationGranted || coarseLocationGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            } else {
                startLocationService()
            }
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        startLocationService()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on while app is running - essential for motorcycle dashboard
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setupOverlayCollector()

        setContent {
            MotoRiderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = androidx.navigation.compose.rememberNavController()
                    
                    androidx.navigation.compose.NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            val viewModel = androidx.hilt.navigation.compose.hiltViewModel<com.motorider.ui.dashboard.DashboardViewModel>()
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        
                        composable("settings") {
                            val viewModel = androidx.hilt.navigation.compose.hiltViewModel<com.motorider.ui.dashboard.DashboardViewModel>()
                            com.motorider.ui.settings.SettingsScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
        
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Re-trigger overlay check in case user just granted permission
        lifecycleScope.launch {
            val enabled = settingsRepository.overlayEnabled.first()
            updateOverlayService(enabled)
        }
    }

    private fun updateOverlayService(enabled: Boolean) {
        val canDraw = Settings.canDrawOverlays(this)
        if (enabled && canDraw) {
            if (OverlayService.isOverlayActive) return
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } else if (!enabled) {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(intent)
        }
    }

    private fun setupOverlayCollector() {
        lifecycleScope.launch {
            settingsRepository.overlayEnabled.collectLatest { enabled ->
                updateOverlayService(enabled)
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            } else {
                startLocationService()
            }
        } else {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startLocationService()
            }
        }
    }
    
    private fun startLocationService() {
        lifecycleScope.launch {
            if (LocationForegroundService.isTrackingActive) {
                return@launch
            }
            val limit = settingsRepository.speedLimitKmh.first()
            val haptics = settingsRepository.hapticEnabled.first()
            val serviceIntent = Intent(this@MainActivity, LocationForegroundService::class.java).apply {
                action = LocationForegroundService.ACTION_START
                putExtra(LocationForegroundService.EXTRA_SPEED_LIMIT, limit)
                putExtra(LocationForegroundService.EXTRA_ENABLE_HAPTICS, haptics)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
    
    // Removed onDestroy stopSelf logic to allow background measurement during overlay use
}
