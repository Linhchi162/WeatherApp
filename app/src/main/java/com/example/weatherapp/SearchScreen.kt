package com.example.weatherapp


import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import androidx.compose.ui.zIndex
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalTime


@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onDismiss: () -> Unit,
    onShowFilteredResults: () -> Unit,
    viewModel: WeatherViewModel,
    temperatureUnit: UnitConverter.TemperatureUnit = UnitConverter.TemperatureUnit.CELSIUS
) {
    var cityName by remember { mutableStateOf("") }
    var temperatureRange by remember { mutableStateOf(-20f..50f) }
    var windSpeedRange by remember { mutableStateOf(0f..100f) }
    var humidityRange by remember { mutableStateOf(0f..100f) }
    var weatherState by remember { mutableStateOf("Tất cả") }
    var showWeatherDropdown by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf("") }
    var showCountryDropdown by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Biến state riêng cho phần tìm kiếm quốc gia trong màn hình lọc
    var filterCountryQuery by remember { mutableStateOf("") }
    var countrySuggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var isSearchingCountry by remember { mutableStateOf(false) }
    var countrySearchError by remember { mutableStateOf<String?>(null) }
    var searchCountryJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Sử dụng danh sách quốc gia từ API thay vì hardcode
    val apiCountries = viewModel.availableCountries.map { it.countryName }
    
    // Danh sách quốc gia mặc định (fallback khi API chưa load xong)
    val defaultCountries = listOf(
        "Việt Nam", 
        "Hoa Kỳ", 
        "Nhật Bản", 
        "Hàn Quốc", 
        "Trung Quốc",
        "Anh",
        "Pháp",
        "Đức",
        "Nga",
        "Úc",
        "Ấn Độ",
        "Canada",
        "Ý",
        "Tây Ban Nha",
        "Brazil",
        "Mexico",
        "Indonesia",
        "Malaysia",
        "Singapore",
        "Thái Lan"
    )
    
    // Sử dụng danh sách từ API nếu có, nếu không thì dùng danh sách mặc định
    val countriesToUse = if (apiCountries.isNotEmpty()) apiCountries else defaultCountries
    
    // Danh sách làm dự phòng khi API lỗi
    val localFilteredCountries = if (filterCountryQuery.isBlank()) {
        // Nếu truy vấn trống, hiển thị tất cả quốc gia
        countriesToUse.map { country -> 
            PlaceSuggestion(
                formattedName = country,
                city = null,
                country = country,
                latitude = null,
                longitude = null
            )
        }
    } else {
        // Nếu có truy vấn, lọc quốc gia phù hợp với truy vấn
        countriesToUse.filter { 
            it.lowercase().contains(filterCountryQuery.lowercase()) 
        }.map { country ->
            PlaceSuggestion(
                formattedName = country,
                city = null,
                country = country,
                latitude = null,
                longitude = null
            )
        }
    }
    
    val weatherStates = listOf("Tất cả", "Nắng", "Mưa", "Nhiều mây", "Sương mù", "Tuyết")

    // Hàm tìm kiếm quốc gia độc lập, không sử dụng state của ViewModel
    fun searchCountry(query: String) {
        val cleanQuery = query.trim()
        Log.d("SearchScreen", "searchCountry gọi với query: '$cleanQuery'")
        
        // Lọc danh sách local ngay lập tức (sử dụng countriesToUse thay vì defaultCountries)
        val filteredLocalCountries = countriesToUse.filter { 
            it.lowercase().contains(cleanQuery.lowercase()) 
        }.map { country ->
            PlaceSuggestion(
                formattedName = country,
                city = null,
                country = country,
                latitude = null,
                longitude = null
            )
        }
        
        // Hiển thị kết quả tìm kiếm local ngay lập tức
        countrySuggestions = filteredLocalCountries
        
        // Cập nhật thông báo lỗi nếu không tìm thấy kết quả local
        if (filteredLocalCountries.isEmpty() && cleanQuery.isNotEmpty()) {
            countrySearchError = "Không tìm thấy quốc gia phù hợp"
        } else {
            countrySearchError = null
        }
        
        // Chỉ gọi API nếu query đủ dài
        if (cleanQuery.length < 2) {
            isSearchingCountry = false
            return
        }
        
        isSearchingCountry = true
        
        // Hủy job tìm kiếm trước đó nếu đang chạy
        searchCountryJob?.cancel()
        
        searchCountryJob = viewModel.viewModelScope.launch {
            delay(300) // Debounce để giảm số lượng request
            try {
                // Gọi API GeoNames để tìm quốc gia
                val response = withContext(Dispatchers.IO) {
                    viewModel.geoNamesApi.getCitiesByCountry(
                        countryCode = "", // Để trống để tìm toàn cầu
                        featureClass = "A", // A là khu vực hành chính (qua featureClass)
                        maxRows = 10,
                        orderBy = "relevance", // Sắp xếp theo độ liên quan
                        username = RetrofitInstance.GEONAMES_USERNAME,
                        nameStartsWith = cleanQuery // Có thể không tìm được tên Tiếng Việt như "Ấn Độ"
                    )
                }
                
                // Xử lý kết quả trả về từ GeoNames
                val apiSuggestions = response.geonames?.mapNotNull { geoCity ->
                    // Chỉ lấy các kết quả là quốc gia (fcode="PCLI")
                    if (geoCity.fcode == "PCLI") {
                        PlaceSuggestion(
                            formattedName = geoCity.name, // Tên gọn gàng cho quốc gia
                            city = null,
                            country = geoCity.countryName,
                            latitude = geoCity.lat.toDoubleOrNull(),
                            longitude = geoCity.lng.toDoubleOrNull()
                        )
                    } else null
                } ?: emptyList()
                
                // Kết hợp kết quả từ API với danh sách local đã lọc
                val combinedResults = (apiSuggestions + filteredLocalCountries)
                    .distinctBy { it.formattedName } // Loại bỏ trùng lặp
                
                // Cập nhật state local
                countrySuggestions = combinedResults
                countrySearchError = if (combinedResults.isEmpty() && query.isNotEmpty()) {
                    "Không tìm thấy quốc gia phù hợp"
                } else null
                
            } catch (e: Exception) {
                Log.e("SearchScreen", "Lỗi tìm kiếm quốc gia: ${e.message}", e)
                // Vẫn giữ lại kết quả local nếu API lỗi
                if (filteredLocalCountries.isEmpty() && query.isNotEmpty()) {
                    countrySearchError = "Không tìm thấy quốc gia phù hợp"
                }
            } finally {
                isSearchingCountry = false
            }
        }
    }

    // Dark mode detection based on time
    val currentTime = LocalTime.now()
    val isDarkMode = currentTime.hour < 6 || currentTime.hour >= 18
    
    // Colors based on theme
    val backgroundColor = if (isDarkMode) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1A202C), Color(0xFF2D3748))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
        )
    }
    
    val textColor = if (isDarkMode) Color.White else Color(0xFF5372dc)
    val secondaryTextColor = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF5372dc)
    val iconTint = if (isDarkMode) Color.White else Color(0xFF5372dc)
    val cardBackgroundColor = if (isDarkMode) Color(0xFF2D3748) else Color.White
    val inputBackgroundColor = if (isDarkMode) Color(0xFF1A202C) else Color.White

    // Hiển thị màn hình loading nếu đang lọc
    if (isLoading) {
        Dialog(onDismissRequest = { /* Không cho dismiss khi đang loading */ }) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF5372dc))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Đang tìm kiếm thành phố...", color = Color(0xFF5372dc))
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.85f)
                .background(
                    brush = backgroundColor,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            // Tiêu đề và nút quay lại
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Lọc theo điều kiện thời tiết",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(40.dp))
            }

            // Content trong ScrollableColumn để tránh overflow
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Thanh tìm kiếm quốc gia
                    Column {
                        Text(
                            text = "Quốc gia",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = if (filterCountryQuery.isNotEmpty()) filterCountryQuery else selectedCountry,
                            onValueChange = {
                                selectedCountry = ""
                                filterCountryQuery = it
                                searchCountry(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { 
                                        Text(
                                    "Nhập tên quốc gia...", 
                                    fontSize = 14.sp,
                                    color = secondaryTextColor
                                ) 
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = textColor,
                                unfocusedBorderColor = secondaryTextColor.copy(alpha = 0.5f),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                cursorColor = textColor,
                                focusedContainerColor = inputBackgroundColor,
                                unfocusedContainerColor = inputBackgroundColor
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Country suggestions (if any)
                item {
                    val showCountrySuggest = filterCountryQuery.isNotEmpty()
                    if (showCountrySuggest) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(6.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (isSearchingCountry) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = textColor,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else {
                                    val displayList = if (countrySuggestions.isEmpty()) localFilteredCountries else countrySuggestions
                                    if (displayList.isEmpty()) {
                                        Text(
                                            text = "Không tìm thấy quốc gia phù hợp",
                                            color = secondaryTextColor,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    } else {
                                        displayList.forEach { suggestion ->
                                            ListItem(
                                                headlineContent = { 
                                                    Text(
                                                        text = suggestion.formattedName, 
                                                        color = textColor,
                                                        fontSize = 14.sp
                                                    ) 
                                                },
                                                modifier = Modifier
                                                    .clickable {
                                                        selectedCountry = suggestion.formattedName
                                                        filterCountryQuery = ""
                                                        countrySuggestions = emptyList()
                                                    }
                                                    .padding(horizontal = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Bộ lọc nhiệt độ
                    Column {
                        val tempSymbol = if (temperatureUnit == UnitConverter.TemperatureUnit.CELSIUS) "°C" else "°F"
                        Text(
                            text = "Nhiệt độ (${temperatureRange.start.toInt()}$tempSymbol - ${temperatureRange.endInclusive.toInt()}$tempSymbol)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        RangeSlider(
                            value = temperatureRange,
                            onValueChange = { temperatureRange = it },
                            valueRange = -20f..50f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = textColor,
                                activeTrackColor = textColor,
                                inactiveTrackColor = secondaryTextColor.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                item {
                    // Bộ lọc tốc độ gió
                    Column {
                        Text(
                            text = "Tốc độ gió (${windSpeedRange.start.toInt()} - ${windSpeedRange.endInclusive.toInt()} km/h)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        RangeSlider(
                            value = windSpeedRange,
                            onValueChange = { windSpeedRange = it },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = textColor,
                                activeTrackColor = textColor,
                                inactiveTrackColor = secondaryTextColor.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                item {
                    // Bộ lọc độ ẩm
                    Column {
                        Text(
                            text = "Độ ẩm (${humidityRange.start.toInt()}% - ${humidityRange.endInclusive.toInt()}%)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        RangeSlider(
                            value = humidityRange,
                            onValueChange = { humidityRange = it },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = textColor,
                                activeTrackColor = textColor,
                                inactiveTrackColor = secondaryTextColor.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                item {
                    // Bộ lọc trạng thái thời tiết
                    Column {
                        Text(
                            text = "Trạng thái thời tiết",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Box {
                            OutlinedTextField(
                                value = weatherState,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                placeholder = { Text("Tất cả", fontSize = 14.sp) },
                                trailingIcon = {
                                    IconButton(onClick = { showWeatherDropdown = true }) {
                                        Icon(
                                            painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                                            contentDescription = "Dropdown",
                                            tint = textColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = textColor,
                                    unfocusedBorderColor = secondaryTextColor.copy(alpha = 0.5f),
                                    focusedLabelColor = textColor,
                                    cursorColor = textColor
                                ),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )
                            DropdownMenu(
                                expanded = showWeatherDropdown,
                                onDismissRequest = { showWeatherDropdown = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(cardBackgroundColor, RoundedCornerShape(8.dp))
                            ) {
                                weatherStates.forEach { state ->
                                    DropdownMenuItem(
                                        text = { Text(state, color = textColor, fontSize = 14.sp) },
                                        onClick = {
                                            weatherState = state
                                            showWeatherDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Nút áp dụng bộ lọc - cố định ở dưới
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    isLoading = true
                    viewModel.updateFilters(
                        country = selectedCountry,
                        temperatureRange = temperatureRange,
                        windSpeedRange = windSpeedRange,
                        humidityRange = humidityRange,
                        weatherState = weatherState
                    )
                    viewModel.viewModelScope.launch {
                        viewModel.applyFilters()
                        delay(1000)
                        isLoading = false
                        onDismiss()
                        onShowFilteredResults()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkMode) Color(0xFF4A5568) else Color(0xFF5372dc),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                Text("Áp dụng bộ lọc", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SearchScreenPreview() {
    // Sử dụng các giá trị mặc định/mock cho preview
    Box(modifier = Modifier.fillMaxSize().background(Color.LightGray)) {
        Text("Search Screen Preview", modifier = Modifier.align(Alignment.Center))
    }
}


