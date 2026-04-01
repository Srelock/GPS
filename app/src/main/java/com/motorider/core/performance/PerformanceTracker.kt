package com.motorider.core.performance

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class PerformanceStats(
    val topSpeedKmh: Double = 0.0,
    val lastQuarterMileTimeSeconds: Double? = null,
    val lastQuarterMileTrapSpeedKmh: Double? = null,
    val isRunActive: Boolean = false
)

@Singleton
class PerformanceTracker @Inject constructor() {

    private val _stats = MutableStateFlow(PerformanceStats())
    val stats: StateFlow<PerformanceStats> = _stats.asStateFlow()

    // Run state
    private var isReady = false
    private var runStartTime = 0L
    private var startLocation: Location? = null
    
    // Achievement flags for current run
    private var reachedQuarterMile = false

    companion object {
        private const val READY_SPEED_THRESHOLD_KMH = 3.0 // Must be below this to be "Ready"
        private const val LAUNCH_SPEED_THRESHOLD_KMH = 5.0 // Speed > this triggers "Go"
        private const val TARGET_DIST_QUARTER_MILE_METERS = 402.34
    }

    fun processLocation(location: Location, speedKmh: Double) {
        val now = location.time // Use GPS time if reliable, or System.currentTimeMillis()
        
        // 1. Check if we should RESET / BE READY
        if (speedKmh < READY_SPEED_THRESHOLD_KMH) {
            isReady = true
            // If we were running, stop the run
            if (_stats.value.isRunActive) {
                _stats.value = _stats.value.copy(isRunActive = false)
            }
            reachedQuarterMile = false
            return
        }

        // 2. Check for LAUNCH
        if (isReady && speedKmh >= LAUNCH_SPEED_THRESHOLD_KMH) {
            // LAUNCH!
            isReady = false
            runStartTime = System.currentTimeMillis() // Using system time for precision duration
            startLocation = location
            reachedQuarterMile = false
            
            _stats.value = _stats.value.copy(
                isRunActive = true,
                // Optional: Clear previous stats on new run? 
                // Let's keep them until beaten or overwritten? 
                // For now, let's keep showing last run until new one finishes.
            )
        }

        // 3. Track top speed (always, regardless of run state)
        if (speedKmh > _stats.value.topSpeedKmh) {
            _stats.value = _stats.value.copy(topSpeedKmh = speedKmh)
        }

        // 4. Process Active Run
        if (_stats.value.isRunActive) {
            val elapsedTime = (System.currentTimeMillis() - runStartTime) / 1000.0
            val distanceMeters = startLocation?.distanceTo(location)?.toDouble() ?: 0.0

            // Check 1/4 Mile
            if (!reachedQuarterMile && distanceMeters >= TARGET_DIST_QUARTER_MILE_METERS) {
                reachedQuarterMile = true
                
                // Update stats
                _stats.value = _stats.value.copy(
                    lastQuarterMileTimeSeconds = elapsedTime,
                    lastQuarterMileTrapSpeedKmh = speedKmh,
                    isRunActive = false // Run complete (unless we want to track further)
                )
            }
        }
    }

    /**
     * Reset the recorded top speed back to zero.
     */
    fun resetTopSpeed() {
        _stats.value = _stats.value.copy(topSpeedKmh = 0.0)
    }
}
