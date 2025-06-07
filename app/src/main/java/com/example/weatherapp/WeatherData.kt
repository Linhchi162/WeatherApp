package com.example.weatherapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_data")
data class WeatherData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val lastUpdated: Long
)

  
