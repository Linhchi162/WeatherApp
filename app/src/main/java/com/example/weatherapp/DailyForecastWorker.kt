package com.example.weatherapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DailyForecastWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.d("DailyForecastWorker", "Starting doWork for tags: $tags")
        return try {
            val preferences = applicationContext.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val cityName = preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"
            val weatherDao = WeatherDatabase.getDatabase(applicationContext).weatherDao()
            val weatherDataWithDailyDetails = weatherDao.getLatestWeatherDataWithDailyDetailsForCity(cityName)

            if (weatherDataWithDailyDetails == null || weatherDataWithDailyDetails.dailyDetails.isEmpty()) {
                Log.e("DailyForecastWorker", "No weather data available for $cityName")
                return Result.failure()
            }

            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val isTomorrowForecast = tags.contains("forecast_tomorrow") || tags.contains("forecast_tomorrow_immediate")
            val targetDate = if (isTomorrowForecast) tomorrow else today
            val forecast = weatherDataWithDailyDetails.dailyDetails.find { detail ->
                val detailDate = LocalDate.parse(detail.time, DateTimeFormatter.ISO_LOCAL_DATE)
                detailDate == targetDate
            }

            if (forecast == null) {
                Log.e("DailyForecastWorker", "No forecast data for ${if (isTomorrowForecast) "tomorrow" else "today"} in $cityName")
                return Result.failure()
            }

            val maxTemp = forecast.temperature_2m_max.toInt()
            val minTemp = forecast.temperature_2m_min.toInt()
            val precipitation = forecast.precipitation_probability_max.toInt()
            val weatherCode = forecast.weather_code
            val weatherDescription = WeatherUtils.getWeatherDescription(weatherCode, cityName)
            val weatherEmoji = WeatherUtils.getWeatherEmoji(weatherCode)
            val weatherIcon = WeatherUtils.getWeatherIcon(weatherCode)

            val title = "Dự báo thời tiết ${if (isTomorrowForecast) "ngày mai" else "hôm nay"} cho $cityName"
            val message = "Thời tiết: $weatherEmoji $weatherDescription\n" +
                    "Nhiệt độ: $minTemp°C - $maxTemp°C\n" +
                    "Khả năng mưa: $precipitation%"

            sendNotification(applicationContext, title, message, weatherIcon)
            Log.d("DailyForecastWorker", "Notification sent for ${if (isTomorrowForecast) "tomorrow" else "today"}: $title - $message")
            Result.success()
        } catch (e: Exception) {
            Log.e("DailyForecastWorker", "Error in DailyForecastWorker: ${e.message}", e)
            Result.failure()
        }
    }

    private fun sendNotification(context: Context, title: String, message: String, iconResId: Int) {
        Log.d("DailyForecastWorker", "Attempting to send notification: $title")
        val notificationManager = NotificationManagerCompat.from(context)

        val channel = android.app.NotificationChannel(
            "daily_forecast_channel",
            "Dự báo thời tiết hàng ngày",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Thông báo dự báo thời tiết hàng ngày"
        }
        notificationManager.createNotificationChannel(channel)

        val validIconResId = if (WeatherUtils.isResourceAvailable(context, iconResId)) {
            Log.d("DailyForecastWorker", "Using weather icon: $iconResId")
            iconResId
        } else {
            Log.w("DailyForecastWorker", "Weather icon not found, using default icon")
            android.R.drawable.ic_dialog_info
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "daily_forecast_channel")
            .setSmallIcon(validIconResId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
                Log.d("DailyForecastWorker", "Notification successfully sent")
            } else {
                Log.w("DailyForecastWorker", "Notification permission not granted")
            }
        } catch (e: SecurityException) {
            Log.e("DailyForecastWorker", "SecurityException when sending notification: ${e.message}", e)
        }
    }
}