package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherMainScreen(
    viewModel: WeatherViewModel = viewModel(
        factory = WeatherViewModelFactory(
            weatherDao = WeatherDatabase.getDatabase(LocalContext.current).weatherDao()
        )
    ),
    latitude: Double? = null,
    longitude: Double? = null,
    cityName: String = "Đang tải..."
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var lastUpdateTime by remember { mutableStateOf<Long?>(null) }
    var forecastDays by remember { mutableStateOf(7) } // Mặc định 7 ngày
    val weatherDao = WeatherDatabase.getDatabase(context).weatherDao()

    // Lấy thời gian cập nhật cuối cùng từ database
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val weatherData = withContext(Dispatchers.IO) {
                weatherDao.getLatestWeatherDataWithDetails()
            }
            lastUpdateTime = weatherData?.weatherData?.id
        }
        if (latitude != null && longitude != null) {
            viewModel.fetchWeather(latitude, longitude)
        }
    }

    val currentDate = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("MMM, dd")
    val currentDateStr = currentDate.format(dateFormatter)

    val isDataLoaded = viewModel.timeList.isNotEmpty() &&
            viewModel.temperatureList.isNotEmpty() &&
            viewModel.weatherCodeList.isNotEmpty() &&
            viewModel.dailyTimeList.isNotEmpty() &&
            viewModel.dailyTempMaxList.isNotEmpty() &&
            viewModel.dailyTempMinList.isNotEmpty() &&
            viewModel.dailyWeatherCodeList.isNotEmpty()
    val errorMessage = viewModel.errorMessage
    val isNetworkAvailable = isNetworkAvailable(context)

    Scaffold(
        topBar = {
            TopBar(onRefresh = {
                if (latitude != null && longitude != null) {
                    viewModel.fetchWeather(latitude, longitude)
                }
            })
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 30.dp)
        ) {
            when {
                !isNetworkAvailable -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Không có kết nối mạng. Vui lòng kiểm tra lại.",
                                fontSize = 16.sp,
                                color = Color(0xFF5372dc)
                            )
                            lastUpdateTime?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Dữ liệu cập nhật lần cuối: ${
                                        Instant.ofEpochMilli(it)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime()
                                            .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"))
                                    }",
                                    fontSize = 14.sp,
                                    color = Color(0xFF5372dc)
                                )
                            }
                        }
                    }
                }
                !isDataLoaded || errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Đang tải dữ liệu thời tiết...",
                                fontSize = 16.sp,
                                color = Color(0xFF5372dc)
                            )
                            lastUpdateTime?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Dữ liệu cập nhật lần cuối: ${
                                        Instant.ofEpochMilli(it)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime()
                                            .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"))
                                    }",
                                    fontSize = 14.sp,
                                    color = Color(0xFF5372dc)
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        item {
                            val index = viewModel.getCurrentIndex()
                            val currentTemp = viewModel.temperatureList.getOrNull(index)?.toInt() ?: 0
                            val high = viewModel.temperatureList.maxOrNull()?.toInt() ?: 0
                            val low = viewModel.temperatureList.minOrNull()?.toInt() ?: 0
                            val weatherCode = viewModel.weatherCodeList.getOrNull(index) ?: 0
                            val weatherIcon = getWeatherIcon(weatherCode)
                            val weatherText = "Mostly Clear"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(cityName, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color(0xFF5372dc))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("$currentTemp°", fontSize = 70.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5372dc))
                                    Text(weatherText, fontSize = 16.sp, color = Color(0xFF5372dc))
                                    Spacer(modifier = Modifier.height(9.dp))
                                    Text("H: $high°    L: $low°", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
                                }
                                Spacer(modifier = Modifier.width(30.dp))
                                Image(
                                    painter = painterResource(id = weatherIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(170.dp)
                                )
                            }
                        }

                        item {
                            val index = viewModel.getCurrentIndex()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InfoItem(R.drawable.rain_dropp, "${viewModel.precipitationList.getOrNull(index)?.toInt() ?: 0}%", Color(0xFF5372DC))
                                InfoItem(R.drawable.humidity, "${viewModel.humidityList.getOrNull(index)?.toInt() ?: 0}%", Color(0xFFD05CA2))
                                InfoItem(R.drawable.wind_speed, "${viewModel.windSpeedList.getOrNull(index)?.toInt() ?: 0}km/h", Color(0xFF3F9CBE))
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10))
                                    .padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Today", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
                                    Text(currentDateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                val upcoming = viewModel.getUpcomingForecast()

                                if (upcoming.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        upcoming.forEachIndexed { index, (timeStr, temp, weatherCode) ->
                                            val time = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                            val formattedTime = time.format(DateTimeFormatter.ofPattern("HH:mm"))
                                            ForecastItem(
                                                iconId = getWeatherIcon(weatherCode),
                                                temp = "${temp.toInt()}°C",
                                                time = formattedTime,
                                                highlight = index == 0
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Đang tải dữ liệu thời tiết...",
                                            fontSize = 14.sp,
                                            color = Color(0xFF5372dc)
                                        )
                                    }
                                }
                            }
                        }

                        // Thêm nút chọn 7 hoặc 14 ngày
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = { forecastDays = 7 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (forecastDays == 7) Color(0xFF5372dc) else Color.Gray
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("7 ngày", color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Button(
                                    onClick = { forecastDays = 14 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (forecastDays == 14) Color(0xFF5372dc) else Color.Gray
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("14 ngày", color = Color.White)
                                }
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(7))
                                    .padding(16.dp)
                            ) {
                                val dailyForecastData = viewModel.getDailyForecast(forecastDays)

                                if (dailyForecastData.isNotEmpty()) {
                                    dailyForecastData.forEachIndexed { index, (time, temps, weatherCode) ->
                                        val date = LocalDate.parse(time, DateTimeFormatter.ISO_LOCAL_DATE)
                                        val formattedDate = date.format(DateTimeFormatter.ofPattern("E dd"))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                                .background(
                                                    color = if (index == 0) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                                                    shape = RoundedCornerShape(10)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                formattedDate,
                                                color = Color(0xFF5372dc),
                                                fontWeight = FontWeight.Medium
                                            )
                                            Image(
                                                painter = painterResource(id = getWeatherIcon(weatherCode)),
                                                contentDescription = null,
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Text(
                                                "${temps.second.toInt()}° / ${temps.first.toInt()}°",
                                                color = Color(0xFF5372dc)
                                            )
                                            InfoItem(
                                                R.drawable.rain_dropp,
                                                "${viewModel.dailyPrecipitationList.getOrNull(index)?.toInt() ?: 0}%",
                                                Color(0xFF5372dc)
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Đang tải dự báo thời tiết...",
                                            fontSize = 14.sp,
                                            color = Color(0xFF5372dc)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", color = Color(0xFF5372dc), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        item {
                            AirQualitySection(aqi = 92)
                        }

                        item {
                            val index = viewModel.getCurrentIndex()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0f), shape = RoundedCornerShape(20.dp))
                                    .padding(0.dp)
                            ) {
                                val infoItems = listOf(
                                    Triple(R.drawable.uv, "UV", "${viewModel.uvList.getOrNull(index)?.toInt() ?: "-"}"),
                                    Triple(R.drawable.feels_like, "Feels like", "${viewModel.feelsLikeList.getOrNull(index)?.toInt() ?: "-"}°"),
                                    Triple(R.drawable.humidity2, "Humidity", "${viewModel.humidityList.getOrNull(index)?.toInt() ?: "-"}%"),
                                    Triple(R.drawable.ese_wind, "ESE wind", "${viewModel.windSpeedList.getOrNull(index)?.toInt() ?: "-"}km/h"),
                                    Triple(R.drawable.air_pressure, "Air pressure", "${viewModel.pressureList.getOrNull(index)?.let { (it * 0.750061683).toInt() } ?: "-"}mmHg"),
                                    Triple(R.drawable.visibility, "Visibility", "${viewModel.visibilityList.getOrNull(index)?.let { (it / 1000).toInt() } ?: "-"}km")
                                )

                                for (row in infoItems.chunked(3)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        row.forEach { (iconId, label, value) ->
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(110.dp)
                                                    .padding(horizontal = 10.dp)
                                                    .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp))
                                                    .padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Image(
                                                    painter = painterResource(id = iconId),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(label, color = Color(0xFF5372DC), fontSize = 12.sp)
                                                Text(
                                                    value,
                                                    color = Color(0xFF5372DC),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            lastUpdateTime?.let {
                                Text(
                                    text = "Cập nhật lần cuối: ${
                                        Instant.ofEpochMilli(it)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime()
                                            .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"))
                                    }",
                                    fontSize = 12.sp,
                                    color = Color(0xFF5372dc),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Giữ nguyên các composable khác (AirQualitySection, AirQualityBar, InfoItem, ForecastItem, isNetworkAvailable, WeatherScreenPreview)
//xóa
@Composable
fun AirQualitySection(aqi: Int) {
    val (description, color, percentage) = getAqiInfo(aqi)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp))
            .padding(24.dp)
    ) {
        Text("Air quality", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5372DC))
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$aqi", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(8.dp))
            Text(description, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (description) {
                "Good" -> "Air quality is good. Enjoy your activities!"
                "Moderate" -> "Air quality is acceptable. Sensitive people should consider reducing outdoor activity."
                "Unhealthy" -> "Air quality is poor. Consider staying indoors."
                "Very Unhealthy" -> "Health warnings of emergency conditions."
                "Hazardous" -> "Everyone may experience more serious health effects."
                else -> "Air quality information is unavailable."
            },
            fontSize = 13.sp,
            color = Color(0xFF5372DC)
        )
        Spacer(modifier = Modifier.height(16.dp))
        AirQualityBar(percentage = percentage)
    }
}
//
@Composable
fun AirQualityBar(percentage: Float) {
    val barHeight = 5.dp
    val barColors = listOf(Color(0xFF3FAE6D), Color(0xFFA3C919), Color(0xFFFFC107), Color(0xFFFF6F22), Color(0xFFd74944), Color(0xFFa10000))

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)) {
            val segmentWidth = size.width / barColors.size
            barColors.forEachIndexed { i, color ->
                drawRect(
                    color = color,
                    topLeft = Offset(x = i * segmentWidth, y = 0f),
                    size = androidx.compose.ui.geometry.Size(segmentWidth, size.height)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(start = (percentage * (1.0f - 0.05f)).coerceIn(0f, 1f).dp * 300)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun InfoItem(iconId: Int, text: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ForecastItem(iconId: Int, temp: String, time: String, highlight: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color = if (highlight) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(20)
            )
            .padding(6.dp)
            .width(50.dp)
    ) {
        Text(temp, fontSize = 10.sp, color = Color(0xFF5372dc))
        Spacer(modifier = Modifier.height(4.dp))
        Image(painter = painterResource(id = iconId), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(time, fontSize = 8.sp, color = Color(0xFF5372dc))
    }
}

@Composable
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

@Preview(showBackground = true, heightDp = 2000)
@Composable
fun WeatherScreenPreview() {
    WeatherMainScreen()
}
