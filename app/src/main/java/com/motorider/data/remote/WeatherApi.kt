package com.motorider.data.remote

import com.motorider.data.model.PrecipitationType
import com.motorider.data.model.WeatherData
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Open-Meteo API.
 * 
 * NO API KEY REQUIRED.
 * Free for non-commercial use.
 * https://open-meteo.com/
 */
interface WeatherApi {
    
    /**
     * Get current weather conditions.
     */
    @GET("forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh"
    ): OpenMeteoResponse
}

/**
 * Open-Meteo Response Model
 */
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val current: CurrentWeather
)

data class CurrentWeather(
    val time: String,
    val temperature_2m: Double,
    val relative_humidity_2m: Int,
    val weather_code: Int,
    val wind_speed_10m: Double,
    val wind_direction_10m: Int,
    val wind_gusts_10m: Double?,
    val visibility: Double?
)

/**
 * Extension to convert Open-Meteo response to domain model.
 */
fun OpenMeteoResponse.toWeatherData(): WeatherData {
    val current = this.current
    
    // WMO Weather Codes
    // https://open-meteo.com/en/docs
    val (precipType, description, icon) = when (current.weather_code) {
        0 -> Triple(PrecipitationType.NONE, "Clear sky", "01d")
        1, 2, 3 -> Triple(PrecipitationType.NONE, "Partly cloudy", "02d")
        45, 48 -> Triple(PrecipitationType.NONE, "Foggy", "50d")
        51, 53, 55 -> Triple(PrecipitationType.RAIN, "Drizzle", "09d")
        61, 63, 65 -> Triple(PrecipitationType.RAIN, "Rain", "10d")
        66, 67 -> Triple(PrecipitationType.SLEET, "Freezing Rain", "13d")
        71, 73, 75 -> Triple(PrecipitationType.SNOW, "Snow fall", "13d")
        77 -> Triple(PrecipitationType.SNOW, "Snow grains", "13d")
        80, 81, 82 -> Triple(PrecipitationType.RAIN, "Rain showers", "09d")
        85, 86 -> Triple(PrecipitationType.SNOW, "Snow showers", "13d")
        95 -> Triple(PrecipitationType.RAIN, "Thunderstorm", "11d")
        96, 99 -> Triple(PrecipitationType.HAIL, "Thunderstorm with hail", "11d")
        else -> Triple(PrecipitationType.NONE, "Unknown", "01d")
    }
    
    return WeatherData(
        temperature = current.temperature_2m,
        feelsLike = current.temperature_2m, // Open-Meteo requires 'apparent_temperature' separate param, using temp for simplicity or add param
        windSpeed = current.wind_speed_10m,
        windGustSpeed = current.wind_gusts_10m,
        windDirection = current.wind_direction_10m,
        humidity = current.relative_humidity_2m,
        precipitationType = precipType,
        precipitationIntensity = 0.0, // Not available in basic 'current' param without more parsing
        visibility = (current.visibility ?: 10000.0).toInt(),
        weatherDescription = description,
        weatherIcon = icon,
        timestamp = System.currentTimeMillis(),
        latitude = latitude,
        longitude = longitude
    )
}
