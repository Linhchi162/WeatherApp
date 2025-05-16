package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        // Thêm us_aqi, pm2_5 vào hourly nếu muốn xem theo giờ
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,apparent_temperature,uv_index,visibility,surface_pressure,windspeed_10m,precipitation,weathercode",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,weathercode,precipitation_probability_max",
        // Thêm các tham số AQI vào current
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,uv_index,visibility,pressure_msl,windspeed_10m", // Thêm us_aqi, pm2_5
        @Query("timezone") timezone: String = "auto"
        // Lưu ý: Để lấy AQI, bạn có thể cần thêm "&domains=air_quality" vào URL nếu dùng endpoint riêng,
        // nhưng với endpoint /forecast, việc thêm vào 'current' hoặc 'hourly' thường là đủ.
        // Kiểm tra tài liệu Open-Meteo nếu cần thiết.
    ): WeatherRespone.WeatherResponse // Đảm bảo tên lớp Response là đúng
}
