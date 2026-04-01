package com.motorider.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Room entity representing a saved motorcycle route/trip.
 * Stores trip statistics and the polyline of GPS points.
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** User-defined name for the route */
    val name: String,
    
    /** Trip start time in epoch milliseconds */
    val startTime: Long,
    
    /** Trip end time in epoch milliseconds */
    val endTime: Long,
    
    /** Total distance traveled in meters */
    val totalDistanceMeters: Double,
    
    /** Maximum speed reached during the trip in km/h */
    val maxSpeedKmh: Double,
    
    /** Average speed over the trip in km/h */
    val avgSpeedKmh: Double,
    
    /** 
     * JSON-serialized list of RoutePoint objects.
     * Stored as a string for Room compatibility.
     */
    @ColumnInfo(name = "polyline_json")
    val polylineJson: String,
    
    /** Reverse-geocoded starting address (nullable) */
    val startAddress: String? = null,
    
    /** Reverse-geocoded ending address (nullable) */
    val endAddress: String? = null,
    
    /** Record creation timestamp */
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Calculate trip duration in milliseconds.
     */
    val durationMillis: Long
        get() = endTime - startTime
    
    /**
     * Format duration as HH:MM:SS string.
     */
    val formattedDuration: String
        get() {
            val totalSeconds = durationMillis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    
    /**
     * Convert distance to kilometers.
     */
    val distanceKm: Double
        get() = totalDistanceMeters / 1000.0
}

/**
 * A single GPS point recorded during a trip.
 * These are serialized to JSON and stored in RouteEntity.polylineJson.
 */
@Serializable
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,     // Altitude in meters (if available)
    val speedKmh: Double,             // GPS-derived speed at this point
    val timestamp: Long,              // When this point was recorded
    val accuracy: Float               // GPS accuracy in meters
)

/**
 * State representing an active (in-progress) trip.
 * Not persisted to database until trip is stopped.
 */
data class ActiveTrip(
    val startTime: Long = System.currentTimeMillis(),
    val points: MutableList<RoutePoint> = mutableListOf(),
    var totalDistanceMeters: Double = 0.0,
    var maxSpeedKmh: Double = 0.0,
    var currentSpeedKmh: Double = 0.0
) {
    /**
     * Add a new point and update statistics.
     */
    fun addPoint(point: RoutePoint, distanceFromLastPoint: Double) {
        points.add(point)
        totalDistanceMeters += distanceFromLastPoint
        if (point.speedKmh > maxSpeedKmh) {
            maxSpeedKmh = point.speedKmh
        }
        currentSpeedKmh = point.speedKmh
    }
    
    /**
     * Calculate average speed based on distance and time.
     */
    val avgSpeedKmh: Double
        get() {
            val durationHours = (System.currentTimeMillis() - startTime) / 3600000.0
            return if (durationHours > 0) (totalDistanceMeters / 1000.0) / durationHours else 0.0
        }
    
    /**
     * Get elapsed time since trip started.
     */
    val elapsedMillis: Long
        get() = System.currentTimeMillis() - startTime
}
