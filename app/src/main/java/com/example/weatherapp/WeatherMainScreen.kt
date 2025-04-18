package com.example.weatherapp

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun WeatherMainScreen(
    viewModel: WeatherViewModel = viewModel(
        factory = WeatherViewModelFactory(
            weatherDao = WeatherDatabase.getDatabase(LocalContext.current).weatherDao(),
            openMeteoService = RetrofitInstance.api,
            airQualityService = RetrofitInstance.airQualityApi,
            geoapifyService = RetrofitInstance.geoapifyApi
        )
    )
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cities = viewModel.citiesList
    val pagerState = rememberPagerState(initialPage = 0)

    var showSearchOverlay by remember { mutableStateOf(false) }
    var showSearchScreen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    // Đọc đơn vị từ SharedPreferences
    val preferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    var temperatureUnit by remember { mutableStateOf(UnitConverter.TemperatureUnit.CELSIUS) }
    var windSpeedUnit by remember { mutableStateOf(UnitConverter.WindSpeedUnit.KMH) }
    var pressureUnit by remember { mutableStateOf(UnitConverter.PressureUnit.MMHG) }
    var visibilityUnit by remember { mutableStateOf(UnitConverter.VisibilityUnit.KM) }

    // Debug giá trị SharedPreferences
    LaunchedEffect(Unit) {
        Log.d("WeatherMainScreen", "Temperature Unit: ${preferences.getString("temperature_unit", "Độ C (°C)")}")
        Log.d("WeatherMainScreen", "Wind Unit: ${preferences.getString("wind_unit", "Kilomet mỗi giờ (km/h)")}")
        Log.d("WeatherMainScreen", "Pressure Unit: ${preferences.getString("pressure_unit", "Millimet thủy ngân (mmHg)")}")
        Log.d("WeatherMainScreen", "Visibility Unit: ${preferences.getString("visibility_unit", "Kilomet (km)")}")
    }

    // Lắng nghe thay đổi SharedPreferences
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            Log.d("WeatherMainScreen", "SharedPreferences changed: $key = ${prefs.getString(key, "")}")
            when (key) {
                "temperature_unit" -> {
                    temperatureUnit = when (prefs.getString("temperature_unit", "Độ C (°C)")) {
                        "Độ C (°C)" -> UnitConverter.TemperatureUnit.CELSIUS
                        "Độ F (°F)" -> UnitConverter.TemperatureUnit.FAHRENHEIT
                        else -> UnitConverter.TemperatureUnit.CELSIUS
                    }
                }
                "wind_unit" -> {
                    windSpeedUnit = when (prefs.getString("wind_unit", "Kilomet mỗi giờ (km/h)")) {
                        "Kilomet mỗi giờ (km/h)" -> UnitConverter.WindSpeedUnit.KMH
                        "Thang đo Beaufort" -> UnitConverter.WindSpeedUnit.BEAUFORT
                        "Mét mỗi giây (m/s)" -> UnitConverter.WindSpeedUnit.MS
                        "Feet mỗi giây (ft/s)" -> UnitConverter.WindSpeedUnit.FTS
                        "Dặm mỗi giờ (mph)" -> UnitConverter.WindSpeedUnit.MPH
                        "Hải lý mỗi giờ (hải lý)" -> UnitConverter.WindSpeedUnit.KNOTS
                        else -> UnitConverter.WindSpeedUnit.KMH
                    }
                }
                "pressure_unit" -> {
                    pressureUnit = when (prefs.getString("pressure_unit", "Millimet thủy ngân (mmHg)")) {
                        "Hectopascal (hPa)" -> UnitConverter.PressureUnit.HPA
                        "Millimet thủy ngân (mmHg)" -> UnitConverter.PressureUnit.MMHG
                        "Inch thủy ngân (inHg)" -> UnitConverter.PressureUnit.INHG
                        "Millibar (mb)" -> UnitConverter.PressureUnit.MB
                        "Pound trên inch vuông (psi)" -> UnitConverter.PressureUnit.PSI
                        else -> UnitConverter.PressureUnit.MMHG
                    }
                }
                "visibility_unit" -> {
                    visibilityUnit = when (prefs.getString("visibility_unit", "Kilomet (km)")) {
                        "Kilomet (km)" -> UnitConverter.VisibilityUnit.KM
                        "Dặm (mi)" -> UnitConverter.VisibilityUnit.MI
                        "Mét (m)" -> UnitConverter.VisibilityUnit.M
                        "Feet (ft)" -> UnitConverter.VisibilityUnit.FT
                        else -> UnitConverter.VisibilityUnit.KM
                    }
                }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Cập nhật thành phố hiện tại trong ViewModel khi Pager chuyển trang
    LaunchedEffect(pagerState.currentPage, cities.size) {
        if (cities.isNotEmpty() && pagerState.currentPage < cities.size) {
            viewModel.updateCurrentCity(cities[pagerState.currentPage].name)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopBar(
                    context = context,
                    onSearchClick = { showSearchOverlay = true }
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = {
                    if (cities.isNotEmpty() && pagerState.currentPage < cities.size) {
                        isRefreshing = true
                        coroutineScope.launch {
                            val currentCityToRefresh = cities[pagerState.currentPage]
                            viewModel.fetchWeatherAndAirQuality(
                                currentCityToRefresh.name,
                                currentCityToRefresh.latitude,
                                currentCityToRefresh.longitude
                            )
                            isRefreshing = false
                        }
                    } else {
                        isRefreshing = false
                    }
                },
                indicator = { state, trigger ->
                    SwipeRefreshIndicator(
                        state = state,
                        refreshTriggerDistance = trigger,
                        scale = true,
                        backgroundColor = Color.White,
                        contentColor = Color(0xFF5372dc),
                        shape = RoundedCornerShape(50),
                        largeIndication = true,
                        modifier = Modifier
                            .padding(innerPadding)
                            .offset(y = 16.dp)
                            .size(40.dp)
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                            )
                        )
                        .padding(innerPadding)
                ) {
                    if (cities.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Không có thành phố nào. Vui lòng thêm.",
                                fontSize = 16.sp,
                                color = Color(0xFF5372dc),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 30.dp)
                            )
                        }
                    } else {
                        HorizontalPager(
                            count = cities.size,
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val city = cities[page]
                            val weatherData = viewModel.weatherDataMap[city.name]
                            val isNetworkAvailable = isNetworkAvailable(context)

                            when {
                                !isNetworkAvailable -> {
                                    OfflineScreen(lastUpdateTime = weatherData?.lastUpdateTime)
                                }
                                weatherData == null || (weatherData.timeList.isEmpty() && weatherData.dailyTimeList.isEmpty() && weatherData.currentAqi == null) || weatherData.errorMessage != null -> {
                                    LoadingOrErrorScreen(
                                        errorMessage = weatherData?.errorMessage,
                                        lastUpdateTime = weatherData?.lastUpdateTime
                                    )
                                }
                                else -> {
                                    val currentDate = LocalDate.now()
                                    val dateFormatter = remember { DateTimeFormatter.ofPattern("E, dd MMM") }
                                    val currentDateStr = remember(currentDate) { currentDate.format(dateFormatter) }
                                    val currentAqi = weatherData.currentAqi

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 30.dp),
                                        verticalArrangement = Arrangement.spacedBy(24.dp),
                                        contentPadding = PaddingValues(bottom = 30.dp)
                                    ) {
                                        item { CurrentWeatherSection(city, weatherData, viewModel, temperatureUnit) }
                                        item { AdditionalInfoSection(weatherData, viewModel, windSpeedUnit) }
                                        item { HourlyForecastSection(city, weatherData, viewModel, currentDateStr, temperatureUnit) }
                                        item { DailyForecastSection(city, weatherData, viewModel, temperatureUnit) }
                                        item { AirQualitySection(aqi = currentAqi) }
                                        item { OtherDetailsSection(weatherData, viewModel, temperatureUnit, windSpeedUnit, pressureUnit, visibilityUnit) }
                                        item { LastUpdateSection(weatherData) }
                                        item { Spacer(modifier = Modifier.height(20.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSearchOverlay) {
            SearchOverlay(
                onBackClick = { showSearchOverlay = false },
                onFilterClick = {
                    showSearchOverlay = false
                    showSearchScreen = true
                },
                onDismiss = {
                    viewModel.clearSearch()
                    showSearchOverlay = false
                },
                viewModel = viewModel
            )
        }
        if (showSearchScreen) {
            SearchScreen(
                onBackClick = {
                    showSearchScreen = false
                },
                onDismiss = { showSearchScreen = false }
            )
        }
    }
}

@Composable
fun OfflineScreen(lastUpdateTime: Long?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                contentDescription = "Offline",
                tint = Color(0xFF5372dc),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Không có kết nối mạng. Vui lòng kiểm tra lại.",
                fontSize = 16.sp,
                color = Color(0xFF5372dc),
                textAlign = TextAlign.Center
            )
            lastUpdateTime?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dữ liệu cập nhật lần cuối: ${formatTimestamp(it)}",
                    fontSize = 14.sp,
                    color = Color(0xFF5372dc)
                )
            }
        }
    }
}

@Composable
fun LoadingOrErrorScreen(errorMessage: String?, lastUpdateTime: Long?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (errorMessage != null) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                    contentDescription = "Error",
                    tint = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    fontSize = 16.sp,
                    color = Color.Red.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            } else {
                CircularProgressIndicator(color = Color(0xFF5372dc))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Đang tải dữ liệu thời tiết...",
                    fontSize = 16.sp,
                    color = Color(0xFF5372dc)
                )
            }
            lastUpdateTime?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dữ liệu cập nhật lần cuối: ${formatTimestamp(it)}",
                    fontSize = 14.sp,
                    color = Color(0xFF5372dc).copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun CurrentWeatherSection(
    city: City,
    weatherData: WeatherDataState,
    viewModel: WeatherViewModel,
    temperatureUnit: UnitConverter.TemperatureUnit
) {
    val index = remember(city.name, weatherData.timeList) { viewModel.getCurrentIndex(city.name) }
    val currentTemp = weatherData.temperatureList.getOrNull(index)?.let {
        UnitConverter.convertTemperature(it, temperatureUnit).toInt()
    } ?: 0
    val high = weatherData.dailyTempMaxList.firstOrNull()?.let {
        UnitConverter.convertTemperature(it, temperatureUnit).toInt()
    } ?: weatherData.temperatureList.maxOrNull()?.let {
        UnitConverter.convertTemperature(it, temperatureUnit).toInt()
    } ?: 0
    val low = weatherData.dailyTempMinList.firstOrNull()?.let {
        UnitConverter.convertTemperature(it, temperatureUnit).toInt()
    } ?: weatherData.temperatureList.minOrNull()?.let {
        UnitConverter.convertTemperature(it, temperatureUnit).toInt()
    } ?: 0
    val weatherCode = weatherData.weatherCodeList.getOrNull(index) ?: 0
    val weatherIcon = getWeatherIcon(weatherCode)
    val weatherText = getWeatherDescription(weatherCode)
    val tempUnitSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(city.name, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color(0xFF5372dc))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$currentTemp$tempUnitSymbol",
                fontSize = 70.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5372dc)
            )
            Text(weatherText, fontSize = 16.sp, color = Color(0xFF5372dc))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Cao: $high$tempUnitSymbol   Thấp: $low$tempUnitSymbol",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color(0xFF5372dc)
            )
        }
        Image(
            painter = painterResource(id = weatherIcon),
            contentDescription = weatherText,
            modifier = Modifier.size(150.dp)
        )
    }
}

@Composable
fun AdditionalInfoSection(
    weatherData: WeatherDataState,
    viewModel: WeatherViewModel,
    windSpeedUnit: UnitConverter.WindSpeedUnit
) {
    val index = remember(weatherData.timeList) { viewModel.getCurrentIndex(viewModel.currentCity) }
    val precipitation = weatherData.precipitationList.getOrNull(index)?.toInt() ?: 0
    val humidity = weatherData.humidityList.getOrNull(index)?.toInt() ?: 0
    val windSpeed = weatherData.windSpeedList.getOrNull(index)?.let {
        UnitConverter.convertWindSpeed(it, windSpeedUnit)
    } ?: "0"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        InfoItem(R.drawable.rain_dropp, "$precipitation%", "Mưa", Color(0xFF5372DC))
        InfoItem(R.drawable.humidity, "$humidity%", "Độ ẩm", Color(0xFFD05CA2))
        InfoItem(R.drawable.wind_speed, windSpeed, "Gió", Color(0xFF3F9CBE))
    }
}

@Composable
fun HourlyForecastSection(
    city: City,
    weatherData: WeatherDataState,
    viewModel: WeatherViewModel,
    currentDateStr: String,
    temperatureUnit: UnitConverter.TemperatureUnit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hôm nay", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
            Text(currentDateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
        }
        Spacer(modifier = Modifier.height(12.dp))

        val upcoming = remember(city.name, weatherData.timeList) { viewModel.getUpcomingForecast(city.name) }
        val currentIndex = remember(city.name, weatherData.timeList) { viewModel.getCurrentIndex(city.name) }
        val tempUnitSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"

        if (upcoming.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(upcoming) { (timeStr, temp, weatherCode) ->
                    val time = remember(timeStr) { LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
                    val formattedTime = remember(time) { time.format(DateTimeFormatter.ofPattern("HH:mm")) }
                    val isCurrentHour = remember(time, currentIndex, upcoming) {
                        try {
                            upcoming.getOrNull(currentIndex)?.first == timeStr
                        } catch (e: Exception) { false }
                    }
                    val convertedTemp = UnitConverter.convertTemperature(temp, temperatureUnit).toInt()

                    ForecastItem(
                        iconId = getWeatherIcon(weatherCode),
                        temp = "$convertedTemp$tempUnitSymbol",
                        time = formattedTime,
                        highlight = isCurrentHour
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(text = "Không có dữ liệu dự báo theo giờ.", fontSize = 14.sp, color = Color(0xFF5372dc))
            }
        }
    }
}

@Composable
fun DailyForecastSection(
    city: City,
    weatherData: WeatherDataState,
    viewModel: WeatherViewModel,
    temperatureUnit: UnitConverter.TemperatureUnit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text("Dự báo hàng ngày", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
        Spacer(modifier = Modifier.height(8.dp))

        val dailyForecastData = remember(city.name, weatherData.dailyTimeList) { viewModel.getDailyForecast(city.name, 7) }
        val tempUnitSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"

        if (dailyForecastData.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dailyForecastData.forEachIndexed { index, (time, temps, weatherCode) ->
                    val date = remember(time) { LocalDate.parse(time, DateTimeFormatter.ISO_LOCAL_DATE) }
                    val formattedDate = remember(date) {
                        when (date) {
                            LocalDate.now() -> "Hôm nay"
                            LocalDate.now().plusDays(1) -> "Ngày mai"
                            else -> date.format(DateTimeFormatter.ofPattern("E dd"))
                        }
                    }
                    val precipitation = weatherData.dailyPrecipitationList.getOrNull(index)?.toInt() ?: 0
                    val maxTemp = UnitConverter.convertTemperature(temps.first, temperatureUnit).toInt()
                    val minTemp = UnitConverter.convertTemperature(temps.second, temperatureUnit).toInt()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formattedDate,
                            color = Color(0xFF5372dc),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.3f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(0.2f)) {
                            Image(
                                painter = painterResource(id = getWeatherIcon(weatherCode)),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "$precipitation%",
                                color = Color(0xFF5372DC).copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            "$minTemp$tempUnitSymbol",
                            color = Color(0xFF5372dc).copy(alpha = 0.7f),
                            modifier = Modifier.weight(0.2f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            "$maxTemp$tempUnitSymbol",
                            color = Color(0xFF5372dc),
                            modifier = Modifier.weight(0.2f),
                            textAlign = TextAlign.End
                        )
                    }
                    if (index < dailyForecastData.size - 1) {
                        Divider(color = Color(0xFF5372dc).copy(alpha = 0.2f), thickness = 0.5.dp)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(text = "Không có dữ liệu dự báo hàng ngày.", fontSize = 14.sp, color = Color(0xFF5372dc))
            }
        }
    }
}

@Composable
fun AirQualitySection(aqi: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text("Chất lượng không khí", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5372DC))
        Spacer(modifier = Modifier.height(12.dp))

        if (aqi == null) {
            Text(
                text = "Thông tin chất lượng không khí không có sẵn.",
                fontSize = 14.sp,
                color = Color(0xFF5372DC).copy(alpha = 0.8f)
            )
        } else {
            val (description, color, percentage) = remember(aqi) { getAqiInfo(aqi) }

            Row(verticalAlignment = Alignment.Bottom) {
                Text("$aqi", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(description, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = getAqiRecommendation(description),
                fontSize = 13.sp,
                color = Color(0xFF5372DC)
            )
            Spacer(modifier = Modifier.height(16.dp))
            AirQualityBar(percentage = percentage)
        }
    }
}

@Composable
fun OtherDetailsSection(
    weatherData: WeatherDataState,
    viewModel: WeatherViewModel,
    temperatureUnit: UnitConverter.TemperatureUnit,
    windSpeedUnit: UnitConverter.WindSpeedUnit,
    pressureUnit: UnitConverter.PressureUnit,
    visibilityUnit: UnitConverter.VisibilityUnit
) {
    val index = remember(weatherData.timeList) { viewModel.getCurrentIndex(viewModel.currentCity) }
    val tempUnitSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"

    Column(modifier = Modifier.fillMaxWidth()) {
        val infoItems = listOfNotNull(
            weatherData.uvList.getOrNull(index)?.let { Triple(R.drawable.uv, "Chỉ số UV", it.toInt().toString()) },
            weatherData.feelsLikeList.getOrNull(index)?.let {
                Triple(R.drawable.feels_like, "Cảm giác như", "${UnitConverter.convertTemperature(it, temperatureUnit).toInt()}$tempUnitSymbol")
            },
            weatherData.humidityList.getOrNull(index)?.let { Triple(R.drawable.humidity2, "Độ ẩm", "${it.toInt()}%") },
            weatherData.windSpeedList.getOrNull(index)?.let {
                Triple(R.drawable.ese_wind, "Gió", UnitConverter.convertWindSpeed(it, windSpeedUnit))
            },
            weatherData.pressureList.getOrNull(index)?.let {
                Triple(R.drawable.air_pressure, "Áp suất", UnitConverter.convertPressure(it, pressureUnit))
            },
            weatherData.visibilityList.getOrNull(index)?.let {
                Triple(R.drawable.visibility, "Tầm nhìn", UnitConverter.convertVisibility(it, visibilityUnit))
            }
        )

        infoItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowItems.forEach { (iconId, label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = iconId),
                            contentDescription = label,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(label, color = Color(0xFF5372DC).copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center)
                        Text(
                            value,
                            color = Color(0xFF5372DC),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun LastUpdateSection(weatherData: WeatherDataState) {
    weatherData.lastUpdateTime?.let {
        Text(
            text = "Cập nhật lần cuối: ${formatTimestamp(it)}",
            fontSize = 12.sp,
            color = Color(0xFF5372dc).copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun InfoItem(iconId: Int, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = label,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
fun ForecastItem(iconId: Int, temp: String, time: String, highlight: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color = if (highlight) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .width(55.dp)
    ) {
        Text(time, fontSize = 11.sp, color = Color(0xFF5372dc), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(temp, fontSize = 14.sp, color = Color(0xFF5372dc), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AirQualityBar(percentage: Float) {
    val barHeight = 8.dp
    val barColors = listOf(
        Color(0xFF77DA77), Color(0xFFECF667), Color(0xFFEFA95F),
        Color(0xFFEF5365), Color(0xFF973F6F), Color(0xFF7E003B)
    )
    val indicatorWidth = 4.dp
    val indicatorHeight = barHeight + 4.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(indicatorHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxWidthDp = this.maxWidth

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.CenterStart)
        ) {
            val segmentWidth = size.width / barColors.size
            barColors.forEachIndexed { i, color ->
                drawRect(
                    color = color,
                    topLeft = Offset(x = i * segmentWidth, y = 0f),
                    size = Size(segmentWidth, size.height)
                )
            }
        }

        val indicatorOffsetDp = (maxWidthDp * percentage - indicatorWidth / 2)
            .coerceIn(0.dp, maxWidthDp - indicatorWidth)

        Box(
            modifier = Modifier
                .offset(x = indicatorOffsetDp)
                .width(indicatorWidth)
                .height(indicatorHeight)
                .background(Color.White, shape = RoundedCornerShape(2.dp))
                .align(Alignment.CenterStart)
        )
    }
}

fun getAqiInfo(aqi: Int): Triple<String, Color, Float> {
    return when (aqi) {
        in 0..50 -> Triple("Tốt", Color(0xFF6BD56B), calculatePercentage(aqi, 0, 50, 6))
        in 51..100 -> Triple("Trung bình", Color(0xFFF1F15D), calculatePercentage(aqi, 51, 100, 6))
        in 101..150 -> Triple("Không tốt cho nhóm nhạy cảm", Color(0xFFFF7E00), calculatePercentage(aqi, 101, 150, 6))
        in 151..200 -> Triple("Không tốt", Color(0xFFD34444), calculatePercentage(aqi, 151, 200, 6))
        in 201..300 -> Triple("Rất không tốt", Color(0xFF8F3F97), calculatePercentage(aqi, 201, 300, 6))
        else -> Triple("Nguy hiểm", Color(0xFF7E0023), calculatePercentage(aqi, 301, 500, 6))
    }
}

fun calculatePercentage(value: Int, minRange: Int, maxRange: Int, totalSegments: Int): Float {
    val segmentIndex = when {
        value <= 50 -> 0
        value <= 100 -> 1
        value <= 150 -> 2
        value <= 200 -> 3
        value <= 300 -> 4
        else -> 5
    }
    val valueInRange = value.coerceIn(minRange, maxRange)
    val rangeSize = (maxRange - minRange).toFloat().coerceAtLeast(1f)
    val positionInSegment = (valueInRange - minRange) / rangeSize
    val segmentWidthPercentage = 1f / totalSegments
    return (segmentIndex * segmentWidthPercentage) + (positionInSegment * segmentWidthPercentage)
}

fun getAqiRecommendation(description: String): String {
    return when (description) {
        "Tốt" -> "Chất lượng không khí rất tốt. Tận hưởng các hoạt động ngoài trời!"
        "Trung bình" -> "Chất lượng không khí chấp nhận được. Nhóm nhạy cảm nên cân nhắc giảm hoạt động mạnh ngoài trời."
        "Không tốt cho nhóm nhạy cảm" -> "Người có vấn đề hô hấp, tim mạch, người già, trẻ em nên hạn chế ra ngoài."
        "Không tốt" -> "Mọi người có thể bị ảnh hưởng sức khỏe, nhóm nhạy cảm có thể bị ảnh hưởng nghiêm trọng. Hạn chế ra ngoài."
        "Rất không tốt" -> "Cảnh báo sức khỏe nghiêm trọng. Mọi người nên tránh các hoạt động ngoài trời."
        "Nguy hiểm" -> "Cảnh báo khẩn cấp về sức khỏe. Mọi người nên ở trong nhà."
        else -> "Không có thông tin khuyến nghị."
    }
}

@Composable
fun getWeatherIcon(code: Int): Int {
    return remember(code) {
        when (code) {
            0 -> R.drawable.sunny
            1 -> R.drawable.cloudy_with_sun
            2 -> R.drawable.cloudy_with_sun
            3 -> R.drawable.cloudy
            45, 48 -> R.drawable.cloudy
            51, 53, 55 -> R.drawable.rainingg
            56, 57 -> R.drawable.rainingg
            61, 63, 65 -> R.drawable.rainingg
            66, 67 -> R.drawable.rainingg
            71, 73, 75 -> R.drawable.snow
            77 -> R.drawable.snow
            80, 81, 82 -> R.drawable.rainingg
            85, 86 -> R.drawable.snow
            95 -> R.drawable.thunderstorm
            96, 99 -> R.drawable.thunderstorm
            else -> R.drawable.cloudy_with_sun
        }
    }
}

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
        95 -> "Dông"
        96, 99 -> "Dông có mưa đá"
        else -> "Không xác định"
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

fun formatTimestamp(timestamp: Long): String {
    return try {
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"))
    } catch (e: Exception) {
        "Không rõ"
    }
}
