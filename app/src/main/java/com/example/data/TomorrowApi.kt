package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// Response DTOs
@JsonClass(generateAdapter = true)
data class TomorrowForecastResponse(
    @Json(name = "timelines") val timelines: Timelines?,
    @Json(name = "location") val location: LocationInfo?
)

@JsonClass(generateAdapter = true)
data class Timelines(
    @Json(name = "minutely") val minutely: List<MinutelyForecastItem>?,
    @Json(name = "hourly") val hourly: List<HourlyForecastItem>?,
    @Json(name = "daily") val daily: List<DailyForecastItem>?
)

@JsonClass(generateAdapter = true)
data class MinutelyForecastItem(
    @Json(name = "time") val time: String,
    @Json(name = "values") val values: MinutelyValues
)

@JsonClass(generateAdapter = true)
data class MinutelyValues(
    @Json(name = "rainIntensity") val rainIntensity: Double?,
    @Json(name = "snowIntensity") val snowIntensity: Double?,
    @Json(name = "freezingRainIntensity") val freezingRainIntensity: Double?,
    @Json(name = "sleetIntensity") val sleetIntensity: Double?,
    @Json(name = "precipitationProbability") val precipitationProbability: Double?
)

@JsonClass(generateAdapter = true)
data class HourlyForecastItem(
    @Json(name = "time") val time: String,
    @Json(name = "values") val values: HourlyValues
)

@JsonClass(generateAdapter = true)
data class HourlyValues(
    @Json(name = "temperature") val temperature: Double?,
    @Json(name = "temperatureApparent") val temperatureApparent: Double?,
    @Json(name = "humidity") val humidity: Double?,
    @Json(name = "dewPoint") val dewPoint: Double?,
    @Json(name = "windSpeed") val windSpeed: Double?,
    @Json(name = "windGust") val windGust: Double?,
    @Json(name = "windDirection") val windDirection: Double?,
    @Json(name = "uvIndex") val uvIndex: Double?,
    @Json(name = "visibility") val visibility: Double?,
    @Json(name = "cloudCover") val cloudCover: Double?,
    @Json(name = "cloudBase") val cloudBase: Double?,
    @Json(name = "cloudCeiling") val cloudCeiling: Double?,
    @Json(name = "pressureSurfaceLevel") val pressureSurfaceLevel: Double?,
    @Json(name = "pressureSeaLevel") val pressureSeaLevel: Double?,
    @Json(name = "weatherCode") val weatherCode: Int?,
    @Json(name = "precipitationProbability") val precipitationProbability: Double?,
    @Json(name = "precipitationIntensity") val precipitationIntensity: Double?,
    @Json(name = "rainIntensity") val rainIntensity: Double?,
    @Json(name = "snowIntensity") val snowIntensity: Double?,
    @Json(name = "freezingRainIntensity") val freezingRainIntensity: Double?,
    @Json(name = "sleetIntensity") val sleetIntensity: Double?
)

@JsonClass(generateAdapter = true)
data class DailyForecastItem(
    @Json(name = "time") val time: String,
    @Json(name = "values") val values: DailyValues
)

@JsonClass(generateAdapter = true)
data class DailyValues(
    @Json(name = "temperatureMin") val temperatureMin: Double?,
    @Json(name = "temperatureMax") val temperatureMax: Double?,
    @Json(name = "temperatureApparentMin") val temperatureApparentMin: Double?,
    @Json(name = "temperatureApparentMax") val temperatureApparentMax: Double?,
    @Json(name = "precipitationProbabilityAvg") val precipitationProbabilityAvg: Double?,
    @Json(name = "precipitationProbabilityMax") val precipitationProbabilityMax: Double?,
    @Json(name = "precipitationProbability") val precipitationProbability: Double?,
    @Json(name = "precipitationIntensityAvg") val precipitationIntensityAvg: Double?,
    @Json(name = "precipitationType") val precipitationType: Int?,
    @Json(name = "rainIntensityAvg") val rainIntensityAvg: Double?,
    @Json(name = "rainIntensityMax") val rainIntensityMax: Double?,
    @Json(name = "snowIntensityAvg") val snowIntensityAvg: Double?,
    @Json(name = "snowIntensityMax") val snowIntensityMax: Double?,
    @Json(name = "freezingRainIntensityMax") val freezingRainIntensityMax: Double?,
    @Json(name = "sleetIntensityMax") val sleetIntensityMax: Double?,
    @Json(name = "windSpeedAvg") val windSpeedAvg: Double?,
    @Json(name = "windSpeedMax") val windSpeedMax: Double?,
    @Json(name = "windGustAvg") val windGustAvg: Double?,
    @Json(name = "windGustMax") val windGustMax: Double?,
    @Json(name = "humidityAvg") val humidityAvg: Double?,
    @Json(name = "uvIndexMax") val uvIndexMax: Double?,
    @Json(name = "uvHealthConcernMax") val uvHealthConcernMax: Int?,
    @Json(name = "cloudCover") val cloudCover: Double?,
    @Json(name = "cloudCoverAvg") val cloudCoverAvg: Double?,
    @Json(name = "weatherCode") val weatherCode: Int?,
    @Json(name = "weatherCodeMax") val weatherCodeMax: Int?,
    @Json(name = "rainAccumulationSum") val rainAccumulationSum: Double?,
    @Json(name = "sunriseTime") val sunriseTime: String?,
    @Json(name = "sunsetTime") val sunsetTime: String?,
    @Json(name = "moonPhase") val moonPhase: Int?,
    @Json(name = "moonriseTime") val moonriseTime: String?,
    @Json(name = "moonsetTime") val moonsetTime: String?
)

@JsonClass(generateAdapter = true)
data class LocationInfo(
    @Json(name = "lat") val lat: Double?,
    @Json(name = "lon") val lon: Double?,
    @Json(name = "name") val name: String?
)

interface TomorrowApi {
    @GET("v4/weather/forecast")
    suspend fun getForecast(
        @Query("location") location: String, // "lat,lon" or "city"
        @Query("apikey") apiKey: String,
        @Query("units") units: String // "metric" or "imperial"
    ): Response<TomorrowForecastResponse>
}

/**
 * Tomorrow.io's daily timestep does not return a plain `weatherCode` — only `weatherCodeAvg`
 * (a composite like 1261 that doesn't map to any single condition) plus Max/Min. `weatherCodeMax`
 * picks the highest numeric code regardless of how briefly that condition occurred, so a single
 * stray rainy hour can flip the whole day to "rain."
 *
 * This derives a representative single code from the daily aggregates (cloud cover, precip
 * probability, intensity, type) — picking what the day will mostly look like, not its worst
 * minute. Output codes are restricted to those `WeatherCodeMapper` and `WeatherConditionIcon`
 * already support.
 */
fun deriveDailyWeatherCode(values: DailyValues): Int {
    val precipProbAvg = values.precipitationProbabilityAvg
        ?: values.precipitationProbability
        ?: 0.0
    val precipProbMax = values.precipitationProbabilityMax ?: precipProbAvg
    val rainMax = values.rainIntensityMax ?: 0.0
    val snowMax = values.snowIntensityMax ?: 0.0
    val freezingMax = values.freezingRainIntensityMax ?: 0.0
    val sleetMax = values.sleetIntensityMax ?: 0.0
    val cloudCover = values.cloudCoverAvg ?: values.cloudCover ?: 0.0
    val tempMin = values.temperatureMin ?: 10.0
    val windGustMax = values.windGustMax ?: 0.0

    // Frozen precipitation variants first
    if (precipProbMax >= 25.0 && freezingMax > 0.0) return 6001  // Freezing Rain
    if (precipProbMax >= 25.0 && sleetMax > 0.0) return 7000     // Ice Pellets
    if ((snowMax > 0.0 || (precipProbMax >= 25.0 && tempMin < 0.0)) && snowMax >= rainMax) {
        return if (snowMax > 0.5) 5101 else 5100                  // Heavy / Light Snow
    }

    // Thunderstorm proxy: Tomorrow.io doesn't return lightning, so use heavy rain + strong gusts.
    if (precipProbMax >= 60.0 && rainMax >= 3.0 && windGustMax >= 14.0) return 8000

    // Rain bands — Avg drives "most of the day" verdict; Max picks intensity tier.
    if (precipProbAvg >= 50.0 && rainMax >= 2.5) return 4201               // Heavy Rain
    if (precipProbAvg >= 40.0) return 4001                                  // Rain
    if (precipProbAvg >= 20.0 || (precipProbMax >= 50.0 && rainMax > 0.0)) return 4200  // Light Rain
    if (precipProbMax >= 25.0 && rainMax > 0.0) return 4000                 // Drizzle

    // Dry day — fall through to cloud cover buckets.
    return when {
        cloudCover >= 87.0 -> 1001  // Cloudy
        cloudCover >= 60.0 -> 1102  // Mostly Cloudy
        cloudCover >= 25.0 -> 1101  // Partly Cloudy
        cloudCover >= 10.0 -> 1100  // Mostly Clear
        else -> 1000                // Clear
    }
}

// ----- Nowcasting (minute-by-minute precip onset / cessation) -----

enum class PrecipKind(val label: String) {
    Rain("Rain"),
    Snow("Snow"),
    FreezingRain("Freezing rain"),
    Sleet("Sleet")
}

sealed interface NowcastVerdict {
    /** Currently precipitating; expected to stop at [endMillis] (about [minutesUntilEnd] minutes from now). */
    data class Stopping(val kind: PrecipKind, val minutesUntilEnd: Int, val endMillis: Long) : NowcastVerdict
    /** Currently precipitating with no end in sight within the lookahead window. */
    data class Continuing(val kind: PrecipKind) : NowcastVerdict
    /** Dry now; precipitation expected to begin at [startMillis] (about [minutesUntilStart] minutes from now). */
    data class Starting(val kind: PrecipKind, val minutesUntilStart: Int, val startMillis: Long) : NowcastVerdict
    /** Dry now and dry for the entire lookahead window; nothing worth surfacing. */
    data object Clear : NowcastVerdict
}

/**
 * Classifies the next [lookaheadMin] minutes of minutely data into one of four verdicts.
 * Threshold of 0.1 mm/hr filters out the trace-amount noise that Tomorrow.io emits even on dry days.
 */
fun computeNowcast(
    minutely: List<MinutelyForecastItem>,
    lookaheadMin: Int = 60,
    intensityThresholdMmHr: Double = 0.1
): NowcastVerdict {
    if (minutely.isEmpty()) return NowcastVerdict.Clear
    val window = minutely.take(lookaheadMin)

    fun kindOf(v: MinutelyValues): PrecipKind? {
        return when {
            (v.snowIntensity ?: 0.0) >= intensityThresholdMmHr -> PrecipKind.Snow
            (v.freezingRainIntensity ?: 0.0) >= intensityThresholdMmHr -> PrecipKind.FreezingRain
            (v.sleetIntensity ?: 0.0) >= intensityThresholdMmHr -> PrecipKind.Sleet
            (v.rainIntensity ?: 0.0) >= intensityThresholdMmHr -> PrecipKind.Rain
            else -> null
        }
    }

    val nowKind = kindOf(window.first().values)
    if (nowKind != null) {
        val stopIdx = window.indexOfFirst { kindOf(it.values) == null }
        return if (stopIdx > 0) {
            val ts = parseIsoUtcMillis(window[stopIdx].time) ?: return NowcastVerdict.Continuing(nowKind)
            NowcastVerdict.Stopping(nowKind, stopIdx, ts)
        } else NowcastVerdict.Continuing(nowKind)
    }

    val startIdx = window.indexOfFirst { kindOf(it.values) != null }
    if (startIdx <= 0) return NowcastVerdict.Clear
    val futureKind = kindOf(window[startIdx].values) ?: PrecipKind.Rain
    val ts = parseIsoUtcMillis(window[startIdx].time) ?: return NowcastVerdict.Clear
    return NowcastVerdict.Starting(futureKind, startIdx, ts)
}

private fun parseIsoUtcMillis(iso: String): Long? = try {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    fmt.parse(iso)?.time
} catch (e: Exception) {
    null
}

// ----- Pressure trend (compare current vs 6h-out sea-level pressure) -----

enum class PressureTrend(val symbol: String, val label: String) {
    Rising("↑", "Rising"),
    Falling("↓", "Falling"),
    Steady("→", "Steady")
}

fun computePressureTrend(
    hourly: List<HourlyForecastItem>,
    hoursAhead: Int = 6,
    thresholdHpa: Double = 1.0
): PressureTrend? {
    if (hourly.size <= hoursAhead) return null
    val now = hourly.first().values.pressureSeaLevel
        ?: hourly.first().values.pressureSurfaceLevel
        ?: return null
    val later = hourly[hoursAhead].values.pressureSeaLevel
        ?: hourly[hoursAhead].values.pressureSurfaceLevel
        ?: return null
    val delta = later - now
    return when {
        delta >= thresholdHpa -> PressureTrend.Rising
        delta <= -thresholdHpa -> PressureTrend.Falling
        else -> PressureTrend.Steady
    }
}

// ----- UV health concern category (Tomorrow.io 0-4 scale) -----

fun uvHealthLabel(level: Int?): String = when (level) {
    0 -> "Low"
    1 -> "Moderate"
    2 -> "High"
    3 -> "Very High"
    4 -> "Extreme"
    else -> "—"
}

// ----- Moon phase label (Tomorrow.io 0-7 enum) -----

fun moonPhaseLabel(phase: Int?): String = when (phase) {
    0 -> "New Moon"
    1 -> "Waxing Crescent"
    2 -> "First Quarter"
    3 -> "Waxing Gibbous"
    4 -> "Full Moon"
    5 -> "Waning Gibbous"
    6 -> "Last Quarter"
    7 -> "Waning Crescent"
    else -> "—"
}

object WeatherCodeMapper {
    fun getWeatherDescription(code: Int?): String {
        return when (code) {
            1000 -> "Clear"
            1100 -> "Mostly Clear"
            1101 -> "Partly Cloudy"
            1102 -> "Mostly Cloudy"
            1001 -> "Cloudy"
            2000 -> "Fog"
            2100 -> "Light Fog"
            4000 -> "Drizzle"
            4001 -> "Rain"
            4200 -> "Light Rain"
            4201 -> "Heavy Rain"
            5000 -> "Snow"
            5001 -> "Flurries"
            5100 -> "Light Snow"
            5101 -> "Heavy Snow"
            6000 -> "Freezing Drizzle"
            6001 -> "Freezing Rain"
            6200 -> "Light Freezing Rain"
            6201 -> "Heavy Freezing Rain"
            7000 -> "Ice Pellets"
            7100 -> "Light Ice Pellets"
            7101 -> "Heavy Ice Pellets"
            8000 -> "Thunderstorm"
            else -> "Partly Cloudy"
        }
    }
}
