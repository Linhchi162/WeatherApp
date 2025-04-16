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
import android.util.Log

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
    var dailyTimeList: List<String> by mutableStateOf(emptyList())
        private set
    var dailyTempMaxList: List<Double> by mutableStateOf(emptyList())
        private set
    var dailyTempMinList: List<Double> by mutableStateOf(emptyList())
        private set
    var dailyWeatherCodeList: List<Int> by mutableStateOf(emptyList())
        private set
    var dailyPrecipitationList: List<Double> by mutableStateOf(emptyList())
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    fun fetchWeather(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                val weatherDataWithDetails = withContext(Dispatchers.IO) {
                    weatherDao.getLatestWeatherDataWithDetails()
                }
                val weatherDataWithDailyDetails = withContext(Dispatchers.IO) {
                    weatherDao.getLatestWeatherDataWithDailyDetails()
                }

                // Xử lý hourly
                if (weatherDataWithDetails != null && weatherDataWithDetails.details.isNotEmpty()) {
                    val details = weatherDataWithDetails.details
                    val now = LocalDateTime.now()
                    val startTime = now.minusHours(1)
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

                // Xử lý daily
                if (weatherDataWithDailyDetails != null && weatherDataWithDailyDetails.dailyDetails.isNotEmpty()) {
                    val dailyDetails = weatherDataWithDailyDetails.dailyDetails
                    dailyTimeList = dailyDetails.map { it.time }
                    dailyTempMaxList = dailyDetails.map { it.temperature_2m_max }
                    dailyTempMinList = dailyDetails.map { it.temperature_2m_min }
                    dailyWeatherCodeList = dailyDetails.map { it.weather_code }
                    dailyPrecipitationList = dailyDetails.map { it.precipitation_probability_max }
                } else {
                    errorMessage = "Đang tải dữ liệu thời tiết..."
                }
            } catch (e: Exception) {
                errorMessage = "Đang tải dữ liệu thời tiết..."
                Log.e("WeatherViewModel", "Lỗi khi lấy dữ liệu: ${e.message}")
            }
        }
    }

    fun getCurrentIndex(): Int {
        val now = LocalDateTime.now()
        val index = timeList.indexOfLast { time ->
            val detailTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            detailTime.isBefore(now) || detailTime == now
        }
        return if (index >= 0) index else 0
    }

    fun getUpcomingForecast(): List<Triple<String, Double, Int>> {
        val index = getCurrentIndex()
        return timeList.drop(index).take(24).mapIndexed { i, time ->
            Triple(time, temperatureList[index + i], weatherCodeList[index + i])
        }
    }

    fun getDailyForecast(days: Int): List<Triple<String, Pair<Double, Double>, Int>> {
        return dailyTimeList.take(days).mapIndexed { index, time ->
            Triple(
                time,
                Pair(dailyTempMaxList[index], dailyTempMinList[index]),
                dailyWeatherCodeList[index]
            )
        }
    }
}
