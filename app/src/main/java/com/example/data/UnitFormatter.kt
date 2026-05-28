package com.example.data

/**
 * Centralized formatting for measurements that follow the "measurement system" preference
 * (imperial vs metric). Temperature and wind have their own preferences and are not handled here.
 */
object UnitFormatter {

    /** Pressure: API returns hPa. Imperial users prefer inches of mercury. */
    fun pressure(hpa: Double, system: String): String =
        if (system == "imperial") "${"%.2f".format(hpa / 33.8639)} inHg"
        else "${hpa.toInt()} hPa"

    /** Whole-number distance such as horizontal visibility. */
    fun distanceKm(km: Double, system: String): String =
        if (system == "imperial") "${(km * 0.621371).toInt()} mi"
        else "${km.toInt()} km"

    /** Fractional distance for short values such as cloud ceiling. */
    fun distanceKmFractional(km: Double, system: String): String =
        if (system == "imperial") "${"%.1f".format(km * 0.621371)} mi"
        else "${"%.1f".format(km)} km"

    /** Rainfall — input always in mm (what Tomorrow.io returns). */
    fun rainFromMm(mm: Double, system: String): String =
        when {
            mm < 0.05 -> "0"
            system == "imperial" -> "${"%.2f".format(mm / 25.4)} in"
            else -> "${"%.1f".format(mm)} mm"
        }

    /** Tide height — input always in feet (what NOAA returns when units=english). */
    fun tideFromFeet(ft: Double, system: String): String =
        if (system == "imperial") "${"%.1f".format(ft)} ft"
        else "${"%.2f".format(ft * 0.3048)} m"

    fun systemDisplayName(system: String): String =
        if (system == "imperial") "Imperial" else "Metric"
}
