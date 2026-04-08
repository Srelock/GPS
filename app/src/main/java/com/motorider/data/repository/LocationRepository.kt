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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val roadSpeedRepository: RoadSpeedRepository,
    private val speedCameraRepository: SpeedCameraRepository,
    private val routeRepository: RouteRepository
) {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    private val _currentSpeed = MutableStateFlow(0.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow() // km/h
    
    // Auto-detected speed limit from OSM
    private val _roadSpeedLimit = MutableStateFlow<Double?>(null)
    val roadSpeedLimit: StateFlow<Double?> = _roadSpeedLimit.asStateFlow()

    // Nearest speed camera distance in meters (null = no cameras nearby)
    private val _nearestCameraDistance = MutableStateFlow<Float?>(null)
    val nearestCameraDistance: StateFlow<Float?> = _nearestCameraDistance.asStateFlow()
    
    
    private var lastRoadLimitFetchTime = 0L
    private var lastRoadLimitFetchLocation: Location? = null

    private var lastCameraFetchTime = 0L
    private var lastCameraFetchLocation: Location? = null
    
    private val _currentHeading = MutableStateFlow(0f)
    val currentHeading: StateFlow<Float> = _currentHeading.asStateFlow() // degrees
    private val scope = MainScope()

    companion object {
        private const val INTERVAL_MOVING_MS = 1000L
        private const val ROAD_LIMIT_FETCH_COOLDOWN_MS = 15000L // 15 seconds min
        private const val ROAD_LIMIT_MIN_DISTANCE_M = 50.0      // 50 meters min
        private const val SPEED_THRESHOLD_KMH = 5.0

        // Speed camera thresholds
        private const val CAMERA_FETCH_COOLDOWN_MS = 30000L     // 30 seconds
        private const val CAMERA_FETCH_MIN_DISTANCE_M = 500.0   // 500 meters
        private const val CAMERA_ALERT_RANGE_M = 500f           // Show alert within 500m
    }
    
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(highAccuracy: Boolean = true): Flow<Location> = callbackFlow {
        val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        
        val locationRequest = LocationRequest.Builder(priority, INTERVAL_MOVING_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MOVING_MS / 2)
            .build()
        
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    _currentLocation.value = location
                    
                    val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
                    _currentSpeed.value = speedKmh
                    
                    if (location.hasBearing()) _currentHeading.value = location.bearing
                    
                    checkAndFetchRoadSpeed(location)
                    checkAndFetchCameras(location)
                    checkCameraProximity(location)
                    recordRoutePoint(location)
                    trySend(location)
                }
            }
        }
        
        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        
        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    private fun checkAndFetchRoadSpeed(location: Location) {
        val now = System.currentTimeMillis()
        val dt = now - lastRoadLimitFetchTime
        val dist = lastRoadLimitFetchLocation?.distanceTo(location) ?: Float.MAX_VALUE
        
        if (dt > ROAD_LIMIT_FETCH_COOLDOWN_MS && dist > ROAD_LIMIT_MIN_DISTANCE_M) {
            lastRoadLimitFetchTime = now
            lastRoadLimitFetchLocation = location
            
            scope.launch(Dispatchers.IO) {
                val limit = roadSpeedRepository.fetchSpeedLimit(location.latitude, location.longitude)
                if (limit != null) {
                    _roadSpeedLimit.value = limit
                }
            }
        }
    }

    private fun checkAndFetchCameras(location: Location) {
        val now = System.currentTimeMillis()
        val dt = now - lastCameraFetchTime
        val dist = lastCameraFetchLocation?.distanceTo(location) ?: Float.MAX_VALUE

        if (dt > CAMERA_FETCH_COOLDOWN_MS && dist > CAMERA_FETCH_MIN_DISTANCE_M) {
            lastCameraFetchTime = now
            lastCameraFetchLocation = location

            scope.launch(Dispatchers.IO) {
                speedCameraRepository.getCamerasNear(location.latitude, location.longitude)
            }
        }
    }

    private fun checkCameraProximity(location: Location) {
        val nearest = speedCameraRepository.findNearestCamera(
            location.latitude, location.longitude
        )
        _nearestCameraDistance.value = nearest?.let { (_, dist) ->
            if (dist <= CAMERA_ALERT_RANGE_M) dist else null
        }
    }

    private fun recordRoutePoint(location: Location) {
        // Only record if route recording is active
        // This is checked by whether the repository has points being buffered
        // The actual recording state toggle is managed by the ViewModel
        routeRepository.recordPoint(location.latitude, location.longitude)
    }


    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return try {
            val task = fusedLocationClient.lastLocation
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                task.addOnSuccessListener { location -> continuation.resume(location, null) }
                task.addOnFailureListener { exception -> continuation.resume(null, null) }
            }
        } catch (e: Exception) { null }
    }
}

