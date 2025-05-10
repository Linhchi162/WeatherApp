package com.example.weatherapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor // Import logging interceptor
import java.util.concurrent.TimeUnit

/**
 * Singleton object to provide Retrofit service instances.
 * Includes configuration for HTTP request/response logging.
 */
object RetrofitInstance {

    // --- HTTP Client Configuration ---

    // 1. Logging Interceptor (Ensure Level.BODY for debugging)
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // 2. Base OkHttpClient with Logging and Timeouts
    private val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // Add the logger
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // --- Retrofit Instances ---

    /**
     * Retrofit instance for Open-Meteo Weather Forecast API.
     * Uses the base OkHttpClient with logging enabled.
     * --- ĐÂY LÀ INSTANCE ĐÚNG CHO API THỜI TIẾT ---
     */
    val api: OpenMeteoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(baseOkHttpClient) // <-- Sử dụng client CÓ logging
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoService::class.java)
    }

    /**
     * Retrofit instance for Open-Meteo Air Quality API.
     */
    val airQualityApi: AirQualityService by lazy {
        Retrofit.Builder()
            .baseUrl("https://air-quality-api.open-meteo.com/")
            .client(baseOkHttpClient) // <-- Sử dụng client CÓ logging
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityService::class.java)
    }

    /**
     * Retrofit instance for GeoNames API.
     */
    val geoNamesApi: GeoNamesService by lazy {
        Retrofit.Builder()
            .baseUrl("http://api.geonames.org/") // Sử dụng API endpoint chính thức (HTTP)
            .client(baseOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoNamesService::class.java)
    }

    // --- API Keys ---
    // GeoNames username (acts as API key)
    const val GEONAMES_USERNAME = "lilchee" // Tài khoản GeoNames của người dùng
}
