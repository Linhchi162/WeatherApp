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

    fun getWeatherDescription(code: Int): String {
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
            95 -> "Dông có mưa"
            96 -> "Dông nhẹ có mưa đá"
            99 -> "Dông mạnh có mưa đá"
            else -> "Không xác định"
        }
    }

    // Override function with cityName parameter for manual fixes
    fun getWeatherDescription(code: Int, cityName: String?): String {
        // Manual fix for Hanoi region (including Bắc Từ Liêm) - show rain shower instead of thunderstorm with hail
        if (cityName != null && (
            cityName.contains("Hà Nội", ignoreCase = true) ||
            cityName.contains("Bắc Từ Liêm", ignoreCase = true) ||
            cityName.contains("Vị trí hiện tại", ignoreCase = true)
        )) {
            return when (code) {
                95 -> "Mưa rào" // Force rain shower for thunderstorm
                96 -> "Mưa rào" // Force rain shower for light thunderstorm with hail
                99 -> "Mưa rào" // Force rain shower for heavy thunderstorm with hail
                else -> getWeatherDescription(code) // Use normal mapping for other codes
            }
        }
        
        // Default behavior for other cities
        return getWeatherDescription(code)
    }

    fun getWeatherIcon(code: Int): Int {
        return when (code) {
            0 -> R.drawable.sunny
            1, 2 -> R.drawable.cloudy_with_sun
            3, 45, 48 -> R.drawable.cloudy
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.rainingg
            71, 73, 75, 77, 85, 86 -> R.drawable.snow
            95, 96, 99 -> R.drawable.thunderstorm
            else -> R.drawable.cloudy_with_sun
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
            0 -> "☀️" // Trời quang
            1, 2, 3 -> "⛅" // Nắng nhẹ, mây rải rác, nhiều mây
            45, 48 -> "🌫️" // Sương mù
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️" // Mưa
            71, 73, 75, 77, 85, 86 -> "❄️" // Tuyết
            95, 96, 99 -> "⚡️" // Dông
            else -> "🌤️" // Mặc định
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