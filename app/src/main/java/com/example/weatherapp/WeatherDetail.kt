package com.example.weatherapp

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weather_detail",
    foreignKeys = [ForeignKey(
        entity = WeatherData::class,
        parentColumns = ["id"],
        childColumns = ["weatherDataId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["weatherDataId"])]
)
data class WeatherDetail(
    @PrimaryKey(autoGenerate = true) val detailId: Long = 0,
    val cityName: String,
    val weatherDataId: Long,
    val time: String,
    val temperature_2m: Double,
    val uv_index: Double,
    val apparent_temperature: Double,
    val relative_humidity_2m: Double,
    val wind_speed_10m: Double,
    val surface_pressure: Double,
    val visibility: Double,
    val precipitation_probability: Double,
    val weather_code: Int
)
