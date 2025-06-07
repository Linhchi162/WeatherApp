package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query


interface AirQualityService {

    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "us_aqi,pm2_5",
        @Query("timezone") timezone: String = "auto"
    ): AirQualityResponse
}
