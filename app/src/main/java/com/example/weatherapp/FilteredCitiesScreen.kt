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
                    doesWeatherMatchFilter(description, viewModel.weatherStateFilter, timeString)
                } else {
                    true // Giữ lại các thành phố đang tải dữ liệu
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
                                val matchesFilter = doesWeatherMatchFilter(weatherDescription, viewModel.weatherStateFilter, weatherData.timeList.getOrNull(index) ?: "")
                                
                                // Thêm background mờ nếu không khớp bộ lọc
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 
                                                Color.Gray.copy(alpha = 0.1f)
                                            else 
                                                Color.Transparent
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Red.copy(alpha = 0.1f))
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = android.R.drawable.ic_dialog_info),
                                                    contentDescription = "Cảnh báo",
                                                    tint = Color.Red.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Thành phố này có thời tiết là \"$weatherDescription\" không khớp với bộ lọc \"${viewModel.weatherStateFilter}\"",
                                                    fontSize = 12.sp,
                                                    color = Color.Red.copy(alpha = 0.7f),
                                                    fontStyle = FontStyle.Italic
                                                )
                                            }
                                        }
                                
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
                                                    color = Color(0xFF5372dc).copy(alpha = if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 0.6f else 1f)
                                        )
                                        // Hiển thị quốc gia nếu có
                                        if (!city.country.isNullOrBlank()) {
                                            Text(
                                                text = city.country,
                                                fontSize = 14.sp,
                                                        color = Color(0xFF5372dc).copy(alpha = if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 0.5f else 0.8f),
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                        Text(
                                                    text = weatherDescription,
                                            fontSize = 14.sp,
                                                    color = Color(0xFF5372dc).copy(alpha = if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 0.5f else 1f),
                                            style = TextStyle.Default,
                                            onTextLayout = {}
                                        )
                                        
                                        // Hiển thị các thông số chi tiết thay vì tag
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Nhiệt độ: ${currentTemp}°C",
                                            fontSize = 12.sp,
                                                    color = Color(0xFF5372dc).copy(alpha = if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 0.5f else 0.8f)
                                        )
                                        Text(
                                            text = "Độ ẩm: ${currentHumidity}%",
                                            fontSize = 12.sp,
                                                    color = Color(0xFF5372dc).copy(alpha = if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 0.5f else 0.8f)
                                        )
                                        Text(
                                            text = "Tốc độ gió: ${currentWindSpeed} km/h",
                                            fontSize = 12.sp,
                                                    color = Color(0xFF5372dc).copy(alpha = if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 0.5f else 0.8f)
                                        )

                                        // Thêm các thông số khác từ weatherData nếu có
                                        weatherData.currentAqi?.let { aqi ->
                                            Text(
                                                text = "Chỉ số AQI: $aqi (${getAqiDescription(aqi)})",
                                                fontSize = 12.sp,
                                                        color = Color(0xFF5372dc).copy(alpha = if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 0.5f else 0.8f)
                                            )
                                        }

                                        weatherData.uvList.getOrNull(index)?.let { uvIndex ->
                                            Text(
                                                text = "Chỉ số UV: ${uvIndex.toInt()} (${getUvDescription(uvIndex.toInt())})",
                                                fontSize = 12.sp,
                                                        color = Color(0xFF5372dc).copy(alpha = if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") 0.5f else 0.8f)
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        // Hiển thị icon thời tiết
                                                val weatherIcon = getFilteredWeatherIcon(currentWeatherCode)
                                                // Kiểm tra xem thời tiết hiện tại có phù hợp với bộ lọc hay không
                                                val matchesFilter = doesWeatherMatchFilter(weatherDescription, viewModel.weatherStateFilter, weatherData.timeList.getOrNull(index) ?: "")
                                                
                                        Icon(
                                            painter = painterResource(id = weatherIcon),
                                                    contentDescription = weatherDescription,
                                                    tint = if (matchesFilter) Color(0xFF5372dc) else Color(0xFF5372dc).copy(alpha = 0.5f),
                                            modifier = Modifier.size(40.dp)
                                        )
                                                
                                                if (!matchesFilter && viewModel.weatherStateFilter != "Tất cả") {
                                                    Text(
                                                        text = "Không khớp bộ lọc",
                                                        fontSize = 10.sp,
                                                        color = Color.Red.copy(alpha = 0.7f),
                                                        fontStyle = FontStyle.Italic
                                                    )
                                                }
                                            }
                                        }
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
fun getFilteredWeatherIcon(weatherCode: Int): Int {
    return when (weatherCode) {
        0 -> android.R.drawable.ic_menu_compass // Trời quang
        1 -> android.R.drawable.ic_menu_compass // Nắng nhẹ
        2 -> android.R.drawable.ic_menu_crop // Mây rải rác
        3 -> android.R.drawable.ic_menu_crop // Nhiều mây
        45, 48 -> android.R.drawable.ic_menu_myplaces // Sương mù
        in 51..57 -> android.R.drawable.stat_notify_sync // Mưa phùn
        in 61..67 -> android.R.drawable.stat_notify_sync // Mưa
        in 71..77 -> android.R.drawable.presence_offline // Tuyết
        in 80..82 -> android.R.drawable.stat_notify_sync // Mưa rào
        85, 86 -> android.R.drawable.presence_offline // Mưa tuyết
        95, 96, 99 -> android.R.drawable.ic_dialog_alert // Dông
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

// Hàm kiểm tra xem thời tiết có khớp với bộ lọc không
fun doesWeatherMatchFilter(weatherDescription: String, weatherFilter: String, timeString: String = ""): Boolean {
    if (weatherFilter == "Tất cả") return true
    
    // Kiểm tra thời gian trong ngày cho thời tiết "Nắng"
    if (weatherFilter == "Nắng" && timeString.isNotEmpty()) {
        try {
            // Lấy giờ từ chuỗi thời gian ISO (format: "2023-04-20T14:00")
            val hourOfDay = timeString.substringAfterLast("T").substringBefore(":").toIntOrNull() ?: -1
            
            // Nếu là ban đêm (từ 19h đến 6h sáng), không thể có nắng
            if (hourOfDay in 0..6 || hourOfDay in 19..23) {
                return false
            }
        } catch (e: Exception) {
            // Nếu không parse được thời gian, bỏ qua kiểm tra này
        }
    }
    
    // Danh sách từ khóa cho mỗi loại thời tiết
    val keywordMap = mapOf(
        "Nắng" to listOf("nắng", "quang", "nắng nhẹ"),
        "Mây" to listOf("mây", "nhiều mây", "rải rác"),
        "Mưa" to listOf("mưa", "mưa phùn", "mưa rào", "mưa nhỏ", "mưa vừa", "mưa to"),
        "Tuyết" to listOf("tuyết", "hạt tuyết"),
        "Dông" to listOf("dông", "sấm", "sét"),
        "Sương mù" to listOf("sương mù", "mù")
    )
    
    val lowerDesc = weatherDescription.lowercase()
    
    // Kiểm tra các từ khóa tương ứng với loại thời tiết
    val keywords = keywordMap[weatherFilter] ?: return false
    return keywords.any { keyword -> lowerDesc.contains(keyword.lowercase()) }
} 