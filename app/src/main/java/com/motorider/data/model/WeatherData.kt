package com.motorider.data.model

/**
 * Weather data model for current conditions at a GPS coordinate.
 * This represents the data fetched from OpenWeatherMap API.
 */
data class WeatherData(
    val temperature: Double,          // Temperature in °C
    val feelsLike: Double,            // Feels-like temperature in °C
    val windSpeed: Double,            // Wind speed in km/h
    val windGustSpeed: Double?,       // Maximum gust speed in km/h (nullable)
    val windDirection: Int,           // Wind coming FROM this direction (0-360°, 0 = North)
    val humidity: Int,                // Relative humidity percentage
    val precipitationType: PrecipitationType,
    val precipitationIntensity: Double, // Precipitation intensity in mm/h
    val visibility: Int,              // Visibility in meters
    val weatherDescription: String,   // Human-readable weather description
    val weatherIcon: String,          // Icon code from API
    val timestamp: Long,              // When this data was fetched
    val latitude: Double,             // GPS latitude of the reading
    val longitude: Double,            // GPS longitude of the reading
    val locationName: String? = null  // City/Location name
)

/**
 * Types of precipitation that can affect motorcycle riding.
 */
enum class PrecipitationType {
    NONE,   // Clear or cloudy, no precipitation
    RAIN,   // Rain of any intensity
    SNOW,   // Snowfall
    SLEET,  // Mix of rain and snow
    HAIL    // Hailstorm (dangerous for riders)
}

/**
 * Calculated relative wind data based on rider's current heading.
 * Used to display crosswind warnings.
 */
data class RelativeWind(
    val type: WindType,
    val relativeAngle: Int,           // Angle relative to heading (-180 to 180°)
    val effectiveSpeed: Double,       // Wind speed in km/h
    val gustSpeed: Double?,           // Gust speed in km/h
    val alertLevel: WindAlertLevel
)

/**
 * Classification of wind relative to rider direction.
 */
enum class WindType {
    HEADWIND,           // Wind from ahead (±30° from heading)
    TAILWIND,           // Wind from behind (±30° from opposite heading)
    CROSSWIND_LEFT,     // Wind from the left side
    CROSSWIND_RIGHT     // Wind from the right side
}

/**
 * Alert levels for wind conditions based on gust speed.
 * Thresholds based on motorcycle safety recommendations.
 */
enum class WindAlertLevel {
    NORMAL,      // < 40 km/h gusts - Safe riding conditions
    WARNING,     // 40-60 km/h gusts - Caution advised (Orange UI)
    DANGER       // > 60 km/h gusts - Dangerous conditions (Red UI)
}
