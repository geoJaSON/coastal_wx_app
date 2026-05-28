package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PreferencesHelper {
    private const val SECURE_PREFS_NAME = "aero_weather_secure_prefs"
    private const val NORMAL_PREFS_NAME = "aero_weather_normal_prefs"

    // Secure SharedPreferences for API Key
    fun getSecurePrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to normal shared preferences in case of Keystore exceptions
        context.getSharedPreferences(SECURE_PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
    }

    // Normal SharedPreferences for UI Settings
    fun getNormalPrefs(context: Context) = context.getSharedPreferences(NORMAL_PREFS_NAME, Context.MODE_PRIVATE)

    // Helper properties
    fun getApiKey(context: Context): String {
        return getSecurePrefs(context).getString("tomorrow_api_key", "") ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getSecurePrefs(context).edit().putString("tomorrow_api_key", apiKey).apply()
    }

    fun getTempUnit(context: Context): String {
        return getNormalPrefs(context).getString("temp_unit", "F") ?: "F" // F or C
    }

    fun saveTempUnit(context: Context, unit: String) {
        getNormalPrefs(context).edit().putString("temp_unit", unit).apply()
    }

    fun getWindSpeedUnit(context: Context): String {
        return getNormalPrefs(context).getString("wind_unit", "mph") ?: "mph" // mph, km/h, knots
    }

    fun saveWindSpeedUnit(context: Context, unit: String) {
        getNormalPrefs(context).edit().putString("wind_unit", unit).apply()
    }

    /**
     * Measurement system for everything that isn't temperature or wind:
     * pressure (hPa vs inHg), distance/visibility/ceiling (km vs mi),
     * rainfall (mm vs in), tide height (m vs ft).
     *
     * Defaults to imperial if temp unit is °F, else metric — matches what
     * most users implicitly expect when they pick a temp unit.
     */
    fun getMeasurementSystem(context: Context): String {
        val prefs = getNormalPrefs(context)
        prefs.getString("measurement_system", null)?.let { return it }
        return if (getTempUnit(context) == "F") "imperial" else "metric"
    }

    fun saveMeasurementSystem(context: Context, system: String) {
        getNormalPrefs(context).edit().putString("measurement_system", system).apply()
    }

    fun getRefreshIntervalMinutes(context: Context): Int {
        return getNormalPrefs(context).getInt("refresh_interval", 15)
    }

    fun saveRefreshIntervalMinutes(context: Context, minutes: Int) {
        getNormalPrefs(context).edit().putInt("refresh_interval", minutes).apply()
    }

    fun getSevereAlertSeverityThreshold(context: Context): String {
        return getNormalPrefs(context).getString("severe_threshold", "moderate") ?: "moderate" // minor, moderate, severe, extreme
    }

    fun saveSevereAlertSeverityThreshold(context: Context, threshold: String) {
        getNormalPrefs(context).edit().putString("severe_threshold", threshold).apply()
    }

    fun getProximityAlertRadiusMiles(context: Context): Int {
        return getNormalPrefs(context).getInt("proximity_radius", 150)
    }

    fun saveProximityAlertRadiusMiles(context: Context, miles: Int) {
        getNormalPrefs(context).edit().putInt("proximity_radius", miles).apply()
    }

    fun getThemeOverride(context: Context): String {
        return getNormalPrefs(context).getString("theme_override", "system") ?: "system" // system, light, dark
    }

    fun saveThemeOverride(context: Context, theme: String) {
        getNormalPrefs(context).edit().putString("theme_override", theme).apply()
    }

    fun getMapStyle(context: Context): String {
        return getNormalPrefs(context).getString("map_style", "standard") ?: "standard" // standard, satellite, dark
    }

    fun saveMapStyle(context: Context, style: String) {
        getNormalPrefs(context).edit().putString("map_style", style).apply()
    }

    fun getStatusBarTempToggle(context: Context): Boolean {
        return getNormalPrefs(context).getBoolean("status_bar_temp", false)
    }

    fun saveStatusBarTempToggle(context: Context, enabled: Boolean) {
        getNormalPrefs(context).edit().putBoolean("status_bar_temp", enabled).apply()
    }

    fun getActiveLocationId(context: Context): Long {
        return getNormalPrefs(context).getLong("active_location_id", -1L)
    }

    fun saveActiveLocationId(context: Context, id: Long) {
        getNormalPrefs(context).edit().putLong("active_location_id", id).apply()
    }
}
