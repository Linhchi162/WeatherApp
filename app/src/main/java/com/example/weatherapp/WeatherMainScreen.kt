package com.example.weatherapp

import android.content.Context // Import Context
import android.net.ConnectivityManager // Import ConnectivityManager
import android.net.NetworkCapabilities // Import NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items // Import items for LazyColumn/LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size // Import Size for Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import kotlinx.coroutines.launch // Import launch



// Import R if needed (assuming it's in the same package or imported correctly)
// import com.example.weatherapp.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun WeatherMainScreen(
    // Sử dụng factory để khởi tạo ViewModel với các dependencies cần thiết
    viewModel: WeatherViewModel = viewModel(
        factory = WeatherViewModelFactory(
            weatherDao = WeatherDatabase.getDatabase(LocalContext.current).weatherDao(),
            openMeteoService = RetrofitInstance.api, // Đảm bảo dùng đúng instance
            airQualityService = RetrofitInstance.airQualityApi
        )
    )
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Sử dụng danh sách thành phố từ viewModel
    val cities = viewModel.citiesList
    val initialPage = remember(cities, viewModel.currentCity) {
        cities.indexOfFirst { it.name == viewModel.currentCity }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage)

    var showSearchOverlay by remember { mutableStateOf(false) }
    var showSearchScreen by remember { mutableStateOf(false) }
    var showWeatherDetailsScreen by remember { mutableStateOf(false) }
    var showNavBar by remember { mutableStateOf(true) }
    var showDeleteCityDialog by remember { mutableStateOf(false) }
    var showFilteredCitiesScreen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    // Cập nhật thành phố hiện tại trong ViewModel khi Pager chuyển trang
    LaunchedEffect(pagerState.currentPage, cities.size) { // Thêm cities.size để cập nhật nếu list thay đổi
        if (cities.isNotEmpty() && pagerState.currentPage < cities.size) {
            viewModel.updateCurrentCity(cities[pagerState.currentPage].name)
        } else if (cities.isEmpty()) {
            // Xử lý trường hợp không còn thành phố nào
            // viewModel.updateCurrentCity(null) // Ví dụ
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
                            // Không cần delay cứng, isRefreshing sẽ tự false khi fetch xong (nếu logic ViewModel đúng)
                            // kotlinx.coroutines.delay(1000)
                            isRefreshing = false // Nên để ViewModel quản lý trạng thái loading thì tốt hơn
                        }
                    } else {
                        isRefreshing = false // Không có gì để refresh
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
                            .padding(innerPadding) // Áp dụng padding từ Scaffold
                            .offset(y = 16.dp) // Điều chỉnh vị trí indicator nếu cần
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
                        .padding(innerPadding) // Áp dụng padding từ Scaffold vào đây
                ) {
                    if (cities.isEmpty()) {
                        // Hiển thị thông báo khi không có thành phố nào
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
                            // Lấy WeatherDataState từ map trong ViewModel
                            val weatherData = viewModel.weatherDataMap[city.name]
                            val isNetworkAvailable = isNetworkAvailable(context) // Hàm kiểm tra mạng

                            // --- Các trạng thái hiển thị khác nhau ---
                            when {
                                // 1. Không có mạng
                                !isNetworkAvailable -> {
                                    // Hiển thị thông báo mất mạng và thời gian cập nhật cuối (nếu có)
                                    OfflineScreen(lastUpdateTime = weatherData?.lastUpdateTime)
                                }
                                // 2. Đang tải hoặc chưa có dữ liệu hoặc có lỗi từ ViewModel
                                weatherData == null || (weatherData.timeList.isEmpty() && weatherData.dailyTimeList.isEmpty() && weatherData.currentAqi == null) || weatherData.errorMessage != null -> {
                                    LoadingOrErrorScreen(
                                        errorMessage = weatherData?.errorMessage,
                                        lastUpdateTime = weatherData?.lastUpdateTime
                                    )
                                }
                                // 3. Có dữ liệu -> Hiển thị màn hình chính
                                else -> {
                                    val currentDate = LocalDate.now()
                                    val dateFormatter = remember { DateTimeFormatter.ofPattern("E, dd MMM") } // Định dạng ngày thân thiện hơn
                                    val currentDateStr = remember(currentDate) { currentDate.format(dateFormatter) }

                                    // --- Lấy dữ liệu AQI ---
                                    val currentAqi = weatherData.currentAqi // Lấy AQI từ state (nullable)

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 30.dp),
                                        verticalArrangement = Arrangement.spacedBy(24.dp),
                                        contentPadding = PaddingValues(bottom = 30.dp) // Thêm padding dưới cùng
                                    ) {
                                        // Phần thông tin thời tiết chính (Nhiệt độ, icon, H/L)
                                        item { CurrentWeatherSection(city, weatherData, viewModel) }

                                        // Phần thông tin phụ (Mưa, Độ ẩm, Gió)
                                        item { AdditionalInfoSection(weatherData, viewModel) }

                                        // Phần dự báo theo giờ
                                        item { HourlyForecastSection(city, weatherData, viewModel, currentDateStr) }

                                        // Phần dự báo theo ngày
                                        item { DailyForecastSection(city, weatherData, viewModel) }

                                        // --- Phần Chất lượng không khí ---
                                        item {
                                            // Truyền currentAqi (nullable) vào AirQualitySection
                                            AirQualitySection(aqi = currentAqi)
                                        }

                                        // Phần chi tiết khác (UV, Feels like, ...)
                                        item { OtherDetailsSection(weatherData, viewModel) }

                                        // Thời gian cập nhật cuối
                                        item { LastUpdateSection(weatherData) }

                                        // Spacer cuối cùng để tránh bị che khuất
                                        item { Spacer(modifier = Modifier.height(20.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Overlay và Search Screen
        if (showSearchOverlay) {
            SearchOverlay(
                onBackClick = { showSearchOverlay = false },
                onFilterClick = {
                    showSearchScreen = true
                    showSearchOverlay = false
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
                onBackClick = { showSearchScreen = false },
                onDismiss = { showSearchScreen = false },
                onShowFilteredResults = { 
                    showSearchScreen = false
                    showFilteredCitiesScreen = true 
                },
                viewModel = viewModel
            )
        }
        
        // Hiển thị màn hình thành phố đã lọc
        if (showFilteredCitiesScreen) {
            FilteredCitiesScreen(
                onBackClick = { 
                    showFilteredCitiesScreen = false
                    showSearchScreen = true // Quay lại màn hình lọc
                },
                onDismiss = { showFilteredCitiesScreen = false },
                viewModel = viewModel
            )
        }
    }
}

// --- Các Composable con cho màn hình chính (Tách ra cho dễ quản lý) ---

@Composable
fun OfflineScreen(lastUpdateTime: Long?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_dialog_alert), // Icon cảnh báo
                contentDescription = "Offline",
                tint = Color(0xFF5372dc),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Không có kết nối mạng. Vui lòng kiểm tra lại.",
                fontSize = 16.sp, color = Color(0xFF5372dc), textAlign = TextAlign.Center
            )
            lastUpdateTime?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dữ liệu cập nhật lần cuối: ${formatTimestamp(it)}",
                    fontSize = 14.sp, color = Color(0xFF5372dc)
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
                    fontSize = 16.sp, color = Color.Red.copy(alpha = 0.8f), textAlign = TextAlign.Center
                )
            } else {
                CircularProgressIndicator(color = Color(0xFF5372dc))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Đang tải dữ liệu thời tiết...",
                    fontSize = 16.sp, color = Color(0xFF5372dc)
                )
            }
            lastUpdateTime?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dữ liệu cập nhật lần cuối: ${formatTimestamp(it)}",
                    fontSize = 14.sp, color = Color(0xFF5372dc).copy(alpha = 0.8f)
                )
            }
        }
    }
}


@Composable
fun CurrentWeatherSection(city: City, weatherData: WeatherDataState, viewModel: WeatherViewModel) {
    val index = remember(city.name, weatherData.timeList) { viewModel.getCurrentIndex(city.name) }
    val currentTemp = weatherData.temperatureList.getOrNull(index)?.toInt() ?: 0
    // Lấy max/min từ daily data cho ngày hiện tại (index 0) thì chính xác hơn
    val high = weatherData.dailyTempMaxList.firstOrNull()?.toInt() ?: weatherData.temperatureList.maxOrNull()?.toInt() ?: 0 // Fallback
    val low = weatherData.dailyTempMinList.firstOrNull()?.toInt() ?: weatherData.temperatureList.minOrNull()?.toInt() ?: 0 // Fallback
    val weatherCode = weatherData.weatherCodeList.getOrNull(index) ?: 0
    val weatherIcon = getWeatherIcon(weatherCode) // Hàm lấy icon từ code
    val weatherText = getWeatherDescription(weatherCode) // Hàm lấy mô tả thời tiết

    // Đảm bảo hiển thị tỷ lệ mưa đúng cho các mã thời tiết dông/mưa
    val adjustedRainPercentage = remember(weatherCode) {
        when {
            // Nếu là mã thời tiết dông/mưa rào/mưa to mà xác suất mưa < 30% thì điều chỉnh
            (weatherCode in 95..99 || weatherCode in 80..82 || weatherCode in 61..67) && 
              (weatherData.dailyPrecipitationList.getOrNull(index)?.toInt() ?: 0) < 30 -> 
                max(weatherData.dailyPrecipitationList.getOrNull(index)?.toInt() ?: 0, 60)  // Tối thiểu 60% cho dông
            else -> weatherData.dailyPrecipitationList.getOrNull(index)?.toInt() ?: 0
        }
    }
    
    // Khi hiển thị phần mưa, dùng giá trị đã điều chỉnh
    // Tìm đến phần hiển thị % mưa và thay thế bằng giá trị đã điều chỉnh
    val precipitationText = if (weatherCode in 51..67 || weatherCode in 80..82) "$adjustedRainPercentage%" else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically // Căn giữa theo chiều dọc tốt hơn
    ) {
        Column {
            // Spacer(modifier = Modifier.height(10.dp)) // Không cần spacer cứng ở đây
            Text(city.name, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color(0xFF5372dc)) // Tăng cỡ chữ tên TP
            Spacer(modifier = Modifier.height(4.dp)) // Giảm khoảng cách
            Text("$currentTemp°", fontSize = 70.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5372dc))
            Text(weatherText, fontSize = 16.sp, color = Color(0xFF5372dc))
            Spacer(modifier = Modifier.height(6.dp)) // Giảm khoảng cách
            Text("Cao: $high°   Thấp: $low°", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFF5372dc)) // Dùng Medium weight
        }
        // Spacer(modifier = Modifier.width(16.dp)) // Giảm khoảng cách
        Image(
            painter = painterResource(id = weatherIcon),
            contentDescription = weatherText, // Thêm content description
            modifier = Modifier.size(150.dp) // Giảm kích thước icon một chút
        )
    }
}

@Composable
fun AdditionalInfoSection(weatherData: WeatherDataState, viewModel: WeatherViewModel) {
    val index = viewModel.getCurrentIndex(viewModel.currentCity) // Lấy index hiện tại
    val currentWeatherCode = weatherData.weatherCodeList.getOrNull(index) ?: 0

    // Lấy dữ liệu từ weather state
    val currentHumidity = weatherData.humidityList.getOrNull(index)?.toInt() ?: 0
    val currentWindSpeed = weatherData.windSpeedList.getOrNull(index)?.toInt() ?: 0
    
    // Điều chỉnh giá trị % mưa nếu thời tiết là mưa/dông mà % mưa thấp
    val rawPrecipitation = weatherData.dailyPrecipitationList.getOrNull(0)?.toInt() ?: 0 // Dùng tỷ lệ mưa theo ngày
    val minRainProbability = getMinRainProbabilityForWeatherCode(currentWeatherCode)
    val adjustedPrecipitation = if (rawPrecipitation < minRainProbability && minRainProbability > 0) 
                                minRainProbability else rawPrecipitation

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround, // Phân bố đều hơn
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoItem(R.drawable.rain_dropp, "$adjustedPrecipitation%", "Mưa", Color(0xFF5372DC))
            InfoItem(R.drawable.humidity, "$currentHumidity%", "Độ ẩm", Color(0xFFD05CA2))
            InfoItem(R.drawable.wind_speed, "${currentWindSpeed}km/h", "Gió", Color(0xFF3F9CBE))
        }
    }
}

@Composable
fun HourlyForecastSection(city: City, weatherData: WeatherDataState, viewModel: WeatherViewModel, currentDateStr: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp)) // Bo góc ít hơn
            .padding(16.dp) // Giảm padding chút
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hôm nay", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
            Text(currentDateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
        }
        Spacer(modifier = Modifier.height(12.dp)) // Tăng khoảng cách chút

        val upcoming = remember(city.name, weatherData.timeList) { viewModel.getUpcomingForecast(city.name) }
        val currentIndex = remember(city.name, weatherData.timeList) { viewModel.getCurrentIndex(city.name) }

        if (upcoming.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp) // Tăng khoảng cách giữa các item
            ) {
                items(upcoming) { (timeStr, temp, weatherCode) ->
                    val time = remember(timeStr) { LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
                    val formattedTime = remember(time) { time.format(DateTimeFormatter.ofPattern("HH:mm")) }
                    // Xác định xem item này có phải là giờ hiện tại không
                    val isCurrentHour = remember(time, currentIndex, upcoming) {
                        try {
                            upcoming.getOrNull(currentIndex)?.first == timeStr
                        } catch (e: Exception) { false }
                    }

                    ForecastItem(
                        iconId = getWeatherIcon(weatherCode),
                        temp = "${temp.toInt()}°", // Bỏ C
                        time = formattedTime,
                        highlight = isCurrentHour // Highlight giờ hiện tại
                    )
                }
            }
        } else {
            // Hiển thị nếu không có dữ liệu dự báo giờ
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(text = "Không có dữ liệu dự báo theo giờ.", fontSize = 14.sp, color = Color(0xFF5372dc))
            }
        }
    }
}

@Composable
fun DailyForecastSection(city: City, weatherData: WeatherDataState, viewModel: WeatherViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text("Dự báo hàng ngày", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
        Spacer(modifier = Modifier.height(8.dp))

        val dailyForecastData = remember(city.name, weatherData.dailyTimeList) { viewModel.getDailyForecast(city.name, 7) } // Lấy 7 ngày

        if (dailyForecastData.isNotEmpty()) {
            // Không dùng LazyColumn lồng trong LazyColumn, dùng Column đơn giản
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dailyForecastData.forEachIndexed { index, (time, temps, weatherCode) ->
                    val date = remember(time) { LocalDate.parse(time, DateTimeFormatter.ISO_LOCAL_DATE) }
                    // Định dạng ngày: Thứ, ngày (vd: T2, 18) hoặc Hôm nay/Ngày mai
                    val formattedDate = remember(date) {
                        when (date) {
                            LocalDate.now() -> "Hôm nay"
                            LocalDate.now().plusDays(1) -> "Ngày mai"
                            else -> date.format(DateTimeFormatter.ofPattern("E dd"))
                        }
                    }
                    val precipitation = weatherData.dailyPrecipitationList.getOrNull(index)?.toInt() ?: 0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp), // Tăng padding dọc
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formattedDate,
                            color = Color(0xFF5372dc),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.3f) // Phân bổ không gian
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(0.2f)) {
                            Image(
                                painter = painterResource(id = getWeatherIcon(weatherCode)),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp) // Icon nhỏ hơn chút
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "$precipitation%",
                                color = Color(0xFF5372DC).copy(alpha=0.8f),
                                fontSize = 12.sp // Cỡ chữ nhỏ hơn
                            )
                        }

                        Text(
                            "${temps.second.toInt()}°", // Nhiệt độ thấp
                            color = Color(0xFF5372dc).copy(alpha = 0.7f), // Màu nhạt hơn
                            modifier = Modifier.weight(0.2f),
                            textAlign = TextAlign.End // Căn phải
                        )
                        // Có thể thêm thanh progress bar nhỏ thể hiện khoảng nhiệt độ
                        Text(
                            "${temps.first.toInt()}°", // Nhiệt độ cao
                            color = Color(0xFF5372dc),
                            modifier = Modifier.weight(0.2f), // Giảm weight
                            textAlign = TextAlign.End // Căn phải
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

// --- Cập nhật AirQualitySection ---
@Composable
fun AirQualitySection(aqi: Int?) { // Chấp nhận Int? (nullable)
    Column(
        modifier = Modifier
            .fillMaxWidth() // Sửa thành fillMaxWidth
            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
            .padding(16.dp) // Giảm padding
    ) {
        Text("Chất lượng không khí", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5372DC))
        Spacer(modifier = Modifier.height(12.dp)) // Tăng khoảng cách

        // Kiểm tra nếu aqi là null
        if (aqi == null) {
            Text(
                text = "Thông tin chất lượng không khí không có sẵn.",
                fontSize = 14.sp,
                color = Color(0xFF5372DC).copy(alpha = 0.8f)
            )
        } else {
            // Nếu aqi không null, hiển thị như cũ
            val (description, color, percentage) = remember(aqi) { getAqiInfo(aqi) } // Dùng remember

            Row(verticalAlignment = Alignment.Bottom) { // Căn chỉnh dưới để số và chữ thẳng hàng hơn
                Text("$aqi", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color) // Giảm cỡ chữ AQI
                Spacer(modifier = Modifier.width(8.dp))
                Text(description, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(bottom = 4.dp)) // Giảm cỡ chữ mô tả
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = getAqiRecommendation(description), // Hàm lấy khuyến nghị
                fontSize = 13.sp,
                color = Color(0xFF5372DC)
            )
            Spacer(modifier = Modifier.height(16.dp))
            AirQualityBar(percentage = percentage)
        }
    }
}


@Composable
fun OtherDetailsSection(weatherData: WeatherDataState, viewModel: WeatherViewModel) {
    val index = remember(weatherData.timeList) { viewModel.getCurrentIndex(viewModel.currentCity) }
    Column(
        modifier = Modifier.fillMaxWidth()
        // Không cần background riêng ở đây nếu các item con có background
    ) {
        // Chia thành các hàng, mỗi hàng 2 hoặc 3 item
        val infoItems = listOfNotNull(
            weatherData.uvList.getOrNull(index)?.let { Triple(R.drawable.uv, "Chỉ số UV", it.toInt().toString()) },
            weatherData.feelsLikeList.getOrNull(index)?.let { Triple(R.drawable.feels_like, "Cảm giác như", "${it.toInt()}°") },
            weatherData.humidityList.getOrNull(index)?.let { Triple(R.drawable.humidity2, "Độ ẩm", "${it.toInt()}%") },
            weatherData.windSpeedList.getOrNull(index)?.let { Triple(R.drawable.ese_wind, "Gió", "${it.toInt()}km/h") }, // Đơn giản label
            weatherData.pressureList.getOrNull(index)?.let { Triple(R.drawable.air_pressure, "Áp suất", "${(it * 0.75006).toInt()}mmHg") }, // Làm tròn mmHg
            weatherData.visibilityList.getOrNull(index)?.let { Triple(R.drawable.visibility, "Tầm nhìn", "${(it / 1000).toInt()}km") } // Làm tròn km
        )

        infoItems.chunked(2).forEach { rowItems -> // Chia thành các hàng 2 cột
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp), // Thêm padding dọc giữa các hàng
                horizontalArrangement = Arrangement.spacedBy(16.dp) // Khoảng cách giữa các cột
            ) {
                rowItems.forEach { (iconId, label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f) // Chia đều không gian
                            .height(100.dp) // Giảm chiều cao chút
                            .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
                            .padding(12.dp), // Tăng padding bên trong
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center // Căn giữa nội dung
                    ) {
                        Image(
                            painter = painterResource(id = iconId),
                            contentDescription = label, // Thêm content description
                            modifier = Modifier.size(28.dp) // Icon nhỏ hơn
                        )
                        Spacer(modifier = Modifier.height(8.dp)) // Tăng khoảng cách
                        Text(label, color = Color(0xFF5372DC).copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center) // Căn giữa text
                        Text(
                            value,
                            color = Color(0xFF5372DC),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp // Tăng cỡ chữ giá trị
                        )
                    }
                }
                // Nếu hàng chỉ có 1 item (trường hợp list lẻ), thêm Spacer để đẩy item đó sang trái
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
            color = Color(0xFF5372dc).copy(alpha = 0.7f), // Màu nhạt hơn
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}


// --- Các Composable phụ trợ (InfoItem, ForecastItem, AirQualityBar, getAqiInfo) ---

@Composable
fun InfoItem(iconId: Int, value: String, label: String, color: Color) { // Thêm label
    Column(horizontalAlignment = Alignment.CenterHorizontally) { // Dùng Column
        Image(
            painter = painterResource(id = iconId),
            contentDescription = label, // Dùng label làm content description
            modifier = Modifier.size(20.dp) // Icon lớn hơn chút
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color.copy(alpha = 0.8f), fontSize = 11.sp) // Thêm label nhỏ bên dưới
    }
}

@Composable
fun ForecastItem(iconId: Int, temp: String, time: String, highlight: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color = if (highlight) Color.White.copy(alpha = 0.6f) else Color.Transparent, // Nổi bật hơn
                shape = RoundedCornerShape(12.dp) // Bo góc nhiều hơn
            )
            .padding(horizontal = 10.dp, vertical = 8.dp) // Tăng padding
            .width(55.dp) // Rộng hơn chút
    ) {
        Text(time, fontSize = 11.sp, color = Color(0xFF5372dc), fontWeight = FontWeight.Medium) // Tăng cỡ chữ thời gian
        Spacer(modifier = Modifier.height(6.dp)) // Tăng khoảng cách
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null, // Nên thêm content description dựa vào temp/icon
            modifier = Modifier.size(32.dp)) // Icon lớn hơn
        Spacer(modifier = Modifier.height(6.dp)) // Tăng khoảng cách
        Text(temp, fontSize = 14.sp, color = Color(0xFF5372dc), fontWeight = FontWeight.Bold) // Tăng cỡ chữ nhiệt độ
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

    // Sử dụng BoxWithConstraints để lấy chiều rộng tối đa
    BoxWithConstraints( // <--- THAY ĐỔI Ở ĐÂY
        modifier = Modifier
            .fillMaxWidth()
            .height(indicatorHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        // maxWidth đã có sẵn trong scope này (kiểu Dp)
        val maxWidthDp = this.maxWidth

        // Thanh màu gradient
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.CenterStart)
        ) {
            // size.width (pixels) có sẵn trong Canvas
            val segmentWidth = size.width / barColors.size
            barColors.forEachIndexed { i, color ->
                drawRect(
                    color = color,
                    topLeft = Offset(x = i * segmentWidth, y = 0f),
                    size = Size(segmentWidth, size.height)
                )
            }
        }

        // Indicator (vạch trắng)
        // Tính toán offset dựa trên maxWidthDp (kiểu Dp)
        val indicatorOffsetDp = (maxWidthDp * percentage - indicatorWidth / 2)
            .coerceIn(0.dp, maxWidthDp - indicatorWidth) // Đảm bảo offset nằm trong giới hạn

        Box(
            modifier = Modifier
                .offset(x = indicatorOffsetDp) // Áp dụng offset Dp
                .width(indicatorWidth)
                .height(indicatorHeight)
                .background(Color.White, shape = RoundedCornerShape(2.dp))
                .align(Alignment.CenterStart)
        )
    }
}


// --- Hàm tiện ích ---

// Hàm lấy thông tin AQI (giữ nguyên hoặc cải tiến)

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

// Hàm tính toán phần trăm vị trí trên thanh AQI (chia thành 6 đoạn)
fun calculatePercentage(value: Int, minRange: Int, maxRange: Int, totalSegments: Int): Float {
    val segmentIndex = when {
        value <= 50 -> 0
        value <= 100 -> 1
        value <= 150 -> 2
        value <= 200 -> 3
        value <= 300 -> 4
        else -> 5
    }
    // Tính toán vị trí tương đối trong đoạn hiện tại
    val valueInRange = value.coerceIn(minRange, maxRange)
    val rangeSize = (maxRange - minRange).toFloat().coerceAtLeast(1f) // Tránh chia cho 0
    val positionInSegment = (valueInRange - minRange) / rangeSize

    // Tính toán phần trăm tổng thể
    val segmentWidthPercentage = 1f / totalSegments
    return (segmentIndex * segmentWidthPercentage) + (positionInSegment * segmentWidthPercentage)
}


// Hàm lấy khuyến nghị dựa trên mô tả AQI
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


// Hàm lấy icon thời tiết (giữ nguyên hoặc cải tiến)
@Composable
fun getWeatherIcon(code: Int): Int {
    // Kiểm tra thời gian hiện tại
    val isNightTime = remember {
        val currentHour = java.time.LocalTime.now().hour
        currentHour < 6 || currentHour >= 18 // Đêm từ 18:00 đến 6:00
    }
    
    // Nên dùng remember
    return remember(code, isNightTime) {
        when (code) {
            0 -> if (isNightTime) R.drawable.clear_night else R.drawable.sunny // Nắng rõ
            1 -> if (isNightTime) R.drawable.clear_night else R.drawable.cloudy_with_sun // Nắng nhẹ
            2 -> if (isNightTime) R.drawable.cloudy_night else R.drawable.cloudy_with_sun // Mây rải rác
            3 -> R.drawable.cloudy // Nhiều mây, u ám
            45, 48 -> R.drawable.cloudy // Sương mù
            51, 53, 55 -> R.drawable.rainingg // Mưa phùn
            56, 57 -> R.drawable.rainingg // Mưa phùn đông đá (hiếm) -> dùng tạm icon mưa phùn
            61, 63, 65 -> R.drawable.rainingg // Mưa (nhẹ, vừa, nặng)
            66, 67 -> R.drawable.rainingg // Mưa đông đá (hiếm) -> dùng tạm icon mưa
            71, 73, 75 -> R.drawable.snow // Tuyết (nhẹ, vừa, nặng)
            77 -> R.drawable.snow // Hạt tuyết (hiếm) -> dùng tạm icon tuyết
            80, 81, 82 -> R.drawable.rainingg // Mưa rào (nhẹ, vừa, dữ dội)
            85, 86 -> R.drawable.snow // Mưa tuyết (nhẹ, nặng)
            95 -> R.drawable.thunderstorm // Dông (nhẹ/vừa)
            96, 99 -> R.drawable.thunderstorm // Dông có mưa đá (nhẹ/nặng)
            else -> if (isNightTime) R.drawable.cloudy_night else R.drawable.cloudy_with_sun // Mặc định
        }
    }
}

// Hàm lấy mô tả thời tiết (ví dụ)
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

// Thêm hàm mới để lấy xác suất mưa tương ứng với mã thời tiết
fun getMinRainProbabilityForWeatherCode(code: Int): Int {
    return when (code) {
        0, 1 -> 0  // Trời quang, nắng nhẹ
        2, 3 -> 5  // Mây rải rác, nhiều mây
        45, 48 -> 20  // Sương mù
        51, 53, 55 -> 50  // Mưa phùn
        56, 57 -> 60  // Mưa phùn đông đá
        61 -> 65  // Mưa nhỏ
        63 -> 75  // Mưa vừa
        65 -> 85  // Mưa to
        66, 67 -> 70  // Mưa đông đá
        71, 73, 75, 77 -> 60  // Tuyết
        80 -> 60  // Mưa rào nhẹ
        81 -> 75  // Mưa rào vừa
        82 -> 85  // Mưa rào dữ dội
        85, 86 -> 70  // Mưa tuyết
        95, 96, 99 -> 80  // Dông
        else -> 0
    }
}

// Hàm kiểm tra mạng (giữ nguyên)
@Composable
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

// Hàm định dạng timestamp (ví dụ)
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


// Preview (cần cập nhật để hoạt động đúng)
//@Preview(showBackground = true, heightDp = 1200) // Tăng chiều cao preview
//@Composable
//fun WeatherScreenPreview() {
//    // Cần cung cấp ViewModel giả lập hoặc dùng Hilt/Koin Preview
//    // WeatherMainScreen() // Gọi trực tiếp có thể lỗi nếu ViewModel cần dependencies thật
//    // Ví dụ tạo ViewModel giả lập (đơn giản)
//    val fakeViewModel = WeatherViewModel(
//        FakeWeatherDao(), // Dao giả lập
//        FakeOpenMeteoService(), // Service giả lập
//        // Service giả lập removed
//    )
//    // Gán dữ liệu giả vào ViewModel nếu cần để preview
//    fakeViewModel.weatherDataMap = mapOf(
//        "Hà Nội" to WeatherDataState(
//            timeList = listOf("2024-04-18T10:00"),
//            temperatureList = listOf(30.0),
//            weatherCodeList = listOf(1),
//            dailyTempMaxList = listOf(35.0),
//            dailyTempMinList = listOf(25.0),
//            dailyTimeList = listOf("2024-04-18"),
//            dailyWeatherCodeList = listOf(1),
//            currentAqi = 75 // AQI giả lập
//            // ... thêm dữ liệu giả khác ...
//        )
//    )
//    fakeViewModel.updateCurrentCity("Hà Nội")
//
//    WeatherMainScreen(viewModel = fakeViewModel)
//}
//
//// --- Các lớp giả lập cho Preview ---
//class FakeWeatherDao : WeatherDao { /* Implement các hàm với dữ liệu giả hoặc rỗng */
//    override suspend fun insertWeatherData(weatherData: WeatherData): Long = 1L
//    override suspend fun insertWeatherDetails(weatherDetails: List<WeatherDetail>): List<Long> = emptyList()
//    override suspend fun insertWeatherDailyDetails(dailyDetails: List<WeatherDailyDetail>): List<Long> = emptyList()
//    override suspend fun getLatestWeatherDataWithDetailsForCity(cityName: String): WeatherDataWithDetails? = null
//    override suspend fun getLatestWeatherDataWithDailyDetailsForCity(cityName: String): WeatherDataWithDailyDetails? = null
//    override suspend fun getWeatherDetails(weatherDataId: Long): List<WeatherDetail> = emptyList()
//    override suspend fun getWeatherDailyDetails(weatherDataId: Long): List<WeatherDailyDetail> = emptyList()
//    override suspend fun deleteWeatherDataForCity(cityName: String) {}
//    override suspend fun deleteWeatherDetailsForCity(cityName: String) {}
//    override suspend fun deleteDailyDetailsForCity(cityName: String) {}
//    override suspend fun getAllWeatherData(): List<WeatherData> = emptyList()
//}
//class FakeOpenMeteoService : OpenMeteoService { /* Implement hàm getWeather trả về dữ liệu giả */
//    override suspend fun getWeather(latitude: Double, longitude: Double, hourly: String, daily: String, current: String, timezone: String): WeatherResponse {
//        // Trả về một WeatherResponse giả lập
//        return WeatherResponse(
//            hourly = Hourly(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
//            daily = Daily(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
//            current = Current(30.0, 70.0, 32.0, 5.0, 10000.0, 1010.0, 5.0, "2024-04-18T10:00", 75, 25.0)
//        )
//    }
//}
//class FakeService { /* Removed */ }

