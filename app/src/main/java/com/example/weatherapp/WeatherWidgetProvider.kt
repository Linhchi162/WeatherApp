package com.example.weatherapp

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WeatherWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrentWeatherWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("WeatherWidgetProvider", "Updating widget for IDs: ${appWidgetIds.joinToString()}")
        scheduleWidgetUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("WeatherWidgetProvider", "Widget enabled")
        scheduleWidgetUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("WeatherWidgetProvider", "Widget disabled")
        WorkManager.getInstance(context).cancelUniqueWork("WeatherWidgetUpdate")
    }

    private fun scheduleWidgetUpdate(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "WeatherWidgetUpdate",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
        Log.d("WeatherWidgetProvider", "Scheduled widget update every 30 minutes")
    }
}