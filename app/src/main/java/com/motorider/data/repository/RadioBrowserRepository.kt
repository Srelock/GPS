package com.motorider.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Browseable station from the Radio Browser directory (https://www.radio-browser.info/).
 */
data class RadioBrowserStation(
    val id: String,
    val name: String,
    val url: String,
    val country: String?,
    val tags: String?,
    val bitrate: Int?,
    val codec: String?
) {
    fun toRadioStation(): SettingsRepository.RadioStation =
        SettingsRepository.RadioStation(id = id, name = name, url = url)

    val subtitle: String
        get() {
            val parts = mutableListOf<String>()
            bitrate?.takeIf { it > 0 }?.let { parts.add("${it} kbps") }
            codec?.takeIf { it.isNotBlank() }?.let { parts.add(it.uppercase()) }
            country?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            return parts.joinToString(" · ").ifBlank { "Internet radio" }
        }
}

/**
 * Fetches station metadata from the free Radio Browser API.
 * Playback still uses direct stream URLs in [RadioPlayerService].
 */
@Singleton
class RadioBrowserRepository @Inject constructor() {

    private val client = OkHttpClient()

    companion object {
        private const val TAG = "RadioBrowserRepo"
        private const val API_BASE = "https://de1.api.radio-browser.info"
        private const val USER_AGENT = "MotoRider/1.0 (Android; GPS dashboard radio)"
        private const val DEFAULT_LIMIT = 40
    }

    suspend fun searchByName(query: String, limit: Int = DEFAULT_LIMIT): List<RadioBrowserStation> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()
            val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
            fetchStations(
                "/json/stations/search" +
                    "?name=$encoded" +
                    "&limit=$limit" +
                    "&hidebroken=true" +
                    "&order=votes" +
                    "&reverse=true"
            )
        }

    suspend fun stationsByCountryCode(
        countryCode: String = "gb",
        limit: Int = DEFAULT_LIMIT
    ): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        val code = countryCode.trim().lowercase()
        fetchStations(
            "/json/stations/bycountrycodeexact/$code" +
                "?limit=$limit" +
                "&hidebroken=true" +
                "&order=votes" +
                "&reverse=true"
        )
    }

    suspend fun stationsByTag(tag: String, limit: Int = DEFAULT_LIMIT): List<RadioBrowserStation> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(tag.trim(), StandardCharsets.UTF_8.toString())
            if (encoded.isBlank()) return@withContext emptyList()
            fetchStations(
                "/json/stations/bytag/$encoded" +
                    "?limit=$limit" +
                    "&hidebroken=true" +
                    "&order=votes" +
                    "&reverse=true"
            )
        }

    private fun fetchStations(path: String): List<RadioBrowserStation> {
        val request = Request.Builder()
            .url("$API_BASE$path")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w(TAG, "HTTP ${response.code} for $path")
                    return emptyList()
                }
                val body = response.body?.string().orEmpty()
                parseStations(body)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "fetch failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseStations(json: String): List<RadioBrowserStation> {
        if (json.isBlank()) return emptyList()
        val arr = try {
            JSONArray(json)
        } catch (_: Exception) {
            return emptyList()
        }

        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val uuid = obj.optString("stationuuid", "").trim()
                val name = obj.optString("name", "").trim()
                val url = obj.optString("url_resolved", "").trim()
                    .ifBlank { obj.optString("url", "").trim() }
                if (uuid.isBlank() || name.isBlank() || url.isBlank()) continue
                if (!url.startsWith("http://", ignoreCase = true) &&
                    !url.startsWith("https://", ignoreCase = true)
                ) {
                    continue
                }
                add(
                    RadioBrowserStation(
                        id = uuid,
                        name = name,
                        url = url,
                        country = obj.optString("country", "").trim().ifBlank { null },
                        tags = obj.optString("tags", "").trim().ifBlank { null },
                        bitrate = obj.optInt("bitrate", 0).takeIf { it > 0 },
                        codec = obj.optString("codec", "").trim().ifBlank { null }
                    )
                )
            }
        }
    }
}
