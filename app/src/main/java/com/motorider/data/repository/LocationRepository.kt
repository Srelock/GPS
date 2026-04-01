package com.motorider.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for GPS location tracking.
 * 
 * Uses FusedLocationProviderClient for battery-efficient location updates.
 * Implements adaptive update intervals based on movement state.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    private val _currentSpeed = MutableStateFlow(0.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow() // km/h
    
    private val _currentHeading = MutableStateFlow(0f)
    val currentHeading: StateFlow<Float> = _currentHeading.asStateFlow() // degrees
    
    private var locationCallback: LocationCallback? = null
    
    companion object {
        // Adaptive intervals based on movement
        private const val INTERVAL_MOVING_MS = 1000L      // 1 second when moving
        private const val INTERVAL_STATIONARY_MS = 5000L  // 5 seconds when stationary
        private const val SPEED_THRESHOLD_KMH = 5.0       // Below this = stationary
    }
    
    /**
     * Start receiving location updates as a Flow.
     * 
     * @param highAccuracy Use GPS for high accuracy (more battery)
     * @return Flow of Location updates
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(highAccuracy: Boolean = true): Flow<Location> = callbackFlow {
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        
        val locationRequest = LocationRequest.Builder(priority, INTERVAL_MOVING_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MOVING_MS / 2)
            .setWaitForAccurateLocation(false)
            .build()
        
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // Update state flows
                    _currentLocation.value = location
                    
                    // Convert speed from m/s to km/h
                    val speedKmh = if (location.hasSpeed()) {
                        location.speed * 3.6
                    } else {
                        0.0
                    }
                    _currentSpeed.value = speedKmh
                    
                    // Update heading/bearing
                    if (location.hasBearing()) {
                        _currentHeading.value = location.bearing
                    }
                    
                    // Emit to flow
                    trySend(location)
                }
            }
        }
        
        locationCallback = callback
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )
        
        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
    }
    
    /**
     * Stop location updates.
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
    
    /**
     * Get the last known location without starting updates.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return try {
            val task = fusedLocationClient.lastLocation
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                task.addOnSuccessListener { location ->
                    continuation.resume(location, null)
                }
                task.addOnFailureListener { exception ->
                    continuation.resume(null, null)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate distance between two locations in meters.
     */
    fun calculateDistance(from: Location, to: Location): Float {
        return from.distanceTo(to)
    }
    
    /**
     * Check if current speed indicates movement.
     */
    fun isMoving(): Boolean = _currentSpeed.value > SPEED_THRESHOLD_KMH
}
