package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query


data class GeoapifyAutocompleteResponse(
    val features: List<GeoFeature>?,
)

data class GeoFeature(
    val properties: GeoProperties?,
    val geometry: GeoGeometry?
)

data class GeoProperties(
    val formatted: String?,
    val city: String?,
    val county: String?,
    val state: String?,
    val country: String?,
    val postcode: String?,
    val district: String?,
    val suburb: String?,
    val municipality: String?,
    val lat: Double?,
    val lon: Double?,
    val place_id: String?
)

data class GeoGeometry(
    val type: String?,
    val coordinates: List<Double>?
)




interface GeoapifyService {

    @GET("v1/geocode/autocomplete")
    suspend fun searchPlaces(
        @Query("text") searchText: String,
        @Query("apiKey") apiKey: String,
        @Query("lang") language: String = "vi",
        @Query("limit") limit: Int = 10,
        @Query("type") type: String = "street,district,suburb,locality,place",

    ): GeoapifyAutocompleteResponse
}
