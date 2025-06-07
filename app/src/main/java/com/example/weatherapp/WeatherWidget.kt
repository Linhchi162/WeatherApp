package com.example.weatherapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment as GlanceAlignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight as GlanceFontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WeatherWidgetContent(context)
        }
    }
}

class HourlyWeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            HourlyWeatherWidgetContent(context)
        }
    }
}

@Composable
private fun WeatherWidgetContent(context: Context) {
    val weatherData = produceState<WidgetWeatherData?>(initialValue = null) {
        value = getWeatherData(context)
    }
    val temperatureUnit = getTemperatureUnit(context)
    val backgroundRes = getBackgroundResource()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(backgroundRes))
            .clickable {
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
            .padding(6.dp),
        contentAlignment = GlanceAlignment.Center
    ) {
        if (weatherData.value == null) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = GlanceAlignment.Center
            ) {
                Text(
                    text = "Đang tải...",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = GlanceFontWeight.Medium,
                        color = androidx.glance.color.ColorProvider(
                            day = androidx.compose.ui.graphics.Color.White,
                            night = androidx.compose.ui.graphics.Color.White
                        )
                    )
                )
            }
        } else {
            Column(
                horizontalAlignment = GlanceAlignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxSize()
            ) {

                Text(
                    text = weatherData.value!!.cityName,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = GlanceFontWeight.Bold,
                        color = androidx.glance.color.ColorProvider(
                            day = androidx.compose.ui.graphics.Color.White,
                            night = androidx.compose.ui.graphics.Color.White
                        )
                    ),
                    modifier = GlanceModifier.padding(bottom = 4.dp)
                )

                Row(
                    verticalAlignment = GlanceAlignment.CenterVertically,
                    modifier = GlanceModifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "${UnitConverter.convertTemperature(weatherData.value!!.currentTemperature, temperatureUnit).toInt()}°",
                        style = TextStyle(
                            fontSize = 40.sp,
                            fontWeight = GlanceFontWeight.Bold,
                            color = androidx.glance.color.ColorProvider(
                                day = androidx.compose.ui.graphics.Color.White,
                                night = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    )
                    Image(
                        provider = ImageProvider(WeatherUtils.getWeatherIcon(weatherData.value!!.weatherCode, false)),
                        contentDescription = "Weather Icon",
                        modifier = GlanceModifier.size(48.dp).padding(start = 8.dp)
                    )
                }

                Row(
                    horizontalAlignment = GlanceAlignment.CenterHorizontally
                ) {
                    Text(
                        text = "H:${UnitConverter.convertTemperature(weatherData.value!!.temperatureMax, temperatureUnit).toInt()}°",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = GlanceFontWeight.Medium,
                            color = androidx.glance.color.ColorProvider(
                                day = androidx.compose.ui.graphics.Color.White,
                                night = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    )
                    Text(
                        text = " L:${UnitConverter.convertTemperature(weatherData.value!!.temperatureMin, temperatureUnit).toInt()}°",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = GlanceFontWeight.Medium,
                            color = androidx.glance.color.ColorProvider(
                                day = androidx.compose.ui.graphics.Color.White,
                                night = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    )
                }

                weatherData.value!!.lastUpdated?.let { timestamp ->
                    Text(
                        text = formatWidgetTimestamp(timestamp),
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = androidx.glance.color.ColorProvider(
                                day = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                night = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                            )
                        ),
                        modifier = GlanceModifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyWeatherWidgetContent(context: Context) {
    val weatherData = produceState<HourlyWidgetWeatherData?>(initialValue = null) {
        value = getHourlyWeatherData(context)
    }
    val temperatureUnit = getTemperatureUnit(context)
    val backgroundRes = getBackgroundResource()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(backgroundRes))
            .clickable {
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
            .padding(12.dp),
        contentAlignment = GlanceAlignment.Center
    ) {
        if (weatherData.value == null) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = GlanceAlignment.Center
            ) {
                Text(
                    text = "Đang tải dữ liệu thời tiết...",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = GlanceFontWeight.Medium,
                        color = androidx.glance.color.ColorProvider(
                            day = androidx.compose.ui.graphics.Color.White,
                            night = androidx.compose.ui.graphics.Color.White
                        )
                    )
                )
            }
        } else {
            Column(
                horizontalAlignment = GlanceAlignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxSize()
            ) {

                Row(
                    horizontalAlignment = GlanceAlignment.CenterHorizontally,
                    verticalAlignment = GlanceAlignment.CenterVertically
                ) {
                    Text(
                        text = weatherData.value!!.cityName,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = GlanceFontWeight.Bold,
                            color = androidx.glance.color.ColorProvider(
                                day = androidx.compose.ui.graphics.Color.White,
                                night = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    )
                    Text(
                        text = " ${UnitConverter.convertTemperature(weatherData.value!!.currentTemperature, temperatureUnit).toInt()}°",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = GlanceFontWeight.Bold,
                            color = androidx.glance.color.ColorProvider(
                                day = androidx.compose.ui.graphics.Color.White,
                                night = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    )
                }


                Row(
                    horizontalAlignment = GlanceAlignment.CenterHorizontally,
                    modifier = GlanceModifier.padding(top = 8.dp)
                ) {
                    weatherData.value!!.hourlyForecasts.take(5).forEach { forecast ->
                        Column(
                            modifier = GlanceModifier.padding(horizontal = 4.dp),
                            horizontalAlignment = GlanceAlignment.CenterHorizontally
                        ) {
                            Text(
                                text = forecast.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = androidx.glance.color.ColorProvider(
                                        day = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                        night = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            Image(
                                provider = ImageProvider(WeatherUtils.getWeatherIcon(forecast.weatherCode, false)),
                                contentDescription = "Weather Icon",
                                modifier = GlanceModifier.size(20.dp).padding(vertical = 2.dp)
                            )
                            Text(
                                text = "${UnitConverter.convertTemperature(forecast.temperature, temperatureUnit).toInt()}°",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = GlanceFontWeight.Medium,
                                    color = androidx.glance.color.ColorProvider(
                                        day = androidx.compose.ui.graphics.Color.White,
                                        night = androidx.compose.ui.graphics.Color.White
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun getWeatherData(context: Context): WidgetWeatherData? {
    return withContext(Dispatchers.IO) {
        try {
            val preferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val currentCity = preferences.getString("current_location_city", "Hà Nội") ?: "Hà Nội"
            val weatherDao = WeatherDatabase.getDatabase(context).weatherDao()

            Log.d("WeatherWidget", "Fetching weather data for city: $currentCity")

            val dailyWeatherData = weatherDao.getLatestWeatherDataWithDailyDetailsForCity(currentCity)
            val hourlyWeatherData = weatherDao.getLatestWeatherDataWithDetailsForCity(currentCity)

            Log.d("WeatherWidget", "Daily data available: ${dailyWeatherData != null && dailyWeatherData.dailyDetails.isNotEmpty()}")
            Log.d("WeatherWidget", "Hourly data available: ${hourlyWeatherData != null && hourlyWeatherData.details.isNotEmpty()}")

            val dailyDetail = dailyWeatherData?.dailyDetails?.find { detail ->
                try {
                    val detailDate = LocalDateTime.parse(detail.time, DateTimeFormatter.ISO_LOCAL_DATE).toLocalDate()
                    detailDate == LocalDateTime.now().toLocalDate()
                } catch (e: Exception) {
                    Log.e("WeatherWidget", "Error parsing daily detail time: ${detail.time}", e)
                    false
                }
            }

            val now = LocalDateTime.now()
            val currentHourlyDetail = hourlyWeatherData?.details?.filter { detail ->
                try {
                    val detailTime = LocalDateTime.parse(detail.time, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    detailTime <= now && detailTime >= now.minusHours(3)
                } catch (e: Exception) {
                    Log.e("WeatherWidget", "Error parsing hourly detail time: ${detail.time}", e)
                    false
                }
            }?.maxByOrNull { detail ->
                LocalDateTime.parse(detail.time, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toEpochSecond(ZoneId.systemDefault().rules.getOffset(Instant.now()))
            }

            Log.d("WeatherWidget", "Daily detail found: ${dailyDetail != null}")
            Log.d("WeatherWidget", "Current hourly detail found: ${currentHourlyDetail != null}")

            if (dailyDetail == null && currentHourlyDetail == null) {
                Log.e("WeatherWidget", "No valid daily or hourly data for city: $currentCity")
                return@withContext null
            }

            WidgetWeatherData(
                cityName = currentHourlyDetail?.cityName ?: dailyDetail?.cityName ?: currentCity,
                currentTemperature = currentHourlyDetail?.temperature_2m ?: dailyDetail?.temperature_2m_max ?: 0.0,
                temperatureMax = dailyDetail?.temperature_2m_max ?: currentHourlyDetail?.temperature_2m ?: 0.0,
                temperatureMin = dailyDetail?.temperature_2m_min ?: currentHourlyDetail?.temperature_2m ?: 0.0,
                weatherCode = currentHourlyDetail?.weather_code ?: dailyDetail?.weather_code ?: 0,
                lastUpdated = dailyWeatherData?.weatherData?.lastUpdated ?: hourlyWeatherData?.weatherData?.lastUpdated
            )
        } catch (e: Exception) {
            Log.e("WeatherWidget", "Error fetching weather data: ${e.message}", e)
            null
        }
    }
}

private suspend fun getHourlyWeatherData(context: Context): HourlyWidgetWeatherData? {
    return withContext(Dispatchers.IO) {
        try {
            val preferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val currentCity = preferences.getString("current_location_city", "Hà Nội") ?: "Hà Nội"
            val weatherDao = WeatherDatabase.getDatabase(context).weatherDao()
            val weatherData = weatherDao.getLatestWeatherDataWithDetailsForCity(currentCity)
            
            if (weatherData != null && weatherData.details.isNotEmpty()) {
                val now = LocalDateTime.now()
                val hourlyForecasts = weatherData.details
                    .filter { detail ->
                        val detailTime = LocalDateTime.parse(detail.time, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        detailTime >= now && detailTime <= now.plusHours(6)
                    }
                    .map { detail ->
                        HourlyForecast(
                            time = LocalDateTime.parse(detail.time, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            temperature = detail.temperature_2m,
                            weatherCode = detail.weather_code
                        )
                    }
                    .sortedBy { it.time }

                val paddedForecasts = mutableListOf<HourlyForecast>()
                var lastKnownForecast = hourlyForecasts.firstOrNull()
                for (i in 0 until 6) {
                    val targetTime = now.plusHours(i.toLong())
                    val matchingForecast = hourlyForecasts.find { forecast ->
                        forecast.time.hour == targetTime.hour && forecast.time.dayOfMonth == targetTime.dayOfMonth
                    }
                    if (matchingForecast != null) {
                        paddedForecasts.add(matchingForecast)
                        lastKnownForecast = matchingForecast
                    } else if (lastKnownForecast != null) {
                        paddedForecasts.add(
                            HourlyForecast(
                                time = targetTime,
                                temperature = lastKnownForecast.temperature,
                                weatherCode = lastKnownForecast.weatherCode
                            )
                        )
                    }
                }

                HourlyWidgetWeatherData(
                    cityName = currentCity,
                    currentTemperature = hourlyForecasts.firstOrNull()?.temperature ?: 0.0,
                    currentWeatherCode = hourlyForecasts.firstOrNull()?.weatherCode ?: 0,
                    hourlyForecasts = paddedForecasts.take(6)
                )
            } else {
                Log.e("WeatherWidget", "No hourly weather data found for city: $currentCity")
                null
            }
        } catch (e: Exception) {
            Log.e("WeatherWidget", "Error fetching hourly weather data: ${e.message}", e)
            null
        }
    }
}

private fun getTemperatureUnit(context: Context): UnitConverter.TemperatureUnit {
    val preferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    return when (preferences.getString("temperature_unit", "Độ C (°C)")) {
        "Độ C (°C)" -> UnitConverter.TemperatureUnit.CELSIUS
        "Độ F (°F)" -> UnitConverter.TemperatureUnit.FAHRENHEIT
        else -> UnitConverter.TemperatureUnit.CELSIUS
    }
}

private fun formatWidgetTimestamp(timestamp: Long): String {
    return try {
        val dateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        dateTime.format(DateTimeFormatter.ofPattern("HH:mm, dd/MM"))
    } catch (e: Exception) {
        Log.e("WeatherWidget", "Error formatting timestamp: ${e.message}", e)
        "Không rõ"
    }
}

private fun getBackgroundResource(): Int {
    val currentHour = LocalDateTime.now().hour
    return if (currentHour in 6..17) {
        R.drawable.widget_background_day
    } else {
        R.drawable.widget_background_night
    }
}

data class WidgetWeatherData(
    val cityName: String,
    val currentTemperature: Double,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val weatherCode: Int,
    val lastUpdated: Long?
)

data class HourlyWidgetWeatherData(
    val cityName: String,
    val currentTemperature: Double,
    val currentWeatherCode: Int,
    val hourlyForecasts: List<HourlyForecast>
)

data class HourlyForecast(
    val time: LocalDateTime,
    val temperature: Double,
    val weatherCode: Int
)


