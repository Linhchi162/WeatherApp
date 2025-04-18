package com.example.weatherapp

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weather_daily_detail",
    foreignKeys = [ForeignKey(
        entity = WeatherData::class,
        parentColumns = ["id"],
        childColumns = ["weatherDataId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["weatherDataId"])]
)
data class WeatherDailyDetail(
    @PrimaryKey(autoGenerate = true) val detailId: Long = 0,
    val weatherDataId: Long,
    val cityName: String,
    val time: String,
    val temperature_2m_max: Double,
    val temperature_2m_min: Double,
    val precipitation_probability_max: Double,
    val weather_code: Int
)
