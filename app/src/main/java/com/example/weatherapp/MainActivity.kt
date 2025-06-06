package com.example.weatherapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

import java.util.concurrent.TimeUnit
import android.app.PendingIntent
import android.widget.Toast
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import java.time.format.DateTimeFormatter
import android.os.SystemClock
import java.util.Date

interface NominatimService {
    @GET("reverse?format=json")
    fun getCityName(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): Call<NominatimResponse>
}

data class NominatimResponse(
    val address: Address?
)

data class Address(
    val city: String?,
    val town: String?,
    val village: String?
)

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var weatherDatabase: WeatherDatabase
    private lateinit var weatherDao: WeatherDao
    private lateinit var preferences: SharedPreferences
    private var isDailyForecastEnabled: Boolean = false
    private var isRainAlertEnabled: Boolean = false
    private var isSevereWeatherAlertEnabled: Boolean = false
    private var isFirstLaunch: Boolean = true
    private lateinit var settingsReceiver: BroadcastReceiver
    private lateinit var locationRequest: com.google.android.gms.location.LocationRequest
    
    // Biến để lưu trữ tham chiếu đến viewModel
    private var viewModel: WeatherViewModel? = null
    
    // Flag để theo dõi việc lấy vị trí
    private var isLocationRequested = false

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Quyền vị trí được cấp - chuẩn bị lấy vị trí ngay lập tức")
            checkGpsAndPrompt()
            
            // Đảm bảo không thực hiện quá nhiều lần
            if (!isLocationRequested) {
                isLocationRequested = true
                
                // Xóa cache vị trí để đảm bảo lấy vị trí mới
                fusedLocationClient.flushLocations()
                
                // Thêm độ trễ nhỏ trước khi lấy vị trí để đảm bảo hệ thống đã xử lý quyền
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d("MainActivity", "Bắt đầu lấy vị trí sau khi nhận quyền")
                    getLocation() // Lấy vị trí ngay khi được cấp quyền
                }, 300) // Chờ 0.3 giây
            }
        } else {
            Log.e("MainActivity", "Quyền vị trí bị từ chối")
            // Hiển thị hộp thoại giải thích về quyền nếu bị từ chối
            showLocationPermissionRationale()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Quyền gửi thông báo được cấp")
            if (isDailyForecastEnabled) {
                scheduleDailyForecastNotifications(this)
            }
            if (isRainAlertEnabled) {
                scheduleRainAlertWorker(this)
            }
            if (isSevereWeatherAlertEnabled) {
                scheduleSevereWeatherAlertWorker(this)
            }
        } else {
            Log.w("MainActivity", "Notification permission not granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "onCreate started")
        // Thêm log để debug
        Log.e("LocationTest", "===== App khởi động - bắt đầu kiểm tra log vị trí =====")
        
        weatherDatabase = WeatherDatabase.getDatabase(this)
        weatherDao = weatherDatabase.weatherDao()
        preferences = getSharedPreferences("weather_prefs", MODE_PRIVATE)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        
        // Initialize locationRequest
        locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000 // Update interval in milliseconds
            fastestInterval = 500 // Fastest update interval in milliseconds
            maxWaitTime = 5000 // Maximum wait time for location updates
        }
        
        // Kiểm tra vị trí đã lưu từ trước
        val sharedPreferences = getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE)
        val hasSavedLocation = sharedPreferences.getBoolean("has_saved_location", false)
        
        // Nếu có vị trí đã lưu, chúng ta sẽ sử dụng nó tạm thời trong lúc chờ vị trí mới
        if (hasSavedLocation) {
            val savedLatitude = sharedPreferences.getFloat("saved_latitude", 0f).toDouble()
            val savedLongitude = sharedPreferences.getFloat("saved_longitude", 0f).toDouble()
            if (savedLatitude != 0.0 && savedLongitude != 0.0) {
                Log.d("MainActivity", "Đã tìm thấy vị trí đã lưu: lat=$savedLatitude, lon=$savedLongitude")
                // Vị trí đã lưu sẽ được sử dụng trong checkSavedLocation sau khi ViewModel được khởi tạo
            }
        }
        
        // Log debug quyền vị trí
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.e("LocationTest", "Quyền vị trí hiện tại: " + (if (hasLocationPermission) "ĐÃ ĐƯỢC CẤP" else "CHƯA ĐƯỢC CẤP"))

        // Kiểm tra xem có phải lần đầu khởi động app không
        isFirstLaunch = preferences.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            // Đặt lại thông tin quyền để đảm bảo người dùng được hỏi lại
            preferences.edit()
                .putBoolean("has_asked_for_location", false)
                .putBoolean("is_first_launch", false)
                .apply()
            Log.d("MainActivity", "Lần đầu khởi động app, đặt lại trạng thái quyền")
            
            // Yêu cầu quyền vị trí ngay lập tức nếu là lần đầu khởi động
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Yêu cầu quyền vị trí lần đầu")
                requestLocationPermission()
            } else {
                // Nếu đã có quyền, lấy vị trí ngay lập tức
                Log.d("MainActivity", "Đã có quyền vị trí, lấy vị trí ngay lập tức")
                getLocation()
                isLocationRequested = true
            }
        }
        
        // Kiểm tra xem người dùng có muốn đặt lại quyền vị trí không
        val resetLocationPerm = preferences.getBoolean("reset_location_permission", false)
        if (resetLocationPerm) {
            preferences.edit()
                .putBoolean("has_asked_for_location", false)
                .putBoolean("reset_location_permission", false)
                .apply()
            Log.d("MainActivity", "Đặt lại trạng thái quyền vị trí theo yêu cầu người dùng")
        }

        // Kiểm tra và yêu cầu quyền vị trí ngay từ đầu
        checkLocationPermission()

        preferences.edit().apply {
            if (!preferences.contains("temperature_unit")) {
                putString("temperature_unit", "Độ C (°C)")
            }
            if (!preferences.contains("wind_unit")) {
                putString("wind_unit", "Kilomet mỗi giờ (km/h)")
            }
            if (!preferences.contains("pressure_unit")) {
                putString("pressure_unit", "Millimet thủy ngân (mmHg)")
            }
            if (!preferences.contains("visibility_unit")) {
                putString("visibility_unit", "Kilomet (km)")
            }
            apply()
        }

        settingsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.weatherapp.UNIT_CHANGED" -> {
                        val unitType = intent.getStringExtra("unit_type")
                        val unitValue = intent.getStringExtra("unit_value")
                        Log.d("MainActivity", "Unit changed: $unitType = $unitValue")
                    }
                    "com.example.weatherapp.SETTINGS_UPDATED" -> {
                        val dailyForecastEnabled = intent.getBooleanExtra("daily_forecast_enabled", false)
                        Log.d("MainActivity", "Received SETTINGS_UPDATED: daily_forecast_enabled = $dailyForecastEnabled")
                        if (dailyForecastEnabled != isDailyForecastEnabled) {
                            isDailyForecastEnabled = dailyForecastEnabled
                            if (dailyForecastEnabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return
                                }
                                CoroutineScope(Dispatchers.IO).launch {
                                    val cityName = preferences.getString("current_location_city", null)
                                        ?: preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"
                                    val weatherData = weatherDao.getLatestWeatherDataWithDailyDetailsForCity(cityName)
                                    if (weatherData == null || weatherData.dailyDetails.isEmpty()) {
                                        Log.w("MainActivity", "No weather data available for $cityName")
                                    } else {
                                        runOnUiThread {
                                            sendImmediateForecastNotifications(this@MainActivity)
                                            scheduleDailyForecastNotifications(this@MainActivity)
                                        }
                                    }
                                }
                            } else {
                                cancelDailyForecastNotifications(this@MainActivity)
                            }
                        }
                    }
                    "com.example.weatherapp.SEND_IMMEDIATE_FORECAST" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            val cityName = preferences.getString("current_location_city", null)
                                ?: preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"
                            val weatherData = weatherDao.getLatestWeatherDataWithDailyDetailsForCity(cityName)
                            if (weatherData == null || weatherData.dailyDetails.isEmpty()) {
                                Log.w("MainActivity", "No weather data available for $cityName")
                            } else {
                                runOnUiThread {
                                    sendImmediateForecastNotifications(this@MainActivity)
                                }
                            }
                        }
                    }
                    "com.example.weatherapp.RAIN_ALERT_UPDATED" -> {
                        val rainAlertEnabled = intent.getBooleanExtra("rain_alert_enabled", false)
                        Log.d("MainActivity", "Received RAIN_ALERT_UPDATED: rain_alert_enabled = $rainAlertEnabled")
                        if (rainAlertEnabled != isRainAlertEnabled) {
                            isRainAlertEnabled = rainAlertEnabled
                            if (rainAlertEnabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return
                                }
                                scheduleRainAlertWorker(this@MainActivity)
                                val constraints = Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                    .build()
                                val immediateRainRequest = OneTimeWorkRequestBuilder<RainAlertWorker>()
                                    .setConstraints(constraints)
                                    .addTag("rain_alert_immediate")
                                    .build()
                                WorkManager.getInstance(this@MainActivity)
                                    .enqueueUniqueWork(
                                        "rain_alert_immediate",
                                        ExistingWorkPolicy.REPLACE,
                                        immediateRainRequest
                                    )
                            } else {
                                cancelRainAlertWorker(this@MainActivity)
                            }
                        }
                    }
                    "com.example.weatherapp.SEVERE_WEATHER_ALERT_UPDATED" -> {
                        val severeWeatherAlertEnabled = intent.getBooleanExtra("severe_weather_alert_enabled", false)
                        Log.d("MainActivity", "Received SEVERE_WEATHER_ALERT_UPDATED: severe_weather_alert_enabled = $severeWeatherAlertEnabled")
                        if (severeWeatherAlertEnabled != isSevereWeatherAlertEnabled) {
                            isSevereWeatherAlertEnabled = severeWeatherAlertEnabled
                            if (severeWeatherAlertEnabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return
                                }
                                scheduleSevereWeatherAlertWorker(this@MainActivity)
                                val constraints = Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                    .build()
                                val immediateSevereRequest = OneTimeWorkRequestBuilder<SevereWeatherAlertWorker>()
                                    .setConstraints(constraints)
                                    .addTag("severe_weather_alert_immediate")
                                    .build()
                                WorkManager.getInstance(this@MainActivity)
                                    .enqueueUniqueWork(
                                        "severe_weather_alert_immediate",
                                        ExistingWorkPolicy.REPLACE,
                                        immediateSevereRequest
                                    )
                            } else {
                                cancelSevereWeatherAlertWorker(this@MainActivity)
                            }
                        }
                    }
                    "com.example.weatherapp.WEATHER_DATA_CHANGED" -> {
                        val cityName = intent.getStringExtra("city_name") ?: return
                        val date = intent.getStringExtra("date") ?: return
                        val tempDiff = intent.getFloatExtra("temp_diff", 0f)
                        val precipDiff = intent.getFloatExtra("precip_diff", 0f)
                        val weatherCodeChanged = intent.getBooleanExtra("weather_code_changed", false)
                        Log.d("MainActivity", "Weather data changed for $cityName on $date: tempDiff=$tempDiff, precipDiff=$precipDiff, codeChanged=$weatherCodeChanged")
                        sendWeatherChangeNotification(cityName, date, tempDiff, precipDiff, weatherCodeChanged)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.example.weatherapp.UNIT_CHANGED")
            addAction("com.example.weatherapp.SETTINGS_UPDATED")
            addAction("com.example.weatherapp.SEND_IMMEDIATE_FORECAST")
            addAction("com.example.weatherapp.RAIN_ALERT_UPDATED")
            addAction("com.example.weatherapp.SEVERE_WEATHER_ALERT_UPDATED")
            addAction("com.example.weatherapp.WEATHER_DATA_CHANGED")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(settingsReceiver, filter)

        checkAndUpdateWeatherData()

        setContent {
            Log.d("MainActivity", "Starting setContent")
            
            val viewModel: WeatherViewModel by viewModels(
                factoryProducer = {
                    WeatherViewModelFactory(
                        context = this@MainActivity,
                        weatherDao = weatherDao,
                        openMeteoService = RetrofitInstance.api,
                        airQualityService = RetrofitInstance.airQualityApi,
                        geoNamesService = RetrofitInstance.geoNamesApi
                    )
                }
            )
            
            // Lưu tham chiếu đến viewModel để sử dụng trong các phương thức khác
            this@MainActivity.viewModel = viewModel
            Log.d("MainActivity", "ViewModel initialized. Cities: ${viewModel.citiesList.size}, Current city: '${viewModel.currentCity}'")
            
            // Kiểm tra xem có vị trí đã lưu từ trước không (ngay sau khi ViewModel được khởi tạo)
            checkSavedLocation(viewModel)

            WeatherMainScreen(viewModel = viewModel)

            // Nếu đã có quyền vị trí, lấy vị trí sau khi UI đã hiển thị
            LaunchedEffect(Unit) {
                delay(500) // Giảm delay xuống chỉ còn 500ms để UI hiển thị trước
                Log.d("MainActivity", "Checking location permission after UI setup")
                
                // Tự động lấy vị trí khi không có thành phố nào
                if (viewModel.citiesList.isEmpty()) {
                    Log.d("MainActivity", "Không có thành phố nào, tự động lấy vị trí")
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        checkGpsAndPrompt()
                        getLocation() // Sử dụng getLocation thay vì testGetLocation
                        isLocationRequested = true
                    } else {
                        // Yêu cầu quyền vị trí
                        requestLocationPermission()
                    }
                }
                // Vẫn giữ lại trường hợp kiểm tra có quyền mà chưa yêu cầu
                else if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    if (!isLocationRequested) {
                        Log.d("MainActivity", "Location permission granted but location not requested yet, getting location now")
                        checkGpsAndPrompt()
                        getLocation()
                        isLocationRequested = true
                    } else {
                        Log.d("MainActivity", "Location permission granted and location already requested")
                    }
                } else {
                    Log.d("MainActivity", "Location permission not granted after UI setup")
                }
            }
            
            // Force-check if we have empty data but permissions
            LaunchedEffect(Unit) {
                delay(2000) // Giảm xuống 2 giây thay vì 3 giây
                Log.d("MainActivity", "2-second check: Cities: ${viewModel.citiesList.size}, Current city: '${viewModel.currentCity}'")
                
                // If we have permission but no data, try to get location again
                if (viewModel.citiesList.isEmpty() && 
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "We have permission but no data after 2 seconds, trying again")
                    fusedLocationClient.flushLocations() // Force clear cache
                    getLocation() // Try again
                }
            }
            
            // Notification permissions request
            LaunchedEffect(Unit) {
                delay(5000) // Đợi 5 giây sau khi khởi động (sau location permission)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Log.d("MainActivity", "Requesting notification permission")
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
        
        Log.d("MainActivity", "onCreate completed")
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart called. Setting fallback timer")
        
        // Đặt timer để đảm bảo người dùng luôn thấy thông báo yêu cầu quyền vị trí nếu chưa có thành phố
        Handler(Looper.getMainLooper()).postDelayed({
            viewModel?.let { vm ->
                if (vm.citiesList.isEmpty()) {
                    Log.d("MainActivity", "Danh sách thành phố vẫn trống sau timeout")
                    // Yêu cầu quyền vị trí nếu chưa có
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestLocationPermission()
                        Toast.makeText(this, "Ứng dụng cần quyền vị trí để hiển thị thời tiết tại vị trí của bạn", Toast.LENGTH_LONG).show()
                    } else {
                        // Nếu đã có quyền nhưng vẫn chưa có dữ liệu, thử lấy vị trí trực tiếp
                        getLocation() // Sử dụng getLocation thay vì testGetLocation
                    }
                } else {
                    Log.d("MainActivity", "No fallback needed, cities list has ${vm.citiesList.size} items")
                }
            }
        }, 3000) // Giảm xuống 3 giây thay vì 5 giây
    }

    override fun onResume() {
        super.onResume()
        
        Log.d("MainActivity", "onResume called")
        
        // Kiểm tra lại vị trí đã lưu trữ khi quay lại ứng dụng
        viewModel?.let { 
            Log.d("MainActivity", "onResume: viewModel has ${it.citiesList.size} cities, current city: '${it.currentCity}'")
            checkSavedLocation(it)
        }
        
        val currentDailyForecastEnabled = preferences.getBoolean("daily_forecast_enabled", false)
        if (currentDailyForecastEnabled != isDailyForecastEnabled) {
            isDailyForecastEnabled = currentDailyForecastEnabled
            if (isDailyForecastEnabled) {
                scheduleDailyForecastNotifications(this)
            } else {
                cancelDailyForecastNotifications(this)
            }
        }

        val currentRainAlertEnabled = preferences.getBoolean("rain_alert_enabled", false)
        if (currentRainAlertEnabled != isRainAlertEnabled) {
            isRainAlertEnabled = currentRainAlertEnabled
            if (isRainAlertEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scheduleRainAlertWorker(this)
                }
            } else {
                cancelRainAlertWorker(this)
            }
        }

        val currentSevereWeatherAlertEnabled = preferences.getBoolean("severe_weather_alert_enabled", false)
        if (currentSevereWeatherAlertEnabled != isSevereWeatherAlertEnabled) {
            isSevereWeatherAlertEnabled = currentSevereWeatherAlertEnabled
            if (isSevereWeatherAlertEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scheduleSevereWeatherAlertWorker(this)
                }
            } else {
                cancelSevereWeatherAlertWorker(this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isDailyForecastEnabled) {
            scheduleDailyForecastNotifications(this)
        }
        if (isRainAlertEnabled) {
            scheduleRainAlertWorker(this)
        }
        if (isSevereWeatherAlertEnabled) {
            scheduleSevereWeatherAlertWorker(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(settingsReceiver)
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("MainActivity", "Đã có quyền vị trí, kiểm tra GPS")
                checkGpsAndPrompt()
                // Đánh dấu là sẽ lấy vị trí và sẽ lấy vị trí sau khi UI đã hiển thị
                isLocationRequested = true
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionRationale()
            }
            else -> {
                // Yêu cầu quyền vị trí ngay lập tức
                requestLocationPermission()
            }
        }
    }

    private fun checkGpsAndPrompt() {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w("MainActivity", "GPS is disabled")
        }
    }

    private fun fetchCityName(location: Location, viewModel: WeatherViewModel) {
        // Log chi tiết tọa độ nhận được
        Log.i("LocationTest", "Tọa độ nhận được: lat=${location.latitude}, lon=${location.longitude}, độ chính xác=${location.accuracy}m")
        
        // Lưu vị trí vào preferences để có thể sử dụng sau này
        val sharedPreferences = getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putFloat("latitude", location.latitude.toFloat())
            putFloat("longitude", location.longitude.toFloat())
            putFloat("saved_latitude", location.latitude.toFloat())
            putFloat("saved_longitude", location.longitude.toFloat())
            putBoolean("has_saved_location", true)
            apply()
        }

        if (!isInternetAvailable()) {
            Log.e("WeatherApp", "No internet connection available.")
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "No internet connection available.", Toast.LENGTH_LONG).show();
                }
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiKey = "183500a3f01b45a5b6076845dae351b3"
                val url = "https://api.geoapify.com/v1/geocode/reverse?lat=${location.latitude}&lon=${location.longitude}&lang=vi&format=json&apiKey=$apiKey"
                Log.i("LocationTest", "Gọi API Geoapify với URL: $url")
                
                // Tạo client với timeout ngắn hơn để tránh chờ đợi quá lâu
                val client = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)  // Giảm từ mặc định xuống 3 giây
                    .readTimeout(3, TimeUnit.SECONDS)     // Giảm từ mặc định xuống 3 giây
                    .build()
                
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    // Log toàn bộ response nhận được từ Geoapify
                    Log.i("LocationTest", "Response từ Geoapify: $responseBody")
                    
                    val jsonObject = JSONObject(responseBody)
                    val features = jsonObject.getJSONArray("results")
                    
                    var locationName = "Vị trí hiện tại"
                    var countryName: String? = null
                    var detailedArea = ""
                    
                    if (features.length() > 0) {
                        val firstFeature = features.getJSONObject(0)
                        val properties = firstFeature
                        
                        // Log thông tin chi tiết từng trường
                        Log.i("LocationTest", "Thông tin chi tiết vị trí:")
                        Log.i("LocationTest", "- country: ${properties.optString("country", "N/A")}")
                        Log.i("LocationTest", "- state: ${properties.optString("state", "N/A")}")
                        Log.i("LocationTest", "- county: ${properties.optString("county", "N/A")}")
                        Log.i("LocationTest", "- city: ${properties.optString("city", "N/A")}")
                        Log.i("LocationTest", "- suburb: ${properties.optString("suburb", "N/A")}")
                        Log.i("LocationTest", "- quarter: ${properties.optString("quarter", "N/A")}")
                        Log.i("LocationTest", "- street: ${properties.optString("street", "N/A")}")
                        Log.i("LocationTest", "- formatted: ${properties.optString("formatted", "N/A")}")
                        
                        // Lấy tên quốc gia
                        countryName = properties.optString("country", null)
                        
                        // Extract location details in order of preference
                        val district = properties.optString("suburb", "")
                        val suburb = properties.optString("quarter", "")
                        val municipality = properties.optString("county", "")
                        val city = properties.optString("city", "")
                        val county = properties.optString("county", "")
                        val state = properties.optString("state", "")
                        
                        // Build a detailed name with priority to smaller areas (chỉ sử dụng tên gọn gàng)
                        if (district.isNotEmpty()) {
                            detailedArea = district
                            // Chỉ sử dụng district thôi, không ghép với city để tên gọn gàng hơn
                            locationName = district.split(" ").joinToString(" ") { 
                                it.replaceFirstChar { char -> char.uppercaseChar() } 
                            }
                        } else if (suburb.isNotEmpty()) {
                            detailedArea = suburb
                            // Chỉ sử dụng suburb thôi, không ghép với city
                            locationName = suburb.split(" ").joinToString(" ") { 
                                it.replaceFirstChar { char -> char.uppercaseChar() } 
                            }
                        } else if (municipality.isNotEmpty()) {
                            detailedArea = municipality
                            locationName = municipality.split(" ").joinToString(" ") { 
                                it.replaceFirstChar { char -> char.uppercaseChar() } 
                            }
                        } else if (city.isNotEmpty()) {
                            detailedArea = city
                            locationName = city.split(" ").joinToString(" ") { 
                                it.replaceFirstChar { char -> char.uppercaseChar() } 
                            }
                        } else if (county.isNotEmpty()) {
                            detailedArea = county
                            locationName = county.split(" ").joinToString(" ") { 
                                it.replaceFirstChar { char -> char.uppercaseChar() } 
                            }
                        } else if (state.isNotEmpty()) {
                            detailedArea = state
                            locationName = state.split(" ").joinToString(" ") { 
                                it.replaceFirstChar { char -> char.uppercaseChar() } 
                            }
                        }
                        
                        Log.d("WeatherApp", "Chi tiết địa điểm: suburb=$district, quarter=$suburb, county=$municipality, city=$city")
                    }
                    
                    Log.d("WeatherApp", "Tên địa điểm đã chọn: $locationName")
                    Log.i("LocationTest", "Tên địa điểm cuối cùng đã chọn: $locationName")

                    // Lưu tên thành phố vị trí hiện tại vào SharedPreferences
                    val prefs = getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("current_location_city", locationName).apply()

                    withContext(Dispatchers.Main) {
                        val city = City(
                            name = locationName,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            country = countryName
                        )
                        // Thêm thành phố vào đầu danh sách và cập nhật
                        viewModel.addCity(city, true)
                        
                        // Đảm bảo cập nhật ngay currentCity và tải dữ liệu thời tiết
                        viewModel.updateCurrentCity(locationName)
                        
                        // Gọi trực tiếp hàm fetch để đảm bảo dữ liệu được tải ngay lập tức
                        viewModel.fetchWeatherAndAirQuality(locationName, location.latitude, location.longitude)
                        
                        Log.d("WeatherApp", "Đã gọi fetchWeatherAndAirQuality cho $locationName")
                    }
                } else {
                    Log.e("WeatherApp", "Lỗi gọi Geoapify API: ${response.code}")
                    Log.e("LocationTest", "Lỗi gọi Geoapify API: ${response.code}, body: ${responseBody}")
                    withContext(Dispatchers.Main) {
                        val cityName = "Vị trí hiện tại"
                        val city = City(
                            name = cityName,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                        // Thêm thành phố vào đầu danh sách và cập nhật
                        viewModel.addCity(city, true)
                        
                        // Đảm bảo cập nhật ngay currentCity và tải dữ liệu thời tiết
                        viewModel.updateCurrentCity(cityName)
                        viewModel.fetchWeatherAndAirQuality(cityName, location.latitude, location.longitude)
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherApp", "Lỗi gọi Geoapify API: ${e.message}")
                Log.e("LocationTest", "Exception khi gọi Geoapify API", e)
                withContext(Dispatchers.Main) {
                    val cityName = "Vị trí hiện tại"
                    val city = City(
                        name = cityName,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    // Thêm thành phố vào đầu danh sách và cập nhật
                    viewModel.addCity(city, true)
                    
                    // Đảm bảo cập nhật ngay currentCity và tải dữ liệu thời tiết
                    viewModel.updateCurrentCity(cityName)
                    viewModel.fetchWeatherAndAirQuality(cityName, location.latitude, location.longitude)
                }
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkAndUpdateWeatherData() {
        CoroutineScope(Dispatchers.IO).launch {
            val allWeatherData = weatherDao.getAllWeatherData()
            val shouldUpdate = if (allWeatherData.isNotEmpty()) {
                val latestWeatherData = allWeatherData.maxByOrNull { it.lastUpdated }
                val lastUpdateTime = latestWeatherData?.lastUpdated ?: 0L
                val currentTime = System.currentTimeMillis()
                val fifteenMinutesInMillis = 15 * 60 * 1000
                (currentTime - lastUpdateTime) > fifteenMinutesInMillis
            } else {
                true
            }

            if (shouldUpdate && isInternetAvailable()) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneTimeWorkRequest = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(this@MainActivity)
                    .enqueueUniqueWork(
                        "weather_update_immediate",
                        ExistingWorkPolicy.REPLACE,
                        oneTimeWorkRequest
                    )
            }

            isDailyForecastEnabled = preferences.getBoolean("daily_forecast_enabled", false)
            if (isDailyForecastEnabled) {
                scheduleDailyForecastNotifications(this@MainActivity)
            }
            
            scheduleWeatherUpdateWorker()
        }
    }

    private fun scheduleWeatherUpdateWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "weather_update",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
    }

    private fun sendImmediateForecastNotifications(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val immediateTodayRequest = OneTimeWorkRequestBuilder<DailyForecastWorker>()
            .setConstraints(constraints)
            .addTag("forecast_today_immediate")
            .build()

        val immediateTomorrowRequest = OneTimeWorkRequestBuilder<DailyForecastWorker>()
            .setConstraints(constraints)
            .addTag("forecast_tomorrow_immediate")
            .build()

        WorkManager.getInstance(context).apply {
            beginUniqueWork(
                "daily_forecast_immediate",
                ExistingWorkPolicy.REPLACE,
                immediateTodayRequest
            ).then(immediateTomorrowRequest).enqueue()
        }
        Log.d("MainActivity", "Immediate forecast notifications enqueued for today and tomorrow")
    }

    private fun getLocation() {
        Log.e("LocationTest", "getLocation() được gọi - bắt đầu quá trình lấy vị trí")
        try {
            // Bỏ điều kiện kiểm tra danh sách thành phố
            // Luôn cho phép lấy vị trí mới
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("MainActivity", "Quyền vị trí không được cấp")
                Log.e("LocationTest", "Không thể lấy vị trí - quyền chưa được cấp")
                return
            }

            Log.d("WeatherApp", "Bắt đầu lấy vị trí mới")
            Log.e("LocationTest", "Đủ điều kiện để lấy vị trí, tiếp tục quy trình...")
            
            // Xóa cài đặt lastLocation để đảm bảo luôn lấy vị trí mới
            fusedLocationClient.flushLocations()
            
            // Đặt requestNewLocation vào queue để thực hiện sau khi flush đã hoàn thành
            Handler(Looper.getMainLooper()).post {
                // Luôn yêu cầu vị trí mới thay vì dùng lastLocation
                requestNewLocation()
            }
            
            // Luôn kiểm tra lastLocation như một phương án dự phòng, nhưng vẫn ưu tiên requestNewLocation
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d("WeatherApp", "Đã lấy được vị trí từ cache: ${location.latitude}, ${location.longitude}")
                        Log.e("LocationTest", "Vị trí từ cache: lat=${location.latitude}, lon=${location.longitude}")
                        
                        // Sử dụng vị trí từ cache ngay cả khi đã có dữ liệu
                        viewModel?.let { vm ->
                            Log.e("LocationTest", "Sử dụng vị trí từ cache để lấy tên thành phố")
                            fetchCityName(location, vm)
                        }
                    } else {
                        Log.d("WeatherApp", "Không có vị trí trong cache")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("WeatherApp", "Lỗi khi lấy vị trí từ cache: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.e("WeatherApp", "Lỗi quyền vị trí: ${e.message}")
        } catch (e: Exception) {
            Log.e("WeatherApp", "Lỗi không xác định khi lấy vị trí: ${e.message}")
        }
    }

    private fun scheduleDailyForecastNotifications(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Morning notification (9:00 AM, fallback 10:00-11:00 AM)
        val morningDelay = calculateInitialDelay(9, 10, 11)
        val morningNotificationRequest = PeriodicWorkRequestBuilder<DailyForecastWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(morningDelay, TimeUnit.MILLISECONDS)
            .addTag("forecast_today")
            .build()

        // Evening notification (9:00 PM, fallback 10:00-11:00 PM)
        val eveningDelay = calculateInitialDelay(21, 22, 23)
        val eveningNotificationRequest = PeriodicWorkRequestBuilder<DailyForecastWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(eveningDelay, TimeUnit.MILLISECONDS)
            .addTag("forecast_tomorrow")
            .build()

        WorkManager.getInstance(context).apply {
            enqueueUniquePeriodicWork(
                "daily_forecast_today",
                ExistingPeriodicWorkPolicy.REPLACE,
                morningNotificationRequest
            )
            enqueueUniquePeriodicWork(
                "daily_forecast_tomorrow",
                ExistingPeriodicWorkPolicy.REPLACE,
                eveningNotificationRequest
            )
        }
        Log.d("MainActivity", "Scheduled daily forecast notifications at 9:00 AM/10:00-11:00 AM and 9:00 PM/10:00-11:00 PM")
    }

    private fun requestNewLocation() {
        Log.i("LocationTest", "Bắt đầu requestNewLocation()")
        
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.i("LocationTest", "Đã nhận vị trí mới từ requestLocationUpdates:")
                    Log.i("LocationTest", "- Tọa độ: lat=${location.latitude}, lon=${location.longitude}")
                    Log.i("LocationTest", "- Độ chính xác: ${location.accuracy}m")
                    Log.i("LocationTest", "- Thời gian: ${Date(location.time)}")
                    Log.i("LocationTest", "- Provider: ${location.provider}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Log.i("LocationTest", "- Vertical accuracy: ${location.verticalAccuracyMeters}m")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val elapsedMs = SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1000000
                        Log.i("LocationTest", "- Độ trễ: ${elapsedMs}ms")
                    }
                    
                    Log.d("WeatherApp", "Vị trí mới (chính xác): lat=${location.latitude}, lon=${location.longitude}")
                    
                    // Sử dụng viewModel từ setContent
                    viewModel?.let { viewModel ->
                        Log.d("WeatherApp", "ViewModel đã được khởi tạo, bắt đầu lấy tên thành phố")
                        fetchCityName(location, viewModel)
                    } ?: run {
                        Log.e("WeatherApp", "ViewModel chưa được khởi tạo khi nhận vị trí")
                        // Lưu lại vị trí để xử lý sau khi viewModel được khởi tạo
                        val sharedPreferences = getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE)
                        with(sharedPreferences.edit()) {
                            putFloat("saved_latitude", location.latitude.toFloat())
                            putFloat("saved_longitude", location.longitude.toFloat())
                            putBoolean("has_saved_location", true)
                            apply()
                        }
                        
                        Log.d("WeatherApp", "Đã lưu vị trí vào SharedPreferences để xử lý sau")
                    }
                } else {
                    Log.e("WeatherApp", "Không thể lấy vị trí mới")
                    Log.e("LocationTest", "onLocationResult trả về location=null")
                }
                fusedLocationClient.removeLocationUpdates(this)
                Log.i("LocationTest", "Đã gọi removeLocationUpdates để ngừng cập nhật vị trí")
            }
        }

        try {
            locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
                priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 1000 // Giảm xuống 1 giây
                fastestInterval = 500 // Giảm xuống 500ms
                numUpdates = 1 // Chỉ lấy vị trí một lần
                maxWaitTime = 5000 // Giảm thời gian chờ tối đa xuống 5 giây
            }
            
            Log.i("LocationTest", "Tạo locationRequest với:")
            Log.i("LocationTest", "- priority: PRIORITY_HIGH_ACCURACY")
            Log.i("LocationTest", "- interval: 1000ms")
            Log.i("LocationTest", "- fastestInterval: 500ms")
            Log.i("LocationTest", "- numUpdates: 1")
            Log.i("LocationTest", "- maxWaitTime: 5000ms")
            
            // Đảm bảo flushLocations được gọi trước khi yêu cầu vị trí mới
            fusedLocationClient.flushLocations()
            Log.i("LocationTest", "Đã gọi flushLocations()")
            
            Log.d("WeatherApp", "Đang yêu cầu vị trí mới...")
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                .addOnSuccessListener {
                    Log.d("WeatherApp", "Đã đăng ký nhận cập nhật vị trí thành công")
                    Log.i("LocationTest", "requestLocationUpdates: Đăng ký thành công")
                }
                .addOnFailureListener { e ->
                    Log.e("WeatherApp", "Lỗi yêu cầu vị trí mới: ${e.message}")
                    Log.e("LocationTest", "requestLocationUpdates failed", e)
                }
        } catch (e: SecurityException) {
            Log.e("WeatherApp", "Lỗi quyền vị trí: ${e.message}")
            Log.e("LocationTest", "SecurityException khi yêu cầu vị trí:", e)
        } catch (e: Exception) {
            Log.e("WeatherApp", "Lỗi không xác định khi yêu cầu vị trí: ${e.message}")
            Log.e("LocationTest", "Exception không xác định khi yêu cầu vị trí:", e)
        }
    }

    private fun cancelDailyForecastNotifications(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("daily_forecast_immediate")
            cancelUniqueWork("daily_forecast_today")
            cancelUniqueWork("daily_forecast_tomorrow")
        }
        Log.d("MainActivity", "Cancelled daily forecast notifications")
    }

    private fun scheduleRainAlertWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val periodicRainRequest = PeriodicWorkRequestBuilder<RainAlertWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("rain_alert")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "rain_alert",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRainRequest
        )
        Log.d("MainActivity", "Scheduled RainAlertWorker to run every 15 minutes")
    }

    private fun cancelRainAlertWorker(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("rain_alert_immediate")
            cancelUniqueWork("rain_alert")
        }
        Log.d("MainActivity", "Cancelled RainAlertWorker")
    }

    private fun scheduleSevereWeatherAlertWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val periodicSevereRequest = PeriodicWorkRequestBuilder<SevereWeatherAlertWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("severe_weather_alert")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "severe_weather_alert",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSevereRequest
        )
        Log.d("MainActivity", "Scheduled SevereWeatherAlertWorker to run every 15 minutes")
    }

    private fun cancelSevereWeatherAlertWorker(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("severe_weather_alert_immediate")
            cancelUniqueWork("severe_weather_alert")
        }
        Log.d("MainActivity", "Cancelled SevereWeatherAlertWorker")
    }

    private fun calculateInitialDelay(primaryHour: Int, fallbackStartHour: Int, fallbackEndHour: Int): Long {
        val now = LocalDateTime.now()
        val currentTime = LocalTime.now()
        var target = LocalDateTime.now()
            .withHour(primaryHour)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        if (target.isBefore(now)) {
            // If primary time has passed, check fallback window
            val fallbackStart = LocalTime.of(fallbackStartHour, 0)
            val fallbackEnd = LocalTime.of(fallbackEndHour, 0)
            if (currentTime.isAfter(fallbackStart) && currentTime.isBefore(fallbackEnd)) {
                // Schedule for next minute in fallback window
                target = now.plusMinutes(1).withSecond(0).withNano(0)
            } else {
                // Schedule for next day's primary time
                target = target.plusDays(1)
            }
        }

        return Duration.between(now, target).toMillis()
    }

    private fun sendWeatherChangeNotification(cityName: String, date: String, tempDiff: Float, precipDiff: Float, weatherCodeChanged: Boolean) {
        if (!isDailyForecastEnabled) {
            Log.d("MainActivity", "Daily forecast is disabled, skipping weather change notification for $cityName")
            return
        }

        // Lấy dữ liệu thời tiết mới nhất cho ngày hôm nay
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val weatherDataWithDailyDetails = weatherDao.getLatestWeatherDataWithDailyDetailsForCity(cityName)
                if (weatherDataWithDailyDetails == null || weatherDataWithDailyDetails.dailyDetails.isEmpty()) {
                    Log.e("MainActivity", "No weather data available for $cityName")
                    return@launch
                }

                val today = LocalDate.now()
                val forecast = weatherDataWithDailyDetails.dailyDetails.find { detail ->
                    val detailDate = LocalDate.parse(detail.time, DateTimeFormatter.ISO_LOCAL_DATE)
                    detailDate == today
                }

                if (forecast == null) {
                    Log.e("MainActivity", "No forecast data for today in $cityName")
                    return@launch
                }

                // Chuẩn bị nội dung thông báo
                val maxTemp = forecast.temperature_2m_max.toInt()
                val minTemp = forecast.temperature_2m_min.toInt()
                val precipitation = forecast.precipitation_probability_max.toInt()
                val weatherCode = forecast.weather_code
                val weatherDescription = WeatherUtils.getWeatherDescription(weatherCode, cityName)
                val weatherEmoji = WeatherUtils.getWeatherEmoji(weatherCode)
                val weatherIcon = WeatherUtils.getWeatherIcon(weatherCode)

                val title = "Dự báo thời tiết hôm nay cho $cityName"
                val message = "Thời tiết: $weatherEmoji $weatherDescription\n" +
                        "Nhiệt độ: $minTemp°C - $maxTemp°C\n" +
                        "Khả năng mưa: $precipitation%"

                // Kiểm tra thông báo trùng lặp
                val lastNotificationTime = preferences.getLong("last_weather_change_notification_time", 0L)
                val currentTime = System.currentTimeMillis()
                val oneHourInMillis = 60 * 60 * 1000L
                if (currentTime - lastNotificationTime < oneHourInMillis) {
                    Log.d("MainActivity", "Skipping duplicate weather change notification for $cityName")
                    return@launch
                }

                // Gửi thông báo
                val notificationManager = NotificationManagerCompat.from(this@MainActivity)
                val channel = android.app.NotificationChannel(
                    "weather_change_channel",
                    "Thay đổi thời tiết",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Thông báo khi thời tiết thay đổi đáng kể"
                }
                notificationManager.createNotificationChannel(channel)

                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getActivity(this@MainActivity, 0, intent, pendingIntentFlags)

                val validIconResId = if (WeatherUtils.isResourceAvailable(this@MainActivity, weatherIcon)) {
                    Log.d("MainActivity", "Using weather icon: $weatherIcon")
                    weatherIcon
                } else {
                    Log.w("MainActivity", "Weather icon not found, using default icon")
                    android.R.drawable.ic_dialog_info
                }

                val notification = NotificationCompat.Builder(this@MainActivity, "weather_change_channel")
                    .setSmallIcon(validIconResId)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                try {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
                        Log.d("MainActivity", "Weather change notification sent for $cityName: $title - $message")

                        // Lưu thời gian thông báo để tránh trùng lặp
                        preferences.edit().putLong("last_weather_change_notification_time", currentTime).apply()
                    } else {
                        Log.w("MainActivity", "Notification permission not granted for weather change")
                    }
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "SecurityException when sending weather change notification: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error sending weather change notification for $cityName: ${e.message}", e)
            }
        }
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Quyền truy cập vị trí")
            .setMessage("Ứng dụng cần quyền truy cập vị trí để cung cấp thông tin thời tiết cho vị trí của bạn.")
            .setPositiveButton("Cấp quyền") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Không, cảm ơn") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Không thể cung cấp dự báo thời tiết cho vị trí của bạn", Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }

    private fun requestLocationPermission() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun resetLocationPermissionStatus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Lưu trạng thái nếu đã từng yêu cầu quyền này
                val hasAskedForPermission = preferences.getBoolean("has_asked_for_location", false)
                
                if (hasAskedForPermission) {
                    Log.d("MainActivity", "Đặt lại trạng thái quyền vị trí")
                    
                    // Mở cài đặt ứng dụng nếu quyền bị từ chối vĩnh viễn
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        // Hiện dialog xác nhận
                        AlertDialog.Builder(this)
                            .setTitle("Cần quyền vị trí")
                            .setMessage("Ứng dụng cần quyền vị trí để hoạt động chính xác. Bạn muốn mở cài đặt để cấp quyền?")
                            .setPositiveButton("Mở cài đặt") { _, _ ->
                                // Mở cài đặt ứng dụng
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                val uri = android.net.Uri.fromParts("package", packageName, null)
                                intent.data = uri
                                startActivity(intent)
                            }
                            .setNegativeButton("Không") { _, _ ->
                                Toast.makeText(this, "Không thể lấy vị trí hiện tại", Toast.LENGTH_SHORT).show()
                            }
                            .setNeutralButton("Thử lại") { _, _ ->
                                // Đặt lại trạng thái quyền vị trí và thử lại
                                preferences.edit()
                                    .putBoolean("has_asked_for_location", false)
                                    .putBoolean("reset_location_permission", true)
                                    .apply()
                                
                                // Khởi động lại ứng dụng
                                val intent = Intent(this, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(intent)
                                finish()
                            }
                            .show()
                    }
                }
                
                // Đánh dấu là đã yêu cầu quyền
                preferences.edit().putBoolean("has_asked_for_location", true).apply()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Lỗi khi đặt lại trạng thái quyền: ${e.message}")
        }
    }

    // Hàm mới để kiểm tra và sử dụng vị trí đã lưu
    private fun checkSavedLocation(viewModel: WeatherViewModel) {
        val sharedPreferences = getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE)
        val hasSavedLocation = sharedPreferences.getBoolean("has_saved_location", false)
        
        // Nếu có vị trí đã lưu
        if (hasSavedLocation) {
            val latitude = sharedPreferences.getFloat("saved_latitude", 0f).toDouble()
            val longitude = sharedPreferences.getFloat("saved_longitude", 0f).toDouble()
            
            if (latitude != 0.0 && longitude != 0.0) {
                Log.d("WeatherApp", "Sử dụng vị trí đã lưu: lat=$latitude, lon=$longitude")
                
                // Đánh dấu đã sử dụng vị trí lưu trữ này
                sharedPreferences.edit().putBoolean("has_saved_location", false).apply()
                
                // Chỉ sử dụng vị trí đã lưu nếu không có thành phố nào hoặc thành phố hiện tại rỗng
                if (viewModel.citiesList.isEmpty() || viewModel.currentCity.isEmpty()) {
                    // Tạo location object từ dữ liệu đã lưu
                    val location = Location("saved_location").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                    }
                    
                    // Sử dụng vị trí đã lưu để lấy tên thành phố
                    // Sử dụng thread riêng để không block UI thread
                    Handler(Looper.getMainLooper()).post {
                        fetchCityName(location, viewModel)
                        Log.d("WeatherApp", "Đã sử dụng vị trí đã lưu và khởi động quá trình lấy dữ liệu")
                    }
                }
            }
        } else {
            Log.d("WeatherApp", "Không có vị trí đã lưu")
            
            // Nếu không có vị trí đã lưu và không có thành phố nào, thử lấy vị trí ngay lập tức
            if (viewModel.citiesList.isEmpty() && 
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d("WeatherApp", "Không có vị trí đã lưu và không có thành phố, thử lấy vị trí mới")
                
                // Thực hiện trong thread riêng để không block UI
                Handler(Looper.getMainLooper()).post {
                    getLocation()
                }
            }
        }
    }
}


