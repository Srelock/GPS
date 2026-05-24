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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val roadSpeedRepository: RoadSpeedRepository,
    private val speedCameraRepository: SpeedCameraRepository,
    private val routeRepository: RouteRepository,
    private val settingsRepository: SettingsRepository
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

    /** One FusedLocationProvider callback shared by all collectors (avoids stacked GPS listeners). */
    private val locationSubscriberCount = AtomicInteger(0)
    private var fusedLocationCallback: LocationCallback? = null
    private var previousLocation: Location? = null
    private var previousSampleWallTimeMs: Long = 0L
    private val locationUpdates = MutableSharedFlow<Location>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    companion object {
        private const val INTERVAL_MOVING_MS = 1000L
        /** Emulator route playback often omits [Location.speed]; derive from movement instead. */
        private const val MIN_DERIVED_SPEED_DT_MS = 250L
        private const val MIN_DERIVED_SPEED_DISTANCE_M = 1.0
        private const val MAX_REASONABLE_SPEED_KMH = 350.0
        private const val ROAD_LIMIT_FETCH_COOLDOWN_MS = 15000L // 15 seconds min
        private const val ROAD_LIMIT_MIN_DISTANCE_M = 50.0      // 50 meters min
        private const val SPEED_THRESHOLD_KMH = 5.0

        // Speed camera thresholds
        private const val CAMERA_FETCH_COOLDOWN_MS = 30000L     // 30 seconds
        private const val CAMERA_FETCH_MIN_DISTANCE_M = 500.0   // 500 meters
        private const val CAMERA_ALERT_RANGE_M = 150f           // Show alert within 150m
    }
    
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(highAccuracy: Boolean = true): Flow<Location> = callbackFlow {
        registerLocationSubscriber(highAccuracy)
        val collectJob = scope.launch {
            locationUpdates.collect { location ->
                trySend(location)
            }
        }
        awaitClose {
            collectJob.cancel()
            unregisterLocationSubscriber()
        }
    }

    /** Stops the shared GPS callback when no collectors remain (e.g. foreground service stopped). */
    @SuppressLint("MissingPermission")
    fun stopAllLocationUpdates() {
        locationSubscriberCount.set(0)
        removeFusedLocationCallback()
        previousLocation = null
        previousSampleWallTimeMs = 0L
    }

    @SuppressLint("MissingPermission")
    private fun registerLocationSubscriber(highAccuracy: Boolean) {
        if (locationSubscriberCount.getAndIncrement() > 0) return

        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val locationRequest = LocationRequest.Builder(priority, INTERVAL_MOVING_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MOVING_MS / 2)
            .setMaxUpdateDelayMillis(INTERVAL_MOVING_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    dispatchLocation(location)
                }
            }

            override fun onLocationAvailability(availability: com.google.android.gms.location.LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    android.util.Log.w("LocationRepository", "Location is currently unavailable")
                }
            }
        }
        fusedLocationCallback = callback
        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

        // Prime UI with last known fix (helps emulator single points before route starts).
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let { dispatchLocation(it) }
            }
    }

    @SuppressLint("MissingPermission")
    private fun unregisterLocationSubscriber() {
        if (locationSubscriberCount.decrementAndGet() > 0) return
        removeFusedLocationCallback()
    }

    @SuppressLint("MissingPermission")
    private fun removeFusedLocationCallback() {
        fusedLocationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        fusedLocationCallback = null
    }

    private fun dispatchLocation(location: Location) {
        _currentLocation.value = location

        val speedKmh = resolveSpeedKmh(location)
        _currentSpeed.value = speedKmh

        if (location.hasBearing()) _currentHeading.value = location.bearing

        checkAndFetchRoadSpeed(location)
        checkAndFetchCameras(location)
        checkCameraProximity(location)
        recordRoutePoint(location)
        locationUpdates.tryEmit(location)
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
        val alertRange = settingsRepository.speedCameraAlertDistance.value.toFloat()
        _nearestCameraDistance.value = nearest?.let { (_, dist) ->
            if (dist <= alertRange) dist else null
        }
    }

    private fun recordRoutePoint(location: Location) {
        routeRepository.recordPoint(location.latitude, location.longitude)
    }

    /**
     * Prefer GPS-reported speed; fall back to distance over time (required for many emulators).
     */
    private fun resolveSpeedKmh(location: Location): Double {
        if (location.hasSpeed() && location.speed > 0f) {
            resetSpeedSample(location)
            return location.speed * 3.6
        }

        val nowMs = System.currentTimeMillis()
        val prev = previousLocation
        if (prev == null) {
            resetSpeedSample(location, nowMs)
            return 0.0
        }

        val dtMs = nowMs - previousSampleWallTimeMs
        if (dtMs < MIN_DERIVED_SPEED_DT_MS) {
            return _currentSpeed.value
        }

        val distanceM = prev.distanceTo(location).toDouble()
        resetSpeedSample(location, nowMs)

        if (distanceM < MIN_DERIVED_SPEED_DISTANCE_M) {
            return 0.0
        }

        val speedKmh = (distanceM / (dtMs / 1000.0)) * 3.6
        return if (speedKmh in 0.0..MAX_REASONABLE_SPEED_KMH) speedKmh else _currentSpeed.value
    }

    private fun resetSpeedSample(location: Location, wallTimeMs: Long = System.currentTimeMillis()) {
        previousLocation = Location(location)
        previousSampleWallTimeMs = wallTimeMs
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

