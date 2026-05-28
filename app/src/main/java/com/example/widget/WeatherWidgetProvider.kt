package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.DailyForecastItem
import com.example.data.PreferencesHelper
import com.example.data.WeatherCodeMapper
import com.example.data.WeatherDatabase
import com.example.data.deriveDailyWeatherCode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

open class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateCompactWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val compactIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val compactIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, WeatherWidgetProvider::class.java)
            )
            compactIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, compactIds)
            context.sendBroadcast(compactIntent)

            val largeIntent = Intent(context, WeatherWidgetLargeProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val largeIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, WeatherWidgetLargeProvider::class.java)
            )
            largeIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, largeIds)
            context.sendBroadcast(largeIntent)
        }
    }
}

class WeatherWidgetLargeProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateLargeWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

private fun updateCompactWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.widget_compact)

    val launchIntent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        try {
            val db = WeatherDatabase.getDatabase(context)
            val activeId = PreferencesHelper.getActiveLocationId(context)
            if (activeId != -1L) {
                val cache = db.weatherCacheDao.getWeatherCacheByLocation(activeId)
                val location = db.savedLocationDao.getLocationById(activeId)
                if (cache != null && location != null) {
                    val isC = PreferencesHelper.getTempUnit(context) == "C"
                    val tempValue = if (isC) cache.currentTemp else (cache.currentTemp * 1.8 + 32)

                    views.setTextViewText(R.id.widget_temp, "${tempValue.toInt()}°")
                    views.setTextViewText(R.id.widget_location, location.name.split(",").firstOrNull()?.trim() ?: "")
                    views.setTextViewText(R.id.widget_desc, WeatherCodeMapper.getWeatherDescription(cache.weatherCode))

                    val storms = db.stormCacheDao.getAllStorms()
                    views.setViewVisibility(R.id.widget_storm_badge, if (storms.isNotEmpty()) View.VISIBLE else View.GONE)
                }
            }
        } catch (e: Exception) {
            // leave views at their default state
        }
        appWidgetManager.updateAppWidget(widgetId, views)
    }
}

private fun updateLargeWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.widget_large)

    val launchIntent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        try {
            val db = WeatherDatabase.getDatabase(context)
            val activeId = PreferencesHelper.getActiveLocationId(context)
            if (activeId != -1L) {
                val cache = db.weatherCacheDao.getWeatherCacheByLocation(activeId)
                val location = db.savedLocationDao.getLocationById(activeId)
                if (cache != null && location != null) {
                    val isC = PreferencesHelper.getTempUnit(context) == "C"
                    val windUnit = PreferencesHelper.getWindSpeedUnit(context)

                    // Current conditions
                    val tempValue = if (isC) cache.currentTemp else (cache.currentTemp * 1.8 + 32)
                    val apparentValue = if (isC) cache.feelsLike else (cache.feelsLike * 1.8 + 32)
                    val windText = when (windUnit) {
                        "km/h" -> "${(cache.windSpeed * 3.6).toInt()} km/h"
                        "knots" -> "${(cache.windSpeed * 1.94).toInt()} kt"
                        else -> "${(cache.windSpeed * 2.237).toInt()} mph"
                    }

                    views.setTextViewText(R.id.widget_temp, "${tempValue.toInt()}°")
                    views.setTextViewText(R.id.widget_cond, WeatherCodeMapper.getWeatherDescription(cache.weatherCode))
                    views.setTextViewText(R.id.widget_feels_like, "Feels ${apparentValue.toInt()}°")
                    views.setTextViewText(R.id.widget_wind, windText)
                    views.setTextViewText(R.id.widget_humidity, "${cache.humidity.toInt()}% hum")

                    // Tiny location label — skip "Current Location", show city or coords
                    val locLabel = when {
                        location.isGPS -> "${String.format("%.1f", location.latitude)}°N ${String.format("%.1f", Math.abs(location.longitude))}°W"
                        else -> location.name.split(",").firstOrNull()?.trim() ?: location.name
                    }
                    views.setTextViewText(R.id.widget_location, locLabel)

                    // Parse daily forecast for today H/L + next 3 days
                    val moshi = Moshi.Builder().build()
                    val dailyType = Types.newParameterizedType(List::class.java, DailyForecastItem::class.java)
                    val dailyAdapter = moshi.adapter<List<DailyForecastItem>>(dailyType)
                    val daily = try { dailyAdapter.fromJson(cache.dailyJson) ?: emptyList() } catch (e: Exception) { emptyList() }

                    // Today's H/L in the top section
                    if (daily.isNotEmpty()) {
                        val todayVals = daily[0].values
                        val hiC = todayVals.temperatureMax
                        val loC = todayVals.temperatureMin
                        val hi = if (isC) hiC else hiC?.let { it * 1.8 + 32 }
                        val lo = if (isC) loC else loC?.let { it * 1.8 + 32 }
                        views.setTextViewText(R.id.widget_hilo, "H:${hi?.toInt() ?: "--"}°  L:${lo?.toInt() ?: "--"}°")
                    }

                    // Forecast rows for days 1, 2, 3 (skip today at index 0)
                    val forecastDays = daily.drop(1).take(3)
                    val dayRowIds = listOf(
                        Triple(R.id.widget_day1_name, R.id.widget_day1_cond, R.id.widget_day1_hilo),
                        Triple(R.id.widget_day2_name, R.id.widget_day2_cond, R.id.widget_day2_hilo),
                        Triple(R.id.widget_day3_name, R.id.widget_day3_cond, R.id.widget_day3_hilo)
                    )
                    forecastDays.forEachIndexed { i, day ->
                        val (nameId, condId, hiloId) = dayRowIds[i]
                        val dayLabel = parseDayName(day.time)
                        val condLabel = WeatherCodeMapper.getWeatherDescription(deriveDailyWeatherCode(day.values))
                        val hiC = day.values.temperatureMax
                        val loC = day.values.temperatureMin
                        val hi = if (isC) hiC else hiC?.let { it * 1.8 + 32 }
                        val lo = if (isC) loC else loC?.let { it * 1.8 + 32 }
                        val hiloLabel = "${hi?.toInt() ?: "--"}° / ${lo?.toInt() ?: "--"}°"
                        views.setTextViewText(nameId, dayLabel)
                        views.setTextViewText(condId, condLabel)
                        views.setTextViewText(hiloId, hiloLabel)
                    }

                    // Storm alert
                    val storms = db.stormCacheDao.getAllStorms()
                    if (storms.isNotEmpty()) {
                        val s = storms.first()
                        val label = if (s.category > 0) "${s.type} ${s.name} · Cat ${s.category}" else "${s.type} ${s.name}"
                        views.setViewVisibility(R.id.widget_storm_badge, View.VISIBLE)
                        views.setTextViewText(R.id.widget_storm_badge, "⚠ $label")
                    } else {
                        views.setViewVisibility(R.id.widget_storm_badge, View.GONE)
                    }
                }
            }
        } catch (e: Exception) {
            // leave views at their default state
        }
        appWidgetManager.updateAppWidget(widgetId, views)
    }
}

private fun parseDayName(isoTime: String): String = try {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val date = fmt.parse(isoTime) ?: return "—"
    SimpleDateFormat("EEE", Locale.getDefault()).format(date)
} catch (e: Exception) {
    "—"
}
