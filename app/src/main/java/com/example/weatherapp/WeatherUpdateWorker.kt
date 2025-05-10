package com.example.weatherapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val weatherDao = WeatherDatabase.getDatabase(applicationContext).weatherDao()
            // Lấy các service instance từ RetrofitInstance với tên đúng
            val openMeteoService = RetrofitInstance.api
            val airQualityService = RetrofitInstance.airQualityApi

            // Tạo ViewModel với ĐỦ các tham số yêu cầu
            // Lưu ý: Việc tạo ViewModel trực tiếp trong Worker không phải là cách làm tốt nhất,
            // nhưng để sửa lỗi biên dịch trước mắt thì làm như sau:
            val viewModel = WeatherViewModel(
                weatherDao,
                openMeteoService,
                airQualityService, // Truyền airQualityService
            )

            // Lấy danh sách thành phố từ ViewModel
            val cities = viewModel.citiesList

            if (cities.isEmpty()) {
                Log.w("WeatherUpdateWorker", "Không có thành phố nào để cập nhật")
                return@withContext Result.retry()
            }

            // Cập nhật dữ liệu cho từng thành phố
            cities.forEach { city ->
                try {
                    // Gọi API cho từng thành phố
                    val response = openMeteoService.getWeather(
                        latitude = city.latitude,
                        longitude = city.longitude
                    )

                    // Xóa dữ liệu cũ của thành phố
                    weatherDao.deleteWeatherDataForCity(city.name)
                    weatherDao.deleteWeatherDetailsForCity(city.name)
                    weatherDao.deleteDailyDetailsForCity(city.name)

                    // Lưu dữ liệu mới
                    val weatherData = WeatherData(
                        cityName = city.name,
                        latitude = city.latitude,
                        longitude = city.longitude,
                        lastUpdated = System.currentTimeMillis()
                    )
                    val weatherDataId = weatherDao.insertWeatherData(weatherData)
                    Log.d("WeatherUpdateWorker", "Lưu WeatherData thành công với ID: $weatherDataId cho ${city.name}")

                    // Lưu dữ liệu hourly
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

                    // Lưu dữ liệu daily
                    val dailyDetails = response.daily.time.mapIndexed { index, time ->
                        WeatherDailyDetail(
                            weatherDataId = weatherDataId,
                            cityName = city.name,
                            time = time,
                            temperature_2m_max = response.daily.temperature_2m_max[index],
                            temperature_2m_min = response.daily.temperature_2m_min[index],
                            weather_code = response.daily.weathercode[index],
                            precipitation_probability_max = response.daily.precipitation_probability_max[index]
                        )
                    }
                    weatherDao.insertWeatherDailyDetails(dailyDetails)

                    Log.d("WeatherUpdateWorker", "Lưu ${hourlyDetails.size} WeatherDetail thành công cho ${city.name}")
                    Log.d("WeatherUpdateWorker", "Lưu ${dailyDetails.size} WeatherDailyDetail thành công cho ${city.name}")
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