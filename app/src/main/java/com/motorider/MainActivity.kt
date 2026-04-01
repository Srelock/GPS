package com.motorider

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.composable
import com.motorider.service.LocationForegroundService
import com.motorider.ui.dashboard.DashboardScreen
import com.motorider.ui.dashboard.DashboardViewModel
import com.motorider.ui.theme.MotoRiderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
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
                                onNavigateToHistory = { navController.navigate("history") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        
                        composable("history") {
                            com.motorider.ui.history.RideHistoryScreen(
                                onBackClick = { navController.popBackStack() }
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
        val serviceIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_START
            putExtra(LocationForegroundService.EXTRA_SPEED_LIMIT, 120.0)
            putExtra(LocationForegroundService.EXTRA_ENABLE_HAPTICS, true)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val serviceIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = LocationForegroundService.ACTION_STOP
            }
            startService(serviceIntent)
        }
    }
}
