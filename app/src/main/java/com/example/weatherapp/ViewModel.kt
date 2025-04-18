package com.example.weatherapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async // Import async
import kotlinx.coroutines.awaitAll // Import awaitAll nếu gọi nhiều async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive

data class City(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class WeatherDataState(
    var timeList: List<String> = emptyList(),
    var temperatureList: List<Double> = emptyList(),
    var weatherCodeList: List<Int> = emptyList(),
    var precipitationList: List<Double> = emptyList(),
    var humidityList: List<Double> = emptyList(),
    var windSpeedList: List<Double> = emptyList(),
    var uvList: List<Double> = emptyList(),
    var feelsLikeList: List<Double> = emptyList(),
    var pressureList: List<Double> = emptyList(),
    var visibilityList: List<Double> = emptyList(),
    var dailyTimeList: List<String> = emptyList(),
    var dailyTempMaxList: List<Double> = emptyList(),
    var dailyTempMinList: List<Double> = emptyList(),
    var dailyWeatherCodeList: List<Int> = emptyList(),
    var dailyPrecipitationList: List<Double> = emptyList(),
    var lastUpdateTime: Long? = null,
    var errorMessage: String? = null,
    // --- Thêm trường AQI ---
    var currentAqi: Int? = null, // Chỉ số AQI hiện tại (nullable)
    var currentPm25: Double? = null // Chỉ số PM2.5 hiện tại (nullable)
)

data class PlaceSuggestion( // Lớp đơn giản để hiển thị trong UI
    val formattedName: String,
    val city: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?
)

class WeatherViewModel(
    private val weatherDao: WeatherDao,
    private val openMeteoService: OpenMeteoService, // Service for weather forecast
    private val airQualityService: AirQualityService, // Service for air quality
    private val geoapifyService: GeoapifyService = RetrofitInstance.geoapifyApi // Service for place search
) : ViewModel() {
    private var cities by mutableStateOf(
        listOf(
            City("Hà Nội", 21.0285, 105.8542),
            City("TP. Hồ Chí Minh", 10.7769, 106.7009),
            City("Đà Nẵng", 16.0544, 108.2022)
        )
    )

    var weatherDataMap: Map<String, WeatherDataState> by mutableStateOf(emptyMap())
        private set

    var currentCity: String by mutableStateOf(cities.firstOrNull()?.name ?: "Hà Nội")
        private set

    // --- State mới cho tìm kiếm địa điểm ---
    var searchQuery by mutableStateOf("") // State cho nội dung ô tìm kiếm
    var placeSuggestions by mutableStateOf<List<PlaceSuggestion>>(emptyList()) // State cho kết quả gợi ý
        private set
    var isSearching by mutableStateOf(false) // State cho trạng thái loading tìm kiếm
        private set
    var searchError by mutableStateOf<String?>(null) // State cho lỗi tìm kiếm
        private set
    private var searchJob: Job? = null // Job để quản lý coroutine tìm kiếm (cho debounce)

    init {
        cities.forEach { city ->
            fetchWeatherForCity(city.name, city.latitude, city.longitude)
        }
    }

    fun addCity(city: City) {
        if (cities.none { it.name == city.name }) {
            cities = cities + city
            fetchWeatherForCity(city.name, city.latitude, city.longitude)
        }
    }

    fun fetchWeatherForCity(cityName: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                // Gọi API
                val response = withContext(Dispatchers.IO) {
                    // Cân nhắc thêm logging ở đây để xem raw response nếu cần
                    // Ví dụ:
                    // val rawResponse = openMeteoService.getWeatherRaw(latitude, longitude) // Tạo một hàm mới trả về Response<ResponseBody>
                    // Log.d("WeatherViewModel", "Raw response for $cityName: ${rawResponse.body()?.string()}")
                    // Sau đó gọi hàm gốc:
                    openMeteoService.getWeather(
                        latitude = latitude,
                        longitude = longitude
                    )
                }

                // --- Lấy dữ liệu AQI từ response.current ---
                val currentAqiData = response.current?.us_aqi
                val currentPm25Data = response.current?.pm2_5
                Log.d("WeatherViewModel", "AQI data for $cityName: AQI=$currentAqiData, PM2.5=$currentPm25Data")

                // Kiểm tra response và các list con trước khi xử lý
                if (response.hourly == null || response.hourly.time == null || response.hourly.temperature_2m == null ||
                    response.hourly.weathercode == null || response.hourly.precipitation == null ||
                    response.hourly.relative_humidity_2m == null || response.hourly.windspeed_10m == null ||
                    response.hourly.uv_index == null || response.hourly.apparent_temperature == null ||
                    response.hourly.surface_pressure == null || response.hourly.visibility == null) {
                    Log.e("WeatherViewModel", "Hourly data or its lists are null in response for $cityName")
                    // Cập nhật trạng thái lỗi cho thành phố này
                    weatherDataMap = weatherDataMap.toMutableMap().apply {
                        put(cityName, WeatherDataState(errorMessage = "Lỗi: Dữ liệu giờ không đầy đủ từ API."))
                    }
                    return@launch // Thoát khỏi coroutine cho thành phố này
                }

                if (response.daily == null || response.daily.time == null || response.daily.temperature_2m_max == null ||
                    response.daily.temperature_2m_min == null || response.daily.weathercode == null ||
                    response.daily.precipitation_probability_max == null) {
                    Log.e("WeatherViewModel", "Daily data or its lists are null in response for $cityName")
                    // Cập nhật trạng thái lỗi cho thành phố này
                    weatherDataMap = weatherDataMap.toMutableMap().apply {
                        put(cityName, WeatherDataState(errorMessage = "Lỗi: Dữ liệu ngày không đầy đủ từ API."))
                    }
                    return@launch // Thoát khỏi coroutine cho thành phố này
                }


                // Xóa dữ liệu cũ trong IO context
                withContext(Dispatchers.IO) {
                    weatherDao.deleteWeatherDataForCity(cityName)
                    weatherDao.deleteWeatherDetailsForCity(cityName)
                    weatherDao.deleteDailyDetailsForCity(cityName)
                }


                // Lưu dữ liệu vào cơ sở dữ liệu trong IO context
                val weatherDataId = withContext(Dispatchers.IO) {
                    val weatherData = WeatherData(
                        cityName = cityName,
                        latitude = latitude,
                        longitude = longitude,
                        lastUpdated = System.currentTimeMillis()
                    )
                    weatherDao.insertWeatherData(weatherData)
                }


                // Lưu dữ liệu hourly trong IO context
                withContext(Dispatchers.IO) {
                    val hourlyDetails = response.hourly.time.mapIndexedNotNull { index, time ->
                        // Kiểm tra index hợp lệ cho tất cả các list hourly cần dùng
                        if (index < response.hourly.temperature_2m.size &&
                            index < response.hourly.weathercode.size &&
                            index < response.hourly.precipitation.size &&
                            index < response.hourly.relative_humidity_2m.size &&
                            index < response.hourly.windspeed_10m.size &&
                            index < response.hourly.uv_index.size &&
                            index < response.hourly.apparent_temperature.size &&
                            index < response.hourly.surface_pressure.size &&
                            index < response.hourly.visibility.size)
                        {
                            WeatherDetail(
                                weatherDataId = weatherDataId,
                                cityName = cityName,
                                time = time,
                                temperature_2m = response.hourly.temperature_2m[index],
                                weather_code = response.hourly.weathercode[index],
                                precipitation_probability = response.hourly.precipitation[index],
                                relative_humidity_2m = response.hourly.relative_humidity_2m[index],
                                wind_speed_10m = response.hourly.windspeed_10m[index],
                                uv_index = response.hourly.uv_index[index],
                                apparent_temperature = response.hourly.apparent_temperature[index],
                                surface_pressure = response.hourly.surface_pressure[index],
                                visibility = response.hourly.visibility[index]
                            )
                        } else {
                            Log.e("WeatherViewModel", "Inconsistent list sizes in hourly data for $cityName at index $index")
                            null // Bỏ qua nếu index không hợp lệ
                        }
                    }
                    if (hourlyDetails.isNotEmpty()) {
                        weatherDao.insertWeatherDetails(hourlyDetails)
                    } else {
                        Log.w("WeatherViewModel", "No valid hourly details parsed to insert for $cityName")
                    }
                }

                // Lưu dữ liệu daily trong IO context
                withContext(Dispatchers.IO) {
                    val dailyDetails = response.daily.time.mapIndexedNotNull { index, time ->
                        // Kiểm tra index hợp lệ cho tất cả các list daily cần dùng
                        if (index < response.daily.temperature_2m_max.size &&
                            index < response.daily.temperature_2m_min.size &&
                            index < response.daily.weathercode.size &&
                            index < response.daily.precipitation_probability_max.size)
                        {
                            WeatherDailyDetail(
                                weatherDataId = weatherDataId,
                                cityName = cityName,
                                time = time,
                                temperature_2m_max = response.daily.temperature_2m_max[index],
                                temperature_2m_min = response.daily.temperature_2m_min[index],
                                weather_code = response.daily.weathercode[index],
                                precipitation_probability_max = response.daily.precipitation_probability_max[index]
                            )
                        } else {
                            Log.e("WeatherViewModel", "Inconsistent list sizes in daily data for $cityName at index $index")
                            null // Bỏ qua nếu index không hợp lệ
                        }
                    }
                    if (dailyDetails.isNotEmpty()) {
                        weatherDao.insertWeatherDailyDetails(dailyDetails)
                    } else {
                        Log.w("WeatherViewModel", "No valid daily details parsed to insert for $cityName")
                    }
                }


                // Lấy dữ liệu từ cơ sở dữ liệu trong IO context
                val weatherDataWithDetails = withContext(Dispatchers.IO) {
                    weatherDao.getLatestWeatherDataWithDetailsForCity(cityName)
                }
                val weatherDataWithDailyDetails = withContext(Dispatchers.IO) {
                    weatherDao.getLatestWeatherDataWithDailyDetailsForCity(cityName)
                }

                // --- Xử lý dữ liệu để cập nhật State (trên Main thread nếu cần thiết cho UI) ---
                // Chuyển sang Main thread để cập nhật State an toàn
                withContext(Dispatchers.Main) {
                    val newWeatherData = WeatherDataState()

                    // Xử lý hourly
                    if (weatherDataWithDetails != null && weatherDataWithDetails.details.isNotEmpty()) {
                        val details = weatherDataWithDetails.details
                        val now = LocalDateTime.now()
                        val startTime = now.minusHours(1) // Lấy dữ liệu từ 1h trước
                        val endTime = now.plusHours(24) // Lấy dữ liệu đến 24h sau

                        // Lọc dữ liệu trong khoảng thời gian cần thiết
                        val filteredDetails = details.filter {
                            try {
                                val detailTime = LocalDateTime.parse(it.time, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                detailTime.isAfter(startTime) && detailTime.isBefore(endTime)
                            } catch (e: Exception) {
                                Log.e("WeatherViewModel", "Error parsing time string: ${it.time}", e)
                                false // Bỏ qua nếu không parse được thời gian
                            }
                        }

                        if (filteredDetails.isNotEmpty()) {
                            newWeatherData.timeList = filteredDetails.map { it.time }
                            newWeatherData.temperatureList = filteredDetails.map { it.temperature_2m }
                            newWeatherData.weatherCodeList = filteredDetails.map { it.weather_code }
                            newWeatherData.precipitationList = filteredDetails.map { it.precipitation_probability }
                            newWeatherData.humidityList = filteredDetails.map { it.relative_humidity_2m }
                            newWeatherData.windSpeedList = filteredDetails.map { it.wind_speed_10m }
                            newWeatherData.uvList = filteredDetails.map { it.uv_index }
                            newWeatherData.feelsLikeList = filteredDetails.map { it.apparent_temperature }
                            newWeatherData.pressureList = filteredDetails.map { it.surface_pressure }
                            newWeatherData.visibilityList = filteredDetails.map { it.visibility }
                            newWeatherData.lastUpdateTime = weatherDataWithDetails.weatherData.lastUpdated
                            newWeatherData.errorMessage = null
                        } else {
                            Log.w("WeatherViewModel", "No hourly details found within the desired time range for $cityName")
                            newWeatherData.errorMessage = "Không có dữ liệu giờ phù hợp." // Hoặc thông báo khác
                            newWeatherData.lastUpdateTime = weatherDataWithDetails.weatherData.lastUpdated // Vẫn cập nhật thời gian update
                        }

                    } else {
                        Log.w("WeatherViewModel", "No hourly details found in DB for $cityName")
                        newWeatherData.errorMessage = "Không có dữ liệu giờ từ DB."
                        // Có thể lấy lastUpdateTime từ weatherData nếu weatherDataWithDetails.weatherData không null
                        weatherDataWithDetails?.weatherData?.lastUpdated?.let {
                            newWeatherData.lastUpdateTime = it
                        }
                    }

                    // Xử lý daily
                    if (weatherDataWithDailyDetails != null && weatherDataWithDailyDetails.dailyDetails.isNotEmpty()) {
                        val dailyDetails = weatherDataWithDailyDetails.dailyDetails
                        newWeatherData.dailyTimeList = dailyDetails.map { it.time }
                        newWeatherData.dailyTempMaxList = dailyDetails.map { it.temperature_2m_max }
                        newWeatherData.dailyTempMinList = dailyDetails.map { it.temperature_2m_min }
                        newWeatherData.dailyWeatherCodeList = dailyDetails.map { it.weather_code }
                        newWeatherData.dailyPrecipitationList = dailyDetails.map { it.precipitation_probability_max }
                        // Nếu hourly data bị lỗi, xóa errorMessage ở đây nếu daily data thành công
                        if(newWeatherData.errorMessage != null && newWeatherData.dailyTimeList.isNotEmpty()) {
                            // Giữ lại errorMessage nếu cả hourly và daily đều lỗi, hoặc chỉ hiển thị lỗi nếu cả 2 đều rỗng
                            if (newWeatherData.timeList.isEmpty()) {
                                // Có thể giữ lỗi cũ hoặc cập nhật lỗi mới
                            } else {
                                newWeatherData.errorMessage = null // Có dữ liệu daily, xóa lỗi nếu có dữ liệu hourly
                            }
                        }
                    } else {
                        Log.w("WeatherViewModel", "No daily details found in DB for $cityName")
                        // Chỉ đặt lỗi nếu hourly cũng không có dữ liệu
                        if (newWeatherData.timeList.isEmpty()) {
                            newWeatherData.errorMessage = "Không có dữ liệu ngày từ DB."
                        }
                        // Có thể lấy lastUpdateTime từ weatherData nếu weatherDataWithDailyDetails.weatherData không null
                        weatherDataWithDailyDetails?.weatherData?.lastUpdated?.let {
                            // Chỉ cập nhật nếu lastUpdateTime hiện tại là null hoặc cũ hơn
                            if (newWeatherData.lastUpdateTime == null || it > newWeatherData.lastUpdateTime!!) {
                                newWeatherData.lastUpdateTime = it
                            }
                        }
                    }

                    newWeatherData.currentAqi = currentAqiData
                    newWeatherData.currentPm25 = currentPm25Data // Lưu cả PM2.5 nếu muốn hiển thị

                    // Cập nhật dữ liệu thời tiết cho thành phố
                    weatherDataMap = weatherDataMap.toMutableMap().apply {
                        put(cityName, newWeatherData)
                    }
                } // end withContext(Dispatchers.Main)

            } catch (e: Exception) {
                // Log lỗi gốc xảy ra trong try block
                Log.e("WeatherViewModel", "Lỗi khi lấy dữ liệu cho $cityName: ${e.message}", e)
                // Cập nhật trạng thái lỗi trên Main thread
                withContext(Dispatchers.Main) {
                    weatherDataMap = weatherDataMap.toMutableMap().apply {
                        // Giữ lại dữ liệu cũ nếu có, chỉ cập nhật lỗi
                        val existingState = get(cityName) ?: WeatherDataState()
                        existingState.errorMessage = "Lỗi khi tải dữ liệu: ${e.message}"
                        put(cityName, existingState)
                    }
                }
            }
        }
    }

    fun fetchWeatherAndAirQuality(cityName: String, latitude: Double, longitude: Double) {
        Log.i("WeatherViewModel", "Starting fetch for $cityName")
        // Optional: Show loading state immediately for this city
        updateStateWithLoading(cityName)

        viewModelScope.launch {
            var weatherResponse: WeatherRespone.WeatherResponse? = null
            var airQualityResponse: AirQualityResponse? = null
            var fetchError: Exception? = null

            try {
                // Launch both API calls concurrently using async
                val weatherDeferred = async(Dispatchers.IO) {
                    Log.d("WeatherViewModel", "Fetching weather for $cityName (Lat: $latitude, Lon: $longitude)")
                    openMeteoService.getWeather(latitude, longitude)
                }
                val airQualityDeferred = async(Dispatchers.IO) {
                    Log.d("WeatherViewModel", "Fetching air quality for $cityName (Lat: $latitude, Lon: $longitude)")
                    airQualityService.getAirQuality(latitude, longitude) // Gọi hàm mới qua service mới
                }

                // Await results from both calls
                weatherResponse = weatherDeferred.await()
                airQualityResponse = airQualityDeferred.await()
                Log.i("WeatherViewModel", "Finished fetching both APIs for $cityName")

            } catch (e: CancellationException) {
                Log.i("WeatherViewModel", "Fetch job cancelled for $cityName")
                // If cancelled, we might want to revert loading state if we set it earlier
                // Or just let the UI handle the potentially empty state
                return@launch // Exit the coroutine
            } catch (e: Exception) {
                // Catch errors from either API call
                Log.e("WeatherViewModel", "Error fetching data for $cityName: ${e.message}", e)
                fetchError = e // Store the error
            }

            // --- Data Processing and State Update ---

            // Process results and update the state on the Main thread
            withContext(Dispatchers.Main) {
                val newState = processApiResponse(cityName, weatherResponse, airQualityResponse, fetchError)
                // Update the map with the new state
                weatherDataMap = weatherDataMap.toMutableMap().apply {
                    put(cityName, newState)
                }
                Log.d("WeatherViewModel", "Updated state for $cityName. AQI: ${newState.currentAqi}, Error: ${newState.errorMessage}")
            }
        }
    }
    private suspend fun processApiResponse( // Đánh dấu suspend vì có thể gọi hàm IO bên trong (dù hiện tại không gọi)
        cityName: String,
        weatherResp: WeatherRespone.WeatherResponse?,
        aqiResp: AirQualityResponse?,
        error: Exception?
    ): WeatherDataState {
        val currentState = weatherDataMap[cityName] ?: WeatherDataState() // Get current state or create new
        val newState = WeatherDataState() // Start with a fresh state object
        var weatherDbSaved = false // Flag to check if weather data was saved to DB

        // Process and Save Weather Data (if successful)
        if (weatherResp != null && weatherResp.hourly != null && weatherResp.daily != null) {
            try {
                // Save to DB first (in IO context)
                // Giả sử bạn có hàm get latitude/longitude từ cityName nếu cần, hoặc lấy từ tham số
                val cityInfo = cities.find { it.name == cityName } // Tìm lại city để lấy lat/lon
                val latitude = cityInfo?.latitude ?: 0.0 // Cần lat/lon để lưu WeatherData
                val longitude = cityInfo?.longitude ?: 0.0

                val weatherDataId = withContext(Dispatchers.IO) {
                    val weatherData = WeatherData(
                        cityName = cityName, latitude = latitude, longitude = longitude,
                        lastUpdated = System.currentTimeMillis()
                    )
                    weatherDao.insertWeatherData(weatherData)
                }

                // Save hourly details
                withContext(Dispatchers.IO) {
                    val hourlyDetails = weatherResp.hourly.time?.mapIndexedNotNull { index, time ->
                        // Safe mapping with index and null checks for all required lists
                        val temp = weatherResp.hourly.temperature_2m?.getOrNull(index)
                        val code = weatherResp.hourly.weathercode?.getOrNull(index)
                        val precip = weatherResp.hourly.precipitation?.getOrNull(index)
                        val humid = weatherResp.hourly.relative_humidity_2m?.getOrNull(index)
                        val wind = weatherResp.hourly.windspeed_10m?.getOrNull(index)
                        val uv = weatherResp.hourly.uv_index?.getOrNull(index)
                        val feels = weatherResp.hourly.apparent_temperature?.getOrNull(index)
                        val pressure = weatherResp.hourly.surface_pressure?.getOrNull(index) // surface_pressure
                        val vis = weatherResp.hourly.visibility?.getOrNull(index)

                        if (time != null && temp != null && code != null && precip != null && humid != null && wind != null && uv != null && feels != null && pressure != null && vis != null) {
                            WeatherDetail(
                                weatherDataId = weatherDataId, cityName = cityName, time = time,
                                temperature_2m = temp, weather_code = code, precipitation_probability = precip,
                                relative_humidity_2m = humid, wind_speed_10m = wind, uv_index = uv,
                                apparent_temperature = feels, surface_pressure = pressure, visibility = vis
                            )
                        } else {
                            Log.w("WeatherViewModel", "Incomplete hourly data at index $index for $cityName")
                            null
                        }
                    }
                    if (!hourlyDetails.isNullOrEmpty()) weatherDao.insertWeatherDetails(hourlyDetails)
                }

                // Save daily details
                withContext(Dispatchers.IO) {
                    val dailyDetails = weatherResp.daily.time?.mapIndexedNotNull { index, time ->
                        // Safe mapping with index and null checks
                        val maxT = weatherResp.daily.temperature_2m_max?.getOrNull(index)
                        val minT = weatherResp.daily.temperature_2m_min?.getOrNull(index)
                        val code = weatherResp.daily.weathercode?.getOrNull(index)
                        val precip = weatherResp.daily.precipitation_probability_max?.getOrNull(index)

                        if (time != null && maxT != null && minT != null && code != null && precip != null) {
                            WeatherDailyDetail(
                                weatherDataId = weatherDataId, cityName = cityName, time = time,
                                temperature_2m_max = maxT, temperature_2m_min = minT,
                                weather_code = code, precipitation_probability_max = precip
                            )
                        } else {
                            Log.w("WeatherViewModel", "Incomplete daily data at index $index for $cityName")
                            null
                        }
                    }
                    if (!dailyDetails.isNullOrEmpty()) weatherDao.insertWeatherDailyDetails(dailyDetails)
                }
                weatherDbSaved = true // Mark as saved if no DB error
                Log.d("WeatherViewModel", "Weather data saved to DB for $cityName")

                // Populate newState from the successful response (no need to read back from DB immediately)
                newState.timeList = weatherResp.hourly.time ?: emptyList()
                newState.temperatureList = weatherResp.hourly.temperature_2m?.mapNotNull { it } ?: emptyList()
                newState.weatherCodeList = weatherResp.hourly.weathercode?.mapNotNull { it } ?: emptyList()
                newState.precipitationList = weatherResp.hourly.precipitation?.mapNotNull { it } ?: emptyList()
                newState.humidityList = weatherResp.hourly.relative_humidity_2m?.mapNotNull { it } ?: emptyList()
                newState.windSpeedList = weatherResp.hourly.windspeed_10m?.mapNotNull { it } ?: emptyList()
                newState.uvList = weatherResp.hourly.uv_index?.mapNotNull { it } ?: emptyList()
                newState.feelsLikeList = weatherResp.hourly.apparent_temperature?.mapNotNull { it } ?: emptyList()
                newState.pressureList = weatherResp.hourly.surface_pressure?.mapNotNull { it } ?: emptyList()
                newState.visibilityList = weatherResp.hourly.visibility?.mapNotNull { it } ?: emptyList()
                newState.dailyTimeList = weatherResp.daily.time ?: emptyList()
                newState.dailyTempMaxList = weatherResp.daily.temperature_2m_max?.mapNotNull { it } ?: emptyList()
                newState.dailyTempMinList = weatherResp.daily.temperature_2m_min?.mapNotNull { it } ?: emptyList()
                newState.dailyWeatherCodeList = weatherResp.daily.weathercode?.mapNotNull { it } ?: emptyList()
                newState.dailyPrecipitationList = weatherResp.daily.precipitation_probability_max?.mapNotNull { it } ?: emptyList()
                newState.lastUpdateTime = System.currentTimeMillis()


            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Error saving weather data to DB for $cityName", e)
                // If DB save failed, try to keep old data
                newState.copyWeatherFieldsFrom(currentState)
                // Maybe set a specific DB error message?
                newState.errorMessage = "Lỗi lưu trữ dữ liệu thời tiết."
            }
        } else {
            // Keep old weather data if fetch failed
            newState.copyWeatherFieldsFrom(currentState)
            Log.w("WeatherViewModel", "Weather response null or incomplete for $cityName, keeping old weather data if available.")
        }

        // Process Air Quality Data
        if (aqiResp != null && aqiResp.current != null) {
            newState.currentAqi = aqiResp.current.us_aqi
            newState.currentPm25 = aqiResp.current.pm2_5
            // Update lastUpdateTime if AQI is newer or weather failed/wasn't saved
            if (!weatherDbSaved || newState.lastUpdateTime == null) {
                newState.lastUpdateTime = System.currentTimeMillis()
            }
        } else {
            // Keep old AQI data if fetch failed
            newState.currentAqi = currentState.currentAqi
            newState.currentPm25 = currentState.currentPm25
            Log.w("WeatherViewModel", "AirQuality response null or incomplete for $cityName, keeping old AQI data if available.")
        }

        // Final error handling
        if (error != null && !weatherDbSaved && newState.currentAqi == null) {
            // Only show fetch error if nothing was successful
            newState.errorMessage = "Lỗi tải dữ liệu: ${error.message}"
        } else if (newState.timeList.isEmpty() && newState.dailyTimeList.isEmpty() && newState.currentAqi == null && error == null) {
            // No error, but no data
            newState.errorMessage = "Không có dữ liệu."
        } else if (error == null && (newState.timeList.isNotEmpty() || newState.dailyTimeList.isNotEmpty() || newState.currentAqi != null)) {
            // Has some data and no fetch error
            newState.errorMessage = null // Clear previous errors if data is now available
        }
        // If there was a DB error but fetch was okay, errorMessage might already be set

        // Ensure last update time is carried over if nothing new was fetched at all
        if (newState.lastUpdateTime == null) {
            newState.lastUpdateTime = currentState.lastUpdateTime
        }

        return newState
    }


    /** Helper function to copy weather fields from one state to another */
    private fun WeatherDataState.copyWeatherFieldsFrom(source: WeatherDataState) {
        this.timeList = source.timeList
        this.temperatureList = source.temperatureList
        this.weatherCodeList = source.weatherCodeList
        this.precipitationList = source.precipitationList
        this.humidityList = source.humidityList
        this.windSpeedList = source.windSpeedList
        this.uvList = source.uvList
        this.feelsLikeList = source.feelsLikeList
        this.pressureList = source.pressureList
        this.visibilityList = source.visibilityList
        this.dailyTimeList = source.dailyTimeList
        this.dailyTempMaxList = source.dailyTempMaxList
        this.dailyTempMinList = source.dailyTempMinList
        this.dailyWeatherCodeList = source.dailyWeatherCodeList
        this.dailyPrecipitationList = source.dailyPrecipitationList
        // Do not copy AQI fields or lastUpdateTime or errorMessage here
    }


    /** Helper to update state for a city to show loading (clears error) */
    private fun updateStateWithLoading(cityName: String) {
        val currentState = weatherDataMap[cityName] ?: WeatherDataState()
        // Create a new state that keeps old data but sets error to null (loading)
        // Or maybe add an explicit isLoading flag to WeatherDataState?
        // For now, just ensure error is cleared before fetch starts.
        val loadingState = currentState.copy(errorMessage = null) // Keep old data, clear error

        weatherDataMap = weatherDataMap.toMutableMap().apply {
            put(cityName, loadingState)
        }
    }


    fun updateCurrentCity(cityName: String) {
        currentCity = cityName
    }

    val citiesList: List<City>
        get() = cities

    fun getCurrentIndex(cityName: String): Int {
        val data = weatherDataMap[cityName] ?: return 0
        if (data.timeList.isEmpty()) return 0 // Tránh lỗi nếu list rỗng

        val now = LocalDateTime.now()
        val index = data.timeList.indexOfLast { time ->
            try {
                val detailTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                detailTime.isBefore(now) || detailTime == now
            } catch (e: Exception) {
                false // Bỏ qua nếu không parse được
            }
        }
        // Nếu không tìm thấy index nào trước hoặc bằng thời gian hiện tại (có thể do list chỉ có dữ liệu tương lai), trả về 0
        return if (index >= 0) index else 0
    }


    fun getUpcomingForecast(cityName: String): List<Triple<String, Double, Int>> {
        val data = weatherDataMap[cityName] ?: return emptyList()
        if (data.timeList.isEmpty()) return emptyList() // Tránh lỗi nếu list rỗng

        val index = getCurrentIndex(cityName)
        // Đảm bảo index không vượt quá giới hạn của các list khác
        val endIndex = (index + 24).coerceAtMost(data.timeList.size - 1)
            .coerceAtMost(data.temperatureList.size - 1)
            .coerceAtMost(data.weatherCodeList.size - 1)

        if (index > endIndex) return emptyList() // Trường hợp index không hợp lệ

        // Lấy dữ liệu từ index đến endIndex
        return (index..endIndex).mapNotNull { i ->
            // Kiểm tra lại i có hợp lệ cho tất cả các list không (dù đã coerce ở trên)
            if (i < data.timeList.size && i < data.temperatureList.size && i < data.weatherCodeList.size) {
                Triple(data.timeList[i], data.temperatureList[i], data.weatherCodeList[i])
            } else {
                null // Bỏ qua nếu có sự không nhất quán nào đó
            }
        }
    }


    fun getDailyForecast(cityName: String, days: Int): List<Triple<String, Pair<Double, Double>, Int>> {
        val data = weatherDataMap[cityName] ?: return emptyList()
        if (data.dailyTimeList.isEmpty()) return emptyList() // Tránh lỗi

        val actualDays = days.coerceAtMost(data.dailyTimeList.size)
            .coerceAtMost(data.dailyTempMaxList.size)
            .coerceAtMost(data.dailyTempMinList.size)
            .coerceAtMost(data.dailyWeatherCodeList.size)

        return (0 until actualDays).mapNotNull { index ->
            // Kiểm tra lại index (dù đã coerce)
            if (index < data.dailyTimeList.size &&
                index < data.dailyTempMaxList.size &&
                index < data.dailyTempMinList.size &&
                index < data.dailyWeatherCodeList.size)
            {
                Triple(
                    data.dailyTimeList[index],
                    Pair(data.dailyTempMaxList[index], data.dailyTempMinList[index]),
                    data.dailyWeatherCodeList[index]
                )
            } else {
                null
            }
        }
    }

    // Hàm được gọi khi text trong ô tìm kiếm thay đổi
    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        searchJob?.cancel() // Hủy job tìm kiếm cũ nếu có

        if (query.length < 3) { // Chỉ tìm kiếm nếu query đủ dài (vd: >= 3 ký tự)
            placeSuggestions = emptyList()
            isSearching = false
            searchError = null
            return
        }

        isSearching = true // Bắt đầu trạng thái loading
        searchError = null // Xóa lỗi cũ

        searchJob = viewModelScope.launch {
            delay(500L) // Debounce: Chờ 500ms sau khi người dùng ngừng gõ mới tìm kiếm
            try {
                val response = withContext(Dispatchers.IO) {
                    geoapifyService.searchPlaces(
                        searchText = query,
                        apiKey = RetrofitInstance.GEOAPIFY_API_KEY // Lấy API key
                    )
                }

                // Xử lý kết quả trả về
                val suggestions = response.features?.mapNotNull { feature ->
                    val props = feature.properties
                    val geometry = feature.geometry
                    var lat = props?.lat
                    var lon = props?.lon

                    // Nếu lat/lon null trong properties, thử lấy từ geometry
                    if ((lat == null || lon == null) && geometry?.type == "Point" && geometry.coordinates?.size == 2) {
                        lon = geometry.coordinates[0]
                        lat = geometry.coordinates[1]
                    }

                    if (props?.formatted != null && lat != null && lon != null) {
                        PlaceSuggestion(
                            formattedName = props.formatted,
                            city = props.city ?: props.county ?: props.state, // Ưu tiên city, county, state
                            country = props.country,
                            latitude = lat,
                            longitude = lon
                        )
                    } else {
                        null // Bỏ qua nếu thiếu thông tin cần thiết
                    }
                } ?: emptyList() // Trả về list rỗng nếu features là null

                placeSuggestions = suggestions
                searchError = if (suggestions.isEmpty() && query.isNotEmpty()) "Không tìm thấy kết quả nào." else null

            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Lỗi tìm kiếm địa điểm: ${e.message}", e)
                searchError = "Lỗi khi tìm kiếm: ${e.message}"
                placeSuggestions = emptyList()
            } finally {
                isSearching = false // Kết thúc trạng thái loading
            }
        }
    }

    // Hàm xóa kết quả tìm kiếm (khi đóng overlay hoặc chọn 1 địa điểm)
    fun clearSearch() {
        searchQuery = ""
        placeSuggestions = emptyList()
        isSearching = false
        searchError = null
        searchJob?.cancel()
    }

}
