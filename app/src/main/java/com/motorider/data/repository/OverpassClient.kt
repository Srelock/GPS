package com.motorider.data.repository

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos

/**
 * Shared client for the public Overpass API mirrors.
 *
 * overpass-api.de regularly answers 502 ("currently at capacity") or times out, so a single
 * endpoint makes the speed-limit and speed-camera features silently show nothing.
 * Requests are rotated across several mirrors and the last mirror that answered is tried first.
 */
object OverpassClient {

    /** Public Overpass mirrors that serve global (not region-restricted) data. */
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    private const val TAG = "OverpassClient"

    /** Index of the mirror that answered most recently — tried first on the next request. */
    private val preferredIndex = AtomicInteger(0)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Runs [query] against the mirrors in rotation until one returns a successful response.
     *
     * @return the raw response body, or null when every mirror failed (caller should retry later).
     */
    fun execute(query: String, userAgent: String, logTag: String): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val start = preferredIndex.get()

        for (offset in ENDPOINTS.indices) {
            val index = (start + offset) % ENDPOINTS.size
            val endpoint = ENDPOINTS[index]
            try {
                val request = Request.Builder()
                    .url("$endpoint?data=$encoded")
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(logTag, "$endpoint -> HTTP ${response.code}")
                        return@use
                    }
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        Log.w(logTag, "$endpoint -> empty body")
                        return@use
                    }
                    preferredIndex.set(index)
                    return body
                }
            } catch (e: Exception) {
                Log.w(logTag, "$endpoint -> ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        Log.e(logTag, "All Overpass mirrors failed for query: ${query.take(80)}")
        return null
    }

    /**
     * Last-resort fallback for when every Overpass mirror is down: the OSM **main API**
     * (`api.openstreetmap.org`) is completely separate infrastructure — the planet/chop
     * servers that serve `/api/0.6/map` — and keeps answering while the Overpass fleet
     * is "at capacity". No key, no account.
     *
     * Returns the raw OSM XML for a square of roughly [radiusM] around the point,
     * or null when the request failed (caller should retry later).
     */
    fun fetchOsmMapXml(
        lat: Double,
        lon: Double,
        radiusM: Double,
        userAgent: String,
        logTag: String
    ): String? {
        val mPerDegLat = 111_320.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(lat))
        val dLat = radiusM / mPerDegLat
        val dLon = radiusM / mPerDegLon
        // bbox order on the OSM API: minLon,minLat,maxLon,maxLat
        val bbox = "%.6f,%.6f,%.6f,%.6f".format(lon - dLon, lat - dLat, lon + dLon, lat + dLat)
        val url = "https://api.openstreetmap.org/api/0.6/map?bbox=$bbox"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(logTag, "OSM main API -> HTTP ${response.code}")
                    return@use null
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(logTag, "OSM main API -> empty body")
                    return@use null
                }
                body
            }
        } catch (e: Exception) {
            Log.w(logTag, "OSM main API -> ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
