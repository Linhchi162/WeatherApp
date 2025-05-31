package com.example.weatherapp

import androidx.room.Embedded
import androidx.room.Relation

data class WeatherDataWithDetails(
    @Embedded val weatherData: WeatherData,
    @Relation(
        parentColumn = "id",
        entityColumn = "weatherDataId",
        entity = WeatherDetail::class
    )
    val details: List<WeatherDetail>
)
