package com.example.weatherapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WeatherViewModel(private val weatherDao: WeatherDao) : ViewModel() {
    var timeList: List<String> by mutableStateOf(emptyList())
        private set
    var temperatureList: List<Double> by mutableStateOf(emptyList())
        private set
    var weatherCodeList: List<Int> by mutableStateOf(emptyList())
        private set
    var precipitationList: List<Double> by mutableStateOf(emptyList())
        private set
    var humidityList: List<Double> by mutableStateOf(emptyList())
        private set
    var windSpeedList: List<Double> by mutableStateOf(emptyList())
        private set
    var uvList: List<Double> by mutableStateOf(emptyList())
        private set
    var feelsLikeList: List<Double> by mutableStateOf(emptyList())
        private set
    var pressureList: List<Double> by mutableStateOf(emptyList())
        private set
    var visibilityList: List<Double> by mutableStateOf(emptyList())
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    fun fetchWeather(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                val weatherDataWithDetails = withContext(Dispatchers.IO) {
                    weatherDao.getLatestWeatherDataWithDetails()
                }
                if (weatherDataWithDetails != null && weatherDataWithDetails.details.isNotEmpty()) {
                    val details = weatherDataWithDetails.details
                    timeList = details.map { it.time }
                    temperatureList = details.map { it.temperature_2m }
                    weatherCodeList = details.map { it.weather_code }
                    precipitationList = details.map { it.precipitation_probability }
                    humidityList = details.map { it.relative_humidity_2m }
                    windSpeedList = details.map { it.wind_speed_10m }
                    uvList = details.map { it.uv_index }
                    feelsLikeList = details.map { it.apparent_temperature }
                    pressureList = details.map { it.surface_pressure }
                    visibilityList = details.map { it.visibility }
                    errorMessage = null
                } else {
                    errorMessage = "Không có dữ liệu thời tiết. Đang chờ dữ liệu từ API..."
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi lấy dữ liệu: ${e.message}"
            }
        }
    }

    fun getCurrentIndex(): Int {
        val currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return timeList.indexOfFirst { it >= currentTime }.takeIf { it >= 0 } ?: timeList.lastIndex.coerceAtLeast(0)
    }

    fun getUpcomingForecast(): List<Pair<String, Double>> {
        return timeList.zip(temperatureList) { time, temp -> Pair(time, temp) }
    }
}