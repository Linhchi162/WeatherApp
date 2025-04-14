package com.example.weatherapp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val sharedPreferences = applicationContext.getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE)
            val latitude = sharedPreferences.getFloat("latitude", 0f).toDouble()
            val longitude = sharedPreferences.getFloat("longitude", 0f).toDouble()

            if (latitude == 0.0 && longitude == 0.0) {
                Log.w("WeatherUpdateWorker", "Tọa độ không hợp lệ: lat=$latitude, lon=$longitude")
                return@withContext Result.retry()
            }

            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,weathercode," +
                    "windspeed_10m,uv_index,apparent_temperature,surface_pressure,visibility" +
                    "&timezone=auto"
            val request = Request.Builder().url(url).build()
            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                val hourly = jsonObject.getJSONObject("hourly")

                val timeList = hourly.getJSONArray("time").let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }
                val temperatureList = hourly.getJSONArray("temperature_2m").let { array ->
                    (0 until array.length()).map { array.getDouble(it) }
                }
                val uvIndexList = hourly.getJSONArray("uv_index").let { array ->
                    (0 until array.length()).map { array.getDouble(it) }
                }
                val apparentTemperatureList = hourly.getJSONArray("apparent_temperature").let { array ->
                    (0 until array.length()).map { array.getDouble(it) }
                }
                val humidityList = hourly.getJSONArray("relative_humidity_2m").let { array ->
                    (0 until array.length()).map { array.getDouble(it) }
                }
                val windSpeedList = hourly.getJSONArray("windspeed_10m").let { array ->
                    (0 until array.length()).map { array.getDouble(it) }
                }
                val pressureList = hourly.getJSONArray("surface_pressure").let { array ->
                    (0 until array.length()).map { array.getDouble(it) }
                }
                val visibilityList = hourly.getJSONArray("visibility").let { array ->
                    (0 until array.length()).map { array.getDouble(it) }
                }
                val precipitationList = hourly.getJSONArray("precipitation_probability").let { array ->
                    (0 until array.length()).map { array.getDouble(it) }
                }
                val weatherCodeList = hourly.getJSONArray("weathercode").let { array ->
                    (0 until array.length()).map { array.getInt(it) }
                }

                val expectedLength = timeList.size
                if (listOf(temperatureList, uvIndexList, apparentTemperatureList, humidityList, windSpeedList,
                        pressureList, visibilityList, precipitationList, weatherCodeList).any { it.size != expectedLength }) {
                    Log.e("WeatherUpdateWorker", "Dữ liệu API không đồng bộ: timeList=$expectedLength, nhưng các mảng khác có độ dài khác")
                    return@withContext Result.retry()
                }

                val weatherData = WeatherData(
                    id = System.currentTimeMillis(),
                    latitude = latitude,
                    longitude = longitude
                )

                val database = WeatherDatabase.getDatabase(applicationContext)
                val weatherDao = database.weatherDao()
                val weatherDataId = weatherDao.insertWeatherData(weatherData)
                Log.d("WeatherUpdateWorker", "Lưu WeatherData thành công với ID: $weatherDataId")

                val weatherDetails = timeList.mapIndexed { index, time ->
                    WeatherDetail(
                        weatherDataId = weatherDataId,
                        time = time,
                        temperature_2m = temperatureList[index],
                        uv_index = uvIndexList[index],
                        apparent_temperature = apparentTemperatureList[index],
                        relative_humidity_2m = humidityList[index],
                        wind_speed_10m = windSpeedList[index],
                        surface_pressure = pressureList[index],
                        visibility = visibilityList[index],
                        precipitation_probability = precipitationList[index],
                        weather_code = weatherCodeList[index]
                    )
                }

                weatherDao.insertWeatherDetails(weatherDetails)
                Log.d("WeatherUpdateWorker", "Lưu ${weatherDetails.size} WeatherDetail thành công")

                Result.success()
            } else {
                Log.w("WeatherUpdateWorker", "Lỗi gọi API Open-Meteo: ${response.code}")
                return@withContext Result.retry()
            }
        } catch (e: Exception) {
            Log.e("WeatherUpdateWorker", "Lỗi trong quá trình cập nhật thời tiết: ${e.message}", e)
            return@withContext Result.retry()
        }
    }
}
