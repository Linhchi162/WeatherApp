package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,apparent_temperature,uv_index,visibility,pressure_msl,windspeed_10m",
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,uv_index,visibility,pressure_msl,windspeed_10m"
    ): WeatherRespone.WeatherResponse
}
