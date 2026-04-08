package com.motorider.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "motorider_settings")

/**
 * Repository for user settings.
 * Uses DataStore to persist settings across app restarts.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class RadioStation(
        val id: String,
        val name: String,
        val url: String
    )

    /**
     * HUD layout modes for the floating overlay.
     */
    enum class HudMode(val label: String) {
        SPEED_ONLY("Speed Only"),
        SPEED_RADIO("Speed + Radio"),
        SPEED_ALERTS("Speed + Alerts"),
        FULL_HUD("Full HUD")
    }

    private object PreferencesKeys {
        val SPEED_LIMIT_KMH = doublePreferencesKey("speed_limit_kmh")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val USE_MPH = booleanPreferencesKey("use_mph")

        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val OVERLAY_X = doublePreferencesKey("overlay_x")
        val OVERLAY_Y = doublePreferencesKey("overlay_y")
        val OVERLAY_WIDTH = doublePreferencesKey("overlay_width")
        val OVERLAY_HEIGHT = doublePreferencesKey("overlay_height")
        val RADIO_STATIONS_JSON = stringPreferencesKey("radio_stations_json")
        val SELECTED_STATION_ID = stringPreferencesKey("selected_station_id")

        // Night mode
        val NIGHT_MODE_AUTO = booleanPreferencesKey("night_mode_auto")
        val NIGHT_MODE_FORCED = booleanPreferencesKey("night_mode_forced")

        // HUD layout mode
        val HUD_MODE = stringPreferencesKey("hud_mode")

        // Favourite routes
        val ROUTES_JSON = stringPreferencesKey("routes_json")
        val ACTIVE_ROUTE_ID = stringPreferencesKey("active_route_id")
        val RECORDING_ROUTE = booleanPreferencesKey("recording_route")

        // Speed camera alerts
        val SPEED_CAMERAS_ENABLED = booleanPreferencesKey("speed_cameras_enabled")
    }

    // Default 120 km/h
    val speedLimitKmh: StateFlow<Double> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SPEED_LIMIT_KMH] ?: 120.0
        }
        .stateIn(scope, SharingStarted.Eagerly, 120.0)

    val hapticEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAPTIC_ENABLED] ?: true
        }
        .stateIn(scope, SharingStarted.Eagerly, true)


    // Use mph instead of km/h
    val useMph: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USE_MPH] ?: true
        }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val overlayEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.OVERLAY_ENABLED] ?: false
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val overlayX: StateFlow<Double> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.OVERLAY_X] ?: 20.0
        }
        .stateIn(scope, SharingStarted.Eagerly, 20.0)

    val overlayY: StateFlow<Double> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.OVERLAY_Y] ?: 200.0
        }
        .stateIn(scope, SharingStarted.Eagerly, 200.0)

    val overlayWidth: StateFlow<Double> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.OVERLAY_WIDTH] ?: 200.0
        }
        .stateIn(scope, SharingStarted.Eagerly, 200.0)

    val overlayHeight: StateFlow<Double> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.OVERLAY_HEIGHT] ?: 150.0
        }
        .stateIn(scope, SharingStarted.Eagerly, 150.0)

    val radioStations: StateFlow<List<RadioStation>> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[PreferencesKeys.RADIO_STATIONS_JSON]
            decodeStations(raw)
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val selectedStationId: StateFlow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SELECTED_STATION_ID]
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // Night mode settings
    val nightModeAuto: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NIGHT_MODE_AUTO] ?: true
        }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val nightModeForced: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NIGHT_MODE_FORCED] ?: false
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // HUD layout mode
    val hudMode: StateFlow<HudMode> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[PreferencesKeys.HUD_MODE]
            try { HudMode.valueOf(raw ?: "") } catch (_: Exception) { HudMode.SPEED_RADIO }
        }
        .stateIn(scope, SharingStarted.Eagerly, HudMode.SPEED_RADIO)

    // Speed camera alerts enabled
    val speedCamerasEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SPEED_CAMERAS_ENABLED] ?: true
        }
        .stateIn(scope, SharingStarted.Eagerly, true)

    // Favourite routes
    val routesJson: StateFlow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ROUTES_JSON] ?: "[]"
        }
        .stateIn(scope, SharingStarted.Eagerly, "[]")

    val activeRouteId: StateFlow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ACTIVE_ROUTE_ID]
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val isRecordingRoute: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.RECORDING_ROUTE] ?: false
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun setSpeedLimit(limit: Double) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.SPEED_LIMIT_KMH] = limit
            }
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.HAPTIC_ENABLED] = enabled
            }
        }
    }


    fun setUseMph(useMph: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.USE_MPH] = useMph
            }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.OVERLAY_ENABLED] = enabled
            }
        }
    }

    fun setOverlayBounds(x: Double, y: Double, width: Double, height: Double) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.OVERLAY_X] = x
                preferences[PreferencesKeys.OVERLAY_Y] = y
                preferences[PreferencesKeys.OVERLAY_WIDTH] = width
                preferences[PreferencesKeys.OVERLAY_HEIGHT] = height
            }
        }
    }

    fun upsertStation(name: String, url: String, id: String = url) {
        scope.launch {
            context.dataStore.edit { preferences ->
                val existing = decodeStations(preferences[PreferencesKeys.RADIO_STATIONS_JSON])
                val updated = existing.toMutableList()
                val idx = updated.indexOfFirst { it.id == id }
                val station = RadioStation(id = id, name = name.trim(), url = url.trim())
                if (idx >= 0) updated[idx] = station else updated.add(station)
                preferences[PreferencesKeys.RADIO_STATIONS_JSON] = encodeStations(updated)
            }
        }
    }

    fun removeStation(id: String) {
        scope.launch {
            context.dataStore.edit { preferences ->
                val existing = decodeStations(preferences[PreferencesKeys.RADIO_STATIONS_JSON])
                val updated = existing.filterNot { it.id == id }
                preferences[PreferencesKeys.RADIO_STATIONS_JSON] = encodeStations(updated)
                if (preferences[PreferencesKeys.SELECTED_STATION_ID] == id) {
                    preferences.remove(PreferencesKeys.SELECTED_STATION_ID)
                }
            }
        }
    }

    fun setSelectedStation(id: String?) {
        scope.launch {
            context.dataStore.edit { preferences ->
                if (id.isNullOrBlank()) {
                    preferences.remove(PreferencesKeys.SELECTED_STATION_ID)
                } else {
                    preferences[PreferencesKeys.SELECTED_STATION_ID] = id
                }
            }
        }
    }

    // Night mode setters
    fun setNightModeAuto(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[PreferencesKeys.NIGHT_MODE_AUTO] = enabled }
        }
    }

    fun setNightModeForced(forced: Boolean) {
        scope.launch {
            context.dataStore.edit { it[PreferencesKeys.NIGHT_MODE_FORCED] = forced }
        }
    }

    // HUD mode setter
    fun setHudMode(mode: HudMode) {
        scope.launch {
            context.dataStore.edit { it[PreferencesKeys.HUD_MODE] = mode.name }
        }
    }

    // Speed camera toggle
    fun setSpeedCamerasEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { it[PreferencesKeys.SPEED_CAMERAS_ENABLED] = enabled }
        }
    }

    // Route setters
    fun setRoutesJson(json: String) {
        scope.launch {
            context.dataStore.edit { it[PreferencesKeys.ROUTES_JSON] = json }
        }
    }

    fun setActiveRouteId(id: String?) {
        scope.launch {
            context.dataStore.edit { preferences ->
                if (id.isNullOrBlank()) preferences.remove(PreferencesKeys.ACTIVE_ROUTE_ID)
                else preferences[PreferencesKeys.ACTIVE_ROUTE_ID] = id
            }
        }
    }

    fun setRecordingRoute(recording: Boolean) {
        scope.launch {
            context.dataStore.edit { it[PreferencesKeys.RECORDING_ROUTE] = recording }
        }
    }

    private fun decodeStations(raw: String?): List<RadioStation> {
        val defaultStations = listOf(
            RadioStation("capital_fm", "Capital FM", "https://media-ssl.musicradio.com/CapitalMP3"),
            RadioStation("capital_dance", "Capital Dance", "https://media-ssl.musicradio.com/CapitalDanceMP3"),
            RadioStation("kiss_fm", "Kiss FM", "https://live-bauerkiss.sharp-stream.com/kissnational.aac"),
            RadioStation("heart_dance", "Heart Dance", "https://media-ssl.musicradio.com/HeartDanceMP3"),
            RadioStation("planet_rock", "Planet Rock", "https://stream-mz.hellorayo.co.uk/planetrock.aac"),
            RadioStation("absolute_radio", "Absolute Radio", "https://stream-ar.hellorayo.co.uk/absoluteradiohigh.aac"),
            RadioStation("radio_x_uk", "Radio X", "https://media-ssl.musicradio.com/RadioXUKMP3"),
            RadioStation("heart_london", "Heart London", "https://media-ssl.musicradio.com/HeartLondonMP3"),
            RadioStation("lbc_london", "LBC London", "https://media-ssl.musicradio.com/LBCLondonMP3"),
            RadioStation("smooth_london", "Smooth London", "https://media-ssl.musicradio.com/SmoothLondonMP3")
        )

        // If nothing has ever been saved, return defaults
        if (raw == null) return defaultStations
        if (raw.isBlank()) return emptyList()

        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    val name = obj.optString("name", "")
                    val url = obj.optString("url", "")
                    if (id.isNotBlank() && name.isNotBlank() && url.isNotBlank()) {
                        add(RadioStation(id = id, name = name, url = url))
                    }
                }
            }
        } catch (_: Exception) {
            defaultStations
        }
    }

    private fun encodeStations(stations: List<RadioStation>): String {
        val arr = JSONArray()
        stations.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("url", s.url)
            arr.put(obj)
        }
        return arr.toString()
    }
}
