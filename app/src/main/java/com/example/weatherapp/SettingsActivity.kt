package com.example.weatherapp
import androidx.compose.ui.window.Dialog
import android.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(onBackClick = { finish() })
        }
    }
}

@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    var startTime by remember { mutableStateOf("6:00") }
    var endTime by remember { mutableStateOf("22:00") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // State cho dialog đơn vị
    var showUnitDialog by remember { mutableStateOf(false) }
    var currentUnitType by remember { mutableStateOf("") }

    // State cho đơn vị đã chọn
    var temperatureUnit by remember { mutableStateOf("Độ C (°C)") }
    var windUnit by remember { mutableStateOf("Kilomet mỗi giờ (km/h)") }
    var pressureUnit by remember { mutableStateOf("Millimet thủy ngân (mmHg)") }
    var visibilityUnit by remember { mutableStateOf("Kilomet (km)") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // Nền sáng
            .padding(16.dp)
    ) {
        // Tiêu đề và nút quay lại
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu_zoom),
                    contentDescription = "Back",
                    tint = Color(0xFF5372dc)
                )
            }
            Text(
                text = "Cài đặt",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5372dc)
            )
            Spacer(modifier = Modifier.width(48.dp)) // Để cân bằng với IconButton
        }
        Text(
            text = "Cảnh báo thời tiết",
            fontSize = 14.sp,
            color = Color(0xFF5372dc),
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAF6FF), shape = RoundedCornerShape(15.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cảnh báo mưa",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF5372dc)
                )
                Switch(
                    checked = false, // Giá trị mặc định
                    onCheckedChange = { /* Xử lý bật/tắt */ },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF5372dc),
                        uncheckedThumbColor = Color(0xFF60616B),
                        checkedTrackColor = Color(0xFF5372dc).copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0f), // Nền trắng với độ trong suốt 0
                        disabledCheckedThumbColor = Color.Gray,
                        disabledUncheckedThumbColor = Color.Gray,
                        disabledCheckedTrackColor = Color.LightGray,
                        disabledUncheckedTrackColor = Color.LightGray,
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp)) // Thêm khoảng cách bên dưới tiêu đề

            // Thời gian bắt đầu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Từ",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF5372dc)
                )
                Text(
                    text = startTime,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF5372dc),
                    modifier = Modifier
                        .clickable { showStartTimePicker = true }
                        .padding(start = 8.dp) // Thêm khoảng cách bên trái
                )
            }

            // Thời gian kết thúc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Đến",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF5372dc)
                )
                Text(
                    text = endTime,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF5372dc),
                    modifier = Modifier
                        .clickable { showEndTimePicker = true }
                        .padding(start = 8.dp) // Thêm khoảng cách bên trái
                )
            }

            // Hiển thị dialog chọn giờ (vẫn giữ nguyên)
            if (showStartTimePicker) {
                TimePickerDialog(
                    onTimeSelected = { selectedTime ->
                        startTime = selectedTime
                    },
                    onDismiss = { showStartTimePicker = false }
                )
            }
            if (showEndTimePicker) {
                TimePickerDialog(
                    onTimeSelected = { selectedTime ->
                        endTime = selectedTime
                    },
                    onDismiss = { showEndTimePicker = false }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cảnh báo thời tiết khắc nghiệt",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF5372dc)
                )
                Switch(
                    checked = false, // Giá trị mặc định
                    onCheckedChange = { /* Xử lý bật/tắt */ },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF5372dc),
                        uncheckedThumbColor = Color(0xFF60616B),
                        checkedTrackColor = Color(0xFF5372dc).copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0f), // Nền trắng với độ trong suốt 0
                        disabledCheckedThumbColor = Color.Gray,
                        disabledUncheckedThumbColor = Color.Gray,
                        disabledCheckedTrackColor = Color.LightGray,
                        disabledUncheckedTrackColor = Color.LightGray,
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phần Dự báo thời tiết hàng ngày
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAF6FF), shape = RoundedCornerShape(15.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Dự báo thời tiết hàng ngày",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF5372dc)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nhận thông tin cập nhật về thời tiết định cho thành phố hiện tại của bạn hai lần một ngày, một lần cho ngày hôm nay và một lần khác cho ngày mai.",
                        fontSize = 13.sp,
                        color = Color(0xFF7380BB)
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Switch(
                    checked = true, // Giá trị mặc định
                    onCheckedChange = { /* Xử lý bật/tắt */ },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF5372dc),
                        uncheckedThumbColor = Color(0xFF60616B),
                        checkedTrackColor = Color(0xFF5372dc).copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0f), // Nền trắng với độ trong suốt 0
                        disabledCheckedThumbColor = Color.Gray,
                        disabledUncheckedThumbColor = Color.Gray,
                        disabledCheckedTrackColor = Color.LightGray,
                        disabledUncheckedTrackColor = Color.LightGray,
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Đơn vị",
            fontSize = 14.sp,
            color = Color(0xFF5372dc),
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        // Phần Đơn vị
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAF6FF), shape = RoundedCornerShape(15.dp))
                .padding(16.dp)
        ) {
            UnitItem(
                label = "Nhiệt độ",
                selectedValue = temperatureUnit,
                onClick = {
                    currentUnitType = "Nhiệt độ"
                    showUnitDialog = true
                }
            )
            UnitItem(
                label = "Gió",
                selectedValue = windUnit,
                onClick = {
                    currentUnitType = "Gió"
                    showUnitDialog = true
                }
            )
            UnitItem(
                label = "Áp suất không khí",
                selectedValue = pressureUnit,
                onClick = {
                    currentUnitType = "Áp suất không khí"
                    showUnitDialog = true
                }
            )
            UnitItem(
                label = "Tầm nhìn",
                selectedValue = visibilityUnit,
                onClick = {
                    currentUnitType = "Tầm nhìn"
                    showUnitDialog = true
                }
            )
        }

        // Dialog chọn đơn vị
        if (showUnitDialog) {
            val unitOptions = when (currentUnitType) {
                "Nhiệt độ" -> listOf("Độ C (°C)", "Độ F (°F)")
                "Gió" -> listOf("Thang đo Beaufort", "kilomet mỗi giờ (km/h)", "Mét mỗi giây (m/s)", "Feet mỗi giây (ft/s)", "Dặm mỗi giờ (mph)", "Hải lý mỗi giờ (hải lý)")
                "Áp suất không khí" -> listOf("Hectopascal (hPa)", "Millimet thủy ngân (mmHg)", "Inch thủy ngân (inHg)", "Millibar (mb)", "Pound trên inch vuông (psi)")
                "Tầm nhìn" -> listOf("Kilomet (km)", "Dặm (mi)", "Mét (m)", "Feet (ft)")
                else -> emptyList()
            }

            UnitSelectionDialog(
                title = currentUnitType,
                options = unitOptions,
                onUnitSelected = { selectedUnit ->
                    when (currentUnitType) {
                        "Nhiệt độ" -> temperatureUnit = selectedUnit
                        "Gió" -> windUnit = selectedUnit
                        "Áp suất không khí" -> pressureUnit = selectedUnit
                        "Tầm nhìn" -> visibilityUnit = selectedUnit
                    }
                    showUnitDialog = false
                },
                onDismiss = { showUnitDialog = false }
            )
        }
    }
}

@Composable
fun UnitItem(label: String, selectedValue: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(30.dp)
            .padding(5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF5372dc)
        )
        Text(
            text = selectedValue,
            fontSize = 14.sp,
            color = Color(0xFF7380BB)
        )
    }
}

@Composable
fun UnitSelectionDialog(
    title: String,
    options: List<String>,
    onUnitSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(Color.White, shape = RoundedCornerShape(20.dp))
                .padding(20.dp)
                .width(280.dp) // Điều chỉnh chiều rộng dialog
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5372dc),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) { // Thêm cuộn nếu nhiều item
                    items(options) { option ->
                        Text(
                            text = option,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUnitSelected(option) }
                                .padding(vertical = 12.dp),
                            fontSize = 16.sp,
                            color = Color(0xFF5372dc)
                        )
                        Divider(color = Color.LightGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
@Composable
fun TimePickerDialog(
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val hours = (0..23).map { String.format("%02d:%02d", it, 0) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(Color.White, shape = RoundedCornerShape(20.dp))
                .padding(20.dp)
                .width(200.dp)
                .heightIn(max = 300.dp)
        ) {
            LazyColumn {
                itemsIndexed(hours) { index, hour ->
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTimeSelected(hour)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hour,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF5372dc)
                            )
                        }
                        if (index < hours.lastIndex) { // Thêm Divider nếu không phải item cuối
                            Divider(color = Color(0xFFBFC5D5), thickness = 0.8.dp)
                        }
                    }
                }
            }
        }
    }
}
@Preview(showBackground = true, heightDp = 2000)
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(onBackClick = { /* No action needed for preview */ })
}