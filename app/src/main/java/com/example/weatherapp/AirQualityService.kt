package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for fetching air quality data from Open-Meteo Air Quality API.
 */
interface AirQualityService {

    /**
     * Fetches current air quality data.
     * @param latitude Latitude of the location.
     * @param longitude Longitude of the location.
     * @param current Comma-separated list of current air quality variables to fetch (e.g., "us_aqi,pm2_5").
     * @param timezone Timezone for the location.
     * @return AirQualityResponse object containing the requested data.
     */
    @GET("v1/air-quality") // Air Quality API endpoint
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "us_aqi,pm2_5", // Request desired AQI parameters
        @Query("timezone") timezone: String = "auto"
        // Add hourly query if hourly AQI data is needed
        // @Query("hourly") hourly: String = "us_aqi,pm2_5"
    ): AirQualityResponse // Use the specific response data class
}
