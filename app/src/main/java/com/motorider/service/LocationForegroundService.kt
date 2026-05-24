package com.motorider.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.motorider.MainActivity
import com.motorider.R
import com.motorider.core.alert.HapticSpeedAlertManager
import com.motorider.data.repository.LocationRepository
import com.motorider.data.repository.SettingsRepository
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
    @Inject lateinit var hapticAlertManager: HapticSpeedAlertManager
    @Inject lateinit var settingsRepository: SettingsRepository
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var locationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val binder = LocalBinder()
    
    // Speed limit for alerts (configurable)
    private var speedLimitKmh: Double = 120.0
    private var enableHapticAlerts: Boolean = true
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "motorider_location_channel"

        // Actions
        const val ACTION_START = "com.motorider.action.START"
        const val ACTION_STOP = "com.motorider.action.STOP"
        
        // Extras
        const val EXTRA_SPEED_LIMIT = "speed_limit"
        const val EXTRA_ENABLE_HAPTICS = "enable_haptics"

        /** True while GPS foreground tracking is active (avoids duplicate starts on relaunch). */
        @Volatile
        var isTrackingActive: Boolean = false
            private set
    }

    private var isTracking = false
    

    
    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService = this@LocationForegroundService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    private var serviceStartTime = 0L
    private var lastLocationTime = 0L
    private var stalledCheckJob: Job? = null
    private var lastNotificationUpdateMs = 0L

    /** Avoid updating the foreground notification on every GPS tick. */
    private val notificationMinIntervalMs = 2000L
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundTracking()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                speedLimitKmh = intent.getDoubleExtra(EXTRA_SPEED_LIMIT, 120.0)
                enableHapticAlerts = intent.getBooleanExtra(EXTRA_ENABLE_HAPTICS, true)
                // Every startForegroundService() delivery must call startForeground(), including
                // duplicate ACTION_START while tracking is already active (common on relaunch).
                promoteToForeground()
                if (isTracking) {
                    return START_NOT_STICKY
                }
                isTracking = true
                isTrackingActive = true
                serviceStartTime = System.currentTimeMillis()
                startForegroundTracking()
            }
            else -> {
                // Process was killed; do not auto-resume (prevents zombie FGS + wake locks).
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        startForegroundTyped(
            NOTIFICATION_ID,
            createNotification(if (isTracking) "GPS tracking active" else "Starting tracking..."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }
    
    /**
     * Start foreground tracking with notification and GPS.
     */
    private fun startForegroundTracking() {
        // Acquire wake lock to keep CPU running
        acquireWakeLock()
        
        // Cancel any existing jobs to prevent stacking
        locationJob?.cancel()
        stalledCheckJob?.cancel()
        
        // Start location updates
        launchLocationJob()
        
        // Periodically check if GPS is stalled
        startStalledCheck()
    }

    private fun launchLocationJob() {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            locationRepository.startLocationUpdates(highAccuracy = true).collect { location ->
                lastLocationTime = System.currentTimeMillis()
                processLocationUpdate(location)
            }
        }
    }

    private fun startStalledCheck() {
        stalledCheckJob?.cancel()
        stalledCheckJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10000) // Check every 10s
                val now = System.currentTimeMillis()
                
                if (lastLocationTime > 0) {
                    // We had a location, but it stopped (30s gap)
                    // 30s is more reasonable for GPS gaps (tunnels, etc.)
                    if (now - lastLocationTime > 30000) {
                        android.util.Log.w("LocationService", "GPS stalled for 30s, restarting...")
                        launchLocationJob()
                    }
                } else {
                    // Never received a location since service start or restart
                    val timeSinceStart = now - serviceStartTime
                    if (timeSinceStart > 30000) {
                        android.util.Log.w("LocationService", "No GPS fix after 30s, retrying...")
                        serviceStartTime = now // Reset start time to give it another 30s
                        launchLocationJob()
                    }
                }
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
        
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateMs >= notificationMinIntervalMs) {
            lastNotificationUpdateMs = now
            updateNotification(speedKmh)
        }
    }
    
    /**
     * Stop foreground tracking completely.
     */
    private fun stopForegroundTracking() {
        if (!isTracking) {
            stopSelf()
            return
        }
        isTracking = false
        isTrackingActive = false
        locationJob?.cancel()
        stalledCheckJob?.cancel()
        locationRepository.stopAllLocationUpdates()
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
        
        val stopIntent = Intent(this, LocationForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
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
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Ride", stopPendingIntent)
            .build()
    }
    
    private fun updateNotification(speedKmh: Double) {
        val contentText = "Speed: ${speedKmh.toInt()} km/h"
        
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun acquireWakeLock() {
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MotoRider::LocationWakeLock"
        ).apply {
            setReferenceCounted(false)
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
        isTracking = false
        isTrackingActive = false
        locationJob?.cancel()
        stalledCheckJob?.cancel()
        locationRepository.stopAllLocationUpdates()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
