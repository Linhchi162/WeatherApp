package com.example.weatherapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.util.Log
import kotlinx.coroutines.CancellationException
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.gms.maps.model.LatLng
import android.widget.Toast


data class City(
    val name: String,
    val latitude: Double,

    val longitude: Double,
    val country: String? = null

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
    var dailySunriseList: List<String> = emptyList(),
    var dailySunsetList: List<String> = emptyList(),
    var lastUpdateTime: Long? = null,
    var errorMessage: String? = null,

    var currentAqi: Int? = null,
    var currentPm25: Double? = null,

    var radarCache: Map<String, RadarLayerCache> = emptyMap(),
    var radarLastUpdate: Long? = null
)

data class RadarLayerCache(
    val layer: String,
    val tiles: List<RadarTile>,
    val timestamp: Long,
    val isComplete: Boolean = false
)

data class RadarTile(
    val x: Int,
    val y: Int,
    val zoom: Int,
    val bitmapData: ByteArray? = null,
    val isLoaded: Boolean = false,
    val bounds: com.google.android.gms.maps.model.LatLngBounds? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RadarTile
        return x == other.x && y == other.y && zoom == other.zoom
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + zoom
        return result
    }
}



data class PlaceSuggestion(
    val formattedName: String,
    val city: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?
)

class WeatherViewModel(
    private val weatherDao: WeatherDao,
    private val openMeteoService: OpenMeteoService,
    private val airQualityService: AirQualityService,

    private val geoNamesService: GeoNamesService = RetrofitInstance.geoNamesApi,
    private val appContext: Context? = null
) : ViewModel() {
    // Hằng số cho debug log
    companion object {
        const val DEBUG_TAG = "API_CITY_CHECK"
        const val RADAR_CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 phút
        const val RADAR_DEFAULT_ZOOM = 5
    }

    private var cities by mutableStateOf(
        listOf<City>() // Không khởi tạo sẵn danh sách thành phố
    )

    // Lưu trữ danh sách thành phố gốc trước khi lọc
    private var originalCities: List<City> = emptyList()

    var weatherDataMap: Map<String, WeatherDataState> by mutableStateOf(emptyMap())
        private set

    var currentCity: String by mutableStateOf("")
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

    // State cho bộ lọc
    var selectedFilterCountry by mutableStateOf("")
        private set
    var temperatureFilterRange by mutableStateOf(-20f..50f)
        private set
    var windSpeedFilterRange by mutableStateOf(0f..100f)
        private set
    var humidityFilterRange by mutableStateOf(0f..100f)
        private set
    var weatherStateFilter by mutableStateOf("Tất cả")
        private set
    
    // State cho danh sách quốc gia từ API
    var availableCountries by mutableStateOf<List<CountryInfo>>(emptyList())
        private set
    var isLoadingCountries by mutableStateOf(false)
        private set
    var countriesError by mutableStateOf<String?>(null)
        private set
    
    // State cho thành phố đã lọc
    var filteredCities by mutableStateOf<List<City>>(emptyList())
        private set
    var isFiltering by mutableStateOf(false)
        private set

    private val gson = Gson()
    private val PREFS_NAME = "weather_prefs"
    private val CITIES_KEY = "cities_json"

    private fun saveCitiesToPrefs() {
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(cities)
            prefs.edit().putString(CITIES_KEY, json).apply()
        }
    }

    private fun loadCitiesFromPrefs() {
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(CITIES_KEY, null)
            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<List<City>>() {}.type
                val loaded = gson.fromJson<List<City>>(json, type)
                if (loaded != null) {
                    cities = loaded
                }
            }
        }
    }

    init {
        // Khởi tạo filteredCities ban đầu bằng toàn bộ cities (sẽ là rỗng)
        filteredCities = cities
        
        // Không thêm thành phố mặc định nữa, để người dùng luôn phải lấy vị trí thực
        Log.d("WeatherViewModel", "Khởi tạo ViewModel - không có thành phố mặc định")
        
        // Khởi tạo trạng thái ban đầu
        cities = emptyList() // Danh sách rỗng
        weatherDataMap = emptyMap() // Map rỗng
        currentCity = "" // Không có thành phố hiện tại
        
        // Fetch danh sách quốc gia từ API
        fetchCountriesFromAPI()
        loadCitiesFromPrefs()
    }

    // Hàm thêm thành phố với tùy chọn thêm vào đầu danh sách
    fun addCity(city: City, isPrimary: Boolean = false) {
        if (cities.none { it.name == city.name }) {
            Log.d("WeatherViewModel", "Adding city: ${city.name}, isPrimary: $isPrimary")
            
            // Cập nhật state trước để UI phản hồi nhanh
            val updatedCities = if (isPrimary) {
                // Thêm vào đầu danh sách nếu là isPrimary
                listOf(city) + cities 
            } else {
                // Thêm vào cuối danh sách theo mặc định
                cities + city
            }
            cities = updatedCities
            
            // Cập nhật originalCities nếu chưa có (để đảm bảo sync)
            if (originalCities.isEmpty()) {
                originalCities = updatedCities.toList()
            } else {
                // Thêm thành phố mới vào originalCities nếu đã có
                originalCities = if (isPrimary) {
                    listOf(city) + originalCities 
                } else {
                    originalCities + city
                }
            }
            
            // Cập nhật map (có thể thêm entry rỗng hoặc loading)
            weatherDataMap = weatherDataMap.toMutableMap().apply {
                put(city.name, WeatherDataState()) // Thêm entry mới
            }
            
            // Fetch data cho thành phố mới
            fetchWeatherAndAirQuality(city.name, city.latitude, city.longitude)
            
            // Preload radar data để cải thiện performance
            preloadRadarForCity(city.name)
            
            // Cập nhật currentCity
            updateCurrentCity(city.name)
            
            Log.d("WeatherViewModel", "City ${city.name} added and set as current city. Fetching weather data.")
            
            // Lưu danh sách thành phố mới vào DB/SharedPreferences nếu cần
            // saveCitiesToDb(updatedCities)
            saveCitiesToPrefs()
        } else {
            Log.d("WeatherViewModel", "City already exists: ${city.name}")
            // Nếu thành phố đã tồn tại, có thể chỉ cần chuyển đến nó
            updateCurrentCity(city.name)
            
            // Refresh data for the existing city to ensure latest data
            val existingCity = cities.find { it.name == city.name }
            if (existingCity != null) {
                Log.d("WeatherViewModel", "Refreshing data for existing city: ${city.name}")
                fetchWeatherAndAirQuality(existingCity.name, existingCity.latitude, existingCity.longitude)
            }
        }
        clearSearch()
    }

    // Hàm mới để xử lý khi người dùng chọn từ gợi ý
    fun onPlaceSuggestionSelected(suggestion: PlaceSuggestion) {
        if (suggestion.latitude != null && suggestion.longitude != null) {
            // Thêm thành phố mới với thông tin từ suggestion
            addCity(
                City(
                    name = suggestion.city ?: suggestion.formattedName,
                    latitude = suggestion.latitude,
                    longitude = suggestion.longitude,
                    country = suggestion.country
                )
            )
        }
    }

    // --- HÀM MỚI ĐỂ XÓA THÀNH PHỐ ---
    fun deleteCity(cityNameToDelete: String) {
        viewModelScope.launch {
            Log.d("WeatherViewModel", "Deleting city: $cityNameToDelete")
            // 1. Cập nhật State List Cities
            val currentList = cities
            val cityToRemove = currentList.find { it.name == cityNameToDelete }
            if (cityToRemove != null) {
                val newList = currentList - cityToRemove
                cities = newList // Cập nhật state

                // Cập nhật originalCities nếu có
                if (originalCities.isNotEmpty()) {
                    originalCities = originalCities.filter { it.name != cityNameToDelete }
                }

                // 2. Clear radar cache for deleted city first
                clearRadarCache(cityNameToDelete)

                // 3. Cập nhật State Weather Map
                weatherDataMap = weatherDataMap.toMutableMap().apply {
                    remove(cityNameToDelete)
                }

                // 4. Cập nhật currentCity nếu thành phố bị xóa là thành phố hiện tại
                if (currentCity == cityNameToDelete) {
                    currentCity = cities.firstOrNull()?.name ?: "" // Chuyển về thành phố đầu tiên hoặc rỗng
                    Log.d("WeatherViewModel", "Current city after delete: $currentCity")
                }

                // 4. Xóa khỏi Database (trong background)
                withContext(Dispatchers.IO) {
                    try {
                        weatherDao.deleteWeatherDataForCity(cityNameToDelete)
                        weatherDao.deleteWeatherDetailsForCity(cityNameToDelete)
                        weatherDao.deleteDailyDetailsForCity(cityNameToDelete)
                        Log.i("WeatherViewModel", "Deleted $cityNameToDelete from Database")
                        // Xóa khỏi danh sách lưu trữ nếu có
                        // deleteCityFromStorage(cityNameToDelete)
                    } catch (e: Exception) {
                        Log.e("WeatherViewModel", "Error deleting $cityNameToDelete from DB", e)
                        // Có thể thông báo lỗi hoặc xử lý khác
                    }
                }
            } else {
                Log.w("WeatherViewModel", "City not found for deletion: $cityNameToDelete")
            }

            saveCitiesToPrefs()
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
                                precipitation_probability_max = response.daily.precipitation_probability_max[index],
                                sunrise = response.daily.sunrise[index],
                                sunset = response.daily.sunset[index]
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
                        newWeatherData.dailySunriseList = dailyDetails.map { it.sunrise }
                        newWeatherData.dailySunsetList = dailyDetails.map { it.sunset }
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

            val weatherResult = runCatching {
                openMeteoService.getWeather(latitude, longitude)
            }
            val airQualityResult = runCatching {
                airQualityService.getAirQuality(latitude, longitude)
            }
            if (weatherResult.isSuccess) {
                weatherResponse = weatherResult.getOrNull()
            } else {
                fetchError = weatherResult.exceptionOrNull() as? Exception
            }
            if (airQualityResult.isSuccess) {
                airQualityResponse = airQualityResult.getOrNull()
            } else if (fetchError == null) {
                fetchError = airQualityResult.exceptionOrNull() as? Exception
            }

            // --- Data Processing and State Update ---
            withContext(Dispatchers.Main) {
                val newState = processApiResponse(cityName, weatherResponse, airQualityResponse, fetchError)
                weatherDataMap = weatherDataMap.toMutableMap().apply {
                    put(cityName, newState)
                }
                if (fetchError != null) {
                    appContext?.let { ctx ->
                        Toast.makeText(ctx, "Không có kết nối mạng hoặc lỗi dữ liệu: ${fetchError?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
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
                                weather_code = code, precipitation_probability_max = precip,
                                sunrise = weatherResp.daily.sunrise[index],
                                sunset = weatherResp.daily.sunset[index]
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
                newState.dailySunriseList = weatherResp.daily.sunrise?.mapNotNull { it } ?: emptyList()
                newState.dailySunsetList = weatherResp.daily.sunset?.mapNotNull { it } ?: emptyList()
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
            newState.errorMessage = "Không có kết nối internet"
        } else if (newState.timeList.isEmpty() && newState.dailyTimeList.isEmpty() && newState.currentAqi == null && error == null) {
            // No error, but no data
            newState.errorMessage = "Không có kết nối internet"
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
        this.dailySunriseList = source.dailySunriseList
        this.dailySunsetList = source.dailySunsetList
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
        Log.d("WeatherViewModel", "Updating current city from '${currentCity}' to '${cityName}'")
        
        // Set the current city immediately
        currentCity = cityName
        
        // Lưu currentCity vào SharedPreferences nếu có context
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("current_city", cityName).apply()
        }
        
        // If there's no data for this city yet or it's stale, fetch it
        val currentData = weatherDataMap[cityName]
        if (currentData == null || currentData.lastUpdateTime == null || 
            System.currentTimeMillis() - currentData.lastUpdateTime!! > 15 * 60 * 1000) {
            
            // Find the city object to get coordinates
            val city = cities.find { it.name == cityName }
            if (city != null) {
                Log.d("WeatherViewModel", "No data or stale data for ${cityName}, fetching new data")
                fetchWeatherAndAirQuality(cityName, city.latitude, city.longitude)
            }
        }
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

    fun onSearchQueryChanged(query: String, type: String = "city") {
        searchQuery = query
        searchJob?.cancel() // Hủy job tìm kiếm cũ nếu có

        if (query.length < 2) { // Sửa từ 3 xuống 2 ký tự
            placeSuggestions = emptyList()
            isSearching = false
            searchError = null
            return
        }

        isSearching = true // Bắt đầu trạng thái loading
        searchError = null // Xóa lỗi cũ

        searchJob = viewModelScope.launch {
            delay(300L) // Debounce: Chờ 300ms sau khi người dùng ngừng gõ mới tìm kiếm
            try {
                // Gọi song song hai ngôn ngữ
                val (responseVi, responseEn) = withContext(Dispatchers.IO) {
                    val vi = async { geoNamesService.getCitiesByCountry(
                        countryCode = "",
                        featureClass = "P",
                        maxRows = 10,
                        orderBy = "relevance",
                        username = RetrofitInstance.GEONAMES_USERNAME,
                        language = "vi",
                        q = query
                    ) }
                    val en = async { geoNamesService.getCitiesByCountry(
                        countryCode = "",
                        featureClass = "P",
                        maxRows = 10,
                        orderBy = "relevance",
                        username = RetrofitInstance.GEONAMES_USERNAME,
                        language = "en",
                        q = query
                    ) }
                    Pair(vi.await(), en.await())
                }

                // Gộp kết quả, loại trùng theo tên và toạ độ
                val allGeoNames = (responseVi.geonames.orEmpty() + responseEn.geonames.orEmpty())
                val uniqueGeoNames = allGeoNames.distinctBy { it.name.lowercase() + "_" + it.lat + "_" + it.lng }

                val suggestions = uniqueGeoNames.map { geoCity ->
                    PlaceSuggestion(
                        formattedName = geoCity.name,
                        city = geoCity.name,
                        country = geoCity.countryName,
                        latitude = geoCity.lat.toDoubleOrNull(),
                        longitude = geoCity.lng.toDoubleOrNull()
                    )
                }
                placeSuggestions = suggestions
                isSearching = false
                searchError = if (suggestions.isEmpty()) "Không tìm thấy địa điểm phù hợp" else null
            } catch (e: Exception) {
                placeSuggestions = emptyList()
                isSearching = false
                searchError = "Lỗi khi tìm kiếm: ${e.message}"
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


    // Lấy mô tả thời tiết từ mã thời tiết
    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Trời quang"
            1 -> "Nắng nhẹ"
            2 -> "Mây rải rác"
            3 -> "Nhiều mây"
            45, 48 -> "Sương mù"
            51, 53, 55 -> "Mưa phùn"
            56, 57 -> "Mưa phùn đông đá"
            61 -> "Mưa nhỏ"
            63 -> "Mưa vừa"
            65 -> "Mưa to"
            66, 67 -> "Mưa đông đá"
            71 -> "Tuyết rơi nhẹ"
            73 -> "Tuyết rơi vừa"
            75 -> "Tuyết rơi dày"
            77 -> "Hạt tuyết"
            80 -> "Mưa rào nhẹ"
            81 -> "Mưa rào vừa"
            82 -> "Mưa rào dữ dội"
            85 -> "Mưa tuyết nhẹ"
            86 -> "Mưa tuyết nặng"
            95 -> "Dông"
            96, 99 -> "Dông có mưa đá"
            else -> "Không xác định"
        }
    }

    // Getter cho GeoNames API để các màn hình khác truy cập
    val geoNamesApi get() = geoNamesService
    
    // Hàm cập nhật giá trị bộ lọc
    fun updateFilters(
        country: String? = null,
        temperatureRange: ClosedFloatingPointRange<Float>? = null,
        windSpeedRange: ClosedFloatingPointRange<Float>? = null,
        humidityRange: ClosedFloatingPointRange<Float>? = null,
        weatherState: String? = null
    ) {
        if (country != null) selectedFilterCountry = country
        if (temperatureRange != null) temperatureFilterRange = temperatureRange
        if (windSpeedRange != null) windSpeedFilterRange = windSpeedRange
        if (humidityRange != null) humidityFilterRange = humidityRange
        if (weatherState != null) weatherStateFilter = weatherState
    }
    
    // Hàm áp dụng bộ lọc và trả về kết quả
    fun applyFilters() {
        viewModelScope.launch {
            isFiltering = true
            
            // Lưu danh sách thành phố gốc trước khi lọc
            if (originalCities.isEmpty()) {
                originalCities = cities.toList()
                Log.d("WeatherViewModel", "Lưu originalCities: ${originalCities.size} thành phố")
            }
            
            try {
                Log.d("WeatherViewModel", "Bắt đầu lọc với quốc gia: ${if(selectedFilterCountry.isEmpty()) "<không chọn>" else selectedFilterCountry}")
                
                // Tạo danh sách tạm thời để lọc mà không thay đổi cities gốc
                var citiesToFilter = cities.toList()
                
                // Chỉ tìm kiếm thành phố của quốc gia cụ thể khi đã chọn quốc gia
                if (selectedFilterCountry.isNotEmpty()) {
                    Log.d("WeatherViewModel", "Tìm kiếm các thành phố cho quốc gia: $selectedFilterCountry")
                    val additionalCities = fetchCitiesForFiltering(selectedFilterCountry)
                    citiesToFilter = (citiesToFilter + additionalCities).distinctBy { it.name }
                    // Tạm dừng để đảm bảo kết quả được cập nhật
                    delay(3000)
                } else {
                    Log.d("WeatherViewModel", "Không có quốc gia được chọn, bỏ qua việc tìm kiếm thành phố mới")
                }
                
                Log.d("WeatherViewModel", "Danh sách thành phố hiện tại: ${cities.size} thành phố")
                
                // Sử dụng danh sách quốc gia từ API để tạo map biến thể
                val countryVariants = mutableMapOf<String, List<String>>()
                
                // Thêm mapping từ API
                availableCountries.forEach { country ->
                    val countryName = country.countryName
                    val countryCode = country.countryCode
                    
                    // Tạo danh sách biến thể cho mỗi quốc gia
                    val variants = mutableListOf<String>()
                    
                    // Thêm các biến thể phổ biến
                    when (countryCode) {
                        "VN" -> variants.addAll(listOf("viet nam", "vietnam", "việt nam"))
                        "US" -> variants.addAll(listOf("mỹ", "my", "hoa kỳ", "hoa ky", "usa", "america", "united states"))
                        "KR" -> variants.addAll(listOf("hàn quốc", "han quoc", "korea", "south korea"))
                        "JP" -> variants.addAll(listOf("nhật bản", "nhat ban", "japan"))
                        "CN" -> variants.addAll(listOf("trung quốc", "trung quoc", "china"))
                        "GB" -> variants.addAll(listOf("anh", "uk", "united kingdom", "england"))
                        "FR" -> variants.addAll(listOf("pháp", "phap", "france"))
                        "DE" -> variants.addAll(listOf("đức", "duc", "germany"))
                        "RU" -> variants.addAll(listOf("nga", "russia"))
                        "AU" -> variants.addAll(listOf("úc", "uc", "australia"))
                        "TH" -> variants.addAll(listOf("thái lan", "thai lan", "thailand"))
                        "IN" -> variants.addAll(listOf("ấn độ", "an do", "india"))
                        "IT" -> variants.addAll(listOf("ý", "y", "italy"))
                        "ES" -> variants.addAll(listOf("tây ban nha", "tay ban nha", "spain"))
                        "BR" -> variants.addAll(listOf("brazil", "brasil"))
                        "ID" -> variants.addAll(listOf("indonesia"))
                        "MY" -> variants.addAll(listOf("malaysia"))
                        "SG" -> variants.addAll(listOf("singapore"))
                        "CA" -> variants.addAll(listOf("canada"))
                        "MX" -> variants.addAll(listOf("mexico"))
                    }
                    
                    // Luôn thêm tên chính thức từ API
                    variants.add(countryName)
                    variants.add(countryCode)
                    
                    // Thêm vào map
                    countryVariants[countryName] = variants.distinct()
                    variants.forEach { variant ->
                        if (!countryVariants.containsKey(variant)) {
                            countryVariants[variant] = listOf(countryName) + variants
                        }
                    }
                }
                
                // Log thông tin quốc gia đã chọn và biến thể nếu có
                if (selectedFilterCountry.isNotEmpty()) {
                    Log.d("WeatherViewModel", "Biến thể của quốc gia ${selectedFilterCountry}: ${countryVariants[selectedFilterCountry] ?: "không có biến thể"}")
                }
                
                // Lọc trên main thread để tránh race condition với UI
                val result = citiesToFilter.filter { city ->
                    // Lọc theo quốc gia (bỏ qua nếu selectedFilterCountry rỗng)
                    val matchesCountry = if (selectedFilterCountry.isEmpty()) {
                        true // Không lọc theo quốc gia nếu không chọn quốc gia
                    } else {
                        val cityCountry = city.country?.trim()
                        
                        if (cityCountry == null || cityCountry.isEmpty()) {
                            // Nếu thành phố không có thông tin quốc gia, bỏ qua
                            Log.d("WeatherViewModel", "Thành phố ${city.name} không có thông tin quốc gia")
                            false
                        } else {
                            // Kiểm tra tên quốc gia chính xác
                            val directMatch = cityCountry.equals(selectedFilterCountry, ignoreCase = true)
                            
                            // Kiểm tra các biến thể tên quốc gia
                            val variantMatch = countryVariants[selectedFilterCountry]?.any { 
                                cityCountry.equals(it, ignoreCase = true)
                            } ?: false
                            
                            // Kiểm tra nếu biến thể của quốc gia thành phố khớp với lựa chọn
                            val reverseMatch = countryVariants.entries.any { (key, variants) ->
                                key.equals(selectedFilterCountry, ignoreCase = true) && 
                                variants.any { it.equals(cityCountry, ignoreCase = true) }
                            }
                            
                            // Log chi tiết kết quả so khớp cho mỗi thành phố
                            val result = directMatch || variantMatch || reverseMatch
                            Log.d("WeatherViewModel", "Thành phố ${city.name} có quốc gia '$cityCountry': directMatch=$directMatch, variantMatch=$variantMatch, reverseMatch=$reverseMatch, kết quả=$result")
                            result
                        }
                    }
                    
                    // Lấy dữ liệu thời tiết hiện tại của thành phố
                    val weatherData = weatherDataMap[city.name]
                    val index = if (weatherData != null && weatherData.timeList.isNotEmpty()) getCurrentIndex(city.name) else -1
                    
                    // Nếu không có dữ liệu, chỉ lọc theo quốc gia
                    if (weatherData == null || index < 0 || weatherData.timeList.isEmpty()) {
                        Log.d("WeatherViewModel", "Thành phố ${city.name} không có dữ liệu thời tiết, chỉ lọc theo quốc gia: $matchesCountry")
                        return@filter matchesCountry
                    }
                    
                    // Lấy các giá trị thời tiết hiện tại
                    val currentTemp = weatherData.temperatureList.getOrNull(index)
                    val currentWindSpeed = weatherData.windSpeedList.getOrNull(index)
                    val currentHumidity = weatherData.humidityList.getOrNull(index)
                    val currentWeatherCode = weatherData.weatherCodeList.getOrNull(index)
                    
                    // Xác định trạng thái thời tiết từ mã
                    val currentWeatherState = when {
                        currentWeatherCode == null -> "Không xác định"
                        currentWeatherCode == 0 || currentWeatherCode == 1 -> "Nắng"
                        currentWeatherCode in 51..86 -> "Mưa"
                        currentWeatherCode == 2 || currentWeatherCode == 3 -> "Nhiều mây"
                        currentWeatherCode == 45 || currentWeatherCode == 48 -> "Sương mù"
                        currentWeatherCode in 71..86 -> "Tuyết"
                        else -> "Không xác định"
                    }
                    
                    // Kiểm tra theo nhiệt độ
                    val matchesTemp = if (currentTemp != null) {
                        currentTemp >= temperatureFilterRange.start && 
                        currentTemp <= temperatureFilterRange.endInclusive
                    } else true
                    
                    // Kiểm tra theo tốc độ gió
                    val matchesWind = if (currentWindSpeed != null) {
                        currentWindSpeed >= windSpeedFilterRange.start && 
                        currentWindSpeed <= windSpeedFilterRange.endInclusive
                    } else true
                    
                    // Kiểm tra theo độ ẩm
                    val matchesHumidity = if (currentHumidity != null) {
                        currentHumidity >= humidityFilterRange.start && 
                        currentHumidity <= humidityFilterRange.endInclusive
                    } else true
                    
                    // Kiểm tra theo trạng thái thời tiết
                    val matchesWeatherState = if (weatherStateFilter != "Tất cả") {
                        currentWeatherState == weatherStateFilter
                    } else true
                    
                    // Log chi tiết về điều kiện lọc
                    Log.d("WeatherViewModel", "Thành phố ${city.name}: quốc gia=$matchesCountry, nhiệt=$matchesTemp ($currentTemp), gió=$matchesWind ($currentWindSpeed), ẩm=$matchesHumidity ($currentHumidity), thời tiết=$matchesWeatherState ($currentWeatherState)")
                    
                    // Kết quả cuối cùng
                    matchesCountry && matchesTemp && matchesWind && matchesHumidity && matchesWeatherState
                }
                
                Log.d("WeatherViewModel", "Kết quả lọc cuối cùng: ${result.size} thành phố - ${result.map { it.name }}")
                
                // Cập nhật state
                filteredCities = result
                
            } catch (e: CancellationException) {
                // Ignore cancellation - đây là hành vi bình thường
                Log.d("WeatherViewModel", "Filter operation cancelled - this is normal")
                throw e
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Lỗi khi áp dụng bộ lọc: ${e.message}", e)
                // Nếu lỗi, trả về tất cả các thành phố
                filteredCities = cities
            } finally {
                isFiltering = false
            }
        }
    }
    
    // Hàm mới để sử dụng GeoNames API
    fun fetchCitiesByCountryWithGeoNames(country: String) {
        viewModelScope.launch {
            try {
                Log.d(DEBUG_TAG, "=========== BẮT ĐẦU TÌM KIẾM THÀNH PHỐ TỪ GEONAMES API ===========")
                Log.d(DEBUG_TAG, "Đang tìm thành phố cho quốc gia: $country")
                
                // Map tên quốc gia tiếng Việt sang mã quốc gia ISO 2 chữ cái
                val countryCode = when (country.lowercase()) {
                    "việt nam", "viet nam", "vietnam" -> "VN"
                    "hàn quốc", "han quoc", "korea", "south korea" -> "KR"
                    "nhật bản", "nhat ban", "japan" -> "JP"
                    "trung quốc", "trung quoc", "china" -> "CN"
                    "hoa kỳ", "hoa ky", "mỹ", "my", "usa", "united states", "america" -> "US"
                    "anh", "united kingdom", "uk", "england" -> "GB"
                    "pháp", "phap", "france" -> "FR"
                    "đức", "duc", "germany" -> "DE"
                    "nga", "russia" -> "RU"
                    "úc", "uc", "australia" -> "AU"
                    "thái lan", "thai lan", "thailand" -> "TH"
                    "ấn độ", "an do", "india" -> "IN"
                    "canada" -> "CA"
                    "ý", "y", "italy" -> "IT"
                    "tây ban nha", "tay ban nha", "spain" -> "ES"
                    "brazil", "brasil" -> "BR"
                    "mexico" -> "MX"
                    "indonesia" -> "ID"
                    "malaysia" -> "MY"
                    "singapore" -> "SG"
                    else -> country // Nếu không tìm thấy, giữ nguyên tên quốc gia
                }
                
                // Thêm các thành phố thủ công vào danh sách (giữ lại cách này để đảm bảo luôn có data)
                val manualCities = when (countryCode) {
                    "VN" -> listOf(
                        City("Hà Nội", 21.0285, 105.8542, "Việt Nam"),
                        City("TP. Hồ Chí Minh", 10.7769, 106.7009, "Việt Nam"),
                        City("Đà Nẵng", 16.0544, 108.2022, "Việt Nam"),
                        City("Cần Thơ", 10.0452, 105.7469, "Việt Nam"),
                        City("Hải Phòng", 20.8449, 106.6881, "Việt Nam"),
                        City("Huế", 16.4619, 107.5909, "Việt Nam"),
                        City("Nha Trang", 12.2388, 109.1967, "Việt Nam"),
                        City("Buôn Ma Thuột", 12.6667, 108.0500, "Việt Nam"),
                        City("Quy Nhơn", 13.7765, 109.2233, "Việt Nam"),
                        City("Thái Nguyên", 21.5672, 105.8252, "Việt Nam"),
                        City("Vinh", 18.6734, 105.6923, "Việt Nam"),
                        City("Biên Hòa", 10.9574, 106.8426, "Việt Nam"),
                        City("Hạ Long", 20.9101, 107.1839, "Việt Nam"),
                        City("Long Xuyên", 10.3867, 105.4352, "Việt Nam"),
                        City("Nam Định", 20.4333, 106.1667, "Việt Nam"),
                        City("Phan Thiết", 10.9378, 108.1100, "Việt Nam"),
                        City("Pleiku", 13.9833, 108.0000, "Việt Nam"),
                        City("Rạch Giá", 10.0167, 105.0833, "Việt Nam"),
                        City("Thủ Dầu Một", 10.9797, 106.6507, "Việt Nam"),
                        City("Việt Trì", 21.3000, 105.4333, "Việt Nam")
                    )
                    "KR" -> listOf(
                        City("Seoul", 37.5665, 126.9780, "Hàn Quốc"),
                        City("Busan", 35.1796, 129.0756, "Hàn Quốc"),
                        City("Incheon", 37.4563, 126.7052, "Hàn Quốc")
                    )
                    "JP" -> listOf(
                        City("Tokyo", 35.6762, 139.6503, "Nhật Bản"),
                        City("Osaka", 34.6937, 135.5023, "Nhật Bản"),
                        City("Kyoto", 35.0116, 135.7681, "Nhật Bản")
                    )
                    "CN" -> listOf(
                        City("Bắc Kinh", 39.9042, 116.4074, "Trung Quốc"),
                        City("Thượng Hải", 31.2304, 121.4737, "Trung Quốc"),
                        City("Quảng Châu", 23.1291, 113.2644, "Trung Quốc")
                    )
                    "US" -> listOf(
                        City("New York", 40.7128, -74.0060, "Hoa Kỳ"),
                        City("Los Angeles", 34.0522, -118.2437, "Hoa Kỳ"),
                        City("Chicago", 41.8781, -87.6298, "Hoa Kỳ")
                    )
                    "GB" -> listOf(
                        City("London", 51.5074, -0.1278, "Anh"),
                        City("Manchester", 53.4808, -2.2426, "Anh"),
                        City("Liverpool", 53.4084, -2.9916, "Anh")
                    )
                    "FR" -> listOf(
                        City("Paris", 48.8566, 2.3522, "Pháp"),
                        City("Marseille", 43.2965, 5.3698, "Pháp"),
                        City("Lyon", 45.7640, 4.8357, "Pháp")
                    )
                    "DE" -> listOf(
                        City("Berlin", 52.5200, 13.4050, "Đức"),
                        City("Munich", 48.1351, 11.5820, "Đức"),
                        City("Hamburg", 53.5511, 9.9937, "Đức")
                    )
                    "RU" -> listOf(
                        City("Moscow", 55.7558, 37.6173, "Nga"),
                        City("Saint Petersburg", 59.9343, 30.3351, "Nga"),
                        City("Kazan", 55.8304, 49.0661, "Nga")
                    )
                    "AU" -> listOf(
                        City("Sydney", -33.8688, 151.2093, "Úc"),
                        City("Melbourne", -37.8136, 144.9631, "Úc"),
                        City("Brisbane", -27.4698, 153.0251, "Úc")
                    )
                    "TH" -> listOf(
                        City("Bangkok", 13.7563, 100.5018, "Thái Lan"),
                        City("Chiang Mai", 18.7883, 98.9853, "Thái Lan"),
                        City("Phuket", 7.9519, 98.3381, "Thái Lan")
                    )
                    "IN" -> listOf(
                        City("New Delhi", 28.6139, 77.2090, "Ấn Độ"),
                        City("Mumbai", 19.0760, 72.8777, "Ấn Độ"),
                        City("Bangalore", 12.9716, 77.5946, "Ấn Độ")
                    )
                    "CA" -> listOf(
                        City("Toronto", 43.6532, -79.3832, "Canada"),
                        City("Vancouver", 49.2827, -123.1207, "Canada"),
                        City("Montreal", 45.5017, -73.5673, "Canada")
                    )
                    "IT" -> listOf(
                        City("Rome", 41.9028, 12.4964, "Ý"),
                        City("Milan", 45.4642, 9.1900, "Ý"),
                        City("Venice", 45.4408, 12.3155, "Ý")
                    )
                    "ES" -> listOf(
                        City("Madrid", 40.4168, -3.7038, "Tây Ban Nha"),
                        City("Barcelona", 41.3851, 2.1734, "Tây Ban Nha"),
                        City("Valencia", 39.4699, -0.3763, "Tây Ban Nha")
                    )
                    "BR" -> listOf(
                        City("Rio de Janeiro", -22.9068, -43.1729, "Brazil"),
                        City("São Paulo", -23.5505, -46.6333, "Brazil"),
                        City("Brasília", -15.7801, -47.9292, "Brazil")
                    )
                    "MX" -> listOf(
                        City("Mexico City", 19.4326, -99.1332, "Mexico"),
                        City("Cancún", 21.1619, -86.8515, "Mexico"),
                        City("Guadalajara", 20.6597, -103.3496, "Mexico")
                    )
                    "ID" -> listOf(
                        City("Jakarta", -6.2088, 106.8456, "Indonesia"),
                        City("Bali", -8.3405, 115.0920, "Indonesia"),
                        City("Surabaya", -7.2575, 112.7521, "Indonesia")
                    )
                    "MY" -> listOf(
                        City("Kuala Lumpur", 3.1390, 101.6869, "Malaysia"),
                        City("Penang", 5.4141, 100.3292, "Malaysia"),
                        City("Johor Bahru", 1.4927, 103.7414, "Malaysia")
                    )
                    "SG" -> listOf(
                        City("Singapore", 1.3521, 103.8198, "Singapore")
                    )
                    else -> emptyList()
                }
                
                // Thêm các thành phố thủ công vào danh sách
                val newManualCities = manualCities.filter { manualCity ->
                    cities.none { it.name == manualCity.name }
                }
                
                if (newManualCities.isNotEmpty()) {
                    Log.d("WeatherViewModel", "Thêm ${newManualCities.size} thành phố thủ công cho ${country}")
                    cities = cities + newManualCities
                    
                    // Khởi tạo WeatherDataState cho các thành phố mới
                    val updatedMap = weatherDataMap.toMutableMap()
                    newManualCities.forEach { city ->
                        updatedMap[city.name] = WeatherDataState()
                        // Fetch dữ liệu thời tiết cho thành phố mới
                        fetchWeatherAndAirQuality(city.name, city.latitude, city.longitude)
                    }
                    weatherDataMap = updatedMap
                }
                
                // Khai báo biến response ở phạm vi ngoài để có thể sử dụng sau này
                var geoNamesResponse: GeoNamesResponse? = null
                
                // Gọi GeoNames API để lấy danh sách thành phố
                try {
                    val response = withContext(Dispatchers.IO) {
                        geoNamesService.getCitiesByCountry(
                            countryCode = countryCode,
                            maxRows = 50, // Lấy tối đa 50 thành phố
                            username = RetrofitInstance.GEONAMES_USERNAME
                        )
                    }
                    
                    // Gán giá trị cho biến ở phạm vi ngoài
                    geoNamesResponse = response
                    
                    // Log chi tiết response từ API
                    Log.d(DEBUG_TAG, "GeoNames API trả về response: ${response != null}")
                    Log.d(DEBUG_TAG, "Response có geonames: ${response.geonames != null}")
                    Log.d(DEBUG_TAG, "Số lượng thành phố: ${response.geonames?.size ?: 0}")
                    
                    // Chuyển đổi data từ API sang đối tượng City
                    val newCities = response.geonames?.filter { geoCity ->
                        // Lọc bỏ các thành phố đã có trong danh sách
                        val cityName = geoCity.name
                        cities.none { it.name == cityName }
                    }?.map { geoCity ->
                        City(
                            name = geoCity.name,
                            latitude = geoCity.lat.toDouble(),
                            longitude = geoCity.lng.toDouble(),
                            country = geoCity.countryName
                        )
                    } ?: emptyList()
                    
                    // Thêm các thành phố mới vào danh sách hiện tại
                    if (newCities.isNotEmpty()) {
                        Log.d("WeatherViewModel", "Tìm thấy ${newCities.size} thành phố mới từ GeoNames: ${newCities.map { it.name }}")
                        
                        // Cập nhật danh sách thành phố
                        cities = cities + newCities
                        
                        // Khởi tạo WeatherDataState cho các thành phố mới
                        val updatedMap = weatherDataMap.toMutableMap()
                        newCities.forEach { city ->
                            updatedMap[city.name] = WeatherDataState()
                            // Fetch dữ liệu thời tiết cho thành phố mới
                            fetchWeatherAndAirQuality(city.name, city.latitude, city.longitude)
                        }
                        weatherDataMap = updatedMap
                            } else {
                        Log.d("WeatherViewModel", "Không tìm thấy thành phố mới từ GeoNames cho quốc gia: $country")
                    }
                } catch (e: CancellationException) {
                    Log.d("WeatherViewModel", "GeoNames fetch cancelled - this is normal")
                    throw e
                } catch (e: Exception) {
                    Log.e("WeatherViewModel", "Lỗi khi lấy thành phố từ GeoNames API: ${e.message}", e)
                    Log.e(DEBUG_TAG, "Lỗi trong quá trình xử lý GeoNames API: ${e.message}", e)
                    
                    // Thử lại với tham số khác nếu không tìm thấy thành phố
                    if (geoNamesResponse?.geonames?.isEmpty() == true) {
                        try {
                            Log.d(DEBUG_TAG, "Thử tìm kiếm lại với thủ đô của quốc gia")
                            // Tìm kiếm thành phố chính/thủ đô của quốc gia
                            val capital = when (countryCode) {
                                "VN" -> "Hanoi"
                                "US" -> "Washington"
                                "JP" -> "Tokyo" 
                                "KR" -> "Seoul"
                                "CN" -> "Beijing"
                                "GB" -> "London"
                                "FR" -> "Paris"
                                "DE" -> "Berlin"
                                "RU" -> "Moscow"
                                else -> null
                            }
                            
                            if (capital != null) {
                                val capitalResponse = withContext(Dispatchers.IO) {
                                    geoNamesService.getCitiesByCountry(
                                        countryCode = countryCode,
                                        maxRows = 1,
                                        username = RetrofitInstance.GEONAMES_USERNAME,
                                        q = capital // Sử dụng fulltext search thay cho nameStartsWith
                                    )
                                }
                                
                                if (capitalResponse.geonames?.isNotEmpty() == true) {
                                    val capital = capitalResponse.geonames.first()
                                    val newCity = City(
                                        name = capital.name,
                                        latitude = capital.lat.toDouble(),
                                        longitude = capital.lng.toDouble(),
                                        country = capital.countryName
                                    )
                                    
                                    if (cities.none { it.name == newCity.name }) {
                                        Log.d(DEBUG_TAG, "Thêm thủ đô ${newCity.name} vào danh sách thành phố")
                                        cities = cities + newCity
                                        
                                        // Khởi tạo WeatherDataState cho thành phố mới
                                        weatherDataMap = weatherDataMap.toMutableMap().apply {
                                            put(newCity.name, WeatherDataState())
                                        }
                                        // Fetch dữ liệu thời tiết
                                        fetchWeatherAndAirQuality(newCity.name, newCity.latitude, newCity.longitude)
                                    }
                                }
                            }
                        } catch (e2: Exception) {
                            Log.e(DEBUG_TAG, "Lỗi khi tìm kiếm thủ đô: ${e2.message}", e2)
                        }
                    }
                }
                
            } catch (e: CancellationException) {
                Log.d("WeatherViewModel", "Fetch cities operation cancelled - this is normal")
                throw e
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Lỗi khi lấy thành phố: ${e.message}", e)
                Log.e(DEBUG_TAG, "Lỗi trong quá trình xử lý API: ${e.message}", e)
            }
            
            // Tổng kết quá trình tìm kiếm API
            Log.d(DEBUG_TAG, "=========== KẾT QUẢ TÌM KIẾM THÀNH PHỐ ===========")
            Log.d(DEBUG_TAG, "Sau khi tìm kiếm, cities có ${cities.size} thành phố")
            Log.d(DEBUG_TAG, "Các thành phố của ${country}: ${cities.filter { it.country?.equals(country, ignoreCase = true) == true }.map { it.name }}")
            Log.d(DEBUG_TAG, "=================================================")
        }
    }
    
    // Hàm fetchCitiesByCountry cũ sẽ gọi đến hàm mới
    fun fetchCitiesByCountry(country: String) {
        fetchCitiesByCountryWithGeoNames(country)
    }

    // Hàm mới để sắp xếp lại thứ tự thành phố
    fun reorderCities(newCitiesList: List<City>) {
        viewModelScope.launch {
            Log.d("WeatherViewModel", "Đang sắp xếp lại thứ tự thành phố: ${newCitiesList.map { it.name }}")
            
            // Cập nhật danh sách cities
            cities = newCitiesList
            
            // Nếu cần lưu trữ thứ tự này xuống DB, có thể thêm logic ở đây
            // Ví dụ:
            // withContext(Dispatchers.IO) {
            //    saveCityOrderToDb(newCitiesList)
            // }
            
            Log.d("WeatherViewModel", "Đã sắp xếp lại thứ tự thành phố thành công")
            saveCitiesToPrefs()
        }
    }

    // Hàm mới để reset kết quả lọc khi quay về màn hình chính
    fun resetFilterResults() {
        Log.d("WeatherViewModel", "Reset kết quả lọc: từ filteredCities.size=${filteredCities.size} về originalCities.size=${originalCities.size}")
        
        // Reset về danh sách thành phố ban đầu nếu có
        if (originalCities.isNotEmpty()) {
            filteredCities = originalCities
            originalCities = emptyList() // Clear originalCities sau khi reset
        } else {
            filteredCities = cities // Fallback nếu originalCities rỗng
        }
        
        isFiltering = false      // Đảm bảo trạng thái lọc cũng được reset
        
        // Reset các bộ lọc về giá trị mặc định
        selectedFilterCountry = ""
        temperatureFilterRange = -20f..50f
        windSpeedFilterRange = 0f..100f
        humidityFilterRange = 0f..100f
        weatherStateFilter = "Tất cả"
    }

    // Hàm mới để lấy thành phố cho việc lọc mà không thay đổi cities gốc
    private suspend fun fetchCitiesForFiltering(country: String): List<City> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(DEBUG_TAG, "Lấy thành phố cho lọc: $country")
                
                // Map tên quốc gia
                val countryCode = when (country.lowercase()) {
                    "việt nam", "viet nam", "vietnam" -> "VN"
                    "hàn quốc", "han quoc", "korea", "south korea" -> "KR"
                    "nhật bản", "nhat ban", "japan" -> "JP"
                    "trung quốc", "trung quoc", "china" -> "CN"
                    "hoa kỳ", "hoa ky", "mỹ", "my", "usa", "united states", "america" -> "US"
                    "anh", "united kingdom", "uk", "england" -> "GB"
                    "pháp", "phap", "france" -> "FR"
                    "đức", "duc", "germany" -> "DE"
                    "nga", "russia" -> "RU"
                    "úc", "uc", "australia" -> "AU"
                    "thái lan", "thai lan", "thailand" -> "TH"
                    "ấn độ", "an do", "india" -> "IN"
                    "canada" -> "CA"
                    "ý", "y", "italy" -> "IT"
                    "tây ban nha", "tay ban nha", "spain" -> "ES"
                    "brazil", "brasil" -> "BR"
                    "mexico" -> "MX"
                    "indonesia" -> "ID"
                    "malaysia" -> "MY"
                    "singapore" -> "SG"
                    else -> country
                }
                
                // Thêm các thành phố thủ công
                val manualCities = when (countryCode) {
                    "VN" -> listOf(
                        City("Hà Nội", 21.0285, 105.8542, "Việt Nam"),
                        City("TP. Hồ Chí Minh", 10.7769, 106.7009, "Việt Nam"),
                        City("Đà Nẵng", 16.0544, 108.2022, "Việt Nam")
                    )
                    "KR" -> listOf(
                        City("Seoul", 37.5665, 126.9780, "Hàn Quốc"),
                        City("Busan", 35.1796, 129.0756, "Hàn Quốc"),
                        City("Incheon", 37.4563, 126.7052, "Hàn Quốc")
                    )
                    "JP" -> listOf(
                        City("Tokyo", 35.6762, 139.6503, "Nhật Bản"),
                        City("Osaka", 34.6937, 135.5023, "Nhật Bản"),
                        City("Kyoto", 35.0116, 135.7681, "Nhật Bản")
                    )
                    // Các quốc gia khác tương tự...
                    else -> emptyList()
                }
                
                val result = mutableListOf<City>()
                
                // Thêm thành phố thủ công
                result.addAll(manualCities)
                
                // Gọi GeoNames API
                try {
                    val response = geoNamesService.getCitiesByCountry(
                        countryCode = countryCode,
                        maxRows = 50,
                        username = RetrofitInstance.GEONAMES_USERNAME
                        // Nếu cần tìm theo tên, thêm q = ...
                    )
                    
                    val apiCities = response.geonames?.map { geoCity ->
                        City(
                            name = geoCity.name,
                            latitude = geoCity.lat.toDouble(),
                            longitude = geoCity.lng.toDouble(),
                            country = geoCity.countryName
                        )
                    } ?: emptyList()
                    
                    result.addAll(apiCities)
                } catch (e: CancellationException) {
                    Log.d("WeatherViewModel", "GeoNames API call cancelled - this is normal")
                    throw e
                } catch (e: Exception) {
                    Log.e("WeatherViewModel", "Lỗi khi gọi GeoNames API: ${e.message}")
                }
                
                Log.d(DEBUG_TAG, "Lấy được ${result.size} thành phố cho lọc")
                result.distinctBy { it.name }
                
            } catch (e: CancellationException) {
                Log.d("WeatherViewModel", "FetchCitiesForFiltering cancelled - this is normal")
                throw e
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Lỗi trong fetchCitiesForFiltering: ${e.message}")
                emptyList()
            }
        }
    }
    
    // Hàm mới để lấy danh sách quốc gia từ API
    fun fetchCountriesFromAPI() {
        viewModelScope.launch {
            isLoadingCountries = true
            countriesError = null
            
            try {
                Log.d("WeatherViewModel", "Đang lấy danh sách quốc gia từ GeoNames API...")
                
                val response = withContext(Dispatchers.IO) {
                    geoNamesService.getAllCountries(
                        username = RetrofitInstance.GEONAMES_USERNAME
                    )
                }
                
                val countries = response.geonames ?: emptyList()
                Log.d("WeatherViewModel", "Lấy được ${countries.size} quốc gia từ API")
                
                // Sắp xếp theo tên quốc gia
                availableCountries = countries.sortedBy { it.countryName }
                
                Log.d("WeatherViewModel", "Một số quốc gia: ${countries.take(5).map { "${it.countryName} (${it.countryCode})" }}")
                
            } catch (e: CancellationException) {
                Log.d("WeatherViewModel", "FetchCountriesFromAPI cancelled - this is normal")
                throw e
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Lỗi khi lấy danh sách quốc gia: ${e.message}", e)
                countriesError = "Lỗi khi tải danh sách quốc gia: ${e.message}"
                
                // Fallback: sử dụng danh sách quốc gia mặc định
                availableCountries = getDefaultCountries()
                
            } finally {
                isLoadingCountries = false
            }
        }
    }
    
    // Hàm tạo danh sách quốc gia mặc định (fallback)
    private fun getDefaultCountries(): List<CountryInfo> {
        return listOf(
            CountryInfo("VN", "Việt Nam", "AS", "Hanoi", "vi", "VND", "", ""),
            CountryInfo("US", "Hoa Kỳ", "NA", "Washington", "en", "USD", "", ""),
            CountryInfo("KR", "Hàn Quốc", "AS", "Seoul", "ko", "KRW", "", ""),
            CountryInfo("JP", "Nhật Bản", "AS", "Tokyo", "ja", "JPY", "", ""),
            CountryInfo("CN", "Trung Quốc", "AS", "Beijing", "zh", "CNY", "", ""),
            CountryInfo("GB", "Anh", "EU", "London", "en", "GBP", "", ""),
            CountryInfo("FR", "Pháp", "EU", "Paris", "fr", "EUR", "", ""),
            CountryInfo("DE", "Đức", "EU", "Berlin", "de", "EUR", "", ""),
            CountryInfo("RU", "Nga", "EU", "Moscow", "ru", "RUB", "", ""),
            CountryInfo("AU", "Úc", "OC", "Canberra", "en", "AUD", "", ""),
            CountryInfo("TH", "Thái Lan", "AS", "Bangkok", "th", "THB", "", ""),
            CountryInfo("IN", "Ấn Độ", "AS", "New Delhi", "hi", "INR", "", ""),
            CountryInfo("CA", "Canada", "NA", "Ottawa", "en", "CAD", "", ""),
            CountryInfo("IT", "Ý", "EU", "Rome", "it", "EUR", "", ""),
            CountryInfo("ES", "Tây Ban Nha", "EU", "Madrid", "es", "EUR", "", ""),
            CountryInfo("BR", "Brazil", "SA", "Brasília", "pt", "BRL", "", ""),
            CountryInfo("MX", "Mexico", "NA", "Mexico City", "es", "MXN", "", ""),
            CountryInfo("ID", "Indonesia", "AS", "Jakarta", "id", "IDR", "", ""),
            CountryInfo("MY", "Malaysia", "AS", "Kuala Lumpur", "ms", "MYR", "", ""),
            CountryInfo("SG", "Singapore", "AS", "Singapore", "en", "SGD", "", "")
        )
    }
    
    // ================ RADAR CACHING FUNCTIONS ================
    
    // Preload radar data for a city
    fun preloadRadarForCity(cityName: String) {
        val city = cities.find { it.name == cityName } ?: return
        
        viewModelScope.launch {
            Log.d("WeatherViewModel", "Preloading radar data for $cityName")
            
            val layers = listOf("precipitation", "clouds", "wind", "temp")
            
            layers.forEach { layer ->
                if (!isRadarCacheValid(cityName, layer)) {
                    loadRadarLayer(cityName, city.latitude, city.longitude, layer)
                }
            }
        }
    }
    
    // Check if radar cache is valid (not expired)
    private fun isRadarCacheValid(cityName: String, layer: String): Boolean {
        val weatherData = weatherDataMap[cityName] ?: return false
        val layerCache = weatherData.radarCache[layer] ?: return false
        
        val now = System.currentTimeMillis()
        return layerCache.isComplete && (now - layerCache.timestamp) < RADAR_CACHE_EXPIRY_MS
    }
    
    // Load a specific radar layer and cache it
    private suspend fun loadRadarLayer(
        cityName: String, 
        latitude: Double, 
        longitude: Double, 
        layer: String
    ) {
        try {
            withContext(Dispatchers.IO) {
                Log.d("WeatherViewModel", "Loading radar layer $layer for $cityName")
                
                val zoom = RADAR_DEFAULT_ZOOM
                val (centerX, centerY) = latLonToTileXY(latitude, longitude, zoom)
                
                val tilesToLoad = mutableListOf<RadarTile>()
                
                // Load 3x3 grid of tiles
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val xTile = centerX + dx
                        val yTile = centerY + dy
                        
                        val n = 1 shl zoom
                        val wrappedX = ((xTile % n) + n) % n
                        
                        val bounds = tileToLatLonBounds(wrappedX, yTile, zoom)
                        val url = "https://tile.openweathermap.org/map/$layer/$zoom/$wrappedX/$yTile.png?appid=960b4897d630b53c8faeb909817bf31a"
                        
                        val bitmapData = try {
                            val connection = java.net.URL(url).openConnection()
                            connection.connectTimeout = 3000
                            connection.readTimeout = 3000
                            connection.getInputStream().use { inputStream ->
                                inputStream.readBytes()
                            }
                        } catch (e: Exception) {
                            Log.e("WeatherViewModel", "Failed to load radar tile: $url", e)
                            null
                        }
                        
                        tilesToLoad.add(
                            RadarTile(
                                x = wrappedX,
                                y = yTile,
                                zoom = zoom,
                                bitmapData = bitmapData,
                                isLoaded = bitmapData != null,
                                bounds = bounds
                            )
                        )
                    }
                }
                
                // Update cache on main thread
                withContext(Dispatchers.Main) {
                    updateRadarCache(cityName, layer, tilesToLoad)
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherViewModel", "Error loading radar layer $layer for $cityName", e)
        }
    }
    
    // Update radar cache for a city and layer
    private fun updateRadarCache(cityName: String, layer: String, tiles: List<RadarTile>) {
        val currentData = weatherDataMap[cityName] ?: WeatherDataState()
        val newRadarCache = currentData.radarCache.toMutableMap()
        
        val layerCache = RadarLayerCache(
            layer = layer,
            tiles = tiles,
            timestamp = System.currentTimeMillis(),
            isComplete = tiles.all { it.isLoaded }
        )
        
        newRadarCache[layer] = layerCache
        
        val updatedData = WeatherDataState(
            timeList = currentData.timeList,
            temperatureList = currentData.temperatureList,
            weatherCodeList = currentData.weatherCodeList,
            precipitationList = currentData.precipitationList,
            humidityList = currentData.humidityList,
            windSpeedList = currentData.windSpeedList,
            uvList = currentData.uvList,
            feelsLikeList = currentData.feelsLikeList,
            pressureList = currentData.pressureList,
            visibilityList = currentData.visibilityList,
            dailyTimeList = currentData.dailyTimeList,
            dailyTempMaxList = currentData.dailyTempMaxList,
            dailyTempMinList = currentData.dailyTempMinList,
            dailyWeatherCodeList = currentData.dailyWeatherCodeList,
            dailyPrecipitationList = currentData.dailyPrecipitationList,
            dailySunriseList = currentData.dailySunriseList,
            dailySunsetList = currentData.dailySunsetList,
            lastUpdateTime = currentData.lastUpdateTime,
            errorMessage = currentData.errorMessage,
            currentAqi = currentData.currentAqi,
            currentPm25 = currentData.currentPm25,
            radarCache = newRadarCache,
            radarLastUpdate = System.currentTimeMillis()
        )
        
        weatherDataMap = weatherDataMap.toMutableMap().apply {
            put(cityName, updatedData)
        }
        
        Log.d("WeatherViewModel", "Updated radar cache for $cityName, layer $layer: ${tiles.count { it.isLoaded }}/${tiles.size} tiles loaded")
    }
    
    // Get cached radar tiles for a layer
    fun getCachedRadarTiles(cityName: String, layer: String): List<RadarTile> {
        val weatherData = weatherDataMap[cityName] ?: return emptyList()
        val layerCache = weatherData.radarCache[layer] ?: return emptyList()
        
        // Check if cache is still valid
        if (isRadarCacheValid(cityName, layer)) {
            return layerCache.tiles.filter { it.isLoaded }
        }
        
        return emptyList()
    }
    
    // Convert lat/lon to tile coordinates
    private fun latLonToTileXY(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom
        val x = ((lon + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        return Pair(x, y)
    }
    
    // Convert tile coordinates to lat/lon bounds
    private fun tileToLatLonBounds(x: Int, y: Int, zoom: Int): com.google.android.gms.maps.model.LatLngBounds {
        val n = 1 shl zoom
        val lonDeg1 = x.toDouble() / n * 360.0 - 180.0
        val lonDeg2 = (x + 1).toDouble() / n * 360.0 - 180.0
        val latRad1 = Math.atan(Math.sinh(Math.PI * (1 - 2.0 * y / n)))
        val latRad2 = Math.atan(Math.sinh(Math.PI * (1 - 2.0 * (y + 1) / n)))
        val latDeg1 = Math.toDegrees(latRad1)
        val latDeg2 = Math.toDegrees(latRad2)
        
        return com.google.android.gms.maps.model.LatLngBounds(
            com.google.android.gms.maps.model.LatLng(latDeg2, lonDeg1), // southwest
            com.google.android.gms.maps.model.LatLng(latDeg1, lonDeg2)  // northeast
        )
    }
    
    // Clear radar cache for a city (useful when removing city)
    fun clearRadarCache(cityName: String) {
        val currentData = weatherDataMap[cityName] ?: return
        
        // Create updated data by copying all fields manually
        val updatedData = WeatherDataState(
            timeList = currentData.timeList,
            temperatureList = currentData.temperatureList,
            weatherCodeList = currentData.weatherCodeList,
            precipitationList = currentData.precipitationList,
            humidityList = currentData.humidityList,
            windSpeedList = currentData.windSpeedList,
            uvList = currentData.uvList,
            feelsLikeList = currentData.feelsLikeList,
            pressureList = currentData.pressureList,
            visibilityList = currentData.visibilityList,
            dailyTimeList = currentData.dailyTimeList,
            dailyTempMaxList = currentData.dailyTempMaxList,
            dailyTempMinList = currentData.dailyTempMinList,
            dailyWeatherCodeList = currentData.dailyWeatherCodeList,
            dailyPrecipitationList = currentData.dailyPrecipitationList,
            dailySunriseList = currentData.dailySunriseList,
            dailySunsetList = currentData.dailySunsetList,
            lastUpdateTime = currentData.lastUpdateTime,
            errorMessage = currentData.errorMessage,
            currentAqi = currentData.currentAqi,
            currentPm25 = currentData.currentPm25,
            radarCache = emptyMap(),
            radarLastUpdate = null
        )
        
        weatherDataMap = weatherDataMap.toMutableMap().apply {
            put(cityName, updatedData)
        }
        
        Log.d("WeatherViewModel", "Cleared radar cache for $cityName")
    }
    

}
