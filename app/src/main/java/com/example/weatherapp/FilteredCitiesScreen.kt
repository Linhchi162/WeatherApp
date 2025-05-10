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
    val displayCities = viewModel.filteredCities
    
    Log.d("FilteredCitiesScreen", "Opening FilteredCitiesScreen with ${displayCities.size} filtered cities")
    
    // Kiểm tra nếu có thành phố chưa có dữ liệu thời tiết
    LaunchedEffect(displayCities) {
        isLoadingData = displayCities.any { city ->
            viewModel.weatherDataMap[city.name] == null || 
            viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true
        }
        
        // Reset trạng thái timeout khi có danh sách thành phố mới
        loadingTimeoutReached = false
        
        // Tự động tải dữ liệu thời tiết cho các thành phố chưa có dữ liệu
        if (isLoadingData) {
            Log.d("FilteredCitiesScreen", "Some cities missing weather data, loading data...")
            displayCities.forEach { city ->
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
                isLoadingData = displayCities.any { city ->
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                            text = "Không có thành phố nào phù hợp với bộ lọc",
                            fontSize = 16.sp,
                            color = Color(0xFF5372dc),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hãy thử điều chỉnh bộ lọc hoặc thêm thành phố mới",
                            fontSize = 14.sp,
                            color = Color(0xFF5372dc).copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
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
                                            text = getFilteredWeatherDescription(currentWeatherCode),
                                            fontSize = 14.sp,
                                            color = Color(0xFF5372dc),
                                            style = TextStyle.Default,
                                            onTextLayout = {}
                                        )
                                        
                                        // Hiển thị các thông số chi tiết thay vì tag
                                        Spacer(modifier = Modifier.height(8.dp))
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
                                        val weatherIcon = getWeatherIcon(currentWeatherCode)
                                        Icon(
                                            painter = painterResource(id = weatherIcon),
                                            contentDescription = getFilteredWeatherDescription(currentWeatherCode),
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

// Hàm lấy mô tả thời tiết
fun getFilteredWeatherDescription(code: Int): String {
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

// Thêm các hàm tiện ích mới
fun getWeatherIcon(weatherCode: Int): Int {
    return when {
        weatherCode == 0 || weatherCode == 1 -> android.R.drawable.ic_menu_compass // Trời quang/nắng
        weatherCode == 2 || weatherCode == 3 -> android.R.drawable.ic_menu_crop // Mây
        weatherCode in 45..48 -> android.R.drawable.ic_menu_myplaces // Sương mù
        weatherCode in 51..67 -> android.R.drawable.stat_notify_sync // Mưa
        weatherCode in 71..86 -> android.R.drawable.presence_offline // Tuyết
        weatherCode in 95..99 -> android.R.drawable.ic_dialog_alert // Dông
        else -> android.R.drawable.ic_menu_help // Không xác định
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