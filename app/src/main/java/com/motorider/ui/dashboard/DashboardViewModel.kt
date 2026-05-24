package com.motorider.ui.dashboard

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motorider.core.alert.HapticSpeedAlertManager
import com.motorider.core.alert.SpeedAlertState
import com.motorider.core.audio.BluetoothAudioManager
import com.motorider.data.repository.LocationRepository
import com.motorider.data.repository.RadioBrowserRepository
import com.motorider.data.repository.RadioBrowserStation
import com.motorider.data.repository.RouteRepository
import com.motorider.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the main dashboard screen.
 * Combines location, weather, and trip data into UI state.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val hapticAlertManager: HapticSpeedAlertManager,
    private val audioManager: BluetoothAudioManager,
    private val settingsRepository: SettingsRepository,
    private val routeRepository: RouteRepository,
    private val radioBrowserRepository: RadioBrowserRepository
) : ViewModel() {

    private val _radioBrowseResults = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val radioBrowseResults: StateFlow<List<RadioBrowserStation>> = _radioBrowseResults.asStateFlow()

    private val _radioBrowseLoading = MutableStateFlow(false)
    val radioBrowseLoading: StateFlow<Boolean> = _radioBrowseLoading.asStateFlow()

    private val _radioBrowseError = MutableStateFlow<String?>(null)
    val radioBrowseError: StateFlow<String?> = _radioBrowseError.asStateFlow()
    
    // Speed settings delegated to repository
    val speedLimit: StateFlow<Double> = settingsRepository.speedLimitKmh
    val enableHapticAlerts: StateFlow<Boolean> = settingsRepository.hapticEnabled
    val useMph: StateFlow<Boolean> = settingsRepository.useMph

    // Overlay + Radio settings
    val overlayEnabled: StateFlow<Boolean> = settingsRepository.overlayEnabled
    val radioStations: StateFlow<List<SettingsRepository.RadioStation>> = settingsRepository.radioStations
    val selectedStationId: StateFlow<String?> = settingsRepository.selectedStationId

    // HUD layout mode
    val hudMode: StateFlow<SettingsRepository.HudMode> = settingsRepository.hudMode

    // Speed cameras
    val speedCamerasEnabled: StateFlow<Boolean> = settingsRepository.speedCamerasEnabled
    val speedCameraAlertDistance: StateFlow<Double> = settingsRepository.speedCameraAlertDistance
    val nearestCameraDistance: StateFlow<Float?> = locationRepository.nearestCameraDistance

    // Routes
    val routesJson: StateFlow<String> = settingsRepository.routesJson
    val activeRouteId: StateFlow<String?> = settingsRepository.activeRouteId
    val isRecordingRoute: StateFlow<Boolean> = settingsRepository.isRecordingRoute
    
    // Current state from repositories
    val currentSpeed: StateFlow<Double> = locationRepository.currentSpeed
    val alertState: StateFlow<SpeedAlertState> = hapticAlertManager.alertState
    val roadSpeedLimit: StateFlow<Double?> = locationRepository.roadSpeedLimit
    
    // Combined UI state for the dashboard
    val dashboardState: StateFlow<DashboardUiState> = combine(
        currentSpeed, alertState, roadSpeedLimit, locationRepository.nearestCameraDistance
    ) { speed, alert, roadLimit, cameraDist ->
        DashboardUiState(
            speedKmh = speed,
            alertState = alert,
            roadSpeedLimit = roadLimit,
            nearestCameraDistanceM = cameraDist
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
    
    init {
        setSpeedCameraAlertDistance(150.0)
        // Keep GPS active on the dashboard even if the foreground service is delayed (emulator/testing).
        viewModelScope.launch {
            locationRepository.startLocationUpdates(highAccuracy = true).collect { }
        }
    }
    
    /**
     * Update speed limit setting.
     */
    fun setSpeedLimit(limitKmh: Double) {
        settingsRepository.setSpeedLimit(limitKmh)
    }
    
    /**
     * Toggle haptic alerts.
     */
    fun setHapticAlertsEnabled(enabled: Boolean) {
        settingsRepository.setHapticEnabled(enabled)
        if (!enabled) {
            hapticAlertManager.stopAlerts()
        }
    }
    
    /**
     * Toggle mph/km/h display.
     */
    fun setUseMph(useMph: Boolean) {
        settingsRepository.setUseMph(useMph)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        settingsRepository.setOverlayEnabled(enabled)
    }

    fun upsertRadioStation(name: String, url: String) {
        settingsRepository.upsertStation(name = name, url = url)
    }

    fun removeRadioStation(id: String) {
        settingsRepository.removeStation(id)
    }

    fun setSelectedRadioStation(id: String?) {
        settingsRepository.setSelectedStation(id)
    }

    fun searchRadioBrowser(query: String) {
        viewModelScope.launch {
            _radioBrowseLoading.value = true
            _radioBrowseError.value = null
            try {
                val results = radioBrowserRepository.searchByName(query)
                _radioBrowseResults.value = results
                if (results.isEmpty() && query.trim().length >= 2) {
                    _radioBrowseError.value = "No stations found"
                }
            } catch (e: Exception) {
                _radioBrowseError.value = "Search failed"
                _radioBrowseResults.value = emptyList()
            } finally {
                _radioBrowseLoading.value = false
            }
        }
    }

    fun loadRadioBrowserUk() {
        viewModelScope.launch {
            _radioBrowseLoading.value = true
            _radioBrowseError.value = null
            try {
                val results = radioBrowserRepository.stationsByCountryCode("gb")
                _radioBrowseResults.value = results
                if (results.isEmpty()) {
                    _radioBrowseError.value = "No UK stations returned"
                }
            } catch (e: Exception) {
                _radioBrowseError.value = "Could not load UK stations"
                _radioBrowseResults.value = emptyList()
            } finally {
                _radioBrowseLoading.value = false
            }
        }
    }

    fun loadRadioBrowserByTag(tag: String) {
        viewModelScope.launch {
            _radioBrowseLoading.value = true
            _radioBrowseError.value = null
            try {
                val results = radioBrowserRepository.stationsByTag(tag)
                _radioBrowseResults.value = results
                if (results.isEmpty()) {
                    _radioBrowseError.value = "No stations for \"$tag\""
                }
            } catch (e: Exception) {
                _radioBrowseError.value = "Could not load tag"
                _radioBrowseResults.value = emptyList()
            } finally {
                _radioBrowseLoading.value = false
            }
        }
    }

    fun addRadioBrowserStationToFavourites(station: RadioBrowserStation) {
        val radio = station.toRadioStation()
        settingsRepository.upsertStation(
            name = radio.name,
            url = radio.url,
            id = radio.id
        )
        settingsRepository.setSelectedStation(radio.id)
    }

    fun clearRadioBrowseResults() {
        _radioBrowseResults.value = emptyList()
        _radioBrowseError.value = null
    }

    // HUD mode
    fun setHudMode(mode: SettingsRepository.HudMode) {
        settingsRepository.setHudMode(mode)
    }

    // Speed cameras
    fun setSpeedCamerasEnabled(enabled: Boolean) {
        settingsRepository.setSpeedCamerasEnabled(enabled)
    }

    fun setSpeedCameraAlertDistance(distanceM: Double) {
        settingsRepository.setSpeedCameraAlertDistance(distanceM)
    }

    // Routes
    fun setRoutesJson(json: String) {
        settingsRepository.setRoutesJson(json)
    }

    fun setActiveRouteId(id: String?) {
        settingsRepository.setActiveRouteId(id)
    }

    fun setRecordingRoute(recording: Boolean) {
        settingsRepository.setRecordingRoute(recording)
    }

    /** Clears the buffer and enables GPS capture for a new route. */
    fun startRouteRecording() {
        routeRepository.beginRecordingSession()
        settingsRepository.setRecordingRoute(true)
    }

    fun cancelRouteRecording() {
        routeRepository.endRecordingSession()
        settingsRepository.setRecordingRoute(false)
    }

    /**
     * Persists the in-memory recording buffer as a named route.
     * @return false if the route is too short or the name is blank.
     */
    fun saveRouteRecording(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        if (!routeRepository.saveRecording(trimmed)) return false
        settingsRepository.setRecordingRoute(false)
        return true
    }
    
    /**
     * Announce current speed via Bluetooth audio.
     */
    fun announceSpeed() {
        if (audioManager.isReady.value) {
            audioManager.announceSpeedWarning(
                currentSpeed = currentSpeed.value.toInt(),
                speedLimit = speedLimit.value.toInt()
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        hapticAlertManager.stopAlerts()
    }
}

/**
 * UI state for the dashboard screen.
 */
data class DashboardUiState(
    val speedKmh: Double = 0.0,
    val alertState: SpeedAlertState = SpeedAlertState.NORMAL,
    val roadSpeedLimit: Double? = null,
    val nearestCameraDistanceM: Float? = null
) {
    val speedInt: Int get() = speedKmh.toInt()
}

