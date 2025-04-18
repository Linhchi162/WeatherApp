package com.example.weatherapp

/**
 * Data class representing the overall response from the Open-Meteo Air Quality API.
 */
data class AirQualityResponse(
    val latitude: Double?,
    val longitude: Double?,
    val generationtime_ms: Double?,
    val utc_offset_seconds: Int?,
    val timezone: String?,
    val timezone_abbreviation: String?,
    val elevation: Double?,
    val current_units: AirQualityUnits?,
    val current: AirQualityCurrent?
    // Add hourly fields if hourly AQI is requested
    // val hourly_units: AirQualityUnits?,
    // val hourly: AirQualityHourly?
)

/**
 * Data class for units of the current air quality data.
 */
data class AirQualityUnits(
    val time: String?,
    val interval: String?,
    val us_aqi: String?,
    val pm2_5: String?
    // Add units for other requested parameters if any
)

/**
 * Data class for the current air quality data values.
 */
data class AirQualityCurrent(
    val time: String?,
    val interval: Int?,
    val us_aqi: Int?,
    val pm2_5: Double?
    // Add other requested AQI parameters if any
)

/**
 * Data class for hourly air quality data (if requested).
 */
// data class AirQualityHourly(
//     val time: List<String>?,
//     val us_aqi: List<Int?>?,
//     val pm2_5: List<Double?>?
//     // Add other requested hourly AQI parameters if any
// )
