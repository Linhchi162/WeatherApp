package com.example.weatherapp

import com.example.weatherapp.R

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items // Import items for LazyColumn/LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.geometry.Size // Import Size for Canvas

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.localbroadcastmanager.content.LocalBroadcastManager

import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.GroundOverlay
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.GoogleMap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.URL

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

import com.example.weatherapp.City
import com.example.weatherapp.WeatherDataState
import com.example.weatherapp.WeatherViewModel
import com.example.weatherapp.UnitConverter

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.rememberDismissState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import java.util.Collections
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt // Added for rounding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.material.icons.filled.ArrowDropDown

// ========== Utility Functions ==========
// Hàm kiểm tra mạng
@Composable
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

// Format timestamp
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

// Weather icon function
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
            0 -> if (isNightTime) R.drawable.clear_night else R.drawable.sunny // Nắng rõ / Đêm quang
            1 -> if (isNightTime) R.drawable.cloudy_with_moon else R.drawable.cloudy_with_sun // Nắng nhẹ / Đêm ít mây
            2 -> if (isNightTime) R.drawable.cloudy_with_moon else R.drawable.cloudy_with_sun // Mây rải rác
            3 -> if (isNightTime) R.drawable.cloudy_night else R.drawable.cloudy // Nhiều mây, u ám
            45, 48 -> if (isNightTime) R.drawable.cloudy_night else R.drawable.cloudy // Sương mù
            51, 53, 55 -> if (isNightTime) R.drawable.night_rain else R.drawable.rainingg // Mưa phùn
            56, 57 -> if (isNightTime) R.drawable.night_rain else R.drawable.rainingg // Mưa phùn đông đá (hiếm)
            61, 63, 65 -> if (isNightTime) R.drawable.night_rain else R.drawable.rainingg // Mưa (nhẹ, vừa, nặng)
            66, 67 -> if (isNightTime) R.drawable.night_rain else R.drawable.rainingg // Mưa đông đá
            71, 73, 75 -> if (isNightTime) R.drawable.snow_night else R.drawable.snow // Tuyết
            77 -> if (isNightTime) R.drawable.snow_night else R.drawable.snow // Hạt tuyết
            80, 81, 82 -> if (isNightTime) R.drawable.night_rain else R.drawable.rainingg // Mưa rào
            85, 86 -> if (isNightTime) R.drawable.snow_night else R.drawable.snow // Mưa tuyết
            95 -> if (isNightTime) R.drawable.night_thunderraining else R.drawable.thunderstorm // Dông
            96, 99 -> if (isNightTime) R.drawable.night_thunderraining else R.drawable.thunderstorm // Dông có mưa đá
            else -> if (isNightTime) R.drawable.cloudy_with_moon else R.drawable.cloudy_with_sun
        }
    }
}

// Weather description
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

// Calculate percentage function for AQI
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

// Get AQI info
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

// Get AQI recommendation
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

// Calculate sun position for animation
fun calculateSunPosition(currentHour: Int, currentMinute: Int, sunriseHour: Int, sunriseMinute: Int, sunsetHour: Int, sunsetMinute: Int): Float {
    val currentTimeInMinutes = currentHour * 60 + currentMinute
    val sunriseTimeInMinutes = sunriseHour * 60 + sunriseMinute
    val sunsetTimeInMinutes = sunsetHour * 60 + sunsetMinute
    
    return when {
        currentTimeInMinutes < sunriseTimeInMinutes -> 0f // Before sunrise
        currentTimeInMinutes > sunsetTimeInMinutes -> 1f // After sunset
        else -> {
            // During the day
            val dayDuration = sunsetTimeInMinutes - sunriseTimeInMinutes
            val timeSinceSunrise = currentTimeInMinutes - sunriseTimeInMinutes
            (timeSinceSunrise.toFloat() / dayDuration.toFloat()).coerceIn(0f, 1f)
        }
    }
}

// Get sunrise and sunset times (simplified calculation or use default times)
fun getSunTimes(latitude: Double): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    // Simplified calculation based on latitude - in real app you'd use proper sun calculation
    val baseOffset = (latitude / 90.0 * 2).coerceIn(-2.0, 2.0) // Max 2 hours offset
    
    val sunriseHour = (6 - baseOffset.toInt()).coerceIn(4, 8)
    val sunsetHour = (18 + baseOffset.toInt()).coerceIn(16, 20)
    
    return Pair(Pair(sunriseHour, 0), Pair(sunsetHour, 0))
}

// Thêm hàm formatTime
fun formatTime(isoTime: String): String {
    return try {
        val dateTime = LocalDateTime.parse(isoTime, DateTimeFormatter.ISO_DATE_TIME)
        return String.format("%02d:%02d", dateTime.hour, dateTime.minute)
    } catch (e: Exception) {
        isoTime // Trả về chuỗi gốc nếu không parse được
    }
}

// Helper để loại bỏ tiền tố 'Quận', 'Huyện', 'Thị xã', 'Thành phố', 'Phường', 'Xã', 'Thị trấn' khỏi tên vị trí
fun cleanLocationName(name: String): String {
    val prefixes = listOf("Quận ", "Huyện ", "Thị xã ", "Thành phố ", "Phường ", "Xã ", "Thị trấn ")
    var result = name
    for (prefix in prefixes) {
        if (result.startsWith(prefix, ignoreCase = true)) {
            result = result.removePrefix(prefix)
            break
        }
    }
    return result.trim()
}

// ========== Reusable UI Components ==========
// Air Quality Bar composable
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

// Offline Screen composable
@Composable
fun OfflineScreen(lastUpdateTime: Long?, isNightTime: Boolean) {
    val iconColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val textColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                contentDescription = "Offline",
                tint = iconColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Không có kết nối mạng. Vui lòng kiểm tra lại.",
                fontSize = 16.sp,
                color = textColor,
                textAlign = TextAlign.Center
            )
            lastUpdateTime?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dữ liệu cập nhật lần cuối: ${formatTimestamp(it)}",
                    fontSize = 14.sp,
                    color = textColor
                )
            }
        }
    }
}

// Loading or Error Screen composable
@Composable
fun LoadingOrErrorScreen(errorMessage: String?, lastUpdateTime: Long?, isNightTime: Boolean) {
    val iconColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val textColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val errorColor = if (isNightTime) Color(0xFFFF6B6B) else Color.Red.copy(alpha = 0.8f)
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (errorMessage != null) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                    contentDescription = "Error",
                    tint = errorColor,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    fontSize = 16.sp, 
                    color = errorColor, 
                    textAlign = TextAlign.Center
                )
            } else {
                CircularProgressIndicator(color = iconColor)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Đang tải dữ liệu thời tiết...",
                    fontSize = 16.sp, 
                    color = textColor
                )
            }
            lastUpdateTime?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dữ liệu cập nhật lần cuối: ${formatTimestamp(it)}",
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// InfoItem composable
@Composable
fun InfoItem(iconId: Int, value: String, label: String, color: Color, isNightTime: Boolean) {
    val textColor = if (isNightTime) Color.White else color
    val labelColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else color.copy(alpha = 0.8f)
    
    // Màu icon - ban đêm sẽ đậm hơn và bão hòa hơn
    val iconColor = if (isNightTime) {
        when (color) {
            Color(0xFF5372DC) -> Color(0xFF889AE7) // Xanh dương đậm hơn cho mưa
            Color(0xFFD05CA2) -> Color(0xFFDC97AE) // Hồng đậm hơn cho độ ẩm
            Color(0xFF3F9CBE) -> Color(0xFF77D3E1) // Xanh da trời đậm hơn cho gió
            else -> color
        }
    } else {
        color
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconColor)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = labelColor, fontSize = 11.sp)
    }
}

// ForecastItem composable
@Composable
fun ForecastItem(iconId: Int, temp: String, time: String, highlight: Boolean = false, isNightTime: Boolean) {
    val textColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val highlightBackground = if (isNightTime) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.6f)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color = if (highlight) highlightBackground else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .width(55.dp)
    ) {
        Text(text = time, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = temp, fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Bold)
    }
}

// ========== Content Section Composables ==========
// Current Weather Section
@Composable
fun CurrentWeatherSection(city: City, weatherData: WeatherDataState, viewModel: WeatherViewModel, temperatureUnit: UnitConverter.TemperatureUnit, isNightTime: Boolean) {
    val index = remember(city.name, weatherData.timeList) { viewModel.getCurrentIndex(city.name) }
    
    // Màu chữ theo thời gian
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
    
    // Chuyển đổi nhiệt độ theo đơn vị được chọn
    val currentTempRaw = weatherData.temperatureList.getOrNull(index) ?: 0.0
    val highRaw = weatherData.dailyTempMaxList.firstOrNull() ?: weatherData.temperatureList.maxOrNull() ?: 0.0
    val lowRaw = weatherData.dailyTempMinList.firstOrNull() ?: weatherData.temperatureList.minOrNull() ?: 0.0
    
    val currentTemp = UnitConverter.convertTemperature(currentTempRaw, temperatureUnit).toInt()
    val high = UnitConverter.convertTemperature(highRaw, temperatureUnit).toInt()
    val low = UnitConverter.convertTemperature(lowRaw, temperatureUnit).toInt()
    
    val tempSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"
    
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
    val precipitationText = if (weatherCode in 51..67 || weatherCode in 80..82) "$adjustedRainPercentage%" else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "$currentTemp$tempSymbol", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
            Text(text = weatherText, fontSize = 14.sp, color = secondaryTextColor)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Cao: $high$tempSymbol   Thấp: $low$tempSymbol", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = secondaryTextColor)
        }
        Image(
            painter = painterResource(id = weatherIcon),
            contentDescription = weatherText,
            modifier = Modifier
                .size(150.dp) // Giảm từ 180dp xuống 150dp
                .padding(start = 8.dp) // Đẩy sát cạnh phải hơn
        )
    }
}

// Additional Info Section
@Composable
fun AdditionalInfoSection(weatherData: WeatherDataState, viewModel: WeatherViewModel, windSpeedUnit: UnitConverter.WindSpeedUnit, isNightTime: Boolean) {
    val index = remember(weatherData.timeList) { viewModel.getCurrentIndex(viewModel.currentCity) }
    
    // Màu nền card theo thời gian
    val cardBackgroundColor = if (isNightTime) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.4f)
    
    // Lấy xác suất mưa hôm nay thay vì lượng mưa hiện tại
    val todayPrecipitationProbability = weatherData.dailyPrecipitationList.getOrNull(0)?.toInt() ?: 0
    
    val humidity = weatherData.humidityList.getOrNull(index)?.toInt() ?: 0
    val windSpeed = weatherData.windSpeedList.getOrNull(index)?.let {
        UnitConverter.convertWindSpeed(it, windSpeedUnit)
    } ?: "0"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackgroundColor, shape = RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoItem(R.drawable.rain_dropp, "$todayPrecipitationProbability%", "Mưa", Color(0xFF5372DC), isNightTime)
            InfoItem(R.drawable.humidity, "$humidity%", "Độ ẩm", Color(0xFFD05CA2), isNightTime)
            InfoItem(R.drawable.wind_speed, "$windSpeed", "Gió", Color(0xFF3F9CBE), isNightTime)
        }
    }
}

// Hourly Forecast Section
@Composable
fun HourlyForecastSection(city: City, weatherData: WeatherDataState, viewModel: WeatherViewModel, currentDateStr: String, temperatureUnit: UnitConverter.TemperatureUnit, isNightTime: Boolean) {
    val cardBackgroundColor = if (isNightTime) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.4f)
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackgroundColor, shape = RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Hôm nay", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryTextColor)
            Text(text = currentDateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryTextColor)
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
                        highlight = isCurrentHour,
                        isNightTime = isNightTime
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(text = "Không có dữ liệu dự báo theo giờ.", fontSize = 14.sp, color = primaryTextColor)
            }
        }
    }
}

// Daily Forecast Section
@Composable
fun DailyForecastSection(
    city: City, 
    weatherData: WeatherDataState, 
    viewModel: WeatherViewModel, 
    temperatureUnit: UnitConverter.TemperatureUnit,
    isNightTime: Boolean,
    onDayClick: (Int) -> Unit = {}
) {
    val cardBackgroundColor = if (isNightTime) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.4f)
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
    val dividerColor = if (isNightTime) Color.White.copy(alpha = 0.2f) else Color(0xFF5372dc).copy(alpha = 0.2f)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackgroundColor, shape = RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text(text = "Dự báo hàng ngày", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryTextColor)
        Spacer(modifier = Modifier.height(8.dp))

        val dailyForecastData = remember(city.name, weatherData.dailyTimeList) { viewModel.getDailyForecast(city.name, 7) }
        val tempUnitSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"

        if (dailyForecastData.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dailyForecastData.forEachIndexed { index, data ->
                    val (time, temps, weatherCode) = data
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
                            .clickable { onDayClick(index) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedDate,
                            color = primaryTextColor,
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
                                text = "$precipitation%",
                                color = secondaryTextColor,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "$minTemp$tempUnitSymbol",
                            color = secondaryTextColor,
                            modifier = Modifier.weight(0.2f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "$maxTemp$tempUnitSymbol",
                            color = primaryTextColor,
                            modifier = Modifier.weight(0.2f),
                            textAlign = TextAlign.End
                        )
                    }
                    if (index < dailyForecastData.size - 1) {
                        Divider(color = dividerColor, thickness = 0.5.dp)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(text = "Không có dữ liệu dự báo hàng ngày.", fontSize = 14.sp, color = primaryTextColor)
            }
        }
    }
}

// Air Quality Section
@Composable
fun AirQualitySection(aqi: Int?, isNightTime: Boolean) {
    val cardBackgroundColor = if (isNightTime) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.4f)
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372DC)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372DC).copy(alpha = 0.8f)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackgroundColor, shape = RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text(text = "Chất lượng không khí", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
        Spacer(modifier = Modifier.height(12.dp))

        if (aqi == null) {
            Text(
                text = "Thông tin chất lượng không khí không có sẵn.",
                fontSize = 14.sp,
                color = secondaryTextColor
            )
        } else {
            val (description, color, percentage) = getAqiInfo(aqi)

            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = aqi.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = description, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = getAqiRecommendation(description),
                fontSize = 13.sp,
                color = primaryTextColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            AirQualityBar(percentage = percentage)
        }
    }
}

// Other Details Section
@Composable
fun OtherDetailsSection(weatherData: WeatherDataState, viewModel: WeatherViewModel, temperatureUnit: UnitConverter.TemperatureUnit, windSpeedUnit: UnitConverter.WindSpeedUnit, pressureUnit: UnitConverter.PressureUnit, visibilityUnit: UnitConverter.VisibilityUnit, isNightTime: Boolean) {
    val index = remember(weatherData.timeList) { viewModel.getCurrentIndex(viewModel.currentCity) }
    val cardBackgroundColor = if (isNightTime) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.4f)
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372DC)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372DC).copy(alpha = 0.8f)
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Chia thành các hàng, mỗi hàng 2 hoặc 3 item
        val infoItems = listOfNotNull(
            weatherData.uvList.getOrNull(index)?.let { Triple(R.drawable.uv, "Chỉ số UV", it.toInt().toString()) },
            weatherData.feelsLikeList.getOrNull(index)?.let { 
                val tempSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"
                val convertedTemp = UnitConverter.convertTemperature(it, temperatureUnit).toInt()
                Triple(R.drawable.feels_like, "Cảm giác như", "$convertedTemp$tempSymbol") 
            },
            weatherData.humidityList.getOrNull(index)?.let { Triple(R.drawable.humidity2, "Độ ẩm", "${it.toInt()}%") },
            weatherData.windSpeedList.getOrNull(index)?.let { Triple(R.drawable.ese_wind, "Gió", UnitConverter.convertWindSpeed(it, windSpeedUnit)) },
            weatherData.pressureList.getOrNull(index)?.let { Triple(R.drawable.air_pressure, "Áp suất", UnitConverter.convertPressure(it, pressureUnit)) },
            weatherData.visibilityList.getOrNull(index)?.let { Triple(R.drawable.visibility, "Tầm nhìn", UnitConverter.convertVisibility(it, visibilityUnit)) }
        )

        infoItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowItems.forEach { (iconId, label, value) ->
                    // Màu icon - chỉ sử dụng màu xanh, ban đêm nhạt hơn
                    val iconColor = if (isNightTime) {
                        Color(0xFF89A7E0) // Xanh nhạt cho ban đêm
                    } else {
                        Color(0xFF5372DC) // Xanh gốc cho ban ngày
                    }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(cardBackgroundColor, shape = RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = iconId),
                            contentDescription = label,
                            modifier = Modifier.size(28.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconColor)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = label, color = secondaryTextColor, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Text(
                            text = value,
                            color = primaryTextColor,
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

// Sun Info Section
@Composable
fun SunInfoSection(city: City, viewModel: WeatherViewModel) {
    val currentTime = remember { LocalDateTime.now() }
    val weatherData = viewModel.weatherDataMap[city.name] ?: return
    
    // Lấy thời gian mặt trời mọc và lặn từ API data
    val todayIndex = weatherData.dailyTimeList.indexOfFirst { date ->
        date.startsWith(currentTime.toLocalDate().toString())
    }
    val tomorrowIndex = todayIndex + 1
    
    val (todaySunrise, todaySunset) = if (todayIndex >= 0) {
        val sunrise = weatherData.dailySunriseList.getOrNull(todayIndex)?.let {
            try {
                val time = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
                Pair(time.hour, time.minute)
            } catch (e: Exception) {
                Pair(6, 0)
            }
        } ?: Pair(6, 0)
        
        val sunset = weatherData.dailySunsetList.getOrNull(todayIndex)?.let {
            try {
                val time = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
                Pair(time.hour, time.minute)
            } catch (e: Exception) {
                Pair(18, 0)
            }
        } ?: Pair(18, 0)
        
        Pair(sunrise, sunset)
    } else {
        Pair(Pair(6, 0), Pair(18, 0))
    }
    
    val (nextSunrise, nextSunset) = if (tomorrowIndex < weatherData.dailySunriseList.size) {
        val sunrise = weatherData.dailySunriseList[tomorrowIndex].let {
            try {
                val time = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
                Pair(time.hour, time.minute)
            } catch (e: Exception) {
                Pair(6, 0)
            }
        }
        
        val sunset = weatherData.dailySunsetList[tomorrowIndex].let {
            try {
                val time = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
                Pair(time.hour, time.minute)
            } catch (e: Exception) {
                Pair(18, 0)
            }
        }
        
        Pair(sunrise, sunset)
    } else {
        Pair(Pair(6, 0), Pair(18, 0))
    }
    
    var boxWidth by remember { mutableStateOf(0) }
    
    // Convert times to minutes for easier calculation
    val currentMinutes = currentTime.hour * 60 + currentTime.minute
    val sunriseMinutes = todaySunrise.first * 60 + todaySunrise.second
    val sunsetMinutes = todaySunset.first * 60 + todaySunset.second
    val nextSunriseMinutes = nextSunrise.first * 60 + nextSunrise.second + 1440 // Add 24 hours
    
    val isNight = currentMinutes >= sunsetMinutes || currentMinutes <= sunriseMinutes
    val isDaytime = !isNight
    
    // Calculate progress
    val progress = remember(currentMinutes, sunriseMinutes, sunsetMinutes, nextSunriseMinutes) {
        if (isNight) {
            // Night time progress calculation (for moon)
            if (currentMinutes >= sunsetMinutes) {
                val totalNightMinutes = nextSunriseMinutes - sunsetMinutes
                val minutesFromSunset = currentMinutes - sunsetMinutes
                (minutesFromSunset.toFloat() / totalNightMinutes.toFloat()).coerceIn(0f, 1f)
            } else {
                val totalNightMinutes = nextSunriseMinutes - sunsetMinutes
                val minutesFromSunset = currentMinutes + (1440 - sunsetMinutes)
                (minutesFromSunset.toFloat() / totalNightMinutes.toFloat()).coerceIn(0f, 1f)
            }
        } else {
            // Day time progress calculation (for sun)
            val totalDayMinutes = sunsetMinutes - sunriseMinutes
            val minutesFromSunrise = currentMinutes - sunriseMinutes
            (minutesFromSunrise.toFloat() / totalDayMinutes.toFloat()).coerceIn(0f, 1f)
        }
    }
    
    val cardBackgroundColor = Color.Black.copy(alpha = 0.3f)
    val density = LocalDensity.current
    val iconSizePx = remember(density) { with(density) { 24.dp.toPx() } }
    val trackHeightDp = 3.dp
    val trackColor = Color.White.copy(alpha = 0.7f)
    val gapPaddingDp = 4.dp
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                cardBackgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        // Labels at top with dynamic order based on time of day
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isDaytime) {
                Text(
                    text = "Bình minh",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "Hoàng hôn",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = "Hoàng hôn",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "Bình minh",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .onSizeChanged { boxWidth = it.width }
        ) {
            // Icons at top with dynamic order
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (isDaytime) {
                    Image(
                        painter = painterResource(id = R.drawable.sun_go_up),
                        contentDescription = "Bình minh",
                        modifier = Modifier.size(24.dp)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.sun_go_down),
                        contentDescription = "Hoàng hôn",
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.sun_go_down),
                        contentDescription = "Hoàng hôn",
                        modifier = Modifier.size(24.dp)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.sun_go_up),
                        contentDescription = "Bình minh",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Calculate icon position
            val iconActualLeftPx = remember(progress, boxWidth, iconSizePx) {
                if (boxWidth > 0) {
                    val targetCenterPx = progress * boxWidth
                    val desiredLeftEdgePx = targetCenterPx - (iconSizePx / 2f)
                    val maxLeftEdgePx = (boxWidth - iconSizePx).coerceAtLeast(0f)
                    desiredLeftEdgePx.coerceIn(0f, maxLeftEdgePx)
                } else {
                    -iconSizePx
                }
            }

            // Track line
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeightDp)
                    .align(Alignment.Center)
            ) {
                val canvasWidth = size.width
                val trackThicknessPx = trackHeightDp.toPx()
                val currentGapPaddingPx = with(density) { gapPaddingDp.toPx() }

                if (boxWidth > 0 && iconActualLeftPx >= -currentGapPaddingPx && iconActualLeftPx <= canvasWidth - iconSizePx + currentGapPaddingPx) {
                    val gapStartOnCanvas = (iconActualLeftPx - currentGapPaddingPx).coerceAtLeast(0f)
                    val gapEndOnCanvas = (iconActualLeftPx + iconSizePx + currentGapPaddingPx).coerceAtMost(canvasWidth)

                    if (gapStartOnCanvas > 0f) {
                        drawLine(
                            color = trackColor,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(gapStartOnCanvas, size.height / 2f),
                            strokeWidth = trackThicknessPx,
                            cap = StrokeCap.Round
                        )
                    }

                    if (gapEndOnCanvas < canvasWidth) {
                        drawLine(
                            color = trackColor,
                            start = Offset(gapEndOnCanvas, size.height / 2f),
                            end = Offset(canvasWidth, size.height / 2f),
                            strokeWidth = trackThicknessPx,
                            cap = StrokeCap.Round
                        )
                    }
                } else {
                    drawLine(
                        color = trackColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(canvasWidth, size.height / 2f),
                        strokeWidth = trackThicknessPx,
                        cap = StrokeCap.Round
                    )
                }
            }
            
            // Moving icon (sun or moon)
            if (boxWidth > 0 && iconActualLeftPx >= 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(iconActualLeftPx.roundToInt(), 0)
                        }
                ) {
                    Image(
                        painter = painterResource(
                            id = if (isNight) R.drawable.sparkle_moon else R.drawable.sparkle_sun
                        ),
                        contentDescription = if (isNight) "Moon position" else "Sun position",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Time text at bottom with dynamic order
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isDaytime) {
                    Text(
                        text = "${String.format("%02d", todaySunrise.first)}:${String.format("%02d", todaySunrise.second)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${String.format("%02d", todaySunset.first)}:${String.format("%02d", todaySunset.second)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "${String.format("%02d", todaySunset.first)}:${String.format("%02d", todaySunset.second)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${String.format("%02d", nextSunrise.first)}:${String.format("%02d", nextSunrise.second)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// Last Update Section
@Composable
fun LastUpdateSection(weatherData: WeatherDataState, isNightTime: Boolean) {
    val textColor = if (isNightTime) Color.White.copy(alpha = 0.7f) else Color(0xFF5372dc).copy(alpha = 0.7f)
    
    weatherData.lastUpdateTime?.let {
        Text(
            text = "Cập nhật lần cuối: ${formatTimestamp(it)}",
            fontSize = 12.sp,
            color = textColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

// Weather Radar Helper Functions
fun latLonToTileXY(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
    val latRad = Math.toRadians(lat)
    val n = 1 shl zoom
    val xTile = ((lon + 180.0) / 360.0 * n).toInt()
    val yTile = ((1.0 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2.0 * n).toInt()
    return Pair(xTile, yTile)
}

fun tileToLatLonBounds(x: Int, y: Int, zoom: Int): LatLngBounds {
    val n = 1 shl zoom
    val lonDeg1 = x.toDouble() / n * 360.0 - 180.0
    val lonDeg2 = (x + 1).toDouble() / n * 360.0 - 180.0

    val latRad1 = atan(sinh(PI * (1 - 2 * y.toDouble() / n)))
    val latDeg1 = Math.toDegrees(latRad1)
    val latRad2 = atan(sinh(PI * (1 - 2 * (y + 1).toDouble() / n)))
    val latDeg2 = Math.toDegrees(latRad2)

    val bounds = LatLngBounds(
        LatLng(latDeg2, lonDeg1), // Góc Tây Nam
        LatLng(latDeg1, lonDeg2)  // Góc Đông Bắc
    )

    Log.d("WeatherRadarSection", "Bounds for tile ($x, $y) at zoom $zoom: SW(${latDeg2}, ${lonDeg1}), NE(${latDeg1}, ${lonDeg2})")
    return bounds
}

fun getTilesForViewport(bounds: LatLngBounds, zoom: Int): List<Pair<Int, Int>> {
    val southwest = bounds.southwest
    val northeast = bounds.northeast

    val (minX, maxY) = latLonToTileXY(southwest.latitude, southwest.longitude, zoom)
    val (maxX, minY) = latLonToTileXY(northeast.latitude, northeast.longitude, zoom)

    val tiles = mutableListOf<Pair<Int, Int>>()
    val n = 1 shl zoom

    if (maxX >= minX) {
        // Bình thường
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val wrappedX = (x + n) % n
                tiles.add(Pair(wrappedX, y))
            }
        }
    } else {
        // Qua kinh tuyến đổi ngày, chia làm 2 đoạn
        for (x in minX until n) {
            for (y in minY..maxY) {
                val wrappedX = (x + n) % n
                tiles.add(Pair(wrappedX, y))
            }
        }
        for (x in 0..maxX) {
            for (y in minY..maxY) {
                val wrappedX = (x + n) % n
                tiles.add(Pair(wrappedX, y))
            }
        }
    }

    Log.d("WeatherRadarSection", "Tiles for viewport at zoom $zoom: $tiles")
    return tiles
}

// Weather Radar Section - Clickable with Full Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherRadarSection(city: City, onFullScreenClick: () -> Unit = {}, isNightTime: Boolean) {
    var selectedLayer by remember { mutableStateOf("precipitation") }
    var expanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    val cardBackgroundColor = if (isNightTime) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.4f)
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.7f) else Color(0xFF5372dc).copy(alpha = 0.7f)

    val weatherLayers = remember {
        listOf(
            "precipitation" to "Mưa",
            "clouds" to "Mây", 
            "wind" to "Gió",
            "temp" to "Nhiệt độ"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackgroundColor, shape = RoundedCornerShape(10.dp))
            .clickable { onFullScreenClick() }
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Radar Thời Tiết",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Xem toàn màn hình",
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(180f),
                    tint = secondaryTextColor
                )
            }

            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        text = weatherLayers.find { it.first == selectedLayer }?.second ?: "Mưa",
                        color = primaryTextColor,
                        fontSize = 14.sp
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    weatherLayers.forEach { (layer, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedLayer = layer
                                expanded = false
                                hasError = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Content
        if (hasError) {
            // Error fallback
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        primaryTextColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cloudy),
                    contentDescription = "Radar không khả dụng",
                    modifier = Modifier.size(48.dp),
                    tint = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Radar Thời Tiết",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dịch vụ radar tạm thời không khả dụng.\nVui lòng thử lại sau.",
                    fontSize = 14.sp,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { hasError = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc)
                    )
                ) {
                    Text("Thử lại", color = if (isNightTime) Color.Black else Color.White)
                }
            }
        } else {
            // Map content
            SimpleWeatherMap(
                city = city,
                selectedLayer = selectedLayer,
                onLoadingChange = { isLoading = it },
                onError = { hasError = true }
            )
        }
    }
}

@Composable
private fun SimpleWeatherMap(
    city: City,
    selectedLayer: String,
    onLoadingChange: (Boolean) -> Unit,
    onError: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var lastLoadedLayer by remember { mutableStateOf("") }
    var currentMarker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }

    // Get ViewModel from LocalContext
    val context = LocalContext.current
    val viewModel = remember {
        val activity = context as? ComponentActivity
        activity?.let {
            ViewModelProvider(it, WeatherViewModelFactory(
                context = context,
                weatherDao = WeatherDatabase.getDatabase(context).weatherDao()
            ))[WeatherViewModel::class.java]
        }
    }

    // Enhanced loadOverlay function with caching
    val loadOverlay = remember(city.latitude, city.longitude) {
        { layer: String ->
            if (googleMap == null || layer == lastLoadedLayer) return@remember
            
            coroutineScope.launch {
                onLoadingChange(true)
                
                try {
                    // First try to use cached data
                    val cachedTiles = viewModel?.getCachedRadarTiles(city.name, layer) ?: emptyList()
                    
                    if (cachedTiles.isNotEmpty()) {
                        // Use cached data
                        Log.d("WeatherRadarSection", "Using cached radar data for ${city.name}, layer $layer: ${cachedTiles.size} tiles")
                        
                        withContext(Dispatchers.Main) {
                            googleMap?.clear()
                            
                            // Restore marker immediately after clear
                            val cityLocation = LatLng(city.latitude, city.longitude)
                            currentMarker = googleMap?.addMarker(
                                MarkerOptions()
                                    .position(cityLocation)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                    .title(city.name)
                                    .anchor(0.5f, 0.5f)
                                    .zIndex(1.0f)
                            )
                            
                            val transparency = when (layer) {
                                "precipitation" -> 0.0f
                                "clouds" -> 0.2f
                                "wind" -> 0.4f
                                "temp" -> 0.3f
                                else -> 0.3f
                            }
                            
                            cachedTiles.forEach { tile ->
                                tile.bitmapData?.let { bitmapData ->
                                    try {
                                        val bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.size)
                                        if (bitmap != null && tile.bounds != null) {
                                            googleMap?.addGroundOverlay(
                                                GroundOverlayOptions()
                                                    .image(BitmapDescriptorFactory.fromBitmap(bitmap))
                                                    .positionFromBounds(tile.bounds)
                                                    .transparency(transparency)
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e("WeatherRadarSection", "Error loading cached tile bitmap", e)
                                    }
                                }
                            }
                            
                            lastLoadedLayer = layer
                            onLoadingChange(false)
                        }
                    } else {
                        // Fallback to network loading (original code)
                        withContext(Dispatchers.IO) {
                            val zoom = 5  // Lower zoom for larger coverage
                            val (centerX, centerY) = latLonToTileXY(city.latitude, city.longitude, zoom)
                            
                            // Load 3x3 grid of tiles centered on the city
                            val tilesToLoad = mutableListOf<Triple<Int, Int, Bitmap?>>()
                            
                            for (dx in -1..1) {
                                for (dy in -1..1) {
                                    val xTile = centerX + dx
                                    val yTile = centerY + dy
                                    
                                    // Handle tile wrapping for X coordinate
                                    val n = 1 shl zoom
                                    val wrappedX = ((xTile % n) + n) % n
                                    
                                    val url = "https://tile.openweathermap.org/map/$layer/$zoom/$wrappedX/$yTile.png?appid=960b4897d630b53c8faeb909817bf31a"
                                    
                                    val bitmap = try {
                                        URL(url).openConnection().apply {
                                            connectTimeout = 2000
                                            readTimeout = 2000
                                        }.getInputStream().use { BitmapFactory.decodeStream(it) }
                                    } catch (e: Exception) {
                                        null
                                    }
                                    
                                    if (bitmap != null) {
                                        tilesToLoad.add(Triple(wrappedX, yTile, bitmap))
                                    }
                                }
                            }
                            
                            withContext(Dispatchers.Main) {
                                if (tilesToLoad.isNotEmpty() && googleMap != null) {
                                    googleMap!!.clear()
                                    
                                    // Always restore marker first
                                    val cityLocation = LatLng(city.latitude, city.longitude)
                                    currentMarker = googleMap!!.addMarker(
                                        MarkerOptions()
                                            .position(cityLocation)
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                            .title(city.name)
                                            .anchor(0.5f, 0.5f)
                                            .zIndex(1.0f)
                                    )
                                    
                                    val transparency = when (layer) {
                                        "precipitation" -> 0.0f
                                        "clouds" -> 0.2f
                                        "wind" -> 0.4f
                                        "temp" -> 0.3f
                                        else -> 0.3f
                                    }
                                    
                                    // Add all loaded tiles
                                    tilesToLoad.forEach { (xTile, yTile, bitmap) ->
                                        bitmap?.let {
                                            val bounds = tileToLatLonBounds(xTile, yTile, zoom)
                                            googleMap!!.addGroundOverlay(
                                                GroundOverlayOptions()
                                                    .image(BitmapDescriptorFactory.fromBitmap(it))
                                                    .positionFromBounds(bounds)
                                                    .transparency(transparency)
                                            )
                                        }
                                    }
                                    
                                    lastLoadedLayer = layer
                                    onLoadingChange(false)
                                    
                                    // Preload radar for this city if not cached
                                    viewModel?.preloadRadarForCity(city.name)
                                } else {
                                    onError()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onLoadingChange(false)
                        onError()
                    }
                }
            }
        }
    }

    // Only load when layer actually changes
    LaunchedEffect(selectedLayer) {
        if (googleMap != null && selectedLayer != lastLoadedLayer) {
            loadOverlay(selectedLayer)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            googleMap?.clear()
            mapView?.onDestroy()
            mapView = null
            googleMap = null
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        factory = { ctx ->
            MapView(ctx).apply {
                onCreate(null)
                onStart()
                getMapAsync { map ->
                    val cityLocation = LatLng(city.latitude, city.longitude)
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(cityLocation, 8.5f)
                    )
                    
                    currentMarker = map.addMarker(
                        MarkerOptions()
                            .position(cityLocation)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            .title(city.name)
                            .anchor(0.5f, 0.5f)
                            .zIndex(1.0f)
                    )

                    map.uiSettings.isZoomControlsEnabled = false
                    map.uiSettings.isScrollGesturesEnabled = false
                    map.uiSettings.isZoomGesturesEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.uiSettings.isMapToolbarEnabled = false
                    map.mapType = GoogleMap.MAP_TYPE_NORMAL

                    googleMap = map
                    mapView = this
                    
                    // Initial load
                    loadOverlay(selectedLayer)
                }
            }
        },
        update = { /* No updates needed */ }
    )
}

// Full Screen Weather Radar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenWeatherRadar(
    city: City,
    onBackClick: () -> Unit
) {
    var selectedLayer by remember { mutableStateOf("precipitation") }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(true) }
    var currentZoom by remember { mutableStateOf(8) }

    val weatherLayers = remember {
        listOf(
            Triple("precipitation", "Mưa", "🌧️"),
            Triple("clouds", "Mây", "☁️"), 
            Triple("wind", "Gió", "💨"),
            Triple("temp", "Nhiệt độ", "🌡️")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                )
            )
    ) {
        // Bản đồ radar full màn hình và TopBar
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content (bản đồ)
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasError) {
                    // ... giữ nguyên phần lỗi ...
                } else {
                    FullScreenWeatherMap(
                        city = city,
                        selectedLayer = selectedLayer,
                        zoom = currentZoom,
                        onLoadingChange = { isLoading = it },
                        onError = { hasError = true },
                        onZoomChange = { currentZoom = it }
                    )
                    if (isLoading) {
                        // ... giữ nguyên overlay loading ...
                    }
                }
            }
            // TopBar phía trên cùng
            TopAppBar(
                title = {
                    Column {
                        Text("Radar Thời Tiết", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(city.name, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = "Quay lại",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    var showLayerMenu by remember { mutableStateOf(false) }
                    val current = weatherLayers.find { it.first == selectedLayer }
                    TextButton(onClick = { showLayerMenu = true }) {
                        Text(text = current?.second ?: "Lớp phủ", color = Color.White, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Mở menu lớp phủ",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showLayerMenu,
                        onDismissRequest = { showLayerMenu = false }
                    ) {
                        weatherLayers.forEach { (layer, label, _) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (selectedLayer == layer) Color(0xFF5372dc) else Color.Black,
                                        fontWeight = if (selectedLayer == layer) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedLayer = layer
                                    hasError = false
                                    showLayerMenu = false
                                },
                                modifier = if (selectedLayer == layer) Modifier.background(Color(0xFFE3ECFF)) else Modifier
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF5372dc).copy(alpha = 0.9f)
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
            // Overlay ColorScaleBar ở dưới cùng, căn giữa, chỉ khi không lỗi
            if (!hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ColorScaleBar(selectedLayer = selectedLayer)
                }
            }
        }
    }
}

@Composable
private fun FullScreenWeatherMap(
    city: City,
    selectedLayer: String,
    zoom: Int,
    onLoadingChange: (Boolean) -> Unit,
    onError: () -> Unit,
    onZoomChange: (Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var lastLoadedLayer by remember { mutableStateOf("") }
    var lastLoadedZoom by remember { mutableStateOf(0) }
    var lastLoadedBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    val currentLayer by rememberUpdatedState(selectedLayer)

    // Load tiles theo bounds và zoom hiện tại
    fun loadTilesForBounds(layer: String, bounds: LatLngBounds, targetZoom: Int) {
        if (googleMap == null) return
        coroutineScope.launch {
            onLoadingChange(true)
            try {
                withContext(Dispatchers.IO) {
                    val tiles = getTilesForViewport(bounds, targetZoom)
                    val jobs = tiles.map { (x, y) ->
                        async {
                            val url = "https://tile.openweathermap.org/map/$layer/$targetZoom/$x/$y.png?appid=960b4897d630b53c8faeb909817bf31a"
                            val bitmap = try {
                                URL(url).openConnection().apply {
                                    connectTimeout = 1200
                                    readTimeout = 1200
                                }.getInputStream().use { BitmapFactory.decodeStream(it) }
                            } catch (e: Exception) { null }
                            Triple(x, y, bitmap)
                        }
                    }
                    val results = jobs.awaitAll()
                    withContext(Dispatchers.Main) {
                        if (results.isNotEmpty() && googleMap != null) {
                            googleMap!!.clear()
                            // Thêm marker cho thành phố hiện tại
                            val cityLocation = LatLng(city.latitude, city.longitude)
                            googleMap!!.addMarker(
                                MarkerOptions()
                                    .position(cityLocation)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                    .title(city.name)
                                    .anchor(0.5f, 0.5f)
                                    .zIndex(1.0f)
                            )
                            val transparency = when (layer) {
                                "precipitation" -> 0.0f
                                "clouds" -> 0.2f
                                "wind" -> 0.4f
                                "temp" -> 0.3f
                                else -> 0.3f
                            }
                            for (result in results) {
                                val (xTile, yTile, bitmap) = result
                                if (bitmap != null) {
                                    val tileBounds = tileToLatLonBounds(xTile, yTile, targetZoom)
                                    googleMap!!.addGroundOverlay(
                                        GroundOverlayOptions()
                                            .image(BitmapDescriptorFactory.fromBitmap(bitmap))
                                            .positionFromBounds(tileBounds)
                                            .transparency(transparency)
                                    )
                                }
                            }
                            lastLoadedLayer = layer
                            lastLoadedZoom = targetZoom
                            lastLoadedBounds = bounds
                            onLoadingChange(false)
                        } else {
                            onError()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onLoadingChange(false)
                    onError()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            googleMap?.clear()
            mapView?.onDestroy()
            mapView = null
            googleMap = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                onCreate(null)
                onStart()
                getMapAsync { map ->
                    val cityLocation = LatLng(city.latitude, city.longitude)
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(cityLocation, zoom.toFloat())
                    )
                    map.uiSettings.isZoomControlsEnabled = true
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.uiSettings.isRotateGesturesEnabled = true
                    map.uiSettings.isCompassEnabled = true
                    map.mapType = GoogleMap.MAP_TYPE_NORMAL
                    googleMap = map
                    mapView = this
                    // Lần đầu load
                    val initialBounds = map.projection.visibleRegion.latLngBounds
                    loadTilesForBounds(selectedLayer, initialBounds, zoom)
                    // Lắng nghe camera idle để load tile động
                    map.setOnCameraIdleListener {
                        val bounds = map.projection.visibleRegion.latLngBounds
                        val newZoom = map.cameraPosition.zoom.toInt().coerceIn(2, 12)
                        onZoomChange(newZoom)
                        // Dùng currentLayer thay vì selectedLayer
                        if (currentLayer != lastLoadedLayer || newZoom != lastLoadedZoom || lastLoadedBounds == null || !lastLoadedBounds!!.contains(bounds.northeast) || !lastLoadedBounds!!.contains(bounds.southwest)) {
                            loadTilesForBounds(currentLayer, bounds, newZoom)
                        }
                    }
                }
            }
        },
        update = { /* No updates needed */ }
    )

    // Reload tile khi đổi lớp phủ
    LaunchedEffect(selectedLayer) {
        googleMap?.let { map ->
            val bounds = map.projection.visibleRegion.latLngBounds
            val currentZoom = map.cameraPosition.zoom.toInt().coerceIn(2, 12)
            loadTilesForBounds(selectedLayer, bounds, currentZoom)
        }
    }
}

@Composable
private fun ColorScaleBar(
    selectedLayer: String,
    modifier: Modifier = Modifier
) {
    val (colors, values, unit, label) = when (selectedLayer) {
        "precipitation" -> Quadruple(
            listOf(
                Color(0xFFB4F0FF), Color(0xFF4FC3F7), Color(0xFF1976D2), Color(0xFF1565C0), Color(0xFFB71C1C)
            ),
            listOf("0", "2", "5", "10", "20", "50"),
            "mm/h",
            "Mưa, mm/h"
        )
        "clouds" -> Quadruple(
            listOf(
                Color(0xFFFFFFFF), Color(0xFFD3D3D3), Color(0xFFA9A9A9), Color(0xFF696969), Color(0xFF2F4F4F)
            ),
            listOf("0", "25", "50", "75", "100"),
            "%",
            "Mây, %"
        )
        "wind" -> Quadruple(
            listOf(
                Color(0xFFB2EBF2), Color(0xFF4FC3F7), Color(0xFF1976D2), Color(0xFFFFF176), Color(0xFFFFA726), Color(0xFFD32F2F)
            ),
            listOf("0", "5", "10", "15", "25", "35"),
            "km/h",
            "Gió, km/h"
        )
        "temp" -> Quadruple(
            listOf(
                Color(0xFF6E40AA), Color(0xFF2A6BD4), Color(0xFF00A6FF), Color(0xFF00FFEA), Color(0xFF7FFF00), Color(0xFFFFF500), Color(0xFFFFAA00), Color(0xFFFF0000)
            ),
            listOf("-40", "-20", "0", "20", "40"),
            "°C",
            "Nhiệt độ, °C"
        )
        else -> Quadruple(
            listOf(Color.Gray),
            listOf("0"),
            "",
            ""
        )
    }
    Card(
        modifier = modifier
            .width(226.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = Color(0xFF222222),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            // Box width 200.dp, căn giữa trong Card
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    // Box chứa các số đo, width nhỏ hơn, căn giữa
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        values.forEachIndexed { index, value ->
                            val xPosition = ((index.toFloat() / (values.size - 1).coerceAtLeast(1)) * 180f).dp
                            Text(
                                text = value,
                                fontSize = 10.sp,
                                color = Color(0xFF5372dc),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = xPosition - 10.dp, y = 0.dp)
                            )
                        }
                    }
                    // Thanh màu giữ nguyên width
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                brush = Brush.horizontalGradient(colors)
                            )
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

// Helper data class
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ========== Main Screen Composable ==========
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun WeatherMainScreen(
    // Sử dụng factory để khởi tạo ViewModel với các dependencies cần thiết
    viewModel: WeatherViewModel = run {
        val context = LocalContext.current
        val factory = remember(context) {
            WeatherViewModelFactory(
                context = context,
                weatherDao = WeatherDatabase.getDatabase(context).weatherDao(),
                openMeteoService = RetrofitInstance.api,
                airQualityService = RetrofitInstance.airQualityApi,
                geoNamesService = RetrofitInstance.geoNamesApi
            )
        }
        viewModel(factory = factory)
    }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cities = viewModel.citiesList
    val pagerState = rememberPagerState(initialPage = 0)

    var showSearchOverlay by remember { mutableStateOf(false) }
    var showSearchScreen by remember { mutableStateOf(false) }
    var showFilteredCitiesScreen by remember { mutableStateOf(false) }
    var showCityManagementScreen by remember { mutableStateOf(false) }
    var showSidebar by remember { mutableStateOf(false) }
    var showDayDetailScreen by remember { mutableStateOf(false) }
    var selectedDayDetail by remember { mutableStateOf<DayWeatherDetail?>(null) }
    var showFullScreenRadar by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)
    
    // Kiểm tra thời gian để áp dụng theme ban đêm
    val isNightTime = remember {
        val currentHour = java.time.LocalTime.now().hour
        currentHour < 6 || currentHour >= 18 // Đêm từ 18:00 đến 6:00
    }

    // Màu nền và màu chữ theo thời gian
    val backgroundColors = if (isNightTime) {
        listOf(Color(0xFF475985), Color(0xFF5F4064)) // Gradient tối cho ban đêm - đồng bộ với main screen
    } else {
        listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6)) // Gradient sáng cho ban ngày
    }
    
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)

    val preferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    var temperatureUnit by remember { mutableStateOf(UnitConverter.TemperatureUnit.CELSIUS) }
    var windSpeedUnit by remember { mutableStateOf(UnitConverter.WindSpeedUnit.KMH) }
    var pressureUnit by remember { mutableStateOf(UnitConverter.PressureUnit.MMHG) }
    var visibilityUnit by remember { mutableStateOf(UnitConverter.VisibilityUnit.KM) }

    LaunchedEffect(Unit) {
        temperatureUnit = when (preferences.getString("temperature_unit", "Độ C (°C)")) {
            "Độ C (°C)" -> UnitConverter.TemperatureUnit.CELSIUS
            "Độ F (°F)" -> UnitConverter.TemperatureUnit.FAHRENHEIT
            else -> UnitConverter.TemperatureUnit.CELSIUS
        }
        windSpeedUnit = when (preferences.getString("wind_unit", "Kilomet mỗi giờ (km/h)")) {
            "Kilomet mỗi giờ (km/h)" -> UnitConverter.WindSpeedUnit.KMH
            "Thang đo Beaufort" -> UnitConverter.WindSpeedUnit.BEAUFORT
            "Mét mỗi giây (m/s)" -> UnitConverter.WindSpeedUnit.MS
            "Feet mỗi giây (ft/s)" -> UnitConverter.WindSpeedUnit.FTS
            "Dặm mỗi giờ (mph)" -> UnitConverter.WindSpeedUnit.MPH
            "Hải lý mỗi giờ (hải lý)" -> UnitConverter.WindSpeedUnit.KNOTS
            else -> UnitConverter.WindSpeedUnit.KMH
        }
        pressureUnit = when (preferences.getString("pressure_unit", "Millimet thủy ngân (mmHg)")) {
            "Hectopascal (hPa)" -> UnitConverter.PressureUnit.HPA
            "Millimet thủy ngân (mmHg)" -> UnitConverter.PressureUnit.MMHG
            "Inch thủy ngân (inHg)" -> UnitConverter.PressureUnit.INHG
            "Millibar (mb)" -> UnitConverter.PressureUnit.MB
            "Pound trên inch vuông (psi)" -> UnitConverter.PressureUnit.PSI
            else -> UnitConverter.PressureUnit.MMHG
        }
        visibilityUnit = when (preferences.getString("visibility_unit", "Kilomet (km)")) {
            "Kilomet (km)" -> UnitConverter.VisibilityUnit.KM
            "Dặm (mi)" -> UnitConverter.VisibilityUnit.MI
            "Mét (m)" -> UnitConverter.VisibilityUnit.M
            "Feet (ft)" -> UnitConverter.VisibilityUnit.FT
            else -> UnitConverter.VisibilityUnit.KM
        }
        Log.d("WeatherMainScreen", "Temperature Unit: $temperatureUnit")
        Log.d("WeatherMainScreen", "Wind Unit: $windSpeedUnit")
        Log.d("WeatherMainScreen", "Pressure Unit: $pressureUnit")
        Log.d("WeatherMainScreen", "Visibility Unit: $visibilityUnit")
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.weatherapp.UNIT_CHANGED") {
                    val unitType = intent.getStringExtra("unit_type")
                    val unitValue = intent.getStringExtra("unit_value")
                    Log.d("WeatherMainScreen", "Received unit change: $unitType = $unitValue")
                    when (unitType) {
                        "Nhiệt độ" -> {
                            temperatureUnit = when (unitValue) {
                                "Độ C (°C)" -> UnitConverter.TemperatureUnit.CELSIUS
                                "Độ F (°F)" -> UnitConverter.TemperatureUnit.FAHRENHEIT
                                else -> UnitConverter.TemperatureUnit.CELSIUS
                            }
                        }
                        "Gió" -> {
                            windSpeedUnit = when (unitValue) {
                                "Kilomet mỗi giờ (km/h)" -> UnitConverter.WindSpeedUnit.KMH
                                "Thang đo Beaufort" -> UnitConverter.WindSpeedUnit.BEAUFORT
                                "Mét mỗi giây (m/s)" -> UnitConverter.WindSpeedUnit.MS
                                "Feet mỗi giây (ft/s)" -> UnitConverter.WindSpeedUnit.FTS
                                "Dặm mỗi giờ (mph)" -> UnitConverter.WindSpeedUnit.MPH
                                "Hải lý mỗi giờ (hải lý)" -> UnitConverter.WindSpeedUnit.KNOTS
                                else -> UnitConverter.WindSpeedUnit.KMH
                            }
                        }
                        "Áp suất không khí" -> {
                            pressureUnit = when (unitValue) {
                                "Hectopascal (hPa)" -> UnitConverter.PressureUnit.HPA
                                "Millimet thủy ngân (mmHg)" -> UnitConverter.PressureUnit.MMHG
                                "Inch thủy ngân (inHg)" -> UnitConverter.PressureUnit.INHG
                                "Millibar (mb)" -> UnitConverter.PressureUnit.MB
                                "Pound trên inch vuông (psi)" -> UnitConverter.PressureUnit.PSI
                                else -> UnitConverter.PressureUnit.MMHG
                            }
                        }
                        "Tầm nhìn" -> {
                            visibilityUnit = when (unitValue) {
                                "Kilomet (km)" -> UnitConverter.VisibilityUnit.KM
                                "Dặm (mi)" -> UnitConverter.VisibilityUnit.MI
                                "Mét (m)" -> UnitConverter.VisibilityUnit.M
                                "Feet (ft)" -> UnitConverter.VisibilityUnit.FT
                                else -> UnitConverter.VisibilityUnit.KM
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter("com.example.weatherapp.UNIT_CHANGED")
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

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
                    onManageCitiesClick = { showCityManagementScreen = true },
                    onMenuClick = { showSidebar = true },
                    isNightTime = isNightTime,
                    currentCityName = cities.getOrNull(pagerState.currentPage)?.name ?: "",
                    scrollOffset = 0f,
                    cityCount = cities.size,
                    currentIndex = pagerState.currentPage
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
                        contentColor = if (isNightTime) Color(0xFF1a1a2e) else Color(0xFF5372dc),
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
                            brush = Brush.verticalGradient(colors = backgroundColors)
                        )
                        .padding(innerPadding)
                ) {
                    if (cities.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 30.dp)
                            ) {
                                CircularProgressIndicator(color = primaryTextColor)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Đang tìm vị trí của bạn...",
                                    fontSize = 16.sp,
                                    color = primaryTextColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Log.d("WeatherMainScreen", "Rendering HorizontalPager với ${cities.size} thành phố")
                        HorizontalPager(
                            count = cities.size,
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val city = cities[page]
                            Log.d("WeatherMainScreen", "Rendering page $page cho thành phố: ${city.name}")
                            val weatherData = viewModel.weatherDataMap[city.name]
                            val isNetworkAvailable = isNetworkAvailable(context)

                            when {
                                !isNetworkAvailable -> {
                                    OfflineScreen(lastUpdateTime = weatherData?.lastUpdateTime, isNightTime = isNightTime)
                                }
                                weatherData == null || (weatherData.timeList.isEmpty() && weatherData.dailyTimeList.isEmpty() && weatherData.currentAqi == null) || weatherData.errorMessage != null -> {
                                    LoadingOrErrorScreen(
                                        errorMessage = weatherData?.errorMessage,
                                        lastUpdateTime = weatherData?.lastUpdateTime,
                                        isNightTime = isNightTime
                                    )
                                }
                                else -> {
                                    val currentDate = LocalDate.now()
                                    val dateFormatter = remember { DateTimeFormatter.ofPattern("E, dd MMM") }
                                    val currentDateStr = remember(currentDate) { currentDate.format(dateFormatter) }
                                    val currentAqi = weatherData.currentAqi
                                    
                                    // Track scroll state for TopBar animation - bỏ để tránh lag
                                    val lazyListState = rememberLazyListState()

                                    LazyColumn(
                                        state = lazyListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 30.dp),
                                        verticalArrangement = Arrangement.spacedBy(24.dp),
                                        contentPadding = PaddingValues(bottom = 30.dp)
                                    ) {
                                        item { CurrentWeatherSection(city, weatherData, viewModel, temperatureUnit, isNightTime) }
                                        item { AdditionalInfoSection(weatherData, viewModel, windSpeedUnit, isNightTime) }
                                        item { HourlyForecastSection(city, weatherData, viewModel, currentDateStr, temperatureUnit, isNightTime) }
                                        item { 
                                            DailyForecastSection(
                                                city = city, 
                                                weatherData = weatherData, 
                                                viewModel = viewModel, 
                                                temperatureUnit = temperatureUnit,
                                                isNightTime = isNightTime,
                                                onDayClick = { dayIndex ->
                                                    val dayDetail = createDayWeatherDetail(dayIndex, weatherData, viewModel)
                                                    dayDetail?.let {
                                                        selectedDayDetail = it
                                                        showDayDetailScreen = true
                                                    }
                                                }
                                            )
                                        }
                                        item { AirQualitySection(aqi = currentAqi, isNightTime = isNightTime) }
                                        item { SunInfoSection(city = city, viewModel = viewModel) }
                                        item { OtherDetailsSection(weatherData, viewModel, temperatureUnit, windSpeedUnit, pressureUnit, visibilityUnit, isNightTime) }
                                        item { 
                                            WeatherRadarSection(
                                                city = city, 
                                                onFullScreenClick = { showFullScreenRadar = true },
                                                isNightTime = isNightTime
                                            ) 
                                        }
                                        item { LastUpdateSection(weatherData, isNightTime) }
                                        item { Spacer(modifier = Modifier.height(20.dp)) }
                                    }
                                    
                                    // Update TopBar with scroll offset - với throttling
                                    LaunchedEffect(lazyListState) {
                                        snapshotFlow { 
                                            lazyListState.firstVisibleItemIndex * 1000f + lazyListState.firstVisibleItemScrollOffset 
                                        }
                                        .collect { offset ->
                                            // Chỉ update khi thay đổi đáng kể để giảm recomposition
                                            val newOffset = (offset / 100f).toInt() * 100f
                                            if (kotlin.math.abs(0f - newOffset) > 50f) {
                                                // currentScrollOffset = newOffset // Đã xóa để tránh lag
                                            }
                                        }
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
                onDismiss = { showSearchScreen = false },
                onShowFilteredResults = { showFilteredCitiesScreen = true },
                viewModel = viewModel,
                temperatureUnit = temperatureUnit,
                windSpeedUnit = windSpeedUnit
            )
        }
        
        // Hiển thị màn hình kết quả lọc khi showFilteredCitiesScreen = true
        if (showFilteredCitiesScreen) {
            FilteredCitiesScreen(
                onBackClick = { 
                    viewModel.resetFilterResults() // Reset kết quả lọc
                    showFilteredCitiesScreen = false 
                },
                onDismiss = { 
                    viewModel.resetFilterResults() // Reset kết quả lọc
                    showFilteredCitiesScreen = false 
                },
                viewModel = viewModel,
                temperatureUnit = temperatureUnit,
                windSpeedUnit = windSpeedUnit,
                isNightTime = isNightTime
            )
        }

        // Hiển thị màn hình quản lý thành phố
        if (showCityManagementScreen) {
            CityManagementScreen(
                onBackClick = { showCityManagementScreen = false },
                viewModel = viewModel
            )
        }

        // Sidebar
        SidebarOverlay(
            isVisible = showSidebar,
            onDismiss = { showSidebar = false },
            onSettingsClick = {
                showSidebar = false
                val settingsIntent = Intent(context, SettingsActivity::class.java)
                context.startActivity(settingsIntent)
            },
            onCityManagementClick = {
                showSidebar = false
                showCityManagementScreen = true
            },
            onFilterClick = {
                showSidebar = false
                showSearchScreen = true
            }
        )

        // Màn hình chi tiết thời tiết ngày
        if (showDayDetailScreen && selectedDayDetail != null) {
            DayWeatherDetailScreen(
                dayDetail = selectedDayDetail!!,
                temperatureUnit = temperatureUnit,
                windSpeedUnit = windSpeedUnit,
                pressureUnit = pressureUnit,
                visibilityUnit = visibilityUnit,
                onBackClick = {
                    showDayDetailScreen = false
                    selectedDayDetail = null
                }
            )
        }

        // Màn hình radar đầy đủ
        if (showFullScreenRadar && cities.isNotEmpty()) {
            val currentCity = cities.getOrNull(pagerState.currentPage)
            currentCity?.let { city ->
                FullScreenWeatherRadar(
                    city = city,
                    onBackClick = { showFullScreenRadar = false }
                )
            }
        }
    }
}

// TopBar composable with animated city title
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    context: Context,
    onManageCitiesClick: () -> Unit,
    onMenuClick: () -> Unit,
    isNightTime: Boolean,
    currentCityName: String = "",
    scrollOffset: Float = 0f,
    cityCount: Int,
    currentIndex: Int
) {
    val iconColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val titleColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val titleSize = 20f
    TopAppBar(
        title = { 
            if (currentCityName.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = cleanLocationName(currentCityName),
                        color = titleColor,
                        fontSize = titleSize.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp)
                    )
                    CityTabIndicator(
                        cityCount = cityCount,
                        currentIndex = currentIndex,
                        isNightTime = isNightTime
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = iconColor
                )
            }
        },
        actions = { }, // Bỏ nút tìm kiếm
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent // Trong suốt để hiện gradient phía sau
        )
    )
}

// City Management Screen Composable - Simple version with search button
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityManagementScreen(
    onBackClick: () -> Unit,
    viewModel: WeatherViewModel
) {
    var cities by remember { mutableStateOf(viewModel.citiesList) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Drag & Drop states
    var isDragging by remember { mutableStateOf(false) }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableStateOf(0f) }
    var lastSwapIndex by remember { mutableStateOf<Int?>(null) }
    
    // Hàm tính toán vị trí mới khi thả item
    fun calculateNewIndex(): Int? {
        val from = draggedItemIndex ?: return null
        val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == from } ?: return null
        val itemHeight = itemInfo.size
        val offsetY = itemInfo.offset + draggedOffset
        val to = listState.layoutInfo.visibleItemsInfo.minByOrNull { info ->
            val center = info.offset + info.size / 2
            kotlin.math.abs(center - (offsetY + itemHeight / 2))
        }?.index ?: from
        return to
    }
    
    // Theo dõi các thay đổi trong danh sách thành phố từ ViewModel
    LaunchedEffect(viewModel.citiesList) {
        cities = viewModel.citiesList
    }
    // Khôi phục lại dark mode cho quản lý thành phố
    val isNightTime = remember {
        val currentHour = java.time.LocalTime.now().hour
        currentHour < 6 || currentHour >= 18
    }
    val backgroundColors = if (isNightTime) {
        listOf(Color(0xFF475985), Color(0xFF5F4064))
    } else {
        listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
    }
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = backgroundColors
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopAppBar(
                title = { Text("Quản lý thành phố", color = primaryTextColor) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = "Quay lại",
                            tint = primaryTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchOverlay = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add),
                            contentDescription = "Thêm thành phố mới",
                            tint = primaryTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            // Instructions text
            Text(
                text = "Quản lý danh sách thành phố của bạn. Giữ và kéo để sắp xếp lại thứ tự.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 14.sp,
                color = secondaryTextColor,
                textAlign = TextAlign.Center
            )
            // City list with drag & drop
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                    offset.y.toInt() in item.offset..(item.offset + item.size)
                                }?.let { item ->
                                    isDragging = true
                                    draggedItemIndex = item.index
                                    lastSwapIndex = item.index
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consumeAllChanges()
                                draggedOffset += dragAmount.y
                                isDragging = true
                                val from = draggedItemIndex
                                if (from != null) {
                                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == from }
                                    if (itemInfo != null) {
                                        val itemHeight = itemInfo.size
                                        val currentTop = itemInfo.offset + draggedOffset
                                        // Kéo xuống
                                        val nextItem = listState.layoutInfo.visibleItemsInfo.find { it.index == from + 1 }
                                        if (draggedOffset > 0 && nextItem != null) {
                                            val nextCenter = nextItem.offset + nextItem.size / 2
                                            if (currentTop + itemHeight > nextCenter) {
                                                // Swap với item dưới
                                                val mutable = cities.toMutableList()
                                                val item = mutable.removeAt(from)
                                                mutable.add(from + 1, item)
                                                cities = mutable
                                                draggedItemIndex = from + 1
                                                draggedOffset -= nextItem.size
                                            }
                                        }
                                        // Kéo lên
                                        val prevItem = listState.layoutInfo.visibleItemsInfo.find { it.index == from - 1 }
                                        if (draggedOffset < 0 && prevItem != null) {
                                            val prevCenter = prevItem.offset + prevItem.size / 2
                                            if (currentTop < prevCenter) {
                                                // Swap với item trên
                                                val mutable = cities.toMutableList()
                                                val item = mutable.removeAt(from)
                                                mutable.add(from - 1, item)
                                                cities = mutable
                                                draggedItemIndex = from - 1
                                                draggedOffset += prevItem.size
                                            }
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                if (cities != viewModel.citiesList) {
                                    viewModel.reorderCities(cities)
                                }
                                isDragging = false
                                draggedItemIndex = null
                                draggedOffset = 0f
                                lastSwapIndex = null
                            },
                            onDragCancel = {
                                isDragging = false
                                draggedItemIndex = null
                                draggedOffset = 0f
                                lastSwapIndex = null
                            }
                        )
                    }
            ) {
                itemsIndexed(
                    items = cities,
                    key = { _, item -> item.name }
                ) { index, city ->
                    val isDragged = index == draggedItemIndex
                    val elevation = if (isDragged) 8.dp else 0.dp
                    SimpleCityItem(
                        city = city,
                        weatherData = viewModel.weatherDataMap[city.name],
                        onDelete = {
                            scope.launch {
                                viewModel.deleteCity(city.name)
                                cities = viewModel.citiesList
                            }
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                if (isDragged) {
                                    translationY = draggedOffset
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                }
                            }
                            .zIndex(if (isDragged) 1f else 0f),
                        isNightTime = isNightTime,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // Show empty state if no cities
                if (cities.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Không có thành phố nào. Thêm thành phố từ tính năng tìm kiếm.",
                                textAlign = TextAlign.Center,
                                color = secondaryTextColor,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
        // SearchOverlay
        if (showSearchOverlay) {
            SearchOverlay(
                onBackClick = { showSearchOverlay = false },
                onFilterClick = { },
                onDismiss = {
                    viewModel.clearSearch()
                    showSearchOverlay = false
                },
                viewModel = viewModel
            )
        }
    }
}

// Simplified City Item Composable for management screen
@Composable
fun SimpleCityItem(
    city: City,
    weatherData: WeatherDataState?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isNightTime: Boolean = false,
    primaryTextColor: Color = Color(0xFF5372dc),
    secondaryTextColor: Color = Color(0xFF5372dc).copy(alpha = 0.7f)
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isNightTime) Color(0xFF332B41).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
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
                    text = city.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                if (!city.country.isNullOrBlank()) {
                    Text(
                        text = city.country,
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_delete),
                        contentDescription = "Xóa",
                        tint = primaryTextColor
                    )
                }
            }
        }
    }
}

// Sidebar Overlay Component
@Composable
fun SidebarOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSettingsClick: () -> Unit,
    onCityManagementClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    val isNightTime = remember {
        val currentHour = java.time.LocalTime.now().hour
        currentHour < 6 || currentHour >= 18
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .clickable(enabled = false) { },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNightTime) Color(0xFF1A202C) else Color.White
                    ),
                    shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        SidebarMenuItem(
                            icon = Icons.Default.LocationCity,
                            title = "Quản lý thành phố",
                            subtitle = "",
                            onClick = onCityManagementClick,
                            isNightTime = isNightTime
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SidebarMenuItem(
                            icon = Icons.Default.FilterList,
                            title = "Lọc thành phố",
                            subtitle = "",
                            onClick = onFilterClick,
                            isNightTime = isNightTime
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SidebarMenuItem(
                            icon = Icons.Default.Settings,
                            title = "Cài đặt",
                            subtitle = "",
                            onClick = onSettingsClick,
                            isNightTime = isNightTime
                        )
                    }
                }
            }
        }
    }
}

// Sidebar Menu Item Component
@Composable
fun SidebarMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isNightTime: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isNightTime) Color(0xFF2D3748).copy(alpha = 0.8f) else Color(0xFFF8F9FA)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(48.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNightTime) Color(0xFF4A5568) else Color(0xFF5372dc).copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isNightTime) Color.White else Color(0xFF5372dc),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = title,
                color = if (isNightTime) Color.White else Color(0xFF2D3748),
                    fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = "Mở",
                tint = if (isNightTime) Color.White.copy(alpha = 0.7f) else Color(0xFF718096),
                modifier = Modifier
                    .size(16.dp)
                    .rotate(180f)
            )
        }
    }
}

// Data class cho thông tin chi tiết một ngày
data class DayWeatherDetail(
    val date: String,
    val formattedDate: String,
    val maxTemp: Double,
    val minTemp: Double,
    val weatherCode: Int,
    val precipitation: Int,
    val humidity: Int?,
    val windSpeed: Double?,
    val pressure: Double?,
    val uvIndex: Double?,
    val visibility: Double?,
    val weatherDescription: String,
    val sunrise: String = "6:00",
    val sunset: String = "18:00"
)

// Màn hình chi tiết thời tiết hàng ngày
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayWeatherDetailScreen(
    dayDetail: DayWeatherDetail,
    temperatureUnit: UnitConverter.TemperatureUnit,
    windSpeedUnit: UnitConverter.WindSpeedUnit,
    pressureUnit: UnitConverter.PressureUnit,
    visibilityUnit: UnitConverter.VisibilityUnit,
    onBackClick: () -> Unit
) {
    val tempSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"
    val maxTemp = UnitConverter.convertTemperature(dayDetail.maxTemp, temperatureUnit).toInt()
    val minTemp = UnitConverter.convertTemperature(dayDetail.minTemp, temperatureUnit).toInt()
    
    // Kiểm tra thời gian để áp dụng theme ban đêm
    val isNightTime = remember {
        val currentHour = java.time.LocalTime.now().hour
        currentHour < 6 || currentHour >= 18 // Đêm từ 18:00 đến 6:00
    }
    
    // Màu nền và màu chữ theo thời gian
    val backgroundColors = if (isNightTime) {
        listOf(Color(0xFF475985), Color(0xFF5F4064)) // Gradient tối cho ban đêm
    } else {
        listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6)) // Gradient sáng cho ban ngày
    }
    
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
    val cardBackgroundColor = if (isNightTime) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.7f)
    val itemBackgroundColor = if (isNightTime) Color.Black.copy(alpha = 0.2f) else Color(0xFF5372dc).copy(alpha = 0.1f)
    
    Dialog(onDismissRequest = onBackClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.9f)
                .background(
                    brush = Brush.verticalGradient(colors = backgroundColors),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            // Header với nút quay lại
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Quay lại",
                        tint = primaryTextColor
                    )
                }
                Text(
                    text = dayDetail.formattedDate,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Thông tin chính
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = cardBackgroundColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Icon thời tiết lớn
                            Image(
                                painter = painterResource(id = getWeatherIcon(dayDetail.weatherCode)),
                                contentDescription = dayDetail.weatherDescription,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Mô tả thời tiết
                            Text(
                                text = dayDetail.weatherDescription,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Nhiệt độ cao/thấp
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Cao nhất",
                                        fontSize = 14.sp,
                                        color = secondaryTextColor
                                    )
                                    Text(
                                        text = "$maxTemp$tempSymbol",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Thấp nhất",
                                        fontSize = 14.sp,
                                        color = secondaryTextColor
                                    )
                                    Text(
                                        text = "$minTemp$tempSymbol",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Thông tin chi tiết
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = cardBackgroundColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Chi tiết",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Grid các thông tin chi tiết
                            val detailItems = mutableListOf<Triple<Int, String, String>>().apply {
                                add(Triple(R.drawable.rain_dropp, "Khả năng mưa", "${dayDetail.precipitation}%"))
                                dayDetail.humidity?.let { add(Triple(R.drawable.humidity2, "Độ ẩm", "${it}%")) }
                                dayDetail.windSpeed?.let { add(Triple(R.drawable.ese_wind, "Tốc độ gió", UnitConverter.convertWindSpeed(it, windSpeedUnit))) }
                                dayDetail.pressure?.let { add(Triple(R.drawable.air_pressure, "Áp suất", UnitConverter.convertPressure(it, pressureUnit))) }
                                dayDetail.uvIndex?.let { add(Triple(R.drawable.uv, "Chỉ số UV", "${it.toInt()}")) }
                                dayDetail.visibility?.let { add(Triple(R.drawable.visibility, "Tầm nhìn", UnitConverter.convertVisibility(it, visibilityUnit))) }
                            }

                            detailItems.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowItems.forEach { (iconId, label, value) ->
                                        // Màu icon - chỉ sử dụng màu xanh, ban đêm nhạt hơn
                                        val iconColor = if (isNightTime) {
                                            Color(0xFF89A7E0) // Xanh nhạt cho ban đêm
                                        } else {
                                            Color(0xFF5372DC) // Xanh gốc cho ban ngày
                                        }
                                        
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    itemBackgroundColor,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Image(
                                                painter = painterResource(id = iconId),
                                                contentDescription = label,
                                                modifier = Modifier.size(24.dp),
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconColor)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                color = secondaryTextColor,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = value,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = primaryTextColor
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
                }

                // Thông tin mặt trời
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = cardBackgroundColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Mặt trời",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // Mặt trời mọc
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        painter = painterResource(id = R.drawable.sunny),
                                        contentDescription = "Mặt trời mọc",
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "Mặt trời mọc",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
                                    )
                                    Text(
                                        text = dayDetail.sunrise,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor
                                    )
                                }

                                // Mặt trời lặn
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        painter = painterResource(id = R.drawable.cloudy),
                                        contentDescription = "Mặt trời lặn",
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "Mặt trời lặn",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
                                    )
                                    Text(
                                        text = dayDetail.sunset,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor
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

// Hàm helper để tạo DayWeatherDetail từ dữ liệu có sẵn
fun createDayWeatherDetail(
    index: Int,
    weatherData: WeatherDataState,
    viewModel: WeatherViewModel
): DayWeatherDetail? {
    val dailyForecast = viewModel.getDailyForecast(viewModel.currentCity, 7)
    if (index >= dailyForecast.size) return null
    
    val (time, temps, weatherCode) = dailyForecast[index]
    val date = LocalDate.parse(time, DateTimeFormatter.ISO_LOCAL_DATE)
    val formattedDate = when (date) {
        LocalDate.now() -> "Hôm nay"
        LocalDate.now().plusDays(1) -> "Ngày mai" 
        else -> {
            val dayOfWeek = when (date.dayOfWeek.value) {
                1 -> "Thứ Hai"
                2 -> "Thứ Ba"
                3 -> "Thứ Tư"
                4 -> "Thứ Năm"
                5 -> "Thứ Sáu"
                6 -> "Thứ Bảy"
                7 -> "Chủ Nhật"
                else -> ""
            }
            "$dayOfWeek, ${date.dayOfMonth}/${date.monthValue}"
        }
    }
    
    // Lấy dữ liệu chi tiết từ weatherData
    val precipitation = weatherData.dailyPrecipitationList.getOrNull(index)?.toInt() ?: 0
    
    // Sử dụng dữ liệu trung bình trong ngày cho các thông số khác (nếu có)
    val humidity = weatherData.humidityList.getOrNull(index * 8)?.toInt() // Ước tính theo giờ
    val windSpeed = weatherData.windSpeedList.getOrNull(index * 8) // Ước tính theo giờ
    val pressure = weatherData.pressureList.getOrNull(index * 8) // Ước tính theo giờ
    val uvIndex = weatherData.uvList.getOrNull(index * 8) // Ước tính theo giờ
    val visibility = weatherData.visibilityList.getOrNull(index * 8) // Ước tính theo giờ
    
    return DayWeatherDetail(
        date = time,
        formattedDate = formattedDate,
        maxTemp = temps.first,
        minTemp = temps.second,
        weatherCode = weatherCode,
        precipitation = precipitation,
        humidity = humidity,
        windSpeed = windSpeed,
        pressure = pressure,
        uvIndex = uvIndex,
        visibility = visibility,
        weatherDescription = getWeatherDescription(weatherCode),
        sunrise = weatherData.dailyTimeList.getOrNull(index)?.let { date ->
            weatherData.dailySunriseList.getOrNull(index)?.let { formatTime(it) } ?: "6:00"
        } ?: "6:00",
        sunset = weatherData.dailyTimeList.getOrNull(index)?.let { date ->
            weatherData.dailySunsetList.getOrNull(index)?.let { formatTime(it) } ?: "18:00"
        } ?: "18:00"
    )
}

@Preview(showBackground = true, heightDp = 800)
@Composable
fun WeatherMainScreenPreview() {
    // Mock data for preview
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header với thành phố và nhiệt độ hiện tại
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hà Nội",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5372dc),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "25°",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5372dc)
                        )
                        Image(
                            painter = painterResource(id = R.drawable.sunny),
                            contentDescription = "Weather icon",
                            modifier = Modifier
                                .size(80.dp)
                                .padding(start = 16.dp)
                        )
                    }
                    
                    Text(
                        text = "Nắng đẹp",
                        fontSize = 18.sp,
                        color = Color(0xFF5372dc).copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    
                    Text(
                        text = "H:28° L:22°",
                        fontSize = 16.sp,
                        color = Color(0xFF5372dc).copy(alpha = 0.6f)
                    )
                }
            }
            
            // Dự báo theo giờ
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Dự báo theo giờ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5372dc),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(5) { index ->
                                val times = listOf("Bây giờ", "15:00", "16:00", "17:00", "18:00")
                                val temps = listOf("25°", "26°", "24°", "23°", "22°")
                                val icons = listOf(
                                    R.drawable.sunny,
                                    R.drawable.cloudy_with_sun,
                                    R.drawable.cloudy,
                                    R.drawable.rainingg,
                                    R.drawable.cloudy
                                )
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text(
                                        text = times[index],
                                        fontSize = 12.sp,
                                        color = Color(0xFF5372dc).copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Image(
                                        painter = painterResource(id = icons[index]),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = temps[index],
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF5372dc)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Dự báo 7 ngày
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Dự báo 7 ngày",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5372dc),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        val days = listOf(
                            Triple("Hôm nay", R.drawable.sunny, "28° / 22°"),
                            Triple("Thứ 2", R.drawable.cloudy_with_sun, "26° / 20°"),
                            Triple("Thứ 3", R.drawable.rainingg, "23° / 18°"),
                            Triple("Thứ 4", R.drawable.cloudy, "24° / 19°"),
                            Triple("Thứ 5", R.drawable.sunny, "27° / 21°")
                        )
                        
                        days.forEach { (day, icon, temp) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 14.sp,
                                    color = Color(0xFF5372dc),
                                    modifier = Modifier.weight(1f)
                                )
                                Image(
                                    painter = painterResource(id = icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = temp,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF5372dc),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
            
            // Thông tin chi tiết
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Chi tiết",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5372dc),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        val details = listOf(
                            Triple(R.drawable.humidity2, "Độ ẩm", "65%"),
                            Triple(R.drawable.ese_wind, "Tốc độ gió", "15 km/h"),
                            Triple(R.drawable.air_pressure, "Áp suất", "1013 hPa"),
                            Triple(R.drawable.uv, "Chỉ số UV", "6")
                        )
                        
                        details.chunked(2).forEach { rowItems ->
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
                                            .background(
                                                Color(0xFF5372dc).copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(id = iconId),
                                            contentDescription = label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = Color(0xFF5372dc).copy(alpha = 0.8f),
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = value,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5372dc)
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
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherHeaderPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hà Nội",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5372dc),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "25°",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5372dc)
            )
            Image(
                painter = painterResource(id = R.drawable.sunny),
                contentDescription = "Weather icon",
                modifier = Modifier
                    .size(80.dp)
                    .padding(start = 16.dp)
            )
        }
        
        Text(
            text = "Nắng đẹp",
            fontSize = 18.sp,
            color = Color(0xFF5372dc).copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Text(
            text = "H:28° L:22°",
            fontSize = 16.sp,
            color = Color(0xFF5372dc).copy(alpha = 0.6f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HourlyForecastCardPreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Dự báo theo giờ",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5372dc),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(5) { index ->
                    val times = listOf("Bây giờ", "15:00", "16:00", "17:00", "18:00")
                    val temps = listOf("25°", "26°", "24°", "23°", "22°")
                    val icons = listOf(
                        R.drawable.sunny,
                        R.drawable.cloudy_with_sun,
                        R.drawable.cloudy,
                        R.drawable.rainingg,
                        R.drawable.cloudy
                    )
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = times[index],
                            fontSize = 12.sp,
                            color = Color(0xFF5372dc).copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            painter = painterResource(id = icons[index]),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = temps[index],
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5372dc)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherDetailCardPreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Chi tiết",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5372dc),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            val details = listOf(
                Triple(R.drawable.humidity2, "Độ ẩm", "65%"),
                Triple(R.drawable.ese_wind, "Tốc độ gió", "15 km/h"),
                Triple(R.drawable.air_pressure, "Áp suất", "1013 hPa"),
                Triple(R.drawable.uv, "Chỉ số UV", "6")
            )
            
            details.chunked(2).forEach { rowItems ->
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
                                .background(
                                    Color(0xFF5372dc).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = iconId),
                                contentDescription = label,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = Color(0xFF5372dc).copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = value,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5372dc)
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
}

// Thêm composable mới ở cuối file
@Composable
fun CityTabIndicator(cityCount: Int, currentIndex: Int, isNightTime: Boolean) {
    if (cityCount > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, start = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until cityCount) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (currentIndex == i) Color.White else Color.White.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
                if (i < cityCount - 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}


