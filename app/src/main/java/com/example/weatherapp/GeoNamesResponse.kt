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