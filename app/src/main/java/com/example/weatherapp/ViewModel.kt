package com.example.weatherapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
                    // Lấy dữ liệu từ thời gian hiện tại trở về trước 1 giờ và 24 giờ sau
                    val now = LocalDateTime.now()
                    val startTime = now.minusHours(1) // Lấy dữ liệu từ 1 giờ trước để đảm bảo có mốc gần nhất
                    val endTime = now.plusHours(24)
                    val filteredDetails = details.filter {
                        val detailTime = LocalDateTime.parse(it.time, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        detailTime.isAfter(startTime) && detailTime.isBefore(endTime)
                    }

                    timeList = filteredDetails.map { it.time }
                    temperatureList = filteredDetails.map { it.temperature_2m }
                    weatherCodeList = filteredDetails.map { it.weather_code }
                    precipitationList = filteredDetails.map { it.precipitation_probability }
                    humidityList = filteredDetails.map { it.relative_humidity_2m }
                    windSpeedList = filteredDetails.map { it.wind_speed_10m }
                    uvList = filteredDetails.map { it.uv_index }
                    feelsLikeList = filteredDetails.map { it.apparent_temperature }
                    pressureList = filteredDetails.map { it.surface_pressure }
                    visibilityList = filteredDetails.map { it.visibility }
                    errorMessage = null
                } else {
                    errorMessage = "Đang tải dữ liệu thời tiết..."
                }
            } catch (e: Exception) {
                errorMessage = "Đang tải dữ liệu thời tiết..."
            }
        }
    }

    fun getCurrentIndex(): Int {
        val now = LocalDateTime.now()
        // Tìm mốc thời gian gần nhất trước hoặc tại thời gian hiện tại
        val index = timeList.indexOfLast { time ->
            val detailTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            detailTime.isBefore(now) || detailTime == now
        }
        return if (index >= 0) index else 0
    }

    fun getUpcomingForecast(): List<Triple<String, Double, Int>> {
        val index = getCurrentIndex()
        // Lấy tối đa 5 mốc thời gian từ mốc gần nhất
        return timeList.drop(index).take(5).mapIndexed { i, time ->
            Triple(time, temperatureList[index + i], weatherCodeList[index + i])
        }
    }
}
