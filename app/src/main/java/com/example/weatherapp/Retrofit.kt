package com.example.weatherapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit


object RetrofitInstance {


    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // Add the logger
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: OpenMeteoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(baseOkHttpClient) // <-- Sử dụng client CÓ logging
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoService::class.java)
    }
    val airQualityApi: AirQualityService by lazy {
        Retrofit.Builder()
            .baseUrl("https://air-quality-api.open-meteo.com/")
            .client(baseOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityService::class.java)
    }

    val geoNamesApi: GeoNamesService by lazy {
        Retrofit.Builder()
            .baseUrl("http://api.geonames.org/")
            .client(baseOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoNamesService::class.java)
    }


    const val GEONAMES_USERNAME = "lilchee"

}
