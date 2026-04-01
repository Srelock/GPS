package com.motorider.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motorider.data.entity.RouteEntity
import com.motorider.data.repository.RouteRepository
import com.motorider.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Ride History screen.
 */
@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    val routes: StateFlow<List<RouteEntity>> = routeRepository.getAllRoutes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val useMph: StateFlow<Boolean> = settingsRepository.useMph
    
    fun deleteRoute(routeId: Long) {
        viewModelScope.launch {
            routeRepository.deleteRouteById(routeId)
        }
    }
    
    /**
     * Convert km/h to mph if needed.
     */
    fun formatSpeed(speedKmh: Double): String {
        return if (useMph.value) {
            String.format("%.0f mph", speedKmh * 0.621371)
        } else {
            String.format("%.0f km/h", speedKmh)
        }
    }
    
    /**
     * Convert km to miles if needed.
     */
    fun formatDistance(distanceKm: Double): String {
        return if (useMph.value) {
            String.format("%.1f mi", distanceKm * 0.621371)
        } else {
            String.format("%.1f km", distanceKm)
        }
    }
}
