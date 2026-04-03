package com.motorider.ui.dashboard

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motorider.core.alert.HapticSpeedAlertManager
import com.motorider.core.alert.SpeedAlertState
import com.motorider.core.audio.BluetoothAudioManager
import com.motorider.data.repository.LocationRepository
import com.motorider.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    // Speed settings delegated to repository
    val speedLimit: StateFlow<Double> = settingsRepository.speedLimitKmh
    val enableHapticAlerts: StateFlow<Boolean> = settingsRepository.hapticEnabled
    val useMph: StateFlow<Boolean> = settingsRepository.useMph

    // Overlay + Radio settings
    val overlayEnabled: StateFlow<Boolean> = settingsRepository.overlayEnabled
    val radioStations: StateFlow<List<SettingsRepository.RadioStation>> = settingsRepository.radioStations
    val selectedStationId: StateFlow<String?> = settingsRepository.selectedStationId
    
    // Current state from repositories
    val currentSpeed: StateFlow<Double> = locationRepository.currentSpeed
    val alertState: StateFlow<SpeedAlertState> = hapticAlertManager.alertState
    val roadSpeedLimit: StateFlow<Double?> = locationRepository.roadSpeedLimit
    
    // Combined UI state for the dashboard
    val dashboardState: StateFlow<DashboardUiState> = combine(
        currentSpeed, alertState, roadSpeedLimit
    ) { speed, alert, roadLimit ->
        DashboardUiState(
            speedKmh = speed,
            alertState = alert,
            roadSpeedLimit = roadLimit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
    
    init {
        // Initial setup if needed
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
    val roadSpeedLimit: Double? = null
) {
    val speedInt: Int get() = speedKmh.toInt()
}
