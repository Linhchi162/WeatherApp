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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
            setContent {
                WeatherMainScreen(cityName = "Không có quyền vị trí")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        weatherDatabase = WeatherDatabase.getDatabase(this)
        weatherDao = weatherDatabase.weatherDao()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setContent {
            WeatherMainScreen()
        }
        checkLocationPermission()
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
                setContent {
                    WeatherMainScreen(cityName = "Lỗi quyền vị trí")
                }
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Log.d("WeatherApp", "Vị trí cuối cùng: lat=${location.latitude}, lon=${location.longitude}")
                    fetchCityName(location)
                } else {
                    Log.d("WeatherApp", "Vị trí cuối cùng không khả dụng, yêu cầu cập nhật vị trí mới")
                    requestNewLocation()
                }
            }.addOnFailureListener { e ->
                Log.e("WeatherApp", "Lỗi lấy vị trí cuối cùng: ${e.message}")
                Toast.makeText(this, "Lỗi vị trí: ${e.message}", Toast.LENGTH_LONG).show()
                setContent {
                    WeatherMainScreen(cityName = "Lỗi vị trí")
                }
            }
        } catch (e: SecurityException) {
            Log.e("WeatherApp", "Lỗi quyền vị trí: ${e.message}")
            Toast.makeText(this, "Lỗi quyền vị trí", Toast.LENGTH_LONG).show()
            setContent {
                WeatherMainScreen(cityName = "Lỗi quyền vị trí")
            }
        } catch (e: Exception) {
            Log.e("WeatherApp", "Lỗi không xác định khi lấy vị trí: ${e.message}")
            Toast.makeText(this, "Lỗi không xác định: ${e.message}", Toast.LENGTH_LONG).show()
            setContent {
                WeatherMainScreen(cityName = "Lỗi không xác định")
            }
        }
    }

    private fun requestNewLocation() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdates(1)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.d("WeatherApp", "Vị trí mới: lat=${location.latitude}, lon=${location.longitude}")
                    fetchCityName(location)
                } else {
                    Log.e("WeatherApp", "Không thể lấy vị trí mới")
                    Toast.makeText(this@MainActivity, "Không lấy được vị trí", Toast.LENGTH_LONG).show()
                    setContent {
                        WeatherMainScreen(cityName = "Vị trí không khả dụng")
                    }
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    Log.e("WeatherApp", "Lỗi yêu cầu vị trí mới: ${e.message}")
                    Toast.makeText(this@MainActivity, "Lỗi vị trí: ${e.message}", Toast.LENGTH_LONG).show()
                    setContent {
                        WeatherMainScreen(cityName = "Lỗi vị trí")
                    }
                }
        } catch (e: SecurityException) {
            Log.e("WeatherApp", "Lỗi quyền vị trí: ${e.message}")
            Toast.makeText(this@MainActivity, "Lỗi quyền vị trí", Toast.LENGTH_LONG).show()
            setContent {
                WeatherMainScreen(cityName = "Lỗi quyền vị trí")
            }
        } catch (e: Exception) {
            Log.e("WeatherApp", "Lỗi không xác định khi yêu cầu vị trí: ${e.message}")
            Toast.makeText(this@MainActivity, "Lỗi không xác định: ${e.message}", Toast.LENGTH_LONG).show()
            setContent {
                WeatherMainScreen(cityName = "Lỗi không xác định")
            }
        }
    }

    private fun fetchCityName(location: Location) {
        val sharedPreferences = getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putFloat("latitude", location.latitude.toFloat())
            putFloat("longitude", location.longitude.toFloat())
            apply()
        }

        scheduleWeatherUpdateWorker()

        if (!isInternetAvailable()) {
            Log.w("WeatherApp", "Không có kết nối internet, không thể lấy tên thành phố")
            Toast.makeText(this, "Không có kết nối internet, không thể lấy tên thành phố", Toast.LENGTH_LONG).show()
            setContent {
                val viewModel: WeatherViewModel = viewModel(
                    factory = WeatherViewModelFactory(weatherDao)
                )
                viewModel.fetchWeather(location.latitude, location.longitude)
                WeatherMainScreen(
                    viewModel = viewModel,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    cityName = "Tọa độ: ${location.latitude}, ${location.longitude}"
                )
            }
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
                    val city = if (features.length() > 0) {
                        val firstFeature = features.getJSONObject(0)
                        val properties = firstFeature.getJSONObject("properties")
                        properties.optString("city", "Không xác định")
                    } else {
                        "Không xác định"
                    }
                    Log.d("WeatherApp", "Thành phố từ Geoapify API: $city")

                    withContext(Dispatchers.Main) {
                        setContent {
                            val viewModel: WeatherViewModel = viewModel(
                                factory = WeatherViewModelFactory(weatherDao)
                            )
                            viewModel.fetchWeather(location.latitude, location.longitude)
                            WeatherMainScreen(
                                viewModel = viewModel,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                cityName = if (city == "Không xác định") "Vị trí không xác định" else city
                            )
                        }
                    }
                } else {
                    Log.e("WeatherApp", "Lỗi gọi Geoapify API: ${response.code}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Lỗi lấy tên thành phố từ API", Toast.LENGTH_LONG).show()
                        setContent {
                            val viewModel: WeatherViewModel = viewModel(
                                factory = WeatherViewModelFactory(weatherDao)
                            )
                            viewModel.fetchWeather(location.latitude, location.longitude)
                            WeatherMainScreen(
                                viewModel = viewModel,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                cityName = "Lỗi lấy tên thành phố"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherApp", "Lỗi gọi Geoapify API: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Lỗi lấy tên thành phố: ${e.message}", Toast.LENGTH_LONG).show()
                    setContent {
                        val viewModel: WeatherViewModel = viewModel(
                            factory = WeatherViewModelFactory(weatherDao)
                        )
                        viewModel.fetchWeather(location.latitude, location.longitude)
                        WeatherMainScreen(
                            viewModel = viewModel,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            cityName = "Lỗi lấy tên thành phố"
                        )
                    }
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

fun getAqiInfo(aqi: Int): Triple<String, Color, Float> {
    return when (aqi) {
        in 0..50 -> Triple("Good", Color(0xFF3FAE6D), 0.1f)
        in 51..100 -> Triple("Moderate", Color(0xFF9DBE39), 0.3f)
        in 101..150 -> Triple("Unhealthy", Color(0xFFFFC107), 0.5f)
        in 151..200 -> Triple("Very Unhealthy", Color(0xFFFF6F22), 0.75f)
        in 201..300 -> Triple("Hazardous", Color(0xFFD74944), 0.9f)
        else -> Triple("Severe", Color(0xFFA10000), 1.0f)
    }
}

fun getWeatherIcon(code: Int): Int {
    return when (code) {
        0 -> R.drawable.sunny
        1, 2 -> R.drawable.cloudy_with_sun
        in 80..82 -> R.drawable.rainingg
        else -> R.drawable.cloudy_with_sun
    }
}