package com.example.weatherapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface WeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWeatherData(weatherData: WeatherData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWeatherDetails(weatherDetails: List<WeatherDetail>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWeatherDailyDetails(dailyDetails: List<WeatherDailyDetail>): List<Long>

    @Transaction
    @Query("SELECT * FROM weather_data ORDER BY id DESC LIMIT 1")
    fun getLatestWeatherDataWithDetails(): WeatherDataWithDetails?

    @Transaction
    @Query("SELECT * FROM weather_data ORDER BY id DESC LIMIT 1")
    fun getLatestWeatherDataWithDailyDetails(): WeatherDataWithDailyDetails?

    @Query("SELECT * FROM weather_detail WHERE weatherDataId = :weatherDataId")
    fun getWeatherDetails(weatherDataId: Long): List<WeatherDetail>

    @Query("SELECT * FROM weather_daily_detail WHERE weatherDataId = :weatherDataId")
    fun getWeatherDailyDetails(weatherDataId: Long): List<WeatherDailyDetail>
}
