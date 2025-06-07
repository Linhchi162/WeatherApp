package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

interface GeoNamesService {
    @GET("searchJSON")
    suspend fun getCitiesByCountry(
        @Query("country") countryCode: String = "",
        @Query("featureClass") featureClass: String = "P",
        @Query("maxRows") maxRows: Int = 100,
        @Query("orderby") orderBy: String = "population",
        @Query("username") username: String,
        @Query("lang") language: String = "vi",
        @Query("q") q: String = ""
    ): GeoNamesResponse

    @GET("countryInfoJSON")
    suspend fun getAllCountries(
        @Query("username") username: String,
        @Query("lang") language: String = "vi"
    ): CountryInfoResponse
} 