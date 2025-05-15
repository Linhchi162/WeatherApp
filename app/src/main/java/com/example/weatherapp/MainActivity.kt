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
import java.time.format.DateTimeFormatter

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
    private lateinit var settingsReceiver: BroadcastReceiver

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            checkGpsAndPrompt()
        } else {
            Log.e("MainActivity", "Quyền vị trí bị từ chối")
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
            Toast.makeText(this, "Quyền vị trí bị từ chối", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        weatherDatabase = WeatherDatabase.getDatabase(this)
        weatherDao = weatherDatabase.weatherDao()
        preferences = getSharedPreferences("weather_prefs", MODE_PRIVATE)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

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
                                    val cityName = preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"
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
                            val cityName = preferences.getString("current_city", "Hà Nội") ?: "Hà Nội"
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
val viewModel: WeatherViewModel by viewModels(
    factoryProducer = {
        WeatherViewModelFactory(
            weatherDao = weatherDao,
            openMeteoService = RetrofitInstance.api,
            airQualityService = RetrofitInstance.airQualityApi,
            geoapifyService = RetrofitInstance.geoapifyApi
        )
    }
)

WeatherMainScreen(viewModel = viewModel)

LaunchedEffect(Unit) {
    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        checkGpsAndPrompt()
    }
}

LaunchedEffect(Unit) {
    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                fetchCityName(location, viewModel)
            } else {
                Log.w("MainActivity", "Unable to get location")
                Toast.makeText(this@MainActivity, "Không thể lấy vị trí", Toast.LENGTH_LONG).show()
            }
        }
    } catch (e: SecurityException) {
        Log.e("MainActivity", "Lỗi quyền vị trí: ${e.message}")
    }
}

LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Log.d("MainActivity", "Notification permission already granted on launch")
        }
    }
}

    override fun onResume() {
        super.onResume()
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun checkGpsAndPrompt() {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w("MainActivity", "GPS is disabled")
        }
    }

    private fun fetchCityName(location: Location, viewModel: WeatherViewModel) {
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val nominatimService: NominatimService = retrofit.create(NominatimService::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = nominatimService.getCityName(location.latitude, location.longitude).execute()
                if (response.isSuccessful) {
                    val cityName = response.body()?.address?.city
                        ?: response.body()?.address?.town
                        ?: response.body()?.address?.village
                        ?: "Hà Nội"

                    preferences.edit().apply {
                        putString("current_city", cityName)
                        apply()
                    }

                    viewModel.updateCurrentCity(cityName)
                    viewModel.addCity(City(cityName, location.latitude, location.longitude))
                } else {
                    Log.e("MainActivity", "Error fetching city name: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching city name: ${e.message}")
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

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                checkGpsAndPrompt()
            }

            isRainAlertEnabled = preferences.getBoolean("rain_alert_enabled", false)
            if (isRainAlertEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    runOnUiThread {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    scheduleRainAlertWorker(this@MainActivity)
                }
            }

            isSevereWeatherAlertEnabled = preferences.getBoolean("severe_weather_alert_enabled", false)
            if (isSevereWeatherAlertEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    runOnUiThread {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    scheduleSevereWeatherAlertWorker(this@MainActivity)
                }
            }
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
    try {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MainActivity", "Quyền vị trí không được cấp")
            Toast.makeText(this, "Quyền vị trí không được cấp", Toast.LENGTH_LONG).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.d("WeatherApp", "Vị trí cuối cùng: lat=${location.latitude}, lon=${location.longitude}")
                // viewModel sẽ được truyền từ setContent, không khởi tạo ở đây
                // fetchCityName sẽ được gọi từ LaunchedEffect trong setContent
            } else {
                Log.d("WeatherApp", "Vị trí cuối cùng không khả dụng, yêu cầu cập nhật vị trí mới")
                requestNewLocation()
            }
        }.addOnFailureListener { e ->
            Log.e("WeatherApp", "Lỗi lấy vị trí cuối cùng: ${e.message}")
            Toast.makeText(this, "Lỗi vị trí: ${e.message}", Toast.LENGTH_LONG).show()
        }
    } catch (e: SecurityException) {
        Log.e("WeatherApp", "Lỗi quyền vị trí: ${e.message}")
        Toast.makeText(this, "Lỗi quyền vị trí", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("WeatherApp", "Lỗi không xác định khi lấy vị trí: ${e.message}")
        Toast.makeText(this, "Lỗi không xác định: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

    private fun scheduleDailyForecastNotifications(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

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
    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
                Log.d("WeatherApp", "Vị trí mới: lat=${location.latitude}, lon=${location.longitude}")
                // viewModel sẽ được truyền từ setContent, không khởi tạo ở đây
                // fetchCityName sẽ được gọi từ LaunchedEffect trong setContent
            } else {
                Log.e("WeatherApp", "Không thể lấy vị trí mới")
                Toast.makeText(this@MainActivity, "Không lấy được vị trí", Toast.LENGTH_LONG).show()
            }
            fusedLocationClient.removeLocationUpdates(this)
        }
    }

    try {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            .addOnFailureListener { e ->
                Log.e("WeatherApp", "Lỗi yêu cầu vị trí mới: ${e.message}")
                Toast.makeText(this@MainActivity, "Lỗi vị trí: ${e.message}", Toast.LENGTH_LONG).show()
            }
    } catch (e: SecurityException) {
        Log.e("WeatherApp", "Lỗi quyền vị trí: ${e.message}")
        Toast.makeText(this@MainActivity, "Lỗi quyền vị trí", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("WeatherApp", "Lỗi không xác định khi yêu cầu vị trí: ${e.message}")
        Toast.makeText(this@MainActivity, "Lỗi không xác định: ${e.message}", Toast.LENGTH_LONG).show()
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

private fun fetchCityName(location: Location, viewModel: WeatherViewModel) {
    val sharedPreferences = getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE)
    with(sharedPreferences.edit()) {
        putFloat("latitude", location.latitude.toFloat())
        putFloat("longitude", location.longitude.toFloat())
        apply()
    }

    if (!isInternetAvailable()) {
        Log.w("WeatherApp", "Không có kết nối internet, không thể lấy tên thành phố")
        Toast.makeText(this, "Không có kết nối internet, không thể lấy tên thành phố", Toast.LENGTH_LONG).show()
        val city = City(
            name = "Vị trí hiện tại",
            latitude = location.latitude,
            longitude = location.longitude
        )
        viewModel.addCity(city)
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val apiKey = "183500a3f01b45a5b6076845dae351b3"
            val url = "https://api.geoapify.com/v1/geocode/reverse?lat=${location.latitude}&lon=${location.longitude}&lang=vi&apiKey=$apiKey"
            val request = Request.Builder().url(url).build()
            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                val features = jsonObject.getJSONArray("features")
                val cityName = if (features.length() > 0) {
                    val firstFeature = features.getJSONObject(0)
                    val properties = firstFeature.getJSONObject("properties")
                    properties.optString("city", "Không xác định")
                } else {
                    "Không xác định"
                }
                Log.d("WeatherApp", "Thành phố từ Geoapify API: $cityName")

                withContext(Dispatchers.Main) {
                    val city = City(
                        name = if (cityName == "Không xác định") "Vị trí hiện tại" else cityName,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    viewModel.addCity(city)
                }
            } else {
                Log.e("WeatherApp", "Lỗi gọi Geoapify API: ${response.code}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Lỗi lấy tên thành phố từ API", Toast.LENGTH_LONG).show()
                    val city = City(
                        name = "Vị trí hiện tại",
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    viewModel.addCity(city)
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherApp", "Lỗi gọi Geoapify API: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Lỗi lấy tên thành phố: ${e.message}", Toast.LENGTH_LONG).show()
                val city = City(
                    name = "Vị trí hiện tại",
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                viewModel.addCity(city)
            }
        }
    }
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
                val weatherDescription = WeatherUtils.getWeatherDescription(weatherCode)
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
}

