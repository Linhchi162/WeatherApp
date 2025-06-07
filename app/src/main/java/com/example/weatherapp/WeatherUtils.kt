package com.example.weatherapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log

object WeatherUtils {

    fun getWeatherDescription(code: Int, cityName: String? = null): String {

        return when (code) {
            0 -> "Trời quang"
            1 -> "Nắng nhẹ"
            2 -> "Mây rải rác"
            3 -> "Nhiều mây"
            45, 48 -> "Sương mù"
            51, 53, 55 -> "Mưa phùn"
            56, 57 -> "Mưa phùn đông đá"
            61 -> "Mưa nhỏ"
            63 -> "Mưa vừa"
            65 -> "Mưa to"
            66, 67 -> "Mưa đông đá"
            71 -> "Tuyết rơi nhẹ"
            73 -> "Tuyết rơi vừa"
            75 -> "Tuyết rơi dày"
            77 -> "Hạt tuyết"
            80 -> "Mưa rào nhẹ"
            81 -> "Mưa rào vừa"
            82 -> "Mưa rào dữ dội"
            85 -> "Mưa tuyết nhẹ"
            86 -> "Mưa tuyết nặng"
            95, 96, 99 -> "Mưa rào"
            else -> "Không xác định"
        }
    }

    fun getWeatherIcon(code: Int, isNight: Boolean = false): Int {
        return when (code) {
            0 -> if (isNight) R.drawable.clear_night else R.drawable.sunny
            1 -> if (isNight) R.drawable.cloudy_with_moon else R.drawable.cloudy_with_sun
            2 -> if (isNight) R.drawable.cloudy_with_moon else R.drawable.cloudy_with_sun
            3 -> if (isNight) R.drawable.cloudy_night else R.drawable.cloudy
            45, 48 -> if (isNight) R.drawable.cloudy_night else R.drawable.cloudy
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> if (isNight) R.drawable.night_rain else R.drawable.rainingg
            71, 73, 75, 77, 85, 86 -> if (isNight) R.drawable.snow_night else R.drawable.snow
            95, 96, 99 -> if (isNight) R.drawable.night_thunderraining else R.drawable.thunderstorm
            else -> if (isNight) R.drawable.cloudy_with_moon else R.drawable.cloudy_with_sun
        }
    }

    fun isResourceAvailable(context: Context, resId: Int): Boolean {
        return try {
            context.resources.getResourceEntryName(resId)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getWeatherEmoji(code: Int): String {
        return when (code) {
            0 -> "☀️"
            1, 2, 3 -> "⛅"
            45, 48 -> "🌫️"
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
            71, 73, 75, 77, 85, 86 -> "❄️"
            95, 96, 99 -> "⚡️"
            else -> "🌤️"
        }
    }

    fun isSevereWeather(code: Int): Boolean {
        return code in listOf(56, 57, 65, 66, 67, 75, 82, 86, 95, 96, 99)
    }

    fun sendNotification(context: Context, title: String, message: String, iconResId: Int, channelId: String = "severe_weather_channel", channelName: String = "Cảnh báo thời tiết khắc nghiệt") {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Thông báo cảnh báo thời tiết khắc nghiệt"
        }
        notificationManager.createNotificationChannel(channel)

        val notificationId: Int = (System.currentTimeMillis() % 10000).toInt()

        val validIconResId = if (isResourceAvailable(context, iconResId)) {
            Log.d("WeatherUtils", "Using weather icon: $iconResId")
            iconResId
        } else {
            Log.w("WeatherUtils", "Weather icon not found, using default icon")
            android.R.drawable.ic_dialog_info
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(validIconResId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d("WeatherUtils", "Notification sent with ID: $notificationId")
    }
}