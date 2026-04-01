package com.motorider.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.motorider.MainActivity
import com.motorider.R
import com.motorider.core.alert.HapticSpeedAlertManager
import com.motorider.data.entity.ActiveTrip
import com.motorider.data.entity.RoutePoint
import com.motorider.data.repository.LocationRepository
import com.motorider.data.repository.RouteRepository
import com.motorider.data.repository.SettingsRepository
import com.motorider.data.repository.WeatherRepository
import com.motorider.core.performance.PerformanceTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service for continuous GPS tracking.
 * 
 * This service maintains location updates even when the screen is off,
 * which is essential for motorcycle GPS dashboards where riders need
 * hands-free operation.
 * 
 * Features:
 * - Continuous GPS tracking with partial wake lock
 * - Active trip recording with route point collection
 * - Speed monitoring with haptic alerts
 * - Weather updates based on location changes
 */
@AndroidEntryPoint
class LocationForegroundService : Service() {
    
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var weatherRepository: WeatherRepository
    @Inject lateinit var routeRepository: RouteRepository
    @Inject lateinit var hapticAlertManager: HapticSpeedAlertManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var performanceTracker: PerformanceTracker
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var locationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val binder = LocalBinder()
    
    // Trip state
    private val _activeTrip = MutableStateFlow<ActiveTrip?>(null)
    val activeTrip: StateFlow<ActiveTrip?> = _activeTrip.asStateFlow()
    
    private val _isTripActive = MutableStateFlow(false)
    val isTripActive: StateFlow<Boolean> = _isTripActive.asStateFlow()
    
    // Speed limit for alerts (configurable)
    private var speedLimitKmh: Double = 120.0
    private var enableHapticAlerts: Boolean = true
    
    private var lastLocation: Location? = null
    
    // Auto-detection state
    private val _isAutoRecording = MutableStateFlow(false)
    val isAutoRecording: StateFlow<Boolean> = _isAutoRecording.asStateFlow()
    private var movingStartTime: Long = 0
    private var stoppedStartTime: Long = 0
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "motorider_location_channel"
        
        // Auto-detection thresholds
        private const val START_SPEED_THRESHOLD_KMH = 5.0  // Start when > 5 km/h
        private const val STOP_SPEED_THRESHOLD_KMH = 3.0   // Stop when < 3 km/h
        private const val START_DELAY_MS = 2_000L          // 2 seconds of movement
        private const val STOP_DELAY_MS = 60_000L          // 60 seconds of stillness
        
        // Actions
        const val ACTION_START = "com.motorider.action.START"
        const val ACTION_STOP = "com.motorider.action.STOP"
        const val ACTION_START_TRIP = "com.motorider.action.START_TRIP"
        const val ACTION_STOP_TRIP = "com.motorider.action.STOP_TRIP"
        
        // Extras
        const val EXTRA_SPEED_LIMIT = "speed_limit"
        const val EXTRA_ENABLE_HAPTICS = "enable_haptics"
    }
    

    
    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService = this@LocationForegroundService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                speedLimitKmh = intent.getDoubleExtra(EXTRA_SPEED_LIMIT, 120.0)
                enableHapticAlerts = intent.getBooleanExtra(EXTRA_ENABLE_HAPTICS, true)
                startForegroundTracking()
            }
            ACTION_STOP -> {
                stopForegroundTracking()
            }
            ACTION_START_TRIP -> {
                startTrip()
            }
            ACTION_STOP_TRIP -> {
                stopTrip()
            }
        }
        
        return START_STICKY
    }
    
    /**
     * Start foreground service with notification and GPS tracking.
     */
    private fun startForegroundTracking() {
        // Acquire wake lock to keep CPU running
        acquireWakeLock()
        
        // Start foreground with notification
        val notification = createNotification("Starting...")
        startForeground(NOTIFICATION_ID, notification)
        
        // Start location updates
        locationJob = serviceScope.launch {
            locationRepository.startLocationUpdates(highAccuracy = true).collect { location ->
                processLocationUpdate(location)
            }
        }
    }
    
    /**
     * Process each location update.
     */
    private suspend fun processLocationUpdate(location: Location) {
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        
        // Check speed alerts
        if (enableHapticAlerts) {
            hapticAlertManager.checkSpeed(speedKmh, speedLimitKmh)
        }
        
        // Update performance tracker
        performanceTracker.processLocation(location, speedKmh)
        
        // Auto-detection logic
        if (settingsRepository.autoRecordEnabled.value) {
            checkAutoDetection(speedKmh)
        }
        
        // Record point if trip is active
        if (_isTripActive.value) {
            recordTripPoint(location, speedKmh)
        }
        
        // Update weather if needed (handled by repository caching)
        weatherRepository.fetchWeather(location)
        
        // Update notification
        updateNotification(speedKmh)
        
        lastLocation = location
    }
    
    /**
     * Check if we should auto-start or auto-stop trip based on speed.
     */
    private fun checkAutoDetection(speedKmh: Double) {
        val now = System.currentTimeMillis()
        
        if (!_isTripActive.value) {
            // Not recording - check if we should start
            if (speedKmh > START_SPEED_THRESHOLD_KMH) {
                if (movingStartTime == 0L) {
                    movingStartTime = now
                } else if (now - movingStartTime >= START_DELAY_MS) {
                    // Sustained movement detected - auto-start trip
                    startAutoTrip()
                    movingStartTime = 0
                }
                stoppedStartTime = 0
            } else {
                movingStartTime = 0
            }
        } else if (_isAutoRecording.value) {
            // Auto-recording active - check if we should stop
            if (speedKmh < STOP_SPEED_THRESHOLD_KMH) {
                if (stoppedStartTime == 0L) {
                    stoppedStartTime = now
                } else if (now - stoppedStartTime >= STOP_DELAY_MS) {
                    // Sustained stillness detected - auto-stop trip
                    stopAutoTrip()
                    stoppedStartTime = 0
                }
                movingStartTime = 0
            } else {
                stoppedStartTime = 0
            }
        }
    }
    
    /**
     * Auto-start a new trip with generated name.
     */
    private fun startAutoTrip() {
        val dateFormat = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        val tripName = "Ride " + dateFormat.format(java.util.Date())
        _activeTrip.value = ActiveTrip()
        _isTripActive.value = true
        _isAutoRecording.value = true
        updateNotification(locationRepository.currentSpeed.value, tripActive = true)
    }
    
    /**
     * Auto-stop trip and save with generated name.
     */
    private fun stopAutoTrip() {
        val trip = _activeTrip.value
        
        if (trip != null && trip.points.isNotEmpty()) {
            val dateFormat = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
            val tripName = "Ride " + dateFormat.format(java.util.Date(trip.startTime))
            
            serviceScope.launch {
                routeRepository.saveRoute(
                    name = tripName,
                    points = trip.points.toList(),
                    startTime = trip.startTime,
                    endTime = System.currentTimeMillis(),
                    totalDistanceMeters = trip.totalDistanceMeters,
                    maxSpeedKmh = trip.maxSpeedKmh,
                    avgSpeedKmh = trip.avgSpeedKmh
                )
            }
        }
        
        _activeTrip.value = null
        _isTripActive.value = false
        _isAutoRecording.value = false
        updateNotification(locationRepository.currentSpeed.value, tripActive = false)
    }
    
    /**
     * Record a GPS point to the active trip.
     */
    private fun recordTripPoint(location: Location, speedKmh: Double) {
        val trip = _activeTrip.value ?: return
        
        val point = RoutePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speedKmh = speedKmh,
            timestamp = System.currentTimeMillis(),
            accuracy = location.accuracy
        )
        
        // Calculate distance from last point
        val distanceFromLast = lastLocation?.let { location.distanceTo(it).toDouble() } ?: 0.0
        
        trip.addPoint(point, distanceFromLast)
        _activeTrip.value = trip
    }
    
    /**
     * Start a new trip recording.
     */
    fun startTrip() {
        _activeTrip.value = ActiveTrip()
        _isTripActive.value = true
        updateNotification(locationRepository.currentSpeed.value, tripActive = true)
    }
    
    /**
     * Stop trip recording and save to database.
     */
    fun stopTrip(tripName: String = "My Ride") {
        val trip = _activeTrip.value
        
        if (trip != null && trip.points.isNotEmpty()) {
            serviceScope.launch {
                routeRepository.saveRoute(
                    name = tripName,
                    points = trip.points.toList(),
                    startTime = trip.startTime,
                    endTime = System.currentTimeMillis(),
                    totalDistanceMeters = trip.totalDistanceMeters,
                    maxSpeedKmh = trip.maxSpeedKmh,
                    avgSpeedKmh = trip.avgSpeedKmh
                )
            }
        }
        
        _activeTrip.value = null
        _isTripActive.value = false
        updateNotification(locationRepository.currentSpeed.value, tripActive = false)
    }
    
    /**
     * Stop foreground tracking completely.
     */
    private fun stopForegroundTracking() {
        locationJob?.cancel()
        hapticAlertManager.stopAlerts()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Update speed limit setting.
     */
    fun setSpeedLimit(limitKmh: Double) {
        speedLimitKmh = limitKmh
    }
    
    /**
     * Toggle haptic alerts.
     */
    fun setHapticAlertsEnabled(enabled: Boolean) {
        enableHapticAlerts = enabled
        if (!enabled) {
            hapticAlertManager.stopAlerts()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous GPS tracking for motorcycle dashboard"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification(speedKmh: Double, tripActive: Boolean = _isTripActive.value) {
        val tripStatus = if (tripActive) {
            val trip = _activeTrip.value
            " | Recording: ${String.format("%.1f", (trip?.totalDistanceMeters ?: 0.0) / 1000)} km"
        } else ""
        
        val contentText = "Speed: ${speedKmh.toInt()} km/h$tripStatus"
        
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MotoRider::LocationWakeLock"
        ).apply {
            acquire()
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseWakeLock()
    }
}
