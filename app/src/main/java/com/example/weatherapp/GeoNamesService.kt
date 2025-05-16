package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

interface GeoNamesService {
    @GET("searchJSON")
    suspend fun getCitiesByCountry(
        @Query("country") countryCode: String,
        @Query("featureClass") featureClass: String = "P", // P là địa điểm có dân số (populated place)
        @Query("maxRows") maxRows: Int = 100,
        @Query("orderby") orderBy: String = "population", // Sắp xếp theo dân số
        @Query("username") username: String,
        @Query("lang") language: String = "vi", // Ngôn ngữ tiếng Việt nếu có
        @Query("name_startsWith") nameStartsWith: String = "" // Thêm tham số tìm kiếm theo prefix
    ): GeoNamesResponse
    
    @GET("countryInfoJSON")
    suspend fun getCountryInfo(
        @Query("country") countryCode: String,
        @Query("username") username: String,
        @Query("lang") language: String = "vi" // Ngôn ngữ tiếng Việt nếu có
    ): GeoNamesResponse
} 