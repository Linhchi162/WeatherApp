package com.example.weatherapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class WeatherViewModel : ViewModel() {

    var temperatureList by mutableStateOf<List<Double>>(emptyList())
        private set
    var uvList by mutableStateOf<List<Double>>(emptyList())
        private set

    var feelsLikeList by mutableStateOf<List<Double>>(emptyList())
        private set

    var humidityList by mutableStateOf<List<Double>>(emptyList())
        private set

    var windSpeedList by mutableStateOf<List<Double>>(emptyList())
        private set

    var pressureList by mutableStateOf<List<Double>>(emptyList())
        private set

    var visibilityList by mutableStateOf<List<Double>>(emptyList())
        private set

    var timeList by mutableStateOf<List<String>>(emptyList())
        private set

    var precipitationList by mutableStateOf<List<Double>>(emptyList())
        private set

    var weatherCodeList by mutableStateOf<List<Int>>(emptyList())
        private set

    fun fetchWeather() {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.getWeather(
                    21.0285, 105.8542,
                    hourly = "temperature_2m,uv_index,relative_humidity_2m,apparent_temperature,surface_pressure,windspeed_10m,visibility,precipitation,weathercode"
                )
                temperatureList = response.hourly.temperature_2m
                uvList = response.hourly.uv_index
                feelsLikeList = response.hourly.apparent_temperature
                humidityList = response.hourly.relative_humidity_2m
                windSpeedList = response.hourly.windspeed_10m
                pressureList = response.hourly.surface_pressure
                visibilityList = response.hourly.visibility
                timeList = response.hourly.time
                precipitationList = response.hourly.precipitation
                weatherCodeList = response.hourly.weathercode





            } catch (e: Exception) {
                println("ERROR: ${e.message}")
            }
        }
    }
    fun getUpcomingForecast(hours: Int = 4): List<Pair<String, Double>> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val now = LocalDateTime.now()

        val index = timeList.indexOfFirst {
            try {
                LocalDateTime.parse(it, formatter).isAfter(now) || LocalDateTime.parse(it, formatter).isEqual(now)
            } catch (e: Exception) {
                false
            }
        }

        return if (index != -1 && index + hours <= temperatureList.size) {
            (index until index + hours).map { i ->
                Pair(timeList[i], temperatureList[i])
            }
        } else emptyList()
    }
    fun getCurrentIndex(): Int {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val now = LocalDateTime.now()
        return timeList.indexOfFirst {
            try {
                LocalDateTime.parse(it, formatter).isAfter(now) || LocalDateTime.parse(it, formatter).isEqual(now)
            } catch (e: Exception) {
                false
            }
        }.coerceAtLeast(0)
    }

}
