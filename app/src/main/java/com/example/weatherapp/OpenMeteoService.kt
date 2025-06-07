package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,apparent_temperature,uv_index,visibility,surface_pressure,windspeed_10m,precipitation,weathercode",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,weathercode,precipitation_probability_max,sunrise,sunset",
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,uv_index,visibility,pressure_msl,windspeed_10m",
        @Query("timezone") timezone: String = "auto"
    ): WeatherRespone.WeatherResponse
}
