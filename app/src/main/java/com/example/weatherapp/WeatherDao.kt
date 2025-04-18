package com.example.weatherapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface WeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherData(weatherData: WeatherData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherDetails(weatherDetails: List<WeatherDetail>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherDailyDetails(dailyDetails: List<WeatherDailyDetail>): List<Long>

    @Transaction
    @Query("SELECT * FROM weather_data WHERE cityName = :cityName ORDER BY id DESC LIMIT 1")
    suspend fun getLatestWeatherDataWithDetailsForCity(cityName: String): WeatherDataWithDetails?

    @Transaction
    @Query("SELECT * FROM weather_data WHERE cityName = :cityName ORDER BY id DESC LIMIT 1")
    suspend fun getLatestWeatherDataWithDailyDetailsForCity(cityName: String): WeatherDataWithDailyDetails?

    @Query("SELECT * FROM weather_detail WHERE weatherDataId = :weatherDataId")
    suspend fun getWeatherDetails(weatherDataId: Long): List<WeatherDetail>

    @Query("SELECT * FROM weather_daily_detail WHERE weatherDataId = :weatherDataId")
    suspend fun getWeatherDailyDetails(weatherDataId: Long): List<WeatherDailyDetail>

    @Query("DELETE FROM weather_data WHERE cityName = :cityName")
    suspend fun deleteWeatherDataForCity(cityName: String)

    @Query("DELETE FROM weather_detail WHERE cityName = :cityName")
    suspend fun deleteWeatherDetailsForCity(cityName: String)

    @Query("DELETE FROM weather_daily_detail WHERE cityName = :cityName")
    suspend fun deleteDailyDetailsForCity(cityName: String)

    @Query("SELECT * FROM weather_data")
    suspend fun getAllWeatherData(): List<WeatherData>
}
