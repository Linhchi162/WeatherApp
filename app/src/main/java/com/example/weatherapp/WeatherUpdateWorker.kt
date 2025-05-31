package com.example.weatherapp

import android.content.Context

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

import java.time.LocalDate


class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val weatherDao = WeatherDatabase.getDatabase(applicationContext).weatherDao()
            val openMeteoService = RetrofitInstance.api
            val airQualityService = RetrofitInstance.airQualityApi
            val geoNamesService = RetrofitInstance.geoNamesApi

            val viewModel = WeatherViewModel(
                weatherDao,
                openMeteoService,
                airQualityService,
                geoNamesService
            )

            val cities = viewModel.citiesList

            if (cities.isEmpty()) {
                Log.w("WeatherUpdateWorker", "Không có thành phố nào để cập nhật")
                return@withContext Result.retry()
            }

            cities.forEach { city ->
                try {
                    val response = openMeteoService.getWeather(
                        latitude = city.latitude,
                        longitude = city.longitude
                    )

                    // Get previous weather data for comparison
                    val previousData = weatherDao.getLatestWeatherDataWithDailyDetailsForCity(city.name)
                    val today = LocalDate.now()
                    val tomorrow = today.plusDays(1)

                    // Delete old data
                    weatherDao.deleteWeatherDataForCity(city.name)
                    weatherDao.deleteWeatherDetailsForCity(city.name)
                    weatherDao.deleteDailyDetailsForCity(city.name)

                    // Insert new data
                    val weatherData = WeatherData(
                        cityName = city.name,
                        latitude = city.latitude,
                        longitude = city.longitude,
                        lastUpdated = System.currentTimeMillis()
                    )
                    val weatherDataId = weatherDao.insertWeatherData(weatherData)
                    Log.d("WeatherUpdateWorker", "Lưu WeatherData thành công với ID: $weatherDataId cho ${city.name}")

                    val hourlyDetails = response.hourly.time.mapIndexed { index, time ->
                        WeatherDetail(
                            weatherDataId = weatherDataId,
                            cityName = city.name,
                            time = time,
                            temperature_2m = response.hourly.temperature_2m[index],
                            weather_code = response.hourly.weathercode[index],
                            precipitation_probability = response.hourly.precipitation[index],
                            relative_humidity_2m = response.hourly.relative_humidity_2m[index],
                            wind_speed_10m = response.hourly.windspeed_10m[index],
                            uv_index = response.hourly.uv_index[index],
                            apparent_temperature = response.hourly.apparent_temperature[index],
                            surface_pressure = response.hourly.surface_pressure[index],
                            visibility = response.hourly.visibility[index]
                        )
                    }
                    weatherDao.insertWeatherDetails(hourlyDetails)

                    val dailyDetails = response.daily.time.mapIndexed { index, time ->
                        WeatherDailyDetail(
                            weatherDataId = weatherDataId,
                            cityName = city.name,
                            time = time,
                            temperature_2m_max = response.daily.temperature_2m_max[index],
                            temperature_2m_min = response.daily.temperature_2m_min[index],
                            weather_code = response.daily.weathercode[index],
                            precipitation_probability_max = response.daily.precipitation_probability_max[index],
                            sunrise = response.daily.sunrise[index],
                            sunset = response.daily.sunset[index]
                        )
                    }
                    weatherDao.insertWeatherDailyDetails(dailyDetails)

                    // Log sunrise and sunset times
                    response.daily.sunrise.forEachIndexed { index, sunrise ->
                        val sunset = response.daily.sunset[index]
                        Log.d("WeatherUpdateWorker", "Sunrise for ${city.name} on ${response.daily.time[index]}: $sunrise")
                        Log.d("WeatherUpdateWorker", "Sunset for ${city.name} on ${response.daily.time[index]}: $sunset")
                    }

                    Log.d("WeatherUpdateWorker", "Lưu ${hourlyDetails.size} WeatherDetail thành công cho ${city.name}")
                    Log.d("WeatherUpdateWorker", "Lưu ${dailyDetails.size} WeatherDailyDetail thành công cho ${city.name}")

                    // Compare with previous data
                    if (previousData != null) {
                        val prevDailyDetails = previousData.dailyDetails
                        dailyDetails.forEach { newDetail ->
                            val detailDate = LocalDate.parse(newDetail.time, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                            if (detailDate == today || detailDate == tomorrow) {
                                val prevDetail = prevDailyDetails.find {
                                    LocalDate.parse(it.time, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) == detailDate
                                }
                                if (prevDetail != null) {
                                    val tempMaxDiff = newDetail.temperature_2m_max - prevDetail.temperature_2m_max
                                    val tempMinDiff = newDetail.temperature_2m_min - prevDetail.temperature_2m_min
                                    val tempDiff = maxOf(tempMaxDiff, tempMinDiff)
                                    val precipDiff = newDetail.precipitation_probability_max - prevDetail.precipitation_probability_max
                                    val weatherCodeChanged = newDetail.weather_code != prevDetail.weather_code

                                    if (tempDiff >= 2f || tempDiff <= -2f || precipDiff >= 10f || precipDiff <= -10f || weatherCodeChanged) {
                                        val intent = Intent("com.example.weatherapp.WEATHER_DATA_CHANGED")
                                        intent.putExtra("city_name", city.name)
                                        intent.putExtra("date", newDetail.time)
                                        intent.putExtra("temp_diff", tempDiff)
                                        intent.putExtra("precip_diff", precipDiff)
                                        intent.putExtra("weather_code_changed", weatherCodeChanged)
                                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                                        Log.d("WeatherUpdateWorker", "Significant weather change detected for ${city.name} on ${newDetail.time}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WeatherUpdateWorker", "Lỗi khi cập nhật dữ liệu cho ${city.name}: ${e.message}", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("WeatherUpdateWorker", "Lỗi trong quá trình cập nhật thời tiết: ${e.message}", e)
            Result.retry()
        }
    }
}