package com.motorider.data.repository

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A saved route consisting of GPS waypoints.
 */
data class SavedRoute(
    val id: String,
    val name: String,
    val waypoints: List<Pair<Double, Double>>,  // lat, lon pairs
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Repository for recording, saving, and matching favourite routes.
 * Routes are stored as JSON in DataStore via SettingsRepository.
 */
@Singleton
class RouteRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    // In-memory recording buffer
    private val recordingBuffer = mutableListOf<Pair<Double, Double>>()
    private var lastRecordedLocation: Location? = null

    companion object {
        private const val MIN_RECORD_DISTANCE_M = 100.0  // Record point every 100m
        private const val ROUTE_MATCH_DISTANCE_M = 200f  // Match if within 200m of route
        private const val MAX_ROUTES = 5
    }

    /**
     * Add a GPS point to the recording buffer.
     * Only records if moved at least MIN_RECORD_DISTANCE_M from the last point.
     */
    fun recordPoint(lat: Double, lon: Double) {
        val current = Location("").apply {
            latitude = lat
            longitude = lon
        }

        val lastLoc = lastRecordedLocation
        if (lastLoc == null || lastLoc.distanceTo(current) >= MIN_RECORD_DISTANCE_M) {
            recordingBuffer.add(lat to lon)
            lastRecordedLocation = current
        }
    }

    /**
     * Save the current recording buffer as a named route.
     * Returns true if saved successfully.
     */
    fun saveRecording(name: String): Boolean {
        if (recordingBuffer.size < 2) return false

        val routes = decodeRoutes(settingsRepository.routesJson.value).toMutableList()

        // Enforce max routes limit
        if (routes.size >= MAX_ROUTES) {
            // Remove the oldest route
            routes.removeAt(0)
        }

        val newRoute = SavedRoute(
            id = "route_${System.currentTimeMillis()}",
            name = name.trim(),
            waypoints = recordingBuffer.toList()
        )
        routes.add(newRoute)

        settingsRepository.setRoutesJson(encodeRoutes(routes))
        clearRecording()
        return true
    }

    /**
     * Clear the current recording buffer.
     */
    fun clearRecording() {
        recordingBuffer.clear()
        lastRecordedLocation = null
    }

    /**
     * Get the number of recorded points so far.
     */
    fun getRecordedPointCount(): Int = recordingBuffer.size

    /**
     * Get all saved routes.
     */
    fun getSavedRoutes(): List<SavedRoute> {
        return decodeRoutes(settingsRepository.routesJson.value)
    }

    /**
     * Delete a saved route by ID.
     */
    fun deleteRoute(routeId: String) {
        val routes = decodeRoutes(settingsRepository.routesJson.value)
            .filterNot { it.id == routeId }
        settingsRepository.setRoutesJson(encodeRoutes(routes))

        // If the deleted route was active, clear active
        if (settingsRepository.activeRouteId.value == routeId) {
            settingsRepository.setActiveRouteId(null)
        }
    }

    /**
     * Get a specific route by ID.
     */
    fun getRoute(routeId: String): SavedRoute? {
        return decodeRoutes(settingsRepository.routesJson.value)
            .firstOrNull { it.id == routeId }
    }

    /**
     * Check if current position is near any saved route.
     * Returns the matching route ID or null.
     */
    fun findMatchingRoute(lat: Double, lon: Double): String? {
        val currentLoc = Location("").apply {
            latitude = lat
            longitude = lon
        }

        val routes = decodeRoutes(settingsRepository.routesJson.value)
        for (route in routes) {
            for (waypoint in route.waypoints) {
                val wpLoc = Location("").apply {
                    latitude = waypoint.first
                    longitude = waypoint.second
                }
                if (currentLoc.distanceTo(wpLoc) <= ROUTE_MATCH_DISTANCE_M) {
                    return route.id
                }
            }
        }
        return null
    }

    private fun decodeRoutes(json: String): List<SavedRoute> {
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    val name = obj.optString("name", "")
                    val createdAt = obj.optLong("createdAt", 0)
                    val waypointsArr = obj.optJSONArray("waypoints") ?: continue

                    val waypoints = buildList {
                        for (j in 0 until waypointsArr.length()) {
                            val wp = waypointsArr.optJSONObject(j) ?: continue
                            add(wp.optDouble("lat") to wp.optDouble("lon"))
                        }
                    }

                    if (id.isNotBlank() && name.isNotBlank() && waypoints.isNotEmpty()) {
                        add(SavedRoute(id = id, name = name, waypoints = waypoints, createdAt = createdAt))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeRoutes(routes: List<SavedRoute>): String {
        val arr = JSONArray()
        routes.forEach { route ->
            val obj = JSONObject()
            obj.put("id", route.id)
            obj.put("name", route.name)
            obj.put("createdAt", route.createdAt)

            val waypointsArr = JSONArray()
            route.waypoints.forEach { (lat, lon) ->
                val wp = JSONObject()
                wp.put("lat", lat)
                wp.put("lon", lon)
                waypointsArr.put(wp)
            }
            obj.put("waypoints", waypointsArr)
            arr.put(obj)
        }
        return arr.toString()
    }
}
