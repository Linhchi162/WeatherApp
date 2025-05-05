package com.example.weatherapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import java.util.Locale
import java.util.concurrent.TimeUnit

class SettingsActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("SettingsActivity", "Notification permission granted")
        } else {
            Log.w("SettingsActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(
                onBackClick = { finish() },
                onRequestNotificationPermission = { requestNotificationPermission() }
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("SettingsActivity", "Notification permission already granted")
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    val editor = preferences.edit()

    var startTime by remember { mutableStateOf(preferences.getString("rain_alert_start_time", "6:00") ?: "6:00") }
    var endTime by remember { mutableStateOf(preferences.getString("rain_alert_end_time", "22:00") ?: "22:00") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var showUnitDialog by remember { mutableStateOf(false) }
    var currentUnitType by remember { mutableStateOf("") }

    var temperatureUnit by remember {
        mutableStateOf(
            preferences.getString("temperature_unit", "Độ C (°C)") ?: "Độ C (°C)"
        )
    }
    var windUnit by remember {
        mutableStateOf(
            preferences.getString("wind_unit", "Kilomet mỗi giờ (km/h)") ?: "Kilomet mỗi giờ (km/h)"
        )
    }
    var pressureUnit by remember {
        mutableStateOf(
            preferences.getString("pressure_unit", "Millimet thủy ngân (mmHg)") ?: "Millimet thủy ngân (mmHg)"
        )
    }
    var visibilityUnit by remember {
        mutableStateOf(
            preferences.getString("visibility_unit", "Kilomet (km)") ?: "Kilomet (km)"
        )
    }

    var isDailyForecastEnabled by remember {
        mutableStateOf(
            preferences.getBoolean("daily_forecast_enabled", false)
        )
    }

    var isRainAlertEnabled by remember {
        mutableStateOf(
            preferences.getBoolean("rain_alert_enabled", false)
        )
    }

    var isSevereWeatherAlertEnabled by remember {
        mutableStateOf(
            preferences.getBoolean("severe_weather_alert_enabled", false)
        )
    }

    LaunchedEffect(Unit) {
        onRequestNotificationPermission()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_zoom),
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
            Spacer(modifier = Modifier.width(48.dp))
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
                    checked = isRainAlertEnabled,
                    onCheckedChange = { enabled ->
                        try {
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                onRequestNotificationPermission()
                                return@Switch
                            }
                            isRainAlertEnabled = enabled
                            editor.putBoolean("rain_alert_enabled", enabled).apply()
                            Log.d("SettingsActivity", "Rain alert enabled: $enabled")
                            val intent = Intent("com.example.weatherapp.RAIN_ALERT_UPDATED")
                            intent.putExtra("rain_alert_enabled", enabled)
                            intent.putExtra("rain_alert_start_time", startTime)
                            intent.putExtra("rain_alert_end_time", endTime)
                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                            if (enabled) {
                                val constraints = Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                    .build()
                                val immediateRainRequest = OneTimeWorkRequestBuilder<RainAlertWorker>()
                                    .setConstraints(constraints)
                                    .addTag("rain_alert_immediate")
                                    .build()
                                WorkManager.getInstance(context).enqueueUniqueWork(
                                    "rain_alert_immediate",
                                    ExistingWorkPolicy.REPLACE,
                                    immediateRainRequest
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsActivity", "Error updating rain_alert_enabled: ${e.message}", e)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF5372dc),
                        uncheckedThumbColor = Color(0xFF60616B),
                        checkedTrackColor = Color(0xFF5372dc).copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0f),
                        disabledCheckedThumbColor = Color.Gray,
                        disabledUncheckedThumbColor = Color.Gray,
                        disabledCheckedTrackColor = Color.LightGray,
                        disabledUncheckedTrackColor = Color.LightGray,
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

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
                        .padding(start = 8.dp)
                )
            }

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
                        .padding(start = 8.dp)
                )
            }

            if (showStartTimePicker) {
                TimePickerDialog(
                    onTimeSelected = { selectedTime ->
                        startTime = selectedTime
                        editor.putString("rain_alert_start_time", selectedTime).apply()
                        showStartTimePicker = false
                        val intent = Intent("com.example.weatherapp.RAIN_ALERT_UPDATED")
                        intent.putExtra("rain_alert_enabled", isRainAlertEnabled)
                        intent.putExtra("rain_alert_start_time", startTime)
                        intent.putExtra("rain_alert_end_time", endTime)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                    },
                    onDismiss = { showStartTimePicker = false }
                )
            }
            if (showEndTimePicker) {
                TimePickerDialog(
                    onTimeSelected = { selectedTime ->
                        endTime = selectedTime
                        editor.putString("rain_alert_end_time", selectedTime).apply()
                        showEndTimePicker = false
                        val intent = Intent("com.example.weatherapp.RAIN_ALERT_UPDATED")
                        intent.putExtra("rain_alert_enabled", isRainAlertEnabled)
                        intent.putExtra("rain_alert_start_time", startTime)
                        intent.putExtra("rain_alert_end_time", endTime)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
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
                    checked = isSevereWeatherAlertEnabled,
                    onCheckedChange = { enabled ->
                        try {
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                onRequestNotificationPermission()
                                return@Switch
                            }
                            isSevereWeatherAlertEnabled = enabled
                            editor.putBoolean("severe_weather_alert_enabled", enabled).apply()
                            Log.d("SettingsActivity", "Severe weather alert enabled: $enabled")
                            val intent = Intent("com.example.weatherapp.SEVERE_WEATHER_ALERT_UPDATED")
                            intent.putExtra("severe_weather_alert_enabled", enabled)
                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                            if (enabled) {
                                val constraints = Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                    .build()
                                val immediateSevereRequest = OneTimeWorkRequestBuilder<SevereWeatherAlertWorker>()
                                    .setConstraints(constraints)
                                    .addTag("severe_weather_alert_immediate")
                                    .build()
                                WorkManager.getInstance(context).enqueueUniqueWork(
                                    "severe_weather_alert_immediate",
                                    ExistingWorkPolicy.REPLACE,
                                    immediateSevereRequest
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsActivity", "Error updating severe_weather_alert_enabled: ${e.message}", e)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF5372dc),
                        uncheckedThumbColor = Color(0xFF60616B),
                        checkedTrackColor = Color(0xFF5372dc).copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0f),
                        disabledCheckedThumbColor = Color.Gray,
                        disabledUncheckedThumbColor = Color.Gray,
                        disabledCheckedTrackColor = Color.LightGray,
                        disabledUncheckedTrackColor = Color.LightGray,
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                        text = "Nhận thông tin cập nhật về thời tiết định kỳ cho thành phố hiện tại của bạn hai lần một ngày, một lần cho ngày hôm nay và một lần khác cho ngày mai.",
                        fontSize = 13.sp,
                        color = Color(0xFF7380BB)
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Switch(
                    checked = isDailyForecastEnabled,
                    onCheckedChange = { enabled ->
                        try {
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                onRequestNotificationPermission()
                                return@Switch
                            }
                            isDailyForecastEnabled = enabled
                            editor.putBoolean("daily_forecast_enabled", enabled).apply()
                            Log.d("SettingsActivity", "Daily forecast enabled: $enabled - Sending broadcast")
                            val intent = Intent("com.example.weatherapp.SETTINGS_UPDATED")
                            intent.putExtra("daily_forecast_enabled", enabled)
                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                            if (enabled) {
                                val immediateIntent = Intent("com.example.weatherapp.SEND_IMMEDIATE_FORECAST")
                                LocalBroadcastManager.getInstance(context).sendBroadcast(immediateIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsActivity", "Error updating daily_forecast_enabled: ${e.message}", e)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF5372dc),
                        uncheckedThumbColor = Color(0xFF60616B),
                        checkedTrackColor = Color(0xFF5372dc).copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0f),
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

        if (showUnitDialog) {
            val unitOptions = when (currentUnitType) {
                "Nhiệt độ" -> listOf("Độ C (°C)", "Độ F (°F)")
                "Gió" -> listOf("Thang đo Beaufort", "Kilomet mỗi giờ (km/h)", "Mét mỗi giây (m/s)", "Feet mỗi giây (ft/s)", "Dặm mỗi giờ (mph)", "Hải lý mỗi giờ (hải lý)")
                "Áp suất không khí" -> listOf("Hectopascal (hPa)", "Millimet thủy ngân (mmHg)", "Inch thủy ngân (inHg)", "Millibar (mb)", "Pound trên inch vuông (psi)")
                "Tầm nhìn" -> listOf("Kilomet (km)", "Dặm (mi)", "Mét (m)", "Feet (ft)")
                else -> emptyList()
            }

            UnitSelectionDialog(
                title = currentUnitType,
                options = unitOptions,
                onUnitSelected = { selectedUnit ->
                    try {
                        when (currentUnitType) {
                            "Nhiệt độ" -> {
                                temperatureUnit = selectedUnit
                                editor.putString("temperature_unit", selectedUnit)
                            }
                            "Gió" -> {
                                windUnit = selectedUnit
                                editor.putString("wind_unit", selectedUnit)
                            }
                            "Áp suất không khí" -> {
                                pressureUnit = selectedUnit
                                editor.putString("pressure_unit", selectedUnit)
                            }
                            "Tầm nhìn" -> {
                                visibilityUnit = selectedUnit
                                editor.putString("visibility_unit", selectedUnit)
                            }
                        }
                        editor.apply()
                        Log.d("SettingsActivity", "Unit updated: $currentUnitType = $selectedUnit")
                        val intent = Intent("com.example.weatherapp.UNIT_CHANGED")
                        intent.putExtra("unit_type", currentUnitType)
                        intent.putExtra("unit_value", selectedUnit)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Error updating unit: ${e.message}", e)
                    } finally {
                        showUnitDialog = false
                    }
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
                .width(280.dp)
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5372dc),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
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
    val times = mutableListOf<String>()
    for (hour in 0..23) {
        for (minute in 0..59 step 5) {
            times.add(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
        }
    }
    if (!times.contains("23:59")) {
        times.add("23:59")
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(Color.White, shape = RoundedCornerShape(20.dp))
                .padding(20.dp)
                .width(200.dp)
                .heightIn(max = 300.dp)
        ) {
            LazyColumn {
                items(times) { time ->
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTimeSelected(time)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = time,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF5372dc)
                            )
                        }
                        Divider(color = Color(0xFFBFC5D5), thickness = 0.8.dp)
                    }
                }
            }
        }
    }
}
