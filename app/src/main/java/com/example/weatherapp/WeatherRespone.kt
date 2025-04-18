package com.example.weatherapp

class WeatherRespone {
    data class WeatherResponse(
        val hourly: Hourly,
        val daily: Daily, // Thêm daily
        val current: Current? // optional
    )

    data class Hourly(
        val temperature_2m: List<Double>,
        val time: List<String>,
        val uv_index: List<Double>,
        val relative_humidity_2m: List<Double>,
        val apparent_temperature: List<Double>,
        val surface_pressure: List<Double>,
        val windspeed_10m: List<Double>,
        val visibility: List<Double>,
        val precipitation: List<Double>,
        val weathercode: List<Int>,
    )

    data class Daily(
        val time: List<String>,
        val temperature_2m_max: List<Double>,
        val temperature_2m_min: List<Double>,
        val weathercode: List<Int>,
        val precipitation_probability_max: List<Double>
    )

    data class Current(
        val temperature_2m: Double?, // Nên để nullable cho an toàn
        val relative_humidity_2m: Double?,
        val apparent_temperature: Double?,
        val uv_index: Double?,
        val visibility: Double?,
        val pressure_msl: Double?, // Hoặc surface_pressure nếu đã đổi
        val windspeed_10m: Double?,
        val time: String?,
        // Thêm các trường AQI (nullable)
        val us_aqi: Int?,
        val pm2_5: Double?
    )
}
