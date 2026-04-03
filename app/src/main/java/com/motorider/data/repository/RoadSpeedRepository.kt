package com.motorider.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoadSpeedRepository @Inject constructor() {

    private val client = OkHttpClient()

    /**
     * Fetch the speed limit for the current road near [lat], [lon] using Overpass API.
     * Returns the speed limit in km/h, or null if not found.
     */
    suspend fun fetchSpeedLimit(lat: Double, lon: Double): Double? = withContext(Dispatchers.IO) {
        val query = """
            [out:json];
            (
              way(around:40, $lat, $lon)["maxspeed"];
            );
            out body;
        """.trimIndent()

        val url = "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"

        return@withContext try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MotoRiderApp/1.0 (Android; London-Only)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val json = JSONObject(response.body?.string() ?: "")
                val elements = json.optJSONArray("elements") ?: return@withContext null
                
                if (elements.length() == 0) return@withContext null

                // Pick the first way found
                val way = elements.getJSONObject(0)
                val tags = way.optJSONObject("tags") ?: return@withContext null
                val maxSpeedStr = tags.optString("maxspeed", "")

                parseMaxSpeed(maxSpeedStr)
            }
        } catch (e: Exception) {
            android.util.Log.e("RoadSpeedRepo", "Error fetching speed limit: ${e.message}")
            null
        }
    }

    private fun parseMaxSpeed(raw: String): Double? {
        if (raw.isBlank()) return null
        
        val clean = raw.lowercase().trim()
        val isExplicitMph = clean.contains("mph")
        val numericPart = clean.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return null

        // In London/UK, OSM 'maxspeed' values like "20" or "30" are nearly always MPH.
        // My app uses km/h internally, so I convert them TO km/h here.
        // When the UI displays them, it will convert them BACK to the correct user unit.
        
        return if (isExplicitMph || numericPart <= 80) {
            // If it's 20, 30, 70 etc, it's almost certainly MPH in the UK context.
            // We convert to km/h for the app's internal "LocationRepository.roadSpeedLimit".
            numericPart * 1.60934
        } else {
            // Probably already km/h (e.g. 100, 120)
            numericPart
        }
    }
}
