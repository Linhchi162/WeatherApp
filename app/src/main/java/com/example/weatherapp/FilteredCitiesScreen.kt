package com.example.weatherapp

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

@Composable
fun FilteredCitiesScreen(
    onBackClick: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: WeatherViewModel
) {
    var isLoadingData by remember { mutableStateOf(false) }
    var loadingTimeoutReached by remember { mutableStateOf(false) }
    
    // Danh sách thành phố để hiển thị (sử dụng filteredCities thay vì citiesList)
    val allFilteredCities = viewModel.filteredCities
    
    // Lọc các thành phố theo trạng thái thời tiết
    val displayCities = remember(allFilteredCities, viewModel.weatherDataMap, viewModel.weatherStateFilter) {
        if (viewModel.weatherStateFilter == "Tất cả") {
            allFilteredCities
        } else {
            allFilteredCities.filter { city ->
                val data = viewModel.weatherDataMap[city.name]
                if (data != null && data.timeList.isNotEmpty()) {
                    val index = viewModel.getCurrentIndex(city.name)
                    val weatherCode = data.weatherCodeList.getOrNull(index) ?: 0
                    val description = getFilteredWeatherDescription(weatherCode)
                    val timeString = data.timeList.getOrNull(index) ?: ""
                    
                    // Kiểm tra trạng thái thời tiết theo mã trước
                    val codeMatches = doesWeatherCodeMatchFilter(weatherCode, viewModel.weatherStateFilter)
                    
                    // Nếu không khớp theo mã, thử khớp theo mô tả
                    val descMatches = if (!codeMatches) 
                        doesWeatherMatchFilter(description, viewModel.weatherStateFilter, timeString)
                    else true
                    
                    val matches = codeMatches || descMatches
                    
                    // Thêm log chi tiết để debug
                    Log.d("FilteredCitiesScreen", "Thành phố ${city.name} với mô tả '$description' (code=$weatherCode): " +
                          "codeMatches=$codeMatches, descMatches=$descMatches, finalMatch=$matches")
                    
                    // Chỉ giữ lại những thành phố khớp với bộ lọc thời tiết
                    matches
                } else {
                    false // Bỏ qua các thành phố đang tải dữ liệu
                }
            }
        }
    }
    
    Log.d("FilteredCitiesScreen", "Opening FilteredCitiesScreen with ${displayCities.size} filtered cities")
    
    // Kiểm tra nếu có thành phố chưa có dữ liệu thời tiết
    LaunchedEffect(allFilteredCities) {
        isLoadingData = allFilteredCities.any { city ->
            viewModel.weatherDataMap[city.name] == null || 
            viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true
        }
        
        // Reset trạng thái timeout khi có danh sách thành phố mới
        loadingTimeoutReached = false
        
        // Tự động tải dữ liệu thời tiết cho các thành phố chưa có dữ liệu
        if (isLoadingData) {
            Log.d("FilteredCitiesScreen", "Some cities missing weather data, loading data...")
            allFilteredCities.forEach { city ->
                if (viewModel.weatherDataMap[city.name] == null || 
                    viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true) {
                    Log.d("FilteredCitiesScreen", "Fetching weather for ${city.name}")
                    viewModel.fetchWeatherForCity(city.name, city.latitude, city.longitude)
                }
            }
            
            // Thiết lập timeout để dừng loading sau 10 giây
            val startTime = System.currentTimeMillis()
            while (isLoadingData && !loadingTimeoutReached) {
                // Kiểm tra lại trạng thái loading sau mỗi 500ms
                delay(500)
                
                // Cập nhật trạng thái loading
                isLoadingData = allFilteredCities.any { city ->
                    viewModel.weatherDataMap[city.name] == null || 
                    viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true
                }
                
                // Log trạng thái tải dữ liệu
                Log.d("FilteredCitiesScreen", "Loading check: isLoading=$isLoadingData, timeout=${System.currentTimeMillis() - startTime > 10000}ms")
                
                // Kiểm tra timeout
                if (System.currentTimeMillis() - startTime > 10000) {
                    Log.d("FilteredCitiesScreen", "Loading timeout reached after 10 seconds")
                    loadingTimeoutReached = true
                    isLoadingData = false
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.8f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            // Tiêu đề và nút quay lại
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = "Back",
                        tint = Color(0xFF5372dc)
                    )
                }
                Text(
                    text = "Thành phố đã lọc (${displayCities.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5372dc)
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Hiển thị thông tin bộ lọc đã áp dụng
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Bộ lọc đã áp dụng:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5372dc),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    Text(
                        text = "Quốc gia: ${viewModel.selectedFilterCountry}",
                        fontSize = 14.sp,
                        color = Color(0xFF5372dc)
                    )
                    Text(
                        text = "Nhiệt độ: ${viewModel.temperatureFilterRange.start.toInt()}°C - ${viewModel.temperatureFilterRange.endInclusive.toInt()}°C",
                        fontSize = 14.sp,
                        color = Color(0xFF5372dc)
                    )
                    Text(
                        text = "Tốc độ gió: ${viewModel.windSpeedFilterRange.start.toInt()} - ${viewModel.windSpeedFilterRange.endInclusive.toInt()} km/h",
                        fontSize = 14.sp,
                        color = Color(0xFF5372dc)
                    )
                    Text(
                        text = "Độ ẩm: ${viewModel.humidityFilterRange.start.toInt()} - ${viewModel.humidityFilterRange.endInclusive.toInt()}%",
                        fontSize = 14.sp,
                        color = Color(0xFF5372dc)
                    )
                    Text(
                        text = "Trạng thái thời tiết: ${viewModel.weatherStateFilter}",
                        fontSize = 14.sp,
                        color = Color(0xFF5372dc)
                    )
                }
            }

            // Thêm card tổng kết kết quả lọc
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (displayCities.isNotEmpty()) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFE57373).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (displayCities.isNotEmpty()) android.R.drawable.ic_menu_info_details else android.R.drawable.ic_dialog_alert
                            ),
                            contentDescription = "Kết quả lọc",
                            tint = if (displayCities.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFE57373)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (displayCities.isNotEmpty()) "Tìm thấy ${displayCities.size} thành phố phù hợp" else "Không tìm thấy thành phố nào phù hợp",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (displayCities.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFE57373)
                            )
                            Text(
                                text = if (displayCities.isNotEmpty()) "Kết quả lọc theo các tiêu chí đã chọn" else "Hãy thử điều chỉnh bộ lọc",
                                fontSize = 12.sp,
                                color = if (displayCities.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFE57373)
                            )
                        }
                    }
                    
                    // Nút áp dụng bộ lọc mới
                    Button(
                        onClick = { onBackClick() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (displayCities.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFE57373),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(36.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "Lọc lại",
                            fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Hiển thị loading nếu đang tải dữ liệu
            if (isLoadingData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF5372dc)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Đang tải dữ liệu thời tiết...",
                            color = Color(0xFF5372dc),
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Vui lòng đợi trong giây lát",
                            color = Color(0xFF5372dc).copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Kiểm tra nếu đang lọc
            if (viewModel.isFiltering) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF5372dc))
                }
            }
            
            // Danh sách thành phố
            if (displayCities.isEmpty()) {
                // Hiển thị thông báo khi không có kết quả
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                            contentDescription = "Info",
                            tint = Color(0xFF5372dc),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (viewModel.weatherStateFilter != "Tất cả") 
                                "Không có thành phố nào có thời tiết \"${viewModel.weatherStateFilter}\""
                            else
                                "Không có thành phố nào phù hợp với bộ lọc",
                            fontSize = 16.sp,
                            color = Color(0xFF5372dc),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (viewModel.weatherStateFilter != "Tất cả")
                                "Hãy tắt chế độ \"Chỉ hiện thành phố có thời tiết ${viewModel.weatherStateFilter}\" hoặc thử điều chỉnh bộ lọc"
                            else
                                "Hãy thử điều chỉnh bộ lọc hoặc thêm thành phố mới",
                            fontSize = 14.sp,
                            color = Color(0xFF5372dc).copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        
                        if (viewModel.weatherStateFilter != "Tất cả") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.updateFilters(weatherState = "Tất cả") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5372dc)
                                )
                            ) {
                                Text("Xem tất cả trạng thái thời tiết")
                            }
                        }
                    }
                }
            } else if (loadingTimeoutReached && displayCities.all { city ->
                        viewModel.weatherDataMap[city.name] == null || 
                        viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true
                    }) {
                // Hiển thị thông báo khi hết thời gian tải dữ liệu
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                            contentDescription = "Warning",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Không thể tải dữ liệu thời tiết",
                            fontSize = 16.sp,
                            color = Color(0xFF5372dc),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Đã hết thời gian chờ. Vui lòng kiểm tra kết nối mạng và thử lại sau.",
                            fontSize = 14.sp,
                            color = Color(0xFF5372dc).copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayCities) { city ->
                        val weatherData = viewModel.weatherDataMap[city.name]
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (weatherData != null && weatherData.timeList.isNotEmpty()) {
                                val index = viewModel.getCurrentIndex(city.name)
                                val currentTemp = weatherData.temperatureList.getOrNull(index)?.toInt() ?: 0
                                val currentWeatherCode = weatherData.weatherCodeList.getOrNull(index) ?: 0
                                val currentHumidity = weatherData.humidityList.getOrNull(index)?.toInt() ?: 0
                                val currentWindSpeed = weatherData.windSpeedList.getOrNull(index)?.toInt() ?: 0
                                val weatherDescription = getFilteredWeatherDescription(currentWeatherCode)
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = city.name,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5372dc)
                                        )
                                        // Hiển thị quốc gia nếu có
                                        if (!city.country.isNullOrBlank()) {
                                            Text(
                                                text = city.country,
                                                fontSize = 14.sp,
                                                color = Color(0xFF5372dc).copy(alpha = 0.8f),
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                        Text(
                                            text = weatherDescription,
                                            fontSize = 14.sp,
                                            color = Color(0xFF5372dc),
                                            style = TextStyle.Default,
                                            onTextLayout = {}
                                        )
                                        
                                        // Hiển thị các thông số chi tiết
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Thêm display của nhiệt độ, độ ẩm và tốc độ gió
                                        Text(
                                            text = "Nhiệt độ: ${currentTemp}°C",
                                            fontSize = 12.sp,
                                            color = Color(0xFF5372dc).copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "Độ ẩm: ${currentHumidity}%",
                                            fontSize = 12.sp,
                                            color = Color(0xFF5372dc).copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "Tốc độ gió: ${currentWindSpeed} km/h",
                                            fontSize = 12.sp,
                                            color = Color(0xFF5372dc).copy(alpha = 0.8f)
                                        )
                                        
                                        // Thêm các thông số khác từ weatherData nếu có
                                        weatherData.currentAqi?.let { aqi ->
                                            Text(
                                                text = "Chỉ số AQI: $aqi (${getAqiDescription(aqi)})",
                                                fontSize = 12.sp,
                                                color = Color(0xFF5372dc).copy(alpha = 0.8f)
                                            )
                                        }

                                        weatherData.uvList.getOrNull(index)?.let { uvIndex ->
                                            Text(
                                                text = "Chỉ số UV: ${uvIndex.toInt()} (${getUvDescription(uvIndex.toInt())})",
                                                fontSize = 12.sp,
                                                color = Color(0xFF5372dc).copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        // Hiển thị icon thời tiết
                                        val weatherIcon = getFilteredWeatherIcon(currentWeatherCode)
                                        
                                        Icon(
                                            painter = painterResource(id = weatherIcon),
                                            contentDescription = weatherDescription,
                                            tint = Color(0xFF5372dc),
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            } else {
                                // Hiển thị thông tin đang tải nếu chưa có dữ liệu thời tiết
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = city.name,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5372dc)
                                        )
                                        // Hiển thị quốc gia nếu có
                                        if (!city.country.isNullOrBlank()) {
                                            Text(
                                                text = city.country,
                                                fontSize = 14.sp,
                                                color = Color(0xFF5372dc).copy(alpha = 0.8f),
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                        Text(
                                            text = "Đang tải dữ liệu...",
                                            fontSize = 14.sp,
                                            color = Color(0xFF5372dc).copy(alpha = 0.7f)
                                        )
                                    }
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF5372dc),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Hàm helper để lấy mô tả thời tiết dựa trên mã thời tiết
fun getFilteredWeatherDescription(weatherCode: Int): String {
    // Log mã thời tiết để debug
    Log.d("FilteredCitiesScreen", "Chuyển đổi mã thời tiết: $weatherCode")
    
    return when (weatherCode) {
        0 -> "Bầu trời quang đãng" // Clear sky
        1 -> "Chủ yếu quang đãng" // Mainly clear
        2 -> "Có mây rải rác" // Partly cloudy
        3 -> "Nhiều mây" // Overcast
        45 -> "Sương mù" // Fog
        48 -> "Sương mù giá" // Depositing rime fog
        51 -> "Mưa phùn nhẹ" // Light drizzle
        53 -> "Mưa phùn vừa" // Moderate drizzle
        55 -> "Mưa phùn to" // Dense drizzle
        56 -> "Mưa phùn giá nhẹ" // Light freezing drizzle
        57 -> "Mưa phùn giá to" // Dense freezing drizzle
        61 -> "Mưa nhỏ" // Slight rain
        63 -> "Mưa vừa" // Moderate rain
        65 -> "Mưa to" // Heavy rain
        66 -> "Mưa giá nhẹ" // Light freezing rain
        67 -> "Mưa giá to" // Heavy freezing rain
        71 -> "Tuyết rơi nhẹ" // Slight snow fall
        73 -> "Tuyết rơi vừa" // Moderate snow fall
        75 -> "Tuyết rơi to" // Heavy snow fall
        77 -> "Bông tuyết" // Snow grains
        80 -> "Mưa rào nhẹ" // Slight rain showers
        81 -> "Mưa rào vừa" // Moderate rain showers
        82 -> "Mưa rào mạnh" // Violent rain showers
        85 -> "Mưa tuyết nhẹ" // Slight snow showers
        86 -> "Mưa tuyết to" // Heavy snow showers
        95 -> "Dông bão" // Thunderstorm
        96 -> "Dông bão với mưa đá nhẹ" // Thunderstorm with slight hail
        99 -> "Dông bão với mưa đá to" // Thunderstorm with heavy hail
        else -> "Không xác định" // Unknown
    }
}

// Thêm các hàm tiện ích mới
fun getFilteredWeatherIcon(weatherCode: Int): Int {
    return when (weatherCode) {
        0 -> R.drawable.sunny // Trời quang
        1 -> R.drawable.cloudy_with_sun // Nắng nhẹ
        2 -> R.drawable.cloudy_with_sun // Mây rải rác
        3 -> R.drawable.cloudy // Nhiều mây
        45, 48 -> R.drawable.cloudy // Sương mù
        51, 53, 55 -> R.drawable.rainingg // Mưa phùn
        56, 57 -> R.drawable.rainingg // Mưa phùn đông đá (hiếm)
        61, 63, 65 -> R.drawable.rainingg // Mưa (nhẹ, vừa, nặng)
        66, 67 -> R.drawable.rainingg // Mưa đông đá (hiếm)
        71, 73, 75 -> R.drawable.snow // Tuyết (nhẹ, vừa, nặng)
        77 -> R.drawable.snow // Hạt tuyết (hiếm)
        80, 81, 82 -> R.drawable.rainingg // Mưa rào (nhẹ, vừa, dữ dội)
        85, 86 -> R.drawable.snow // Mưa tuyết (nhẹ, nặng)
        95 -> R.drawable.thunderstorm // Dông (nhẹ/vừa)
        96, 99 -> R.drawable.thunderstorm // Dông có mưa đá (nhẹ/nặng)
        else -> R.drawable.cloudy_with_sun // Mặc định
    }
}

fun getAqiDescription(aqi: Int): String {
    return when {
        aqi <= 50 -> "Tốt"
        aqi <= 100 -> "Trung bình"
        aqi <= 150 -> "Không lành mạnh cho nhóm nhạy cảm"
        aqi <= 200 -> "Không lành mạnh"
        aqi <= 300 -> "Rất không lành mạnh"
        else -> "Nguy hiểm"
    }
}

fun getUvDescription(uv: Int): String {
    return when {
        uv <= 2 -> "Thấp"
        uv <= 5 -> "Trung bình"
        uv <= 7 -> "Cao"
        uv <= 10 -> "Rất cao"
        else -> "Cực đoan"
    }
}

// Hàm kiểm tra xem thời tiết có khớp với bộ lọc không
fun doesWeatherMatchFilter(weatherDescription: String, weatherFilter: String, timeString: String = ""): Boolean {
    if (weatherFilter == "Tất cả") return true
    
    // Log chi tiết cho debug
    Log.d("FilteredCitiesScreen", "Kiểm tra lọc thời tiết: '$weatherDescription' với bộ lọc '$weatherFilter'")
    
    // Trước tiên kiểm tra khớp trực tiếp với từng trạng thái
    val directMatch = when (weatherFilter) {
        "Nắng" -> weatherDescription == "Bầu trời quang đãng" || 
                  weatherDescription == "Chủ yếu quang đãng"
        "Nhiều mây" -> weatherDescription == "Có mây rải rác" || 
                      weatherDescription == "Nhiều mây"
        "Mưa" -> weatherDescription.contains("Mưa")
        "Sương mù" -> weatherDescription.contains("Sương mù")
        "Tuyết" -> weatherDescription.contains("Tuyết")
        else -> false
    }
    
    if (directMatch) {
        Log.d("FilteredCitiesScreen", "Khớp trực tiếp: $weatherDescription -> $weatherFilter")
        return true
    }
    
    // Kiểm tra thời gian trong ngày cho thời tiết "Nắng"
    if (weatherFilter == "Nắng" && timeString.isNotEmpty()) {
        try {
            // Lấy giờ từ chuỗi thời gian ISO (format: "2023-04-20T14:00")
            val hourOfDay = timeString.substringAfterLast("T").substringBefore(":").toIntOrNull() ?: -1
            
            // Nếu là ban đêm (từ 19h đến 6h sáng), không thể có nắng
            if (hourOfDay in 0..6 || hourOfDay in 19..23) {
                Log.d("FilteredCitiesScreen", "Thời điểm $hourOfDay giờ không phù hợp với thời tiết nắng")
                return false
            }
        } catch (e: Exception) {
            // Nếu không parse được thời gian, bỏ qua kiểm tra này
            Log.e("FilteredCitiesScreen", "Lỗi khi phân tích thời gian '$timeString': ${e.message}")
        }
    }
    
    // Danh sách từ khóa cho mỗi loại thời tiết
    val keywordMap = mapOf(
        "Nắng" to listOf("nắng", "quang đãng", "quang", "nắng nhẹ", "clear", "sunny", "fair"),
        "Nhiều mây" to listOf("mây", "nhiều mây", "rải rác", "cloudy", "clouds", "overcast", "có mây", "chủ yếu quang đãng"),
        "Mưa" to listOf("mưa", "mưa phùn", "mưa rào", "mưa nhỏ", "mưa vừa", "mưa to", "rain", "rainy", "showers", "drizzle"),
        "Tuyết" to listOf("tuyết", "hạt tuyết", "snow", "snowy", "snowfall"),
        "Dông" to listOf("dông", "sấm", "sét", "thunderstorm", "thunder", "lightning"),
        "Sương mù" to listOf("sương mù", "mù", "fog", "foggy", "mist", "haze")
    )
    
    val lowerDesc = weatherDescription.lowercase()
    
    // Kiểm tra các từ khóa tương ứng với loại thời tiết
    val keywords = keywordMap[weatherFilter] ?: return false
    val result = keywords.any { keyword -> lowerDesc.contains(keyword.lowercase()) }
    
    Log.d("FilteredCitiesScreen", "Kết quả lọc thời tiết '$weatherDescription' với '$weatherFilter': $result (lowerDesc='$lowerDesc')")
    return result
}

// Hàm kiểm tra trạng thái thời tiết dựa trên mã
fun doesWeatherCodeMatchFilter(weatherCode: Int, weatherFilter: String): Boolean {
    if (weatherFilter == "Tất cả") return true
    
    val result = when (weatherFilter) {
        "Nắng" -> weatherCode == 0 || weatherCode == 1
        "Nhiều mây" -> weatherCode == 2 || weatherCode == 3
        "Mưa" -> weatherCode in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
        "Sương mù" -> weatherCode == 45 || weatherCode == 48
        "Tuyết" -> weatherCode in listOf(71, 73, 75, 77, 85, 86)
        else -> false
    }
    
    Log.d("FilteredCitiesScreen", "Kiểm tra lọc mã thời tiết: $weatherCode với bộ lọc '$weatherFilter': $result")
    return result
} 