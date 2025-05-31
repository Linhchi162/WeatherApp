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

class HourlyWeatherWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourlyWeatherWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("HourlyWeatherWidgetProvider", "Updating widget for IDs: ${appWidgetIds.joinToString()}")
        scheduleWidgetUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("HourlyWeatherWidgetProvider", "Widget enabled")
        scheduleWidgetUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("HourlyWeatherWidgetProvider", "Widget disabled")
        WorkManager.getInstance(context).cancelUniqueWork("HourlyWeatherWidgetUpdate")
    }

    private fun scheduleWidgetUpdate(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "HourlyWeatherWidgetUpdate",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
        Log.d("HourlyWeatherWidgetProvider", "Scheduled widget update every 30 minutes")
    }
}