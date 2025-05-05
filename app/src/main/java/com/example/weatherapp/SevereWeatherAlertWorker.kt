package com.example.weatherapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.util.Log

class SevereWeatherAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val weatherDao = WeatherDatabase.getDatabase(applicationContext).weatherDao()
            val preferences = applicationContext.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val cityName = preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"

            val weatherData = weatherDao.getLatestWeatherDataWithDetailsForCity(cityName)
            if (weatherData == null || weatherData.details.isEmpty()) {
                Log.w("SevereWeatherAlertWorker", "No weather data available for $cityName")
                return@withContext Result.retry()
            }

            val now = LocalDateTime.now()
            val next15Minutes = now.plusMinutes(15)
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            val severeWeatherDetails = weatherData.details.filter { detail ->
                val detailTime = LocalDateTime.parse(detail.time, formatter)
                detailTime.isAfter(now) && detailTime.isBefore(next15Minutes) && WeatherUtils.isSevereWeather(detail.weather_code)
            }

            if (severeWeatherDetails.isEmpty()) {
                Log.d("SevereWeatherAlertWorker", "No severe weather expected in the next 15 minutes for $cityName")
                return@withContext Result.success()
            }

            severeWeatherDetails.forEach { detail ->
                val detailTime = LocalDateTime.parse(detail.time, formatter)
                val timeString = detailTime.format(timeFormatter)
                val description = WeatherUtils.getWeatherDescription(detail.weather_code)
                val emoji = WeatherUtils.getWeatherEmoji(detail.weather_code)
                val title = "Cảnh báo thời tiết khắc nghiệt tại $cityName"
                val message = "Thời tiết khắc nghiệt sắp xảy ra vào $timeString: $description $emoji"

                // Kiểm tra thông báo trùng lặp
                val lastNotificationTime = preferences.getString("last_severe_notification_time", null)
                val lastNotificationMessage = preferences.getString("last_severe_notification_message", null)

                if (lastNotificationTime == detail.time && lastNotificationMessage == message) {
                    Log.d("SevereWeatherAlertWorker", "Duplicate severe weather notification detected, skipping: $title - $message")
                    return@forEach
                }

                WeatherUtils.sendNotification(
                    context = applicationContext,
                    title = title,
                    message = message,
                    iconResId = WeatherUtils.getWeatherIcon(detail.weather_code),
                    channelId = "severe_weather_channel",
                    channelName = "Cảnh báo thời tiết khắc nghiệt"
                )

                // Lưu thông tin thông báo vừa gửi
                preferences.edit()
                    .putString("last_severe_notification_time", detail.time)
                    .putString("last_severe_notification_message", message)
                    .apply()

                Log.d("SevereWeatherAlertWorker", "Severe weather notification sent for $cityName at $timeString: $description")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SevereWeatherAlertWorker", "Error checking severe weather: ${e.message}", e)
            Result.retry()
        }
    }
}