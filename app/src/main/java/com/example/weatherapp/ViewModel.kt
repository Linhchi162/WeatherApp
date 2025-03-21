package com.example.weatherapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class WeatherViewModel : ViewModel() {

    var temperatureList by mutableStateOf<List<Double>>(emptyList())
        private set

    fun fetchWeather() {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.getWeather(21.0285, 105.8542)
                temperatureList = response.hourly.temperature_2m
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
