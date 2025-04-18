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

    // 3. Specific Client for Geoapify (can reuse base client if config is identical)
    private val geoapifyHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            // Add other specific interceptors if needed for Geoapify
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
     * Retrofit instance for Geoapify Geocoding API.
     */
    val geoapifyApi: GeoapifyService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.geoapify.com/")
            .client(geoapifyHttpClient) // Sử dụng client Geoapify
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoapifyService::class.java)
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


    // --- API Keys ---
    // IMPORTANT: Store API keys securely!
    const val GEOAPIFY_API_KEY = "183500a3f01b45a5b6076845dae351b3"

    // --- KHÔNG CÒN DÙNG INSTANCE 'api' CŨ NÀY NỮA ---
    // val api: OpenMeteoService by lazy {
    //     Retrofit.Builder()
    //         .baseUrl("https://api.open-meteo.com/")
    //         .addConverterFactory(GsonConverterFactory.create())
    //         .build()
    //         .create(OpenMeteoService::class.java)
    // }

}
