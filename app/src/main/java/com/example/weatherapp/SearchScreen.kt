package com.example.weatherapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var cityName by remember { mutableStateOf("") }
    var temperatureRange by remember { mutableStateOf(0f..40f) }
    var windSpeedRange by remember { mutableStateOf(0f..50f) }
    var humidityRange by remember { mutableStateOf(0f..100f) }
    var weatherState by remember { mutableStateOf("Tất cả") }
    var showWeatherDropdown by remember { mutableStateOf(false) }

    val weatherStates = listOf("Tất cả", "Nắng", "Mưa", "Nhiều mây", "Sương mù", "Tuyết")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth() // Chiếm toàn bộ chiều rộng màn hình
                .fillMaxHeight(fraction = 0.8f) // Chiếm 80% chiều cao màn hình (4/5)
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
                Spacer(modifier = Modifier.width(48.dp)) // Để cân bằng giao diện
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
                valueRange = 0f..40f,
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
                valueRange = 0f..50f,
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
                onClick = { /* Chưa xử lý logic lọc */ },
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

@Preview(showBackground = true, heightDp = 800, widthDp = 400)
@Composable
fun SearchScreenPreview() {
    SearchScreen(
        onBackClick = {},
        onDismiss = {}
    )
}