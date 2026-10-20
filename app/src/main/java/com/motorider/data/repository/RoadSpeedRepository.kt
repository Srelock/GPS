package com.motorider.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.roundToInt

/** Outcome of a road speed-limit lookup. */
sealed interface RoadSpeedResult {
    /** A speed limit was found for the current road. */
    data class Found(val limitKmh: Double) : RoadSpeedResult

    /** Overpass answered, but no `maxspeed` is mapped near this point. */
    object NoData : RoadSpeedResult

    /** Network/server failure — the caller should retry after a short backoff. */
    object Failed : RoadSpeedResult
}

@Singleton
class RoadSpeedRepository @Inject constructor() {

    companion object {
        private const val TAG = "RoadSpeedRepo"
        /** Radius around the GPS fix that still counts as "the current road". */
        private const val ROAD_RADIUS_M = 50
        /**
         * Box half-size for the OSM main API fallback. Slightly larger than
         * [ROAD_RADIUS_M] so a way just inside the radius is never clipped by the bbox.
         */
        private const val OSM_MAIN_API_RADIUS_M = 60.0
    }

    /**
     * Fetch the speed limit for the current road near [lat], [lon] using the Overpass API.
     * Tries several mirrors before giving up, so a busy primary server does not blank the HUD.
     */
    suspend fun fetchSpeedLimit(lat: Double, lon: Double): RoadSpeedResult =
        withContext(Dispatchers.IO) {
            val query = """
                [out:json];
                (
                  way(around:$ROAD_RADIUS_M, $lat, $lon)["maxspeed"];
                );
                out body;
            """.trimIndent()

            val body = OverpassClient.execute(
                query = query,
                userAgent = "MotoRiderApp/1.0 (Android; London-Only)",
                logTag = TAG
            ) ?: run {
                // Every Overpass mirror failed (they regularly sit at capacity).
                // The OSM main API is separate infrastructure — try it before giving up.
                val xml = OverpassClient.fetchOsmMapXml(
                    lat = lat,
                    lon = lon,
                    radiusM = OSM_MAIN_API_RADIUS_M,
                    userAgent = "MotoRiderApp/1.0 (Android; London-Only)",
                    logTag = TAG
                ) ?: return@withContext RoadSpeedResult.Failed

                return@withContext parseOsmMapXml(xml, lat, lon)
            }

            parseBody(body)
        }

    /**
     * Parses the OSM main API `/map` XML response and returns the speed limit of the
     * nearest `maxspeed`-tagged way within [ROAD_RADIUS_M] of the GPS fix.
     */
    private fun parseOsmMapXml(xml: String, lat: Double, lon: Double): RoadSpeedResult {
        return try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))

            var bestDistM = Double.MAX_VALUE
            var bestMaxSpeed: String? = null

            val nodeCoords = HashMap<String, DoubleArray>()  // node id -> [latDeg, lonDeg]
            var points: MutableList<DoubleArray>? = null      // each entry: [latDeg, lonDeg]
            var maxSpeed: String? = null
            var inWay = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "node" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val nLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val nLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (id != null && nLat != null && nLon != null) {
                                nodeCoords[id] = doubleArrayOf(nLat, nLon)
                            }
                        }
                        "way" -> {
                            points = ArrayList()
                            maxSpeed = null
                            inWay = true
                        }
                        // In OSM XML, <nd> carries only a node reference; the coordinates
                        // live on the <node> elements declared before the ways.
                        "nd" -> if (inWay) {
                            val coord = parser.getAttributeValue(null, "ref")?.let { nodeCoords[it] }
                            if (coord != null) points?.add(coord)
                        }
                        "tag" -> if (inWay && parser.getAttributeValue(null, "k") == "maxspeed") {
                            maxSpeed = parser.getAttributeValue(null, "v")
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "way") {
                        val wayPoints = points
                        val wayMaxSpeed = maxSpeed
                        inWay = false
                        points = null
                        if (wayPoints != null && wayPoints.isNotEmpty() && wayMaxSpeed != null) {
                            val dist = minDistanceToWay(lat, lon, wayPoints)
                            if (dist <= ROAD_RADIUS_M && dist < bestDistM) {
                                bestDistM = dist
                                bestMaxSpeed = wayMaxSpeed
                            }
                        }
                    }
                }
                event = parser.next()
            }

            val raw = bestMaxSpeed ?: run {
                Log.i(TAG, "OSM main API fallback -> no maxspeed-tagged way within ${ROAD_RADIUS_M}m")
                return RoadSpeedResult.NoData
            }
            val limit = parseMaxSpeed(raw)
            if (limit != null) {
                Log.i(TAG, "OSM main API fallback -> $raw (${bestDistM.roundToInt()}m away)")
                RoadSpeedResult.Found(limit)
            } else {
                Log.i(TAG, "OSM main API fallback -> unreadable maxspeed '$raw'")
                RoadSpeedResult.NoData
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OSM map XML: ${e.message}")
            RoadSpeedResult.Failed
        }
    }

    /** Shortest distance in meters from the point to a way's polyline (local projection). */
    private fun minDistanceToWay(lat: Double, lon: Double, points: List<DoubleArray>): Double {
        val mPerLat = 111_320.0
        val mPerLon = mPerLat * cos(Math.toRadians(lat))
        val px = lon * mPerLon
        val py = lat * mPerLat

        var best = Double.MAX_VALUE
        for (i in points.indices) {
            val x = points[i][1] * mPerLon
            val y = points[i][0] * mPerLat
            best = minOf(best, Math.hypot(px - x, py - y))

            if (i > 0) {
                val x0 = points[i - 1][1] * mPerLon
                val y0 = points[i - 1][0] * mPerLat
                val dx = x - x0
                val dy = y - y0
                val lenSq = dx * dx + dy * dy
                if (lenSq > 0.0) {
                    var t = ((px - x0) * dx + (py - y0) * dy) / lenSq
                    t = t.coerceIn(0.0, 1.0)
                    val cx = x0 + t * dx
                    val cy = y0 + t * dy
                    best = minOf(best, Math.hypot(px - cx, py - cy))
                }
            }
        }
        return best
    }

    private fun parseBody(body: String): RoadSpeedResult {
        return try {
            val json = JSONObject(body)
            val elements = json.optJSONArray("elements") ?: return RoadSpeedResult.NoData

            // Use the first way that carries a readable maxspeed value.
            for (i in 0 until elements.length()) {
                val tags = elements.optJSONObject(i)?.optJSONObject("tags") ?: continue
                val limit = parseMaxSpeed(tags.optString("maxspeed", ""))
                if (limit != null) return RoadSpeedResult.Found(limit)
            }
            RoadSpeedResult.NoData
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing speed limit: ${e.message}")
            RoadSpeedResult.Failed
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
