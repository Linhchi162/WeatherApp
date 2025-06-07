package com.example.weatherapp


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
)

data class AirQualityUnits(
    val time: String?,
    val interval: String?,
    val us_aqi: String?,
    val pm2_5: String?
)


data class AirQualityCurrent(
    val time: String?,
    val interval: Int?,
    val us_aqi: Int?,
    val pm2_5: Double?
)

