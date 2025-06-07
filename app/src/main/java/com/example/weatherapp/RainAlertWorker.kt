package com.example.weatherapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RainAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.d("RainAlertWorker", "Starting doWork for tags: $tags")
        return try {
            val preferences = applicationContext.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val isRainAlertEnabled = preferences.getBoolean("rain_alert_enabled", false)
            if (!isRainAlertEnabled) {
                Log.d("RainAlertWorker", "Rain alert is disabled, skipping")
                return Result.success()
            }

            val startTimeStr = preferences.getString("rain_alert_start_time", "6:00") ?: "6:00"
            val endTimeStr = preferences.getString("rain_alert_end_time", "22:00") ?: "22:00"
            val currentTime = LocalTime.now()
            val startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"))
            val endTime = LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm"))

            Log.d("RainAlertWorker", "Current time: $currentTime, Start time: $startTime, End time: $endTime")
            if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
                Log.d("RainAlertWorker", "Current time is outside alert window, skipping")
                return Result.success()
            }

            // Ưu tiên lấy cityName từ current_location_city, fallback sang current_city
            val cityName = preferences.getString("current_location_city", null)
                ?: preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"
            val weatherDao = WeatherDatabase.getDatabase(applicationContext).weatherDao()
            val weatherData = weatherDao.getLatestWeatherDataWithDetailsForCity(cityName)

            if (weatherData == null || weatherData.details.isEmpty()) {
                Log.w("RainAlertWorker", "No weather data available for $cityName")
                return Result.failure()
            }

            val now = LocalDateTime.now()
            val next60Minutes = now.plusMinutes(60)
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            val upcomingRainDetails = weatherData.details.filter { detail ->
                val detailTime = LocalDateTime.parse(detail.time, formatter)
                detailTime.isAfter(now) && detailTime.isBefore(next60Minutes) && detail.precipitation_probability >= 20
            }

            upcomingRainDetails.forEach { detail ->
                val detailTime = LocalDateTime.parse(detail.time, formatter)
                val timeString = detailTime.format(timeFormatter)
                val precipitation = detail.precipitation_probability.toInt()
                val weatherEmoji = WeatherUtils.getWeatherEmoji(detail.weather_code)
                val weatherIcon = WeatherUtils.getWeatherIcon(detail.weather_code)
                val title = "Cảnh báo mưa tại $cityName $weatherEmoji"
                val message = "Khả năng mưa: $precipitation% vào lúc $timeString"

                // Kiểm tra thông báo trùng lặp
                val lastNotificationTime = preferences.getString("last_rain_notification_time", null)
                val lastNotificationMessage = preferences.getString("last_rain_notification_message", null)

                if (lastNotificationTime == detail.time && lastNotificationMessage == message) {
                    Log.d("RainAlertWorker", "Duplicate notification detected, skipping: $title - $message")
                    return@forEach
                }

                sendNotification(applicationContext, title, message, weatherIcon)

                // Lưu thông tin thông báo vừa gửi
                preferences.edit()
                    .putString("last_rain_notification_time", detail.time)
                    .putString("last_rain_notification_message", message)
                    .apply()

                Log.d("RainAlertWorker", "Notification sent: $title - $message")
            }

            if (upcomingRainDetails.isEmpty()) {
                Log.d("RainAlertWorker", "No rain expected with probability >= 20% in the next 15 minutes, skipping notification")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("RainAlertWorker", "Error in RainAlertWorker: ${e.message}", e)
            Result.failure()
        }
    }

    private fun sendNotification(context: Context, title: String, message: String, iconResId: Int) {
        Log.d("RainAlertWorker", "Attempting to send notification: $title")
        val notificationManager = NotificationManagerCompat.from(context)

        val channel = android.app.NotificationChannel(
            "rain_alert_channel",
            "Cảnh báo mưa",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Thông báo cảnh báo mưa"
        }
        notificationManager.createNotificationChannel(channel)

        val validIconResId = if (WeatherUtils.isResourceAvailable(context, iconResId)) {
            Log.d("RainAlertWorker", "Using weather icon: $iconResId")
            iconResId
        } else {
            Log.w("RainAlertWorker", "Weather icon not found, using default icon")
            android.R.drawable.ic_dialog_alert
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntentFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(context, "rain_alert_channel")
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
                Log.d("RainAlertWorker", "Notification successfully sent")
            } else {
                Log.w("RainAlertWorker", "Notification permission not granted")
            }
        } catch (e: SecurityException) {
            Log.e("RainAlertWorker", "SecurityException when sending notification: ${e.message}", e)
        }
    }
}