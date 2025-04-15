package com.example.weatherapp


import androidx.room.Embedded
import androidx.room.Relation

data class WeatherDataWithDailyDetails(
    @Embedded val weatherData: WeatherData,
    @Relation(
        parentColumn = "id",
        entityColumn = "weatherDataId",
        entity = WeatherDailyDetail::class
    )
    val dailyDetails: List<WeatherDailyDetail>
)
