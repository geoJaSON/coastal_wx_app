package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Station list response (mdapi)
@JsonClass(generateAdapter = true)
data class TideStationListResponse(
    @Json(name = "stations") val stations: List<TideStation>?
)

@JsonClass(generateAdapter = true)
data class TideStation(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "lat") val latitude: Double,
    @Json(name = "lng") val longitude: Double,
    @Json(name = "state") val state: String?
)

// Predictions response (datagetter)
@JsonClass(generateAdapter = true)
data class TidePredictionsResponse(
    @Json(name = "predictions") val predictions: List<TidePrediction>?,
    @Json(name = "error") val error: TideError?
)

@JsonClass(generateAdapter = true)
data class TidePrediction(
    @Json(name = "t") val time: String,    // "YYYY-MM-DD HH:mm"
    @Json(name = "v") val value: String,   // height in feet (since units=english)
    @Json(name = "type") val type: String  // "H" for high, "L" for low
)

@JsonClass(generateAdapter = true)
data class TideError(
    @Json(name = "message") val message: String?
)

interface TideStationApi {
    @GET("mdapi/prod/webapi/stations.json")
    suspend fun getStations(
        @Query("type") type: String = "tidepredictions"
    ): Response<TideStationListResponse>
}

interface TidePredictionsApi {
    @GET("api/prod/datagetter")
    suspend fun getPredictions(
        @Query("station") stationId: String,
        @Query("begin_date") beginDate: String,   // YYYYMMDD
        @Query("range") rangeHours: Int = 48,
        @Query("product") product: String = "predictions",
        @Query("datum") datum: String = "MLLW",
        @Query("units") units: String = "english",
        @Query("time_zone") timeZone: String = "lst_ldt",
        @Query("interval") interval: String = "hilo",
        @Query("format") format: String = "json",
        @Query("application") application: String = "CoastalWX"
    ): Response<TidePredictionsResponse>
}

object TidesService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()

    val stationApi: TideStationApi = Retrofit.Builder()
        .baseUrl("https://api.tidesandcurrents.noaa.gov/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TideStationApi::class.java)

    val predictionsApi: TidePredictionsApi = Retrofit.Builder()
        .baseUrl("https://api.tidesandcurrents.noaa.gov/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TidePredictionsApi::class.java)

    fun findNearestStation(
        stations: List<TideStation>,
        lat: Double,
        lon: Double,
        maxMiles: Double = 75.0
    ): TideStation? {
        var best: TideStation? = null
        var bestDist = Double.MAX_VALUE
        for (s in stations) {
            val d = haversineMiles(lat, lon, s.latitude, s.longitude)
            if (d < bestDist) {
                bestDist = d
                best = s
            }
        }
        return if (bestDist <= maxMiles) best else null
    }

    private fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMiles = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMiles * c
    }

    fun todayBeginDate(): String {
        val cal = Calendar.getInstance()
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    fun parsePredictionTime(t: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            // NOAA returns local-station time when time_zone=lst_ldt; we treat the parsed
            // value as local clock time of the station, which is what the user expects.
            sdf.timeZone = TimeZone.getDefault()
            sdf.parse(t)?.time
        } catch (e: Exception) {
            null
        }
    }
}

data class TideSummary(
    val stationName: String,
    val stationId: String,
    val predictions: List<TidePrediction>
) {
    fun nextEvent(now: Long = System.currentTimeMillis()): TidePrediction? {
        return predictions.firstOrNull { p ->
            val t = TidesService.parsePredictionTime(p.time) ?: return@firstOrNull false
            t > now
        }
    }
}
