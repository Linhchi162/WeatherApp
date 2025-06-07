package com.example.weatherapp

import android.util.Log
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.Image

@Composable
fun FilteredCitiesScreen(
    onBackClick: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: WeatherViewModel,
    temperatureUnit: UnitConverter.TemperatureUnit = UnitConverter.TemperatureUnit.CELSIUS,
    windSpeedUnit: UnitConverter.WindSpeedUnit = UnitConverter.WindSpeedUnit.KMH,
    isNightTime: Boolean
) {
    var isLoadingData by remember { mutableStateOf(false) }
    var loadingTimeoutReached by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onDispose {
            Log.d("FilteredCitiesScreen", "Màn hình FilteredCitiesScreen bị hủy - reset kết quả lọc")
            viewModel.resetFilterResults()
        }
    }
    
    val allFilteredCities = viewModel.filteredCities
    
    val displayCities = remember(allFilteredCities, viewModel.weatherDataMap, viewModel.weatherStateFilter) {
        if (viewModel.weatherStateFilter == "Tất cả") {
            allFilteredCities.filter { city ->
                val name = city.name.lowercase()
                !name.contains("quận") && 
                !name.contains("huyện") && 
                !name.contains("phường") && 
                !name.contains("xã") &&
                !name.contains("district") &&
                !name.contains("ward")
            }
        } else {
            allFilteredCities.filter { city ->
                val name = city.name.lowercase()
                if (name.contains("quận") || 
                    name.contains("huyện") || 
                    name.contains("phường") || 
                    name.contains("xã") ||
                    name.contains("district") ||
                    name.contains("ward")) {
                    false
                } else {
                val data = viewModel.weatherDataMap[city.name]
                if (data != null && data.timeList.isNotEmpty()) {
                    val index = viewModel.getCurrentIndex(city.name)
                    val weatherCode = data.weatherCodeList.getOrNull(index) ?: 0
                    val description = WeatherUtils.getWeatherDescription(weatherCode, city.name)
                    val timeString = data.timeList.getOrNull(index) ?: ""
                    
                    val codeMatches = doesWeatherCodeMatchFilter(weatherCode, viewModel.weatherStateFilter)
                    
                    val descMatches = if (!codeMatches)
                        doesWeatherMatchFilter(description, viewModel.weatherStateFilter, timeString)
                    else true
                    
                    val matches = codeMatches || descMatches
                    
                    Log.d("FilteredCitiesScreen", "Thành phố ${city.name} với mô tả '$description' (code=$weatherCode): " +
                          "codeMatches=$codeMatches, descMatches=$descMatches, finalMatch=$matches")
                    
                    matches
                } else {
                    false
                    }
                }
            }
        }
    }
    
    Log.d("FilteredCitiesScreen", "Opening FilteredCitiesScreen with ${displayCities.size} filtered cities")
    
    LaunchedEffect(allFilteredCities) {
        isLoadingData = allFilteredCities.any { city ->
            viewModel.weatherDataMap[city.name] == null || 
            viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true
        }
        
        loadingTimeoutReached = false
        if (isLoadingData) {
            Log.d("FilteredCitiesScreen", "Some cities missing weather data, loading data...")
            allFilteredCities.forEach { city ->
                if (viewModel.weatherDataMap[city.name] == null || 
                    viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true) {
                    Log.d("FilteredCitiesScreen", "Fetching weather for ${city.name}")
                    viewModel.fetchWeatherForCity(city.name, city.latitude, city.longitude)
                }
            }
            
            val startTime = System.currentTimeMillis()
            while (isLoadingData && !loadingTimeoutReached) {
                delay(500)
                
                isLoadingData = allFilteredCities.any { city ->
                    viewModel.weatherDataMap[city.name] == null || 
                    viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true
                }
                
                Log.d("FilteredCitiesScreen", "Loading check: isLoading=$isLoadingData, timeout=${System.currentTimeMillis() - startTime > 10000}ms")
                
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
                        colors = if (isNightTime) listOf(Color(0xFF475985), Color(0xFF5F4064)) else listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
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
                        tint = if (isNightTime) Color.White else Color(0xFF5372dc)
                    )
                }
                Text(
                    text = "Thành phố đã lọc (${displayCities.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isNightTime) Color.White else Color(0xFF5372dc)
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNightTime) Color(0xFF332B41).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Bộ lọc đã áp dụng:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isNightTime) Color.White else Color(0xFF5372dc),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Quốc gia: ${viewModel.selectedFilterCountry}",
                        fontSize = 14.sp,
                        color = if (isNightTime) Color.White else Color(0xFF5372dc)
                    )
                    val tempSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"
                    val tempStart = viewModel.temperatureFilterRange.start.toInt()
                    val tempEnd = viewModel.temperatureFilterRange.endInclusive.toInt()
                    Text(
                        text = "Nhiệt độ: $tempStart$tempSymbol - $tempEnd$tempSymbol",
                        fontSize = 14.sp,
                        color = if (isNightTime) Color.White else Color(0xFF5372dc)
                    )
                    val windLabel = when (windSpeedUnit) {
                        UnitConverter.WindSpeedUnit.KMH -> "km/h"
                        UnitConverter.WindSpeedUnit.MS -> "m/s"
                        UnitConverter.WindSpeedUnit.MPH -> "mph"
                        UnitConverter.WindSpeedUnit.BEAUFORT -> "Bft"
                        UnitConverter.WindSpeedUnit.KNOTS -> "knots"
                        UnitConverter.WindSpeedUnit.FTS -> "ft/s"
                    }
                    val windStart = when (windSpeedUnit) {
                        UnitConverter.WindSpeedUnit.KMH -> viewModel.windSpeedFilterRange.start.toInt()
                        UnitConverter.WindSpeedUnit.MS -> (viewModel.windSpeedFilterRange.start / 3.6f).toInt()
                        UnitConverter.WindSpeedUnit.MPH -> (viewModel.windSpeedFilterRange.start * 0.621371f).toInt()
                        UnitConverter.WindSpeedUnit.BEAUFORT -> viewModel.windSpeedFilterRange.start.toInt()
                        UnitConverter.WindSpeedUnit.KNOTS -> (viewModel.windSpeedFilterRange.start * 0.539957f).toInt()
                        UnitConverter.WindSpeedUnit.FTS -> (viewModel.windSpeedFilterRange.start * 0.911344f).toInt()
                    }
                    val windEnd = when (windSpeedUnit) {
                        UnitConverter.WindSpeedUnit.KMH -> viewModel.windSpeedFilterRange.endInclusive.toInt()
                        UnitConverter.WindSpeedUnit.MS -> (viewModel.windSpeedFilterRange.endInclusive / 3.6f).toInt()
                        UnitConverter.WindSpeedUnit.MPH -> (viewModel.windSpeedFilterRange.endInclusive * 0.621371f).toInt()
                        UnitConverter.WindSpeedUnit.BEAUFORT -> viewModel.windSpeedFilterRange.endInclusive.toInt()
                        UnitConverter.WindSpeedUnit.KNOTS -> (viewModel.windSpeedFilterRange.endInclusive * 0.539957f).toInt()
                        UnitConverter.WindSpeedUnit.FTS -> (viewModel.windSpeedFilterRange.endInclusive * 0.911344f).toInt()
                    }
                    Text(
                        text = "Tốc độ gió: $windStart - $windEnd $windLabel",
                        fontSize = 14.sp,
                        color = if (isNightTime) Color.White else Color(0xFF5372dc)
                    )
                    Text(
                        text = "Độ ẩm: ${viewModel.humidityFilterRange.start.toInt()} - ${viewModel.humidityFilterRange.endInclusive.toInt()}%",
                        fontSize = 14.sp,
                        color = if (isNightTime) Color.White else Color(0xFF5372dc)
                    )
                    Text(
                        text = "Trạng thái thời tiết: ${viewModel.weatherStateFilter}",
                        fontSize = 14.sp,
                        color = if (isNightTime) Color.White else Color(0xFF5372dc)
                    )
                }
            }

            if (displayCities.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                        containerColor = if (isNightTime) Color(0xFF38A169).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f)
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
                                    painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                            contentDescription = "Kết quả lọc",
                                    tint = if (isNightTime) Color(0xFF38A169) else Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                        text = "Tìm thấy ${displayCities.size} thành phố phù hợp",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                        color = if (isNightTime) Color(0xFF38A169) else Color(0xFF4CAF50)
                            )
                            Text(
                                        text = "Kết quả lọc theo các tiêu chí đã chọn",
                                fontSize = 12.sp,
                                        color = if (isNightTime) Color(0xFF38A169) else Color(0xFF4CAF50)
                                    )
                                }
                            }
                    Button(
                        onClick = { onBackClick() },
                        colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isNightTime) Color(0xFF38A169) else Color(0xFF4CAF50),
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
            }

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
                            color = if (isNightTime) Color.White else Color(0xFF5372dc)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Đang tải dữ liệu thời tiết...",
                            color = if (isNightTime) Color.White else Color(0xFF5372dc),
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Vui lòng đợi trong giây lát",
                            color = if (isNightTime) Color.White.copy(alpha = 0.7f) else Color(0xFF5372dc).copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            else if (viewModel.isFiltering) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = if (isNightTime) Color.White else Color(0xFF5372dc))
                }
            }
            
            else if (!isLoadingData && !viewModel.isFiltering && !loadingTimeoutReached && displayCities.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                            contentDescription = "Không tìm thấy thành phố",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Không tìm thấy thành phố nào phù hợp",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hãy thử điều chỉnh bộ lọc",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else if (loadingTimeoutReached && displayCities.all { city ->
                        viewModel.weatherDataMap[city.name] == null || 
                        viewModel.weatherDataMap[city.name]?.timeList?.isEmpty() == true
                    }) {

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
                            tint = if (isNightTime) Color(0xFFFF9800) else Color(0xFFFF9800),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Không thể tải dữ liệu thời tiết",
                            fontSize = 16.sp,
                            color = if (isNightTime) Color.White else Color(0xFF5372dc),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Đã hết thời gian chờ. Vui lòng kiểm tra kết nối mạng và thử lại sau.",
                            fontSize = 14.sp,
                            color = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f),
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
                                containerColor = if (isNightTime) Color(0xFF332B41).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (weatherData != null && weatherData.timeList.isNotEmpty()) {
                                val index = viewModel.getCurrentIndex(city.name)
                                val currentTemp = weatherData.temperatureList.getOrNull(index)?.toInt() ?: 0
                                val currentWeatherCode = weatherData.weatherCodeList.getOrNull(index) ?: 0
                                val currentHumidity = weatherData.humidityList.getOrNull(index)?.toInt() ?: 0
                                val currentWindSpeed = weatherData.windSpeedList.getOrNull(index)?.toInt() ?: 0
                                val weatherDescription = WeatherUtils.getWeatherDescription(currentWeatherCode, city.name)
                                
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
                                            color = if (isNightTime) Color.White else Color(0xFF5372dc)
                                        )

                                        if (!city.country.isNullOrBlank()) {
                                            Text(
                                                text = city.country,
                                                fontSize = 14.sp,
                                                color = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f),
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                        Text(
                                            text = weatherDescription,
                                            fontSize = 14.sp,
                                            color = if (isNightTime) Color.White else Color(0xFF5372dc),
                                            style = TextStyle.Default,
                                            onTextLayout = {}
                                        )
                                        

                                        Spacer(modifier = Modifier.height(8.dp))
                                        

                                        val tempValue = UnitConverter.convertTemperature(currentTemp.toDouble(), temperatureUnit).toInt()
                                        val tempSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"
                                        Text(
                                            text = "Nhiệt độ: $tempValue$tempSymbol",
                                            fontSize = 12.sp,
                                            color = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "Độ ẩm: ${currentHumidity}%",
                                            fontSize = 12.sp,
                                            color = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "Tốc độ gió: ${UnitConverter.convertWindSpeed(currentWindSpeed.toDouble(), windSpeedUnit)}",
                                            fontSize = 12.sp,
                                            color = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
                                        )
                                        

                                        weatherData.currentAqi?.let { aqi ->
                                            Text(
                                                text = "Chỉ số AQI: $aqi (${getAqiDescription(aqi)})",
                                                fontSize = 12.sp,
                                                color = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
                                            )
                                        }

                                        weatherData.uvList.getOrNull(index)?.let { uvIndex ->
                                            Text(
                                                text = "Chỉ số UV: ${uvIndex.toInt()} (${getUvDescription(uvIndex.toInt())})",
                                                fontSize = 12.sp,
                                                color = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {

                                        val weatherIcon = WeatherUtils.getWeatherIcon(currentWeatherCode, isNightTime)
                                        
                                        Image(
                                            painter = painterResource(id = weatherIcon),
                                            contentDescription = weatherDescription,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            } else {

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
                                            color = if (isNightTime) Color.White else Color(0xFF5372dc)
                                        )

                                        if (!city.country.isNullOrBlank()) {
                                            Text(
                                                text = city.country,
                                                fontSize = 14.sp,
                                                color = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f),
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                        Text(
                                            text = "Đang tải dữ liệu...",
                                            fontSize = 14.sp,
                                            color = if (isNightTime) Color.White.copy(alpha = 0.7f) else Color(0xFF5372dc).copy(alpha = 0.7f)
                                        )
                                    }
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = if (isNightTime) Color.White else Color(0xFF5372dc),
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


fun getFilteredWeatherDescription(weatherCode: Int): String {

    Log.d("FilteredCitiesScreen", "Chuyển đổi mã thời tiết: $weatherCode")
    
    return when (weatherCode) {
        0 -> "Bầu trời quang đãng"
        1 -> "Chủ yếu quang đãng"
        2 -> "Có mây rải rác"
        3 -> "Nhiều mây"
        45 -> "Sương mù"
        48 -> "Sương mù giá"
        51 -> "Mưa phùn nhẹ"
        53 -> "Mưa phùn vừa"
        55 -> "Mưa phùn to"
        56 -> "Mưa phùn giá nhẹ"
        57 -> "Mưa phùn giá to"
        61 -> "Mưa nhỏ"
        63 -> "Mưa vừa"
        65 -> "Mưa to"
        66 -> "Mưa giá nhẹ"
        67 -> "Mưa giá to"
        71 -> "Tuyết rơi nhẹ"
        73 -> "Tuyết rơi vừa"
        75 -> "Tuyết rơi to"
        77 -> "Bông tuyết"
        80 -> "Mưa rào nhẹ"
        81 -> "Mưa rào vừa"
        82 -> "Mưa rào mạnh"
        85 -> "Mưa tuyết nhẹ"
        86 -> "Mưa tuyết to"
        95 -> "Dông bão"
        96 -> "Dông bão với mưa đá nhẹ"
        99 -> "Dông bão với mưa đá to"
        else -> "Không xác định"
    }
}


fun getFilteredWeatherIcon(weatherCode: Int): Int {
    return when (weatherCode) {
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


fun doesWeatherMatchFilter(weatherDescription: String, weatherFilter: String, timeString: String = ""): Boolean {
    if (weatherFilter == "Tất cả") return true
    

    Log.d("FilteredCitiesScreen", "Kiểm tra lọc thời tiết: '$weatherDescription' với bộ lọc '$weatherFilter'")
    

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
    

    if (weatherFilter == "Nắng" && timeString.isNotEmpty()) {
        try {

            val hourOfDay = timeString.substringAfterLast("T").substringBefore(":").toIntOrNull() ?: -1
            

            if (hourOfDay in 0..6 || hourOfDay in 19..23) {
                Log.d("FilteredCitiesScreen", "Thời điểm $hourOfDay giờ không phù hợp với thời tiết nắng")
                return false
            }
        } catch (e: Exception) {

            Log.e("FilteredCitiesScreen", "Lỗi khi phân tích thời gian '$timeString': ${e.message}")
        }
    }
    

    val keywordMap = mapOf(
        "Nắng" to listOf("nắng", "quang đãng", "quang", "nắng nhẹ", "clear", "sunny", "fair"),
        "Nhiều mây" to listOf("mây", "nhiều mây", "rải rác", "cloudy", "clouds", "overcast", "có mây", "chủ yếu quang đãng"),
        "Mưa" to listOf("mưa", "mưa phùn", "mưa rào", "mưa nhỏ", "mưa vừa", "mưa to", "rain", "rainy", "showers", "drizzle"),
        "Tuyết" to listOf("tuyết", "hạt tuyết", "snow", "snowy", "snowfall"),
        "Dông" to listOf("dông", "sấm", "sét", "thunderstorm", "thunder", "lightning"),
        "Sương mù" to listOf("sương mù", "mù", "fog", "foggy", "mist", "haze")
    )
    
    val lowerDesc = weatherDescription.lowercase()
    

    val keywords = keywordMap[weatherFilter] ?: return false
    val result = keywords.any { keyword -> lowerDesc.contains(keyword.lowercase()) }
    
    Log.d("FilteredCitiesScreen", "Kết quả lọc thời tiết '$weatherDescription' với '$weatherFilter': $result (lowerDesc='$lowerDesc')")
    return result
}


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

@Preview(showBackground = true)
@Composable
fun FilteredCitiesScreenPreview() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF87CEEB), Color(0xFF98FB98))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = Color(0xFF5372dc)
                    )
                }
                Text(
                    text = "Kết quả tìm kiếm",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5372dc),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.9f)
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
                    Column {
                        Text(
                            text = "Hà Nội",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5372dc)
                        )
                        Text(
                            text = "Việt Nam",
                            fontSize = 14.sp,
                            color = Color(0xFF5372dc).copy(alpha = 0.8f),
                            fontStyle = FontStyle.Italic
                        )
                        Text(
                            text = "25°C • Nắng nhẹ",
                            fontSize = 14.sp,
                            color = Color(0xFF5372dc).copy(alpha = 0.7f)
                        )
                    }
                    Image(
                        painter = painterResource(id = R.drawable.sunny),
                        contentDescription = "Weather icon",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

