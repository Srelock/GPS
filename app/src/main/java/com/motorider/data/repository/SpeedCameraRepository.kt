package com.motorider.data.repository

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a speed camera location.
 */
data class SpeedCamera(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val direction: String? = null,  // e.g. "forward", "backward", "both"
    val maxspeed: String? = null     // e.g. "30" (mph in UK)
)

/**
 * Repository for fetching and caching speed camera locations from OpenStreetMap.
 * Uses the Overpass API to query highway=speed_camera nodes.
 */
@Singleton
class SpeedCameraRepository @Inject constructor() {

    private val client = OkHttpClient()

    // Cached cameras
    private var cachedCameras: List<SpeedCamera> = emptyList()
    private var lastFetchTime = 0L
    private var lastFetchLocation: Location? = null

    companion object {
        private const val FETCH_COOLDOWN_MS = 30_000L   // 30 seconds between fetches
        private const val FETCH_DISTANCE_M = 500.0       // Re-fetch if moved 500m
        private const val SEARCH_RADIUS_M = 1500         // Search 1.5km radius
        private const val TAG = "SpeedCameraRepo"
    }

    /**
     * Fetch speed cameras near the given location.
     * Returns cached results if still within range, otherwise queries Overpass API.
     */
    suspend fun getCamerasNear(lat: Double, lon: Double): List<SpeedCamera> {
        val now = System.currentTimeMillis()
        val timeSinceFetch = now - lastFetchTime

        // Check if we can reuse cache
        val lastLoc = lastFetchLocation
        if (lastLoc != null && timeSinceFetch < FETCH_COOLDOWN_MS) {
            return cachedCameras
        }

        val currentLoc = Location("").apply {
            latitude = lat
            longitude = lon
        }
        val distFromLast = lastLoc?.distanceTo(currentLoc) ?: Float.MAX_VALUE

        if (distFromLast < FETCH_DISTANCE_M && timeSinceFetch < FETCH_COOLDOWN_MS * 2) {
            return cachedCameras
        }

        // Fetch fresh data
        val fetched = fetchFromOverpass(lat, lon)
        if (fetched != null) {
            cachedCameras = fetched
            lastFetchTime = now
            lastFetchLocation = currentLoc
        }

        return cachedCameras
    }

    /**
     * Find the nearest camera to a given location and return distance in meters.
     * Returns null if no cameras are cached or none nearby.
     */
    fun findNearestCamera(lat: Double, lon: Double): Pair<SpeedCamera, Float>? {
        if (cachedCameras.isEmpty()) return null

        val currentLoc = Location("").apply {
            latitude = lat
            longitude = lon
        }

        var nearest: SpeedCamera? = null
        var nearestDist = Float.MAX_VALUE

        for (cam in cachedCameras) {
            val camLoc = Location("").apply {
                latitude = cam.lat
                longitude = cam.lon
            }
            val dist = currentLoc.distanceTo(camLoc)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = cam
            }
        }

        return nearest?.let { it to nearestDist }
    }

    private suspend fun fetchFromOverpass(lat: Double, lon: Double): List<SpeedCamera>? =
        withContext(Dispatchers.IO) {
            val query = """
                [out:json][timeout:10];
                (
                  node["highway"="speed_camera"](around:$SEARCH_RADIUS_M, $lat, $lon);
                );
                out body;
            """.trimIndent()

            val url = "https://overpass-api.de/api/interpreter?data=${
                java.net.URLEncoder.encode(query, "UTF-8")
            }"

            return@withContext try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MotoRiderApp/1.0 (Android; SpeedCameras)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w(TAG, "Overpass API returned ${response.code}")
                        return@withContext null
                    }

                    val json = JSONObject(response.body?.string() ?: "")
                    val elements = json.optJSONArray("elements") ?: return@withContext emptyList()

                    buildList {
                        for (i in 0 until elements.length()) {
                            val el = elements.optJSONObject(i) ?: continue
                            val id = el.optLong("id", -1)
                            val elLat = el.optDouble("lat", Double.NaN)
                            val elLon = el.optDouble("lon", Double.NaN)
                            if (id == -1L || elLat.isNaN() || elLon.isNaN()) continue

                            val tags = el.optJSONObject("tags")
                            add(
                                SpeedCamera(
                                    id = id,
                                    lat = elLat,
                                    lon = elLon,
                                    direction = tags?.optString("direction"),
                                    maxspeed = tags?.optString("maxspeed")
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error fetching speed cameras: ${e.message}")
                null
            }
        }
}
