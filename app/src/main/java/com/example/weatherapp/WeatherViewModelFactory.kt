package com.example.weatherapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating WeatherViewModel instances with necessary dependencies.
 */
class WeatherViewModelFactory(
    private val context: Context,
    private val weatherDao: WeatherDao,
    private val openMeteoService: OpenMeteoService = RetrofitInstance.api,
    private val airQualityService: AirQualityService = RetrofitInstance.airQualityApi,
    private val geoNamesService: GeoNamesService = RetrofitInstance.geoNamesApi
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {

            return WeatherViewModel(
                weatherDao,
                openMeteoService,
                airQualityService,
                geoNamesService,
                context.applicationContext
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
