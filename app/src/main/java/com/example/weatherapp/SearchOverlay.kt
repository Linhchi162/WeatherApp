package com.example.weatherapp

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.* // Chỉ cần import này, không cần import riêng getValue/setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.weatherapp.R // Import R

@Composable
fun SearchOverlay(
    onBackClick: () -> Unit, // Có thể dùng để đóng dialog hoặc xóa text
    onFilterClick: () -> Unit,
    onDismiss: () -> Unit, // Được gọi khi đóng dialog (click ngoài, back press)
    viewModel: WeatherViewModel
) {
    // Lấy các state từ ViewModel
    val searchQuery = viewModel.searchQuery
    val suggestions = viewModel.placeSuggestions
    val isSearching = viewModel.isSearching
    val searchError = viewModel.searchError

    // Dark mode detection
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
    val iconTint = if (isNightTime) Color.White else Color(0xFF5372dc)
    val borderColor = if (isNightTime) Color.White.copy(alpha = 0.5f) else Color(0xFF5372dc).copy(alpha = 0.5f)

    Dialog(onDismissRequest = {
        viewModel.clearSearch() // Reset trạng thái tìm kiếm
        onDismiss() // Gọi hàm dismiss gốc
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.8f)
                .background(
                    brush = Brush.verticalGradient(colors = backgroundColors),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(15.dp)
        ) {
            // --- Thanh tìm kiếm ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    placeholder = {
                        Text(
                            "Tìm kiếm thành phố...",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = secondaryTextColor
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.kinh_lup),
                            contentDescription = "Search",
                            modifier = Modifier.size(20.dp),
                            tint = iconTint
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.onSearchQueryChanged("")
                            }) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                    contentDescription = "Clear",
                                    tint = iconTint
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = borderColor,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = iconTint,
                        cursorColor = iconTint,
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
            }

            // --- Khu vực hiển thị kết quả, loading, lỗi ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Chiếm không gian còn lại
                contentAlignment = Alignment.TopCenter // Căn chỉnh nội dung
            ) {
                // Hiển thị loading indicator
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 16.dp),
                        color = Color(0xFF5372dc)
                    )
                }
                // Hiển thị lỗi
                else if (searchError != null) {
                    Text(
                        text = searchError,
                        color = Color.Red, // Hoặc màu lỗi khác
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                // Hiển thị danh sách kết quả
                else if (suggestions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(), // Lấp đầy Box
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent)
                                    .clickable {
                                        // Chỉ xử lý khi có đủ lat/lon
                                        if (suggestion.latitude != null && suggestion.longitude != null) {
                                            // Lấy tên thành phố (ưu tiên city, nếu null thì lấy phần đầu của formattedName)
                                            val cityName = suggestion.city
                                                ?: suggestion.formattedName.split(",")[0].trim()

                                            val newCity = City(
                                                name = cityName,
                                                latitude = suggestion.latitude,

                                                longitude = suggestion.longitude,
                                                country = suggestion.country // Thêm thông tin quốc gia

                                            )
                                            viewModel.addCity(newCity) // Thêm thành phố vào ViewModel
                                            // ViewModel sẽ tự gọi clearSearch trong addCity
                                            onDismiss() // Đóng dialog
                                        } else {
                                            // Có thể hiển thị thông báo lỗi nếu muốn
                                            Log.w("SearchOverlay", "Missing coordinates for suggestion: ${suggestion.formattedName}")
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp), // Thêm padding
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.gps),
                                    contentDescription = "Location",
                                    tint = if (isNightTime) Color.White else Color(0xFF5372dc).copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp) // Icon nhỏ hơn chút
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column { // Để hiển thị tên chính và phụ (nếu có)
                                    Text(
                                        text = suggestion.formattedName, // Hiển thị tên đầy đủ
                                        fontSize = 15.sp, // Cỡ chữ chính
                                        color = if (isNightTime) Color.White else Color(0xFF5372dc),
                                        fontWeight = FontWeight.Medium
                                    )
                                    // Có thể hiển thị thêm thông tin phụ như quốc gia
                                    /*
                                    if (suggestion.country != null) {
                                        Text(
                                            text = suggestion.country,
                                            fontSize = 12.sp,
                                            color = Color(0xFF5372dc).copy(alpha = 0.8f)
                                        )
                                    }
                                     */
                                }
                            }
                            Divider(color = if (isNightTime) Color.White.copy(alpha = 0.2f) else Color(0xFF5372dc).copy(alpha = 0.2f))
                        }
                    }
                }
                // Có thể hiển thị thông báo nếu không có kết quả và không có lỗi
                else if (searchQuery.length >= 3) { // Chỉ hiển thị khi đã gõ đủ dài
                    Text(
                        text = "Gõ để tìm kiếm địa điểm...", // Hoặc "Không tìm thấy kết quả." nếu muốn
                        color = Color(0xFF5372dc).copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } // End Box
        } // End Column
    } // End Dialog
}

// Preview function cần cập nhật để truyền ViewModel giả hoặc dùng Hilt/Koin Preview
// @Preview(...)
// @Composable
// fun SearchOverlayPreview() { ... }

@Preview(showBackground = true)
@Composable
fun SearchOverlayContentPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(15.dp)
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = "Hà Nội",
                onValueChange = {},
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                placeholder = {
                    Text(
                        "Tìm kiếm thành phố...",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.kinh_lup),
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF5372dc)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Clear",
                            tint = Color(0xFF5372dc)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF5372dc),
                    unfocusedBorderColor = Color(0xFF5372dc).copy(alpha = 0.5f),
                    focusedLabelColor = Color(0xFF5372dc),
                    cursorColor = Color(0xFF5372dc),
                    focusedTextColor = Color(0xFF5372dc),
                    unfocusedTextColor = Color(0xFF5372dc)
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(id = R.drawable.setting),
                    contentDescription = "Filter",
                    tint = Color(0xFF5372dc),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Sample search results
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(3) { index ->
                val cities = listOf("Hà Nội, Việt Nam", "TP. Hồ Chí Minh, Việt Nam", "Đà Nẵng, Việt Nam")
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_myplaces),
                        contentDescription = "Location",
                        tint = Color(0xFF5372dc).copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = cities[index],
                        fontSize = 15.sp,
                        color = Color(0xFF5372dc),
                        fontWeight = FontWeight.Medium
                    )
                }
                Divider(color = Color(0xFF5372dc).copy(alpha = 0.2f))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SearchLoadingStatePreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(15.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = "Searching...",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            placeholder = {
                Text("Tìm kiếm thành phố...", fontSize = 12.sp)
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.kinh_lup),
                    contentDescription = "Search",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF5372dc)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF5372dc),
                unfocusedBorderColor = Color(0xFF5372dc).copy(alpha = 0.5f),
                focusedLabelColor = Color(0xFF5372dc),
                cursorColor = Color(0xFF5372dc),
                focusedTextColor = Color(0xFF5372dc),
                unfocusedTextColor = Color(0xFF5372dc)
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Loading indicator
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFF5372dc)
            )
        }
    }
}
