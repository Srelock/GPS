package com.motorider.data.repository

import android.content.Context
import android.location.Location
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** Outcome of a speed-camera lookup. */
sealed interface CameraFetchResult {
    /** Mirrors answered — [cameras] is the fresh set around the queried point. */
    data class Success(val cameras: List<SpeedCamera>) : CameraFetchResult

    /** Network/server failure — the caller should retry after a short backoff. */
    object Failed : CameraFetchResult
}

/**
 * Repository for fetching and caching speed camera locations from OpenStreetMap.
 * Uses the Overpass API to query highway=speed_camera nodes and ways.
 */
@Singleton
class SpeedCameraRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Cached cameras (bundled GB dataset, or Overpass results as a fallback)
    @Volatile
    private var cachedCameras: List<SpeedCamera> = emptyList()
    private var bundledLoaded = false
    private var lastFetchTime = 0L
    private var lastFetchLocation: Location? = null

    companion object {
        private const val FETCH_COOLDOWN_MS = 30_000L   // 30 seconds between fetches
        private const val FETCH_DISTANCE_M = 500.0       // Re-fetch if moved 500m
        private const val SEARCH_RADIUS_M = 1500         // Search 1.5km radius
        private const val TAG = "SpeedCameraRepo"
        /** Offline camera database shipped in the APK (MapAtlas Camera Index, CC BY 4.0). */
        private const val BUNDLED_ASSET = "cameras_gb.geojson"
    }

    /**
     * Fetch speed cameras near the given location.
     *
     * The bundled GB dataset is the primary source: it needs no network and no server,
     * so camera alerts keep working when every Overpass mirror is down. The Overpass
     * query only runs if the bundled asset is missing or empty (e.g. outside GB it still
     * returns the bundled list — there is simply nothing mapped near the fix).
     *
     * @return [CameraFetchResult.Failed] only when no source produced data.
     */
    suspend fun fetchCamerasNear(lat: Double, lon: Double): CameraFetchResult =
        withContext(Dispatchers.IO) {
            ensureBundledLoaded()
            if (cachedCameras.isNotEmpty()) {
                return@withContext CameraFetchResult.Success(cachedCameras)
            }

            val now = System.currentTimeMillis()
            val timeSinceFetch = now - lastFetchTime

            // Check if we can reuse cache
            val lastLoc = lastFetchLocation
            if (lastLoc != null && timeSinceFetch < FETCH_COOLDOWN_MS) {
                return@withContext CameraFetchResult.Success(cachedCameras)
            }

            val currentLoc = Location("").apply {
                latitude = lat
                longitude = lon
            }
            val distFromLast = lastLoc?.distanceTo(currentLoc) ?: Float.MAX_VALUE

            if (distFromLast < FETCH_DISTANCE_M && timeSinceFetch < FETCH_COOLDOWN_MS * 2) {
                return@withContext CameraFetchResult.Success(cachedCameras)
            }

            val fetched = fetchFromOverpass(lat, lon)
                ?: return@withContext CameraFetchResult.Failed

            cachedCameras = fetched
            lastFetchTime = now
            lastFetchLocation = currentLoc

            CameraFetchResult.Success(fetched)
        }

    /** Loads the bundled GB camera database once (called from the IO dispatcher). */
    private fun ensureBundledLoaded() {
        if (bundledLoaded) return
        bundledLoaded = true
        try {
            val json = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
            val features = JSONObject(json).optJSONArray("features") ?: return
            val loaded = ArrayList<SpeedCamera>(features.length())
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                val lonDeg = coords.optDouble(0, Double.NaN)
                val latDeg = coords.optDouble(1, Double.NaN)
                if (latDeg.isNaN() || lonDeg.isNaN()) continue

                val props = feature.optJSONObject("properties")
                val limitKph = props?.optInt("limitKph", -1) ?: -1
                val approach = props?.optString("approach")?.takeIf { it.isNotEmpty() }

                loaded.add(
                    SpeedCamera(
                        id = i.toLong(),
                        lat = latDeg,
                        lon = lonDeg,
                        direction = approach,
                        maxspeed = limitKph.takeIf { it > 0 }?.toString()
                    )
                )
            }
            if (loaded.isNotEmpty()) {
                cachedCameras = loaded
                Log.i(TAG, "Loaded ${loaded.size} bundled cameras from $BUNDLED_ASSET")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bundled camera asset failed to load: ${e.message}")
        }
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
                  way["highway"="speed_camera"](around:$SEARCH_RADIUS_M, $lat, $lon);
                );
                out body center;
            """.trimIndent()

            val body = OverpassClient.execute(
                query = query,
                userAgent = "MotoRiderApp/1.0 (Android; SpeedCameras)",
                logTag = TAG
            ) ?: return@withContext null

            return@withContext try {
                val json = JSONObject(body)
                val elements = json.optJSONArray("elements") ?: return@withContext emptyList()

                buildList {
                    for (i in 0 until elements.length()) {
                        val el = elements.optJSONObject(i) ?: continue
                        val id = el.optLong("id", -1)

                        // Nodes carry lat/lon directly; ways only after "out center".
                        val center = el.optJSONObject("center")
                        val elLat = el.optDouble("lat", center?.optDouble("lat") ?: Double.NaN)
                        val elLon = el.optDouble("lon", center?.optDouble("lon") ?: Double.NaN)

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
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing speed cameras: ${e.message}")
                null
            }
        }
}
