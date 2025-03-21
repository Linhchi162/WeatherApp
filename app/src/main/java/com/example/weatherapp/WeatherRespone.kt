package com.example.weatherapp

class WeatherRespone {
    data class WeatherResponse(
        val hourly: Hourly
    )

    data class Hourly(
        val temperature_2m: List<Double>,
        val time: List<String>
    )
}