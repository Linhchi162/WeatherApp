
package com.example.weatherapp

import kotlin.math.roundToInt
import kotlin.math.roundToLong

object UnitConverter {


    enum class TemperatureUnit { CELSIUS, FAHRENHEIT }
    enum class WindSpeedUnit { KMH, BEAUFORT, MS, FTS, MPH, KNOTS }
    enum class PressureUnit { HPA, MMHG, INHG, MB, PSI }
    enum class VisibilityUnit { KM, MI, M, FT }


    fun convertTemperature(value: Double, toUnit: TemperatureUnit): Double {
        return when (toUnit) {
            TemperatureUnit.CELSIUS -> value
            TemperatureUnit.FAHRENHEIT -> (value * 9 / 5) + 32
        }
    }


    fun convertWindSpeed(value: Double, toUnit: WindSpeedUnit): String {
        return when (toUnit) {
            WindSpeedUnit.KMH -> "${value.roundToInt()} km/h"
            WindSpeedUnit.MS -> "${(value / 3.6).roundToInt()} m/s"
            WindSpeedUnit.FTS -> "${(value * 3.28084 / 3.6).roundToInt()} ft/s"
            WindSpeedUnit.MPH -> "${(value * 0.621371).roundToInt()} mph"
            WindSpeedUnit.KNOTS -> "${(value * 0.539957).roundToInt()} knots"
            WindSpeedUnit.BEAUFORT -> {
                val beaufort = when {
                    value < 1 -> 0
                    value < 6 -> 1
                    value < 12 -> 2
                    value < 20 -> 3
                    value < 29 -> 4
                    value < 39 -> 5
                    value < 50 -> 6
                    value < 62 -> 7
                    value < 75 -> 8
                    value < 89 -> 9
                    value < 103 -> 10
                    value < 118 -> 11
                    else -> 12
                }
                "$beaufort Bft"
            }
        }
    }


    fun convertPressure(value: Double, toUnit: PressureUnit): String {
        return when (toUnit) {
            PressureUnit.HPA -> "${value.roundToInt()} hPa"
            PressureUnit.MMHG -> "${(value * 0.75006).roundToInt()} mmHg"
            PressureUnit.INHG -> "${(value * 0.02953).roundToInt()} inHg"
            PressureUnit.MB -> "${value.roundToInt()} mb"
            PressureUnit.PSI -> "${(value * 0.0145038).roundToInt()} psi"
        }
    }


    fun convertVisibility(value: Double, toUnit: VisibilityUnit): String {
        return when (toUnit) {
            VisibilityUnit.KM -> "${(value / 1000).roundToInt()} km"
            VisibilityUnit.MI -> "${(value / 1609.34).roundToInt()} mi"
            VisibilityUnit.M -> "${value.roundToLong()} m"
            VisibilityUnit.FT -> "${(value * 3.28084).roundToLong()} ft"
        }
    }
}


