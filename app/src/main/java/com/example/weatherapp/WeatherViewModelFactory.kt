package com.example.weatherapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating WeatherViewModel instances with necessary dependencies.
 */
class WeatherViewModelFactory(
    private val weatherDao: WeatherDao,
    private val openMeteoService: OpenMeteoService = RetrofitInstance.api,
    private val airQualityService: AirQualityService = RetrofitInstance.airQualityApi,
    private val geoNamesService: GeoNamesService = RetrofitInstance.geoNamesApi
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            // Pass all required services to the ViewModel constructor
            return WeatherViewModel(
                weatherDao,
                openMeteoService,
                airQualityService,
                geoNamesService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
