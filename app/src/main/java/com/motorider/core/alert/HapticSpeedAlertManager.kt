package com.motorider.core.alert

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages haptic feedback for speed alerts.
 * 
 * Uses pulsing vibration patterns that riders can feel through phone mounts.
 * The patterns are designed to be distinguishable at different alert levels:
 * 
 * - APPROACHING: Single gentle pulse - a heads-up that limit is near
 * - WARNING: Double pulse every 2 seconds - slow down reminder
 * - CRITICAL: Aggressive rapid pulsing every second - urgent warning
 */
@Singleton
class HapticSpeedAlertManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) 
            as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    private var alertJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Observable alert state for UI binding
    private val _alertState = MutableStateFlow(SpeedAlertState.NORMAL)
    val alertState: StateFlow<SpeedAlertState> = _alertState.asStateFlow()
    
    // Track last state to avoid redundant vibrations
    private var lastTriggeredState: SpeedAlertState = SpeedAlertState.NORMAL
    
    /**
     * Check current speed against the user-defined limit and trigger appropriate haptic response.
     * 
     * @param currentSpeedKmh Current speed from GPS in km/h
     * @param speedLimitKmh User-defined speed limit in km/h
     * @param warningThresholdPercent Percentage over limit to trigger critical alert (default: 10%)
     */
    fun checkSpeed(
        currentSpeedKmh: Double,
        speedLimitKmh: Double,
        warningThresholdPercent: Int = 10
    ) {
        if (speedLimitKmh <= 0) return
        
        val warningSpeed = speedLimitKmh * (1 + warningThresholdPercent / 100.0)
        
        val newState = when {
            currentSpeedKmh > warningSpeed -> SpeedAlertState.CRITICAL
            currentSpeedKmh > speedLimitKmh -> SpeedAlertState.WARNING
            currentSpeedKmh > speedLimitKmh * 0.9 -> SpeedAlertState.APPROACHING
            else -> SpeedAlertState.NORMAL
        }
        
        _alertState.value = newState
        
        // Only trigger new haptic pattern if state changed
        if (newState != lastTriggeredState) {
            lastTriggeredState = newState
            triggerHapticForState(newState)
        }
    }
    
    /**
     * Trigger the appropriate haptic pattern based on alert state.
     */
    private fun triggerHapticForState(state: SpeedAlertState) {
        // Cancel any existing alert pattern
        alertJob?.cancel()
        
        when (state) {
            SpeedAlertState.NORMAL -> {
                vibrator.cancel()
            }
            
            SpeedAlertState.APPROACHING -> {
                // Single gentle pulse - heads up notification
                vibrateOnce(100, VibrationEffect.EFFECT_TICK)
            }
            
            SpeedAlertState.WARNING -> {
                // Double pulse pattern - repeating every 2 seconds
                alertJob = scope.launch {
                    while (isActive) {
                        vibratePattern(
                            pattern = longArrayOf(0, 200, 100, 200),
                            amplitudes = intArrayOf(0, 150, 0, 150)
                        )
                        delay(2000)
                    }
                }
            }
            
            SpeedAlertState.CRITICAL -> {
                // Aggressive rapid pulsing - URGENT warning
                alertJob = scope.launch {
                    while (isActive) {
                        vibratePattern(
                            pattern = longArrayOf(0, 100, 50, 100, 50, 100, 50, 300),
                            amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)
                        )
                        delay(1000)
                    }
                }
            }
        }
    }
    
    /**
     * Play a single vibration effect.
     * 
     * @param durationMs Duration of the vibration in milliseconds
     * @param effectId Optional predefined effect ID for newer devices
     */
    private fun vibrateOnce(durationMs: Long, effectId: Int? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && effectId != null) {
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            } catch (e: Exception) {
                // Fallback if predefined effect not supported
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } else {
            vibrator.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }
    
    /**
     * Play a complex vibration pattern.
     * 
     * @param pattern Timing pattern: [delay, vibrate, pause, vibrate, ...]
     * @param amplitudes Amplitude for each segment (0-255). Array length must match pattern.
     */
    private fun vibratePattern(pattern: LongArray, amplitudes: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, amplitudes, -1) // -1 = no repeat
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
    
    /**
     * Stop all haptic alerts immediately.
     * Call this when the user stops the trip or pauses tracking.
     */
    fun stopAlerts() {
        alertJob?.cancel()
        vibrator.cancel()
        _alertState.value = SpeedAlertState.NORMAL
        lastTriggeredState = SpeedAlertState.NORMAL
    }
    
    /**
     * Manually trigger a test vibration pattern.
     * Useful for settings screen testing.
     */
    fun testVibration(state: SpeedAlertState) {
        triggerHapticForState(state)
        // Auto-stop after 3 seconds for test
        scope.launch {
            delay(3000)
            stopAlerts()
        }
    }
    
    /**
     * Clean up resources. Call when the manager is no longer needed.
     */
    fun destroy() {
        stopAlerts()
        scope.cancel()
    }
}

/**
 * Speed alert states with corresponding UI and haptic feedback.
 * 
 * @property colorHex Suggested UI color for this state
 * @property label Human-readable label for the state
 */
enum class SpeedAlertState(val colorHex: Long, val label: String) {
    NORMAL(0xFF39FF14, "Normal"),            // Neon Green
    APPROACHING(0xFFFFFF00, "Approaching"),  // Yellow
    WARNING(0xFFFF6600, "Over Limit"),        // Neon Orange
    CRITICAL(0xFFFF073A, "Critical")          // Neon Red
}
