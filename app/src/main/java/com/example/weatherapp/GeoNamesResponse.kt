package com.example.weatherapp

data class GeoNamesResponse(
    val totalResultsCount: Int?,
    val geonames: List<GeoNameCity>?
)

data class GeoNameCity(
    val name: String,
    val lat: String,
    val lng: String,
    val countryName: String,
    val countryCode: String,
    val fcl: String,
    val fcode: String,
    val population: Long?,
    val toponymName: String?,
    val adminName1: String?  // Tên tỉnh/bang/vùng
)

// Data class cho country info response
data class CountryInfoResponse(
    val geonames: List<CountryInfo>?
)

data class CountryInfo(
    val countryCode: String,
    val countryName: String,
    val continent: String?,
    val capital: String?,
    val languages: String?,
    val currencyCode: String?,
    val population: String?,
    val areaInSqKm: String?
)