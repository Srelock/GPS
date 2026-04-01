package com.motorider.ui.dashboard

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motorider.core.alert.HapticSpeedAlertManager
import com.motorider.core.alert.SpeedAlertState
import com.motorider.core.audio.BluetoothAudioManager
import com.motorider.core.weather.WindCalculator
import com.motorider.core.performance.PerformanceTracker
import com.motorider.core.performance.PerformanceStats
import com.motorider.data.entity.ActiveTrip
import com.motorider.data.model.RelativeWind
import com.motorider.data.model.WeatherData
import com.motorider.data.repository.LocationRepository
import com.motorider.data.repository.SettingsRepository
import com.motorider.data.repository.WeatherRepository
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
    private val weatherRepository: WeatherRepository,
    private val hapticAlertManager: HapticSpeedAlertManager,
    private val audioManager: BluetoothAudioManager,
    private val settingsRepository: SettingsRepository,
    private val performanceTracker: PerformanceTracker
) : ViewModel() {
    
    // Speed settings delegated to repository
    val speedLimit: StateFlow<Double> = settingsRepository.speedLimitKmh
    val enableHapticAlerts: StateFlow<Boolean> = settingsRepository.hapticEnabled
    val useMph: StateFlow<Boolean> = settingsRepository.useMph
    val autoRecordEnabled: StateFlow<Boolean> = settingsRepository.autoRecordEnabled
    
    // Current state from repositories
    val currentSpeed: StateFlow<Double> = locationRepository.currentSpeed
    val currentLocation: StateFlow<Location?> = locationRepository.currentLocation
    val currentHeading: StateFlow<Float> = locationRepository.currentHeading
    val alertState: StateFlow<SpeedAlertState> = hapticAlertManager.alertState
    val weather: StateFlow<WeatherData?> = weatherRepository.currentWeather
    val isWeatherLoading: StateFlow<Boolean> = weatherRepository.isLoading
    val performanceStats: StateFlow<PerformanceStats> = performanceTracker.stats
    
    // Trip state
    private val _activeTrip = MutableStateFlow<ActiveTrip?>(null)
    val activeTrip: StateFlow<ActiveTrip?> = _activeTrip.asStateFlow()
    
    private val _isTripActive = MutableStateFlow(false)
    val isTripActive: StateFlow<Boolean> = _isTripActive.asStateFlow()
    
    // Calculated relative wind
    private val _relativeWind = MutableStateFlow<RelativeWind?>(null)
    val relativeWind: StateFlow<RelativeWind?> = _relativeWind.asStateFlow()
    
    // Combined UI state for the dashboard
    val dashboardState: StateFlow<DashboardUiState> = combine(
        combine(currentSpeed, alertState, weather) { speed, alert, weather ->
            Triple(speed, alert, weather)
        },
        combine(activeTrip, isTripActive, performanceStats) { trip, isActive, stats ->
            Triple(trip, isActive, stats)
        }
    ) { (speed, alert, weather), (trip, isActive, stats) ->
        DashboardUiState(
            speedKmh = speed,
            alertState = alert,
            weather = weather,
            activeTrip = trip,
            isTripActive = isActive,
            performanceStats = stats
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
    
    init {
        // Observe heading changes to update relative wind
        viewModelScope.launch {
            combine(currentHeading, weather) { heading, weather ->
                weather?.let { w ->
                    WindCalculator.calculateRelativeWind(
                        windDirectionDegrees = w.windDirection,
                        riderHeadingDegrees = heading.toInt(),
                        windSpeedKmh = w.windSpeed,
                        gustSpeedKmh = w.windGustSpeed
                    )
                }
            }.collect { wind ->
                _relativeWind.value = wind
            }
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
    
    /**
     * Toggle auto-record trips.
     */
    fun setAutoRecordEnabled(enabled: Boolean) {
        settingsRepository.setAutoRecordEnabled(enabled)
    }
    
    /**
     * Start recording a new trip.
     */
    fun startTrip() {
        _activeTrip.value = ActiveTrip()
        _isTripActive.value = true
    }
    
    /**
     * Stop and save the current trip.
     */
    fun stopTrip() {
        // Trip saving handled by service
        _activeTrip.value = null
        _isTripActive.value = false
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
    
    /**
     * Announce current weather conditions.
     */
    fun announceWeather() {
        val wind = relativeWind.value ?: return
        val description = WindCalculator.getWindDescription(wind)
        audioManager.announceWeatherAlert(description)
    }
    
    /**
     * Force refresh weather data.
     */
    fun refreshWeather() {
        viewModelScope.launch {
            currentLocation.value?.let { location ->
                weatherRepository.fetchWeather(location, forceRefresh = true)
            }
        }
    }
    
    /**
     * Test haptic vibration patterns.
     */
    fun testHaptics(state: SpeedAlertState) {
        hapticAlertManager.testVibration(state)
    }
    
    /**
     * Reset the top speed record.
     */
    fun resetTopSpeed() {
        performanceTracker.resetTopSpeed()
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
    val weather: WeatherData? = null,
    val activeTrip: ActiveTrip? = null,
    val isTripActive: Boolean = false,
    val performanceStats: PerformanceStats = PerformanceStats()
) {
    val speedInt: Int get() = speedKmh.toInt()
    
    val tripDistanceKm: Double
        get() = (activeTrip?.totalDistanceMeters ?: 0.0) / 1000.0
    
    val tripDurationFormatted: String
        get() {
            val elapsed = activeTrip?.elapsedMillis ?: 0
            val totalSeconds = elapsed / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
}
