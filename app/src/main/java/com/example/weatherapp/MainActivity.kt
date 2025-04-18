package com.example.weatherapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.work.*

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var weatherDatabase: WeatherDatabase
    private lateinit var weatherDao: WeatherDao

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            checkGpsAndPrompt()
        } else {
            Log.e("MainActivity", "Quyền vị trí bị từ chối")
            Toast.makeText(this, "Quyền vị trí bị từ chối", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        weatherDatabase = WeatherDatabase.getDatabase(this)
        weatherDao = weatherDatabase.weatherDao()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Kiểm tra và cập nhật dữ liệu ngay khi mở app
        checkAndUpdateWeatherData()

        setContent {
            val viewModel: WeatherViewModel = viewModel(
                factory = WeatherViewModelFactory(weatherDao)
            )

            WeatherMainScreen(viewModel = viewModel)

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    checkGpsAndPrompt()
                }
            }

            LaunchedEffect(Unit) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            fetchCityName(location, viewModel)
                        } else {
                            Toast.makeText(this@MainActivity, "Không thể lấy vị trí", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "Lỗi quyền vị trí: ${e.message}")
                }
            }
        }
        checkLocationPermission()
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
                true // Nếu không có dữ liệu, cần cập nhật
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

            scheduleWeatherUpdateWorker()
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                checkGpsAndPrompt()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun checkGpsAndPrompt() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isGpsEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Bật GPS")
                .setMessage("GPS hiện đang tắt. Bạn có muốn bật GPS để lấy vị trí chính xác hơn không?")
                .setPositiveButton("Bật GPS") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    getLocation()
                }
                .setNegativeButton("Hủy") { _, _ ->
                    Toast.makeText(this, "Đang lấy vị trí bằng Wi-Fi/mạng...", Toast.LENGTH_SHORT).show()
                    getLocation()
                }
                .setCancelable(false)
                .show()
        } else {
            getLocation()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
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

    private fun requestNewLocation() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdates(1)
            .build()

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

    private fun scheduleWeatherUpdateWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val weatherUpdateRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "weather_update_work",
                ExistingPeriodicWorkPolicy.KEEP,
                weatherUpdateRequest
            )
    }
}



