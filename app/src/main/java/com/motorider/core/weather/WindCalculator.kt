package com.motorider.core.weather

import com.motorider.data.model.RelativeWind
import com.motorider.data.model.WindAlertLevel
import com.motorider.data.model.WindType
import kotlin.math.abs

/**
 * Calculates relative wind direction and danger level based on rider's heading.
 * 
 * This is critical for motorcycle safety: crosswinds are particularly dangerous
 * as they can destabilize the rider, especially at highway speeds or in gusty conditions.
 * 
 * Wind direction conventions:
 * - Meteorological standard: Wind direction is where wind comes FROM (0° = North)
 * - Rider heading: Direction rider is traveling TOWARD (0° = North)
 */
object WindCalculator {
    
    // Angle thresholds for classifying wind type
    private const val HEADWIND_THRESHOLD = 30  // ±30° from ahead = headwind
    private const val TAILWIND_THRESHOLD = 150 // ±30° from behind = tailwind
    
    /**
     * Calculate relative wind from absolute wind direction and rider heading.
     * 
     * @param windDirectionDegrees Wind coming FROM this direction (0=N, 90=E, 180=S, 270=W)
     * @param riderHeadingDegrees Rider traveling TOWARD this direction (0=N, 90=E, etc.)
     * @param windSpeedKmh Sustained wind speed in km/h
     * @param gustSpeedKmh Maximum gust speed in km/h (nullable, defaults to windSpeed)
     * @return RelativeWind object with type, angle, and alert level
     * 
     * @example
     * // Rider heading North (0°), wind from East (90°) = left crosswind
     * calculateRelativeWind(90, 0, 50.0, 65.0) 
     * // Returns: RelativeWind(CROSSWIND_LEFT, -90, 50.0, 65.0, DANGER)
     */
    fun calculateRelativeWind(
        windDirectionDegrees: Int,
        riderHeadingDegrees: Int,
        windSpeedKmh: Double,
        gustSpeedKmh: Double? = null
    ): RelativeWind {
        // Convert "wind from" to "wind going towards" direction
        // Wind FROM North (0°) means air is moving TOWARD South (180°)
        val windTowards = (windDirectionDegrees + 180) % 360
        
        // Calculate relative angle from rider's perspective
        // Positive = wind coming from right, Negative = wind from left
        var relativeAngle = windTowards - riderHeadingDegrees
        
        // Normalize to -180 to 180 range
        if (relativeAngle > 180) relativeAngle -= 360
        if (relativeAngle < -180) relativeAngle += 360
        
        // Classify wind type based on relative angle
        val windType = classifyWindType(relativeAngle)
        
        // Determine alert level based on gust speed (or sustained if no gusts)
        val effectiveGust = gustSpeedKmh ?: windSpeedKmh
        val alertLevel = determineAlertLevel(effectiveGust)
        
        return RelativeWind(
            type = windType,
            relativeAngle = relativeAngle,
            effectiveSpeed = windSpeedKmh,
            gustSpeed = gustSpeedKmh,
            alertLevel = alertLevel
        )
    }
    
    /**
     * Classify wind type based on relative angle.
     */
    private fun classifyWindType(relativeAngle: Int): WindType {
        val absAngle = abs(relativeAngle)
        
        return when {
            absAngle <= HEADWIND_THRESHOLD -> WindType.HEADWIND
            absAngle >= TAILWIND_THRESHOLD -> WindType.TAILWIND
            relativeAngle > 0 -> WindType.CROSSWIND_RIGHT
            else -> WindType.CROSSWIND_LEFT
        }
    }
    
    /**
     * Determine alert level based on gust speed.
     * Thresholds based on motorcycle safety guidelines:
     * - < 40 km/h: Generally safe for most riders
     * - 40-60 km/h: Caution needed, especially for lighter bikes
     * - > 60 km/h: Dangerous, consider stopping or finding shelter
     */
    private fun determineAlertLevel(gustSpeedKmh: Double): WindAlertLevel {
        return when {
            gustSpeedKmh >= 60 -> WindAlertLevel.DANGER
            gustSpeedKmh >= 40 -> WindAlertLevel.WARNING
            else -> WindAlertLevel.NORMAL
        }
    }
    
    /**
     * Get a human-readable description of the wind condition.
     * Useful for TTS announcements over Bluetooth intercom.
     */
    fun getWindDescription(wind: RelativeWind): String {
        val typeDescription = when (wind.type) {
            WindType.HEADWIND -> "headwind"
            WindType.TAILWIND -> "tailwind"
            WindType.CROSSWIND_LEFT -> "crosswind from the left"
            WindType.CROSSWIND_RIGHT -> "crosswind from the right"
        }
        
        val speedDescription = "${wind.effectiveSpeed.toInt()} kilometers per hour"
        
        val gustDescription = wind.gustSpeed?.let {
            ", gusting to ${it.toInt()}"
        } ?: ""
        
        val warningPrefix = when (wind.alertLevel) {
            WindAlertLevel.DANGER -> "Warning! Strong "
            WindAlertLevel.WARNING -> "Caution. Moderate "
            WindAlertLevel.NORMAL -> ""
        }
        
        return "$warningPrefix$typeDescription at $speedDescription$gustDescription"
    }
    
    /**
     * Calculate if wind conditions are safe for riding.
     * Returns true if conditions are within acceptable limits.
     */
    fun isWindSafe(gustSpeedKmh: Double, isCrosswind: Boolean): Boolean {
        // Lower threshold for crosswinds as they're more dangerous
        val threshold = if (isCrosswind) 50.0 else 70.0
        return gustSpeedKmh < threshold
    }
}
