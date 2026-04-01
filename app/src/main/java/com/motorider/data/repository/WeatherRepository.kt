package com.motorider.data.repository

import android.location.Location
import com.motorider.data.model.WeatherData
import com.motorider.data.remote.WeatherApi
import com.motorider.data.remote.toWeatherData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for weather data with smart caching and battery optimization.
 * 
 * Implements distance-based and time-based cache invalidation:
 * - Cache valid for 15 minutes
 * - Cache invalidated if rider moves more than 10km from last check
 */
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class WeatherRepository @Inject constructor(
    private val api: WeatherApi,
    @ApplicationContext private val context: Context
) {
    // Helper property to fix the previous replacement reference
    private val apiOrContext = context
    private val _currentWeather = MutableStateFlow<WeatherData?>(null)
    val currentWeather: StateFlow<WeatherData?> = _currentWeather.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Cache control
    private var lastFetchLocation: Location? = null
    private var lastFetchTime: Long = 0
    
    companion object {
        private const val CACHE_DURATION_MS = 15 * 60 * 1000L // 15 minutes
        private const val CACHE_DISTANCE_METERS = 10_000f     // 10 km
    }
    
    /**
     * Fetch weather data for the given location.
     * Uses cached data if still valid.
     * 
     * @param location Current GPS location
     * @param forceRefresh Bypass cache and fetch fresh data
     * @return WeatherData or null if fetch fails
     */
    suspend fun fetchWeather(location: Location, forceRefresh: Boolean = false): WeatherData? {
        // Check cache validity
        if (!forceRefresh && isCacheValid(location)) {
            return _currentWeather.value
        }
        
        _isLoading.value = true
        _error.value = null
        
        return try {
            val response = api.getCurrentWeather(
                lat = location.latitude,
                lon = location.longitude
            )
            
            val weatherData = response.toWeatherData()
            
            // Reverse geocoding to get city name
            var locationName: String? = null
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                     // We could use the async API here but for simplicity in this suspended function causing compilation complexity,
                     // let's stick to the simpler blocking call wrapped in IO dispatcher if needed,
                     // or just suppress the deprecation for older APIs or use the blocking one which is fine in suspend function context 
                     // if we are on IO dispatcher. Retrofit suspend functions run on main safe? 
                     // Actually, let's inject Geocoder or just create it here.
                     // The Geocoder call is blocking.
                     @Suppress("DEPRECATION")
                     val addresses = android.location.Geocoder(apiOrContext, java.util.Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                     if (!addresses.isNullOrEmpty()) {
                         locationName = addresses[0].locality ?: addresses[0].subAdminArea
                     }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = android.location.Geocoder(apiOrContext, java.util.Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                     if (!addresses.isNullOrEmpty()) {
                         locationName = addresses[0].locality ?: addresses[0].subAdminArea
                     }
                }
            } catch (e: Exception) {
                // Ignore geocoding errors
            }
            
            val finalData = weatherData.copy(locationName = locationName)
            _currentWeather.value = finalData
            
            // Update cache markers
            lastFetchLocation = location
            lastFetchTime = System.currentTimeMillis()
            
            finalData
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to fetch weather"
            null
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Check if cached weather data is still valid.
     */
    private fun isCacheValid(currentLocation: Location): Boolean {
        val cachedData = _currentWeather.value ?: return false
        val lastLocation = lastFetchLocation ?: return false
        
        // Check time-based expiry
        val timeSinceLastFetch = System.currentTimeMillis() - lastFetchTime
        if (timeSinceLastFetch > CACHE_DURATION_MS) {
            return false
        }
        
        // Check distance-based expiry
        val distanceFromLastFetch = currentLocation.distanceTo(lastLocation)
        if (distanceFromLastFetch > CACHE_DISTANCE_METERS) {
            return false
        }
        
        return true
    }
    
    /**
     * Clear cached weather data.
     */
    fun clearCache() {
        _currentWeather.value = null
        lastFetchLocation = null
        lastFetchTime = 0
    }
}
