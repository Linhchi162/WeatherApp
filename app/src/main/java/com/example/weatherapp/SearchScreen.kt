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

@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onDismiss: () -> Unit,
    onShowFilteredResults: () -> Unit,
    viewModel: WeatherViewModel
) {
    var temperatureRange by remember { mutableStateOf(-20f..50f) }
    var windSpeedRange by remember { mutableStateOf(0f..100f) }
    var humidityRange by remember { mutableStateOf(0f..100f) }
    var weatherState by remember { mutableStateOf("Tất cả") }
    var showWeatherDropdown by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf("Việt Nam") }
    var showCountryDropdown by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Biến state riêng cho phần tìm kiếm quốc gia trong màn hình lọc
    var filterCountryQuery by remember { mutableStateOf("") }
    var countrySuggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var isSearchingCountry by remember { mutableStateOf(false) }
    var countrySearchError by remember { mutableStateOf<String?>(null) }
    var searchCountryJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Danh sách mặc định làm dự phòng khi API lỗi
    val fallbackCountries = listOf(
        PlaceSuggestion("Việt Nam", null, "Việt Nam", null, null),
        PlaceSuggestion("Hoa Kỳ", null, "Hoa Kỳ", null, null),
        PlaceSuggestion("Mỹ", null, "Hoa Kỳ", null, null), // Thêm cách gọi phổ biến
        PlaceSuggestion("Nhật Bản", null, "Nhật Bản", null, null),
        PlaceSuggestion("Hàn Quốc", null, "Hàn Quốc", null, null),
        PlaceSuggestion("Trung Quốc", null, "Trung Quốc", null, null),
        PlaceSuggestion("Anh", null, "Anh", null, null),
        PlaceSuggestion("Pháp", null, "Pháp", null, null),
        PlaceSuggestion("Đức", null, "Đức", null, null),
        PlaceSuggestion("Úc", null, "Úc", null, null),
        PlaceSuggestion("Nga", null, "Nga", null, null),
        PlaceSuggestion("Canada", null, "Canada", null, null),
        PlaceSuggestion("Ý", null, "Ý", null, null),
        PlaceSuggestion("Thái Lan", null, "Thái Lan", null, null)
    )
    
    // Tìm kiếm trong danh sách dự phòng
    val localFilteredCountries = remember(filterCountryQuery) {
        val cleanQuery = filterCountryQuery.trim().lowercase()
        
        Log.d("SearchScreen", "Tìm kiếm: '$cleanQuery' trong ${fallbackCountries.size} quốc gia")
        
        if (cleanQuery.isBlank()) {
            emptyList()
        } else {
            // Hiển thị kết quả ngay cả khi chỉ có 1 ký tự
            fallbackCountries.filter { suggestion -> 
                val formattedNameMatch = suggestion.formattedName.lowercase().contains(cleanQuery) 
                val countryMatch = suggestion.country?.lowercase()?.contains(cleanQuery) == true
                
                Log.d("SearchScreen", "Kiểm tra: '${suggestion.formattedName}' - formattedMatch: $formattedNameMatch, countryMatch: $countryMatch")
                
                formattedNameMatch || countryMatch
            }
        }
    }
    
    val weatherStates = listOf("Tất cả", "Nắng", "Mưa", "Nhiều mây", "Sương mù", "Tuyết")

    // Hàm tìm kiếm quốc gia độc lập, không sử dụng state của ViewModel
    fun searchCountry(query: String) {
        val cleanQuery = query.trim()
        Log.d("SearchScreen", "searchCountry gọi với query: '$cleanQuery'")
        
        // Hiển thị kết quả tìm kiếm local ngay lập tức, bất kể độ dài
        countrySuggestions = localFilteredCountries
        
        // Chỉ gọi API nếu query đủ dài
        if (cleanQuery.length < 2) {
            isSearchingCountry = false
            countrySearchError = null
            return
        }
        
        isSearchingCountry = true
        countrySearchError = null
        
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
                        nameStartsWith = cleanQuery
                    )
                }
                
                // Xử lý kết quả trả về từ GeoNames
                val apiSuggestions = response.geonames?.mapNotNull { geoCity ->
                    // Chỉ lấy các kết quả là quốc gia (fcode="PCLI")
                    if (geoCity.fcode == "PCLI") {
                        PlaceSuggestion(
                            formattedName = geoCity.name,
                            city = null,
                            country = geoCity.countryName,
                            latitude = geoCity.lat.toDoubleOrNull(),
                            longitude = geoCity.lng.toDoubleOrNull()
                        )
                    } else null
                } ?: emptyList()
                
                // Kết hợp kết quả từ API với danh sách local
                val combinedResults = (apiSuggestions + localFilteredCountries)
                    .distinctBy { it.formattedName } // Loại bỏ trùng lặp
                
                // Cập nhật state local
                countrySuggestions = combinedResults
                countrySearchError = if (combinedResults.isEmpty() && query.isNotEmpty()) {
                    "Không tìm thấy quốc gia phù hợp"
                } else null
                
            } catch (e: Exception) {
                Log.e("SearchScreen", "Lỗi tìm kiếm quốc gia: ${e.message}", e)
                // Vẫn giữ lại kết quả local nếu API lỗi
                countrySuggestions = localFilteredCountries
                countrySearchError = if (localFilteredCountries.isEmpty()) {
                    "Lỗi khi tìm kiếm API: ${e.message}"
                } else null
            } finally {
                isSearchingCountry = false
            }
        }
    }

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
                    text = "Lọc theo điều kiện thời tiết",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5372dc)
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Thanh tìm kiếm quốc gia - sử dụng API độc lập
            Text(
                text = "Quốc gia",
                fontSize = 16.sp,
                color = Color(0xFF5372dc),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            OutlinedTextField(
                value = if (filterCountryQuery.isNotEmpty()) filterCountryQuery else selectedCountry,
                onValueChange = {
                    selectedCountry = ""
                    filterCountryQuery = it
                    searchCountry(it) // Gọi hàm tìm kiếm quốc gia độc lập
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { Text("Nhập tên quốc gia...") },
                leadingIcon = {
                    if (selectedCountry.isNotEmpty() && filterCountryQuery.isEmpty()) {
                        // Hiển thị badge khi đã chọn quốc gia
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFF5372dc).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = selectedCountry,
                                fontSize = 12.sp,
                                color = Color(0xFF5372dc),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_search),
                            contentDescription = "Search",
                            tint = Color(0xFF5372dc)
                        )
                    }
                },
                trailingIcon = {
                    if (filterCountryQuery.isNotEmpty() || selectedCountry.isNotEmpty()) {
                        IconButton(onClick = {
                            filterCountryQuery = ""
                            selectedCountry = "Việt Nam"
                            countrySuggestions = emptyList()
                        }) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "Clear",
                                tint = Color(0xFF5372dc)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF5372dc),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF5372dc),
                    cursorColor = Color(0xFF5372dc)
                ),
                shape = RoundedCornerShape(15.dp)
            )
            
            // Hiển thị danh sách gợi ý quốc gia
            val showCountrySuggest = filterCountryQuery.isNotEmpty()
            Box(Modifier.zIndex(10f)) {
                if (showCountrySuggest) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .background(Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                                .verticalScroll(scrollState)
                        ) {
                            if (isSearchingCountry) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF5372dc))
                                }
                            } else if (countrySearchError != null && localFilteredCountries.isEmpty()) {
                                Text(
                                    text = countrySearchError ?: "Lỗi không xác định",
                                    color = Color.Red,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else if (countrySuggestions.isEmpty() && filterCountryQuery.length >= 1) {
                                Text(
                                    text = "Không tìm thấy quốc gia phù hợp",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                // Hiển thị danh sách local nếu không có kết quả API
                                val displayList = if (countrySuggestions.isEmpty()) localFilteredCountries else countrySuggestions
                                if (displayList.isEmpty() && filterCountryQuery.isNotEmpty()) {
                                    Text(
                                        text = "Không tìm thấy quốc gia phù hợp",
                                        color = Color.Gray,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                } else {
                                    displayList.forEach { suggestion ->
                                        ListItem(
                                            headlineContent = { Text(suggestion.formattedName, color = Color(0xFF5372dc)) },
                                            modifier = Modifier.clickable {
                                                selectedCountry = suggestion.formattedName
                                                filterCountryQuery = ""
                                                countrySuggestions = emptyList()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bộ lọc nhiệt độ
            Text(
                text = "Nhiệt độ (${temperatureRange.start.toInt()}°C - ${temperatureRange.endInclusive.toInt()}°C)",
                fontSize = 16.sp,
                color = Color(0xFF5372dc),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RangeSlider(
                value = temperatureRange,
                onValueChange = { temperatureRange = it },
                valueRange = -20f..50f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF5372dc),
                    activeTrackColor = Color(0xFF5372dc),
                    inactiveTrackColor = Color.Gray
                )
            )

            // Bộ lọc tốc độ gió
            Text(
                text = "Tốc độ gió (${windSpeedRange.start.toInt()} km/h - ${windSpeedRange.endInclusive.toInt()} km/h)",
                fontSize = 16.sp,
                color = Color(0xFF5372dc),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RangeSlider(
                value = windSpeedRange,
                onValueChange = { windSpeedRange = it },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF5372dc),
                    activeTrackColor = Color(0xFF5372dc),
                    inactiveTrackColor = Color.Gray
                )
            )

            // Bộ lọc độ ẩm
            Text(
                text = "Độ ẩm (${humidityRange.start.toInt()}% - ${humidityRange.endInclusive.toInt()}%)",
                fontSize = 16.sp,
                color = Color(0xFF5372dc),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RangeSlider(
                value = humidityRange,
                onValueChange = { humidityRange = it },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF5372dc),
                    activeTrackColor = Color(0xFF5372dc),
                    inactiveTrackColor = Color.Gray
                )
            )

            // Bộ lọc trạng thái thời tiết
            Text(
                text = "Trạng thái thời tiết",
                fontSize = 16.sp,
                color = Color(0xFF5372dc),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box {
                OutlinedTextField(
                    value = weatherState,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showWeatherDropdown = true }) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                                contentDescription = "Dropdown",
                                tint = Color(0xFF5372dc)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF5372dc),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF5372dc),
                        cursorColor = Color(0xFF5372dc)
                    ),
                    shape = RoundedCornerShape(15.dp)
                )
                DropdownMenu(
                    expanded = showWeatherDropdown,
                    onDismissRequest = { showWeatherDropdown = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    weatherStates.forEach { state ->
                        DropdownMenuItem(
                            text = { Text(state, color = Color(0xFF5372dc)) },
                            onClick = {
                                weatherState = state
                                showWeatherDropdown = false
                            }
                        )
                    }
                }
            }

            // Nút áp dụng bộ lọc
            Button(
                onClick = {
                    // Hiện loading screen
                    isLoading = true
                    
                    // Cập nhật các giá trị lọc vào ViewModel
                    viewModel.updateFilters(
                        country = selectedCountry,
                        temperatureRange = temperatureRange,
                        windSpeedRange = windSpeedRange,
                        humidityRange = humidityRange,
                        weatherState = weatherState
                    )
                    
                    // Áp dụng bộ lọc và đóng dialog khi hoàn thành
                    viewModel.viewModelScope.launch {
                        viewModel.applyFilters()
                        // Tăng thời gian delay để đảm bảo lấy đủ dữ liệu từ API và fetch thời tiết
                        delay(3000) // 3 giây
                        isLoading = false
                        onDismiss() // Đóng dialog khi hoàn thành
                        onShowFilteredResults() // Hiển thị màn hình kết quả lọc
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5372dc),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text("Áp dụng bộ lọc", fontSize = 16.sp)
            }
        }
    }
}

//@Preview(showBackground = true, heightDp = 800, widthDp = 400)
//@Composable
//fun SearchScreenPreview() {
//    SearchScreen(
//        onBackClick = {},
//        onDismiss = {},
//        viewModel = WeatherViewModel()
//    )
//}