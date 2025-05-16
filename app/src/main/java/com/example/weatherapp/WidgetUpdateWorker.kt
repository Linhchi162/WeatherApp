package com.example.weatherapp

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("WidgetUpdateWorker", "Starting widget update")
            val weatherDao = WeatherDatabase.getDatabase(applicationContext).weatherDao()
            val allWeatherData = weatherDao.getAllWeatherData()
            if (allWeatherData.isNotEmpty()) {
                Log.d("WidgetUpdateWorker", "Weather data found for city: ${allWeatherData.first().cityName}")
                HourlyWeatherWidget().updateAll(applicationContext)
                CurrentWeatherWidget().updateAll(applicationContext)
                Log.d("WidgetUpdateWorker", "Widgets updated successfully")
                Result.success()
            } else {
                Log.w("WidgetUpdateWorker", "No weather data available")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Error updating widgets: ${e.message}", e)
            Result.retry()
        }
    }
}