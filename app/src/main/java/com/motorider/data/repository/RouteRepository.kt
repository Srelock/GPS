package com.motorider.data.repository

import com.motorider.data.entity.RouteEntity
import com.motorider.data.entity.RoutePoint
import com.motorider.data.export.GpxExporter
import com.motorider.data.local.RouteDao
import com.motorider.data.local.TotalStats
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing saved routes.
 * Provides operations for CRUD and GPX export.
 */
@Singleton
class RouteRepository @Inject constructor(
    private val routeDao: RouteDao
) {
    private val json = Json { prettyPrint = false }
    
    /**
     * Get all saved routes as a reactive Flow.
     */
    fun getAllRoutes(): Flow<List<RouteEntity>> = routeDao.getAllRoutes()
    
    /**
     * Get a single route by ID.
     */
    suspend fun getRouteById(routeId: Long): RouteEntity? = routeDao.getRouteById(routeId)
    
    /**
     * Save a new route to the database.
     * 
     * @param name User-provided name for the route
     * @param points List of GPS points recorded during the trip
     * @param startTime Trip start time in milliseconds
     * @param endTime Trip end time in milliseconds
     * @param totalDistanceMeters Total distance traveled
     * @param maxSpeedKmh Maximum speed during trip
     * @param avgSpeedKmh Average speed during trip
     * @return ID of the newly created route
     */
    suspend fun saveRoute(
        name: String,
        points: List<RoutePoint>,
        startTime: Long,
        endTime: Long,
        totalDistanceMeters: Double,
        maxSpeedKmh: Double,
        avgSpeedKmh: Double,
        startAddress: String? = null,
        endAddress: String? = null
    ): Long {
        val polylineJson = json.encodeToString(points)
        
        val route = RouteEntity(
            name = name,
            startTime = startTime,
            endTime = endTime,
            totalDistanceMeters = totalDistanceMeters,
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedKmh = avgSpeedKmh,
            polylineJson = polylineJson,
            startAddress = startAddress,
            endAddress = endAddress
        )
        
        return routeDao.insertRoute(route)
    }
    
    /**
     * Delete a route from the database.
     */
    suspend fun deleteRoute(route: RouteEntity) = routeDao.deleteRoute(route)
    
    /**
     * Delete a route by its ID.
     */
    suspend fun deleteRouteById(routeId: Long) = routeDao.deleteRouteById(routeId)
    
    /**
     * Get aggregate statistics across all routes.
     */
    suspend fun getTotalStats(): TotalStats = routeDao.getTotalStats()
    
    /**
     * Search routes by name.
     */
    fun searchRoutes(query: String): Flow<List<RouteEntity>> = routeDao.searchRoutes(query)
    
    /**
     * Export a route to GPX format.
     * 
     * @param routeId ID of the route to export
     * @param outputStream Stream to write GPX content to
     * @return True if export succeeded
     */
    suspend fun exportToGpx(routeId: Long, outputStream: OutputStream): Boolean {
        val route = routeDao.getRouteById(routeId) ?: return false
        
        return try {
            GpxExporter.exportToGpx(route, outputStream)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get suggested filename for GPX export.
     */
    suspend fun getGpxFilename(routeId: Long): String? {
        val route = routeDao.getRouteById(routeId) ?: return null
        return GpxExporter.generateFilename(route)
    }
    
    /**
     * Parse route points from a route entity.
     */
    fun parseRoutePoints(route: RouteEntity): List<RoutePoint> {
        return json.decodeFromString(route.polylineJson)
    }
}
