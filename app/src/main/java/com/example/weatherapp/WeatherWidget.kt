package com.example.weatherapp

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HourlyWeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            HourlyWeatherWidgetContent(context)
        }
    }
}

class CurrentWeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            CurrentWeatherWidgetContent(context)
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
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (weatherData.value == null) {
            Text(
                text = "Không có dữ liệu thời tiết",
                style = TextStyle(fontSize = 14.sp)
            )
        } else {
            val iconRes = if (WeatherUtils.isResourceAvailable(context, WeatherUtils.getWeatherIcon(weatherData.value!!.currentWeatherCode))) {
                WeatherUtils.getWeatherIcon(weatherData.value!!.currentWeatherCode)
            } else {
                android.R.drawable.ic_dialog_info
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxSize()
            ) {
                Text(
                    text = weatherData.value!!.cityName,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(bottom = 4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "${UnitConverter.convertTemperature(weatherData.value!!.currentTemperature, temperatureUnit).toInt()}°${if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "C" else "F"}",
                        style = TextStyle(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = "Weather Icon",
                        modifier = GlanceModifier.size(36.dp).padding(start = 8.dp)
                    )
                }

                Text(
                    text = WeatherUtils.getWeatherDescription(weatherData.value!!.currentWeatherCode),
                    style = TextStyle(fontSize = 14.sp),
                    modifier = GlanceModifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    weatherData.value!!.hourlyForecasts.forEach { forecast ->
                        Column(
                            modifier = GlanceModifier.padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${UnitConverter.convertTemperature(forecast.temperature, temperatureUnit).toInt()}°",
                                style = TextStyle(fontSize = 14.sp)
                            )
                            Text(
                                text = forecast.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = TextStyle(fontSize = 10.sp),
                                modifier = GlanceModifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentWeatherWidgetContent(context: Context) {
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
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (weatherData.value == null) {
            Text(
                text = "Không có dữ liệu thời tiết",
                style = TextStyle(fontSize = 14.sp)
            )
        } else {
            val iconRes = if (WeatherUtils.isResourceAvailable(context, WeatherUtils.getWeatherIcon(weatherData.value!!.weatherCode))) {
                WeatherUtils.getWeatherIcon(weatherData.value!!.weatherCode)
            } else {
                android.R.drawable.ic_dialog_info
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxSize()
            ) {
                Text(
                    text = weatherData.value!!.cityName,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(bottom = 4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "${UnitConverter.convertTemperature(weatherData.value!!.currentTemperature, temperatureUnit).toInt()}°${if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "C" else "F"}",
                        style = TextStyle(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = "Weather Icon",
                        modifier = GlanceModifier.size(36.dp).padding(start = 8.dp)
                    )
                }

                Text(
                    text = WeatherUtils.getWeatherDescription(weatherData.value!!.weatherCode),
                    style = TextStyle(fontSize = 14.sp),
                    modifier = GlanceModifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Thấp: ${UnitConverter.convertTemperature(weatherData.value!!.temperatureMin, temperatureUnit).toInt()}°${if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "C" else "F"}",
                        style = TextStyle(fontSize = 12.sp),
                        modifier = GlanceModifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Cao: ${UnitConverter.convertTemperature(weatherData.value!!.temperatureMax, temperatureUnit).toInt()}°${if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "C" else "F"}",
                        style = TextStyle(fontSize = 12.sp)
                    )
                }
            }
        }
    }
}

private suspend fun getWeatherData(context: Context): WidgetWeatherData? {
    return withContext(Dispatchers.IO) {
        try {
            val preferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val currentCity = preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"
            val weatherDao = WeatherDatabase.getDatabase(context).weatherDao()

            // Log fetching data
            Log.d("WeatherWidget", "Fetching weather data for city: $currentCity")

            // Fetch daily and hourly data
            val dailyWeatherData = weatherDao.getLatestWeatherDataWithDailyDetailsForCity(currentCity)
            val hourlyWeatherData = weatherDao.getLatestWeatherDataWithDetailsForCity(currentCity)

            // Log data availability
            Log.d("WeatherWidget", "Daily data available: ${dailyWeatherData != null && dailyWeatherData.dailyDetails.isNotEmpty()}")
            Log.d("WeatherWidget", "Hourly data available: ${hourlyWeatherData != null && hourlyWeatherData.details.isNotEmpty()}")

            // Try to get daily details for today
            val dailyDetail = dailyWeatherData?.dailyDetails?.find { detail ->
                try {
                    val detailDate = LocalDateTime.parse(detail.time, DateTimeFormatter.ISO_LOCAL_DATE).toLocalDate()
                    detailDate == LocalDateTime.now().toLocalDate()
                } catch (e: Exception) {
                    Log.e("WeatherWidget", "Error parsing daily detail time: ${detail.time}", e)
                    false
                }
            }

            // Get the most recent hourly data (within the last 3 hours to be more lenient)
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

            // Log found details
            Log.d("WeatherWidget", "Daily detail found: ${dailyDetail != null}")
            Log.d("WeatherWidget", "Current hourly detail found: ${currentHourlyDetail != null}")

            // Fallback logic: Use available data even if partial
            if (dailyDetail == null && currentHourlyDetail == null) {
                Log.e("WeatherWidget", "No valid daily or hourly data for city: $currentCity")
                return@withContext null
            }

            // Construct WidgetWeatherData with fallback values
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
            val currentCity = preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"
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