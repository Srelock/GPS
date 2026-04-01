package com.motorider.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.motorider.data.entity.RouteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Route entities.
 * Provides all database operations for saved routes.
 */
@Dao
interface RouteDao {
    
    /**
     * Get all routes ordered by start time (newest first).
     * Returns a Flow for reactive updates.
     */
    @Query("SELECT * FROM routes ORDER BY startTime DESC")
    fun getAllRoutes(): Flow<List<RouteEntity>>
    
    /**
     * Get a single route by its ID.
     */
    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Long): RouteEntity?
    
    /**
     * Insert a new route and return its generated ID.
     */
    @Insert
    suspend fun insertRoute(route: RouteEntity): Long
    
    /**
     * Update an existing route.
     */
    @Update
    suspend fun updateRoute(route: RouteEntity)
    
    /**
     * Delete a route.
     */
    @Delete
    suspend fun deleteRoute(route: RouteEntity)
    
    /**
     * Delete a route by its ID.
     */
    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: Long)
    
    /**
     * Get total statistics across all routes.
     */
    @Query("""
        SELECT 
            COUNT(*) as totalTrips,
            COALESCE(SUM(totalDistanceMeters), 0) as totalDistance,
            COALESCE(MAX(maxSpeedKmh), 0) as topSpeed,
            COALESCE(SUM(endTime - startTime), 0) as totalTime
        FROM routes
    """)
    suspend fun getTotalStats(): TotalStats
    
    /**
     * Search routes by name.
     */
    @Query("SELECT * FROM routes WHERE name LIKE '%' || :query || '%' ORDER BY startTime DESC")
    fun searchRoutes(query: String): Flow<List<RouteEntity>>
}

/**
 * Aggregate statistics container.
 */
data class TotalStats(
    val totalTrips: Int,
    val totalDistance: Double,
    val topSpeed: Double,
    val totalTime: Long
)
