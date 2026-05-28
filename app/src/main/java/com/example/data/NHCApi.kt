package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class NHCCurrentStorms(
    @Json(name = "storms") val storms: List<NHCStorm>?
)

@JsonClass(generateAdapter = true)
data class NHCStorm(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "basin") val basin: String,          // AT, EP, CP (Atlantic, East Pacific, Central Pacific)
    @Json(name = "type") val type: String,            // HU (Hurricane), TS (Tropical Storm), TD (Tropical Depression)
    @Json(name = "category") val category: Int,        // 1-5 (Saffir-Simpson)
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "windSpeed") val windSpeedKts: Double, // Wind speed in knots
    @Json(name = "pressure") val pressureMb: Double,    // Minimum central pressure millibars
    @Json(name = "movementDirection") val direction: String?, // e.g. "WNW"
    @Json(name = "movementSpeed") val speedMph: Double?,      // Speed in mph
    @Json(name = "advisoryNumber") val advisoryNumber: String?,
    @Json(name = "lastUpdated") val lastUpdated: String?
)

interface NHCApi {
    @GET("CurrentStorms.json")
    suspend fun getCurrentStorms(): Response<NHCCurrentStorms>
}

// Data holder for Track Point (to serialize past and future locations for the map coordinates)
@JsonClass(generateAdapter = true)
data class TrackPoint(
    val timeLabel: String,         // e.g. "Past 24h" or "Forecast 36h"
    val latitude: Double,
    val longitude: Double,
    val windSpeedKt: Int,
    val pressureMb: Int,
    val isForecast: Boolean,
    val dateText: String           // e.g. "May 25, 06:00 UTC"
)

@JsonClass(generateAdapter = true)
data class WindRadii(
    val radius34ktNm: Double,      // Nautical miles
    val radius50ktNm: Double,
    val radius64ktNm: Double
)

@JsonClass(generateAdapter = true)
data class StormDetail(
    val id: String,
    val name: String,
    val basin: String,
    val type: String,
    val category: Int,
    val latitude: Double,
    val longitude: Double,
    val windSpeedKts: Double,
    val pressureMb: Double,
    val direction: String,
    val speedMph: Double,
    val trackPoints: List<TrackPoint>,
    val windRadii: WindRadii,
    val watchWarningAreas: List<WatchWarningArea>,
    val advisoryText: String,
    val discussionText: String,
    val detailUrl: String
)

@JsonClass(generateAdapter = true)
data class WatchWarningArea(
    val type: String, // "hurricane_watch", "hurricane_warning", "tropical_storm_watch", "tropical_storm_warning"
    val segmentPath: List<TrackPoint> // Coastline vertices to highlight
)

object NHCService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.nhc.noaa.gov/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val api: NHCApi = retrofit.create(NHCApi::class.java)

    /**
     * Generates active storm tracks for simulation if no real storms are in progress
     */
    fun getSimulatedStorm(currentTime: String = "May 25"): StormDetail {
        val baseLat = 26.5
        val baseLon = -79.2
        
        // 5-day track beginning 24 hours ago and projecting out 120 hours
        val points = listOf(
            // Past spots
            TrackPoint("Past 24h", baseLat - 2.0, baseLon - 3.0, 85, 975, false, "May 24, 00:00 UTC"),
            TrackPoint("Past 12h", baseLat - 1.0, baseLon - 1.5, 95, 964, false, "May 24, 12:00 UTC"),
            TrackPoint("Current Location", baseLat, baseLon, 105, 952, false, "May 24, 23:00 UTC (Current)"),
            // Future forecasts (open dots on map)
            TrackPoint("12h Forecast", baseLat + 1.2, baseLon + 1.2, 110, 948, true, "May 25, 12:00 UTC"),
            TrackPoint("24h Forecast", baseLat + 2.4, baseLon + 2.5, 115, 944, true, "May 25, 23:00 UTC"),
            TrackPoint("36h Forecast", baseLat + 3.6, baseLon + 3.6, 120, 940, true, "May 26, 11:00 UTC"),
            TrackPoint("48h Forecast", baseLat + 4.8, baseLon + 4.6, 115, 944, true, "May 26, 23:00 UTC"),
            TrackPoint("72h Forecast", baseLat + 7.0, baseLon + 6.0, 100, 958, true, "May 27, 23:00 UTC"),
            TrackPoint("96h Forecast", baseLat + 9.5, baseLon + 7.2, 85, 970, true, "May 28, 23:00 UTC"),
            TrackPoint("120h Forecast", baseLat + 12.0, baseLon + 8.1, 65, 985, true, "May 29, 23:00 UTC")
        )

        val windRadii = WindRadii(
            radius34ktNm = 120.0,
            radius50ktNm = 75.0,
            radius64ktNm = 40.0
        )

        // Coastal segments (approximation for visual mapping on map)
        val watchWarningAreas = listOf(
            WatchWarningArea(
                "hurricane_warning",
                listOf(
                    TrackPoint("P1", 25.5, -80.4, 0, 0, false, ""),
                    TrackPoint("P2", 26.8, -80.0, 0, 0, false, ""),
                    TrackPoint("P3", 28.0, -80.6, 0, 0, false, "")
                )
            ),
            WatchWarningArea(
                "tropical_storm_watch",
                listOf(
                    TrackPoint("W1", 24.5, -81.8, 0, 0, false, ""),
                    TrackPoint("W2", 25.0, -80.8, 0, 0, false, ""),
                    TrackPoint("W3", 29.0, -81.0, 0, 0, false, "")
                )
            )
        )

        val advisoryText = """
            SIMULATED TROPICAL CYCLONE ADVISORY
            NATIONAL HURRICANE CENTER MIAMI FL
            1100 PM EDT SUN MAY 24 2026

            ...SIMULATED STORM CORE PASSING NEAR EASTERN BAHAMAS...
            ...EXPECTED TO STRENGTHEN AS IT TRACKS TOWARD THE CAROLINAS...

            SUMMARY OF 1100 PM EDT...0300 UTC...INFORMATION
            ----------------------------------------------
            LOCATION...26.5N 79.2W
            ABOUT 120 MI ENE OF MIAMI FLORIDA
            MAXIMUM SUSTAINED WINDS...105 KTS...120 MPH
            PRESENT MOVEMENT...NE OR 45 DEGREES AT 14 MPH
            MINIMUM CENTRAL PRESSURE...952 MB

            WATCHES AND WARNINGS
            --------------------
            A HURRICANE WARNING IS IN EFFECT FOR...
            * COASTAL BROWARD, PALM BEACH, AND MARTIN COUNTIES IN FLORIDA.

            A TROPICAL STORM WATCH IS IN EFFECT FOR...
            * FLOODING CONDITIONS EXTENDING INTO COASTAL GEORGIA.
        """.trimIndent()

        val discussionText = """
            SIMULATED TROPICAL CYCLONE DISCUSSION
            NWS NATIONAL HURRICANE CENTER MIAMI FL
            1100 PM EDT SUN MAY 24 2026

            This simulated cyclone remains well-organized on infrared satellite imagery with a clear, stable 20-nautical-mile wide eye visible in regional Doppler radar scans. Upper air assessments indicate extremely favorable environments with shear below 8 knots and warm sea-surface temperature anomalies of 2.2 degrees Celsius above climatological levels. 
            
            The current trajectory tracker estimates a steady poleward flow fueled by the Bermuda High pressure ridge. Rapid intensification models suggest the simulated cyclone could approach Category 4 intensity within the next 24 to 36 hours as it passes over the core gulf streams. Saved locations within the forecast swath are strongly encouraged to finalize mitigation plans as storm surge and local rain totals could escalate significantly.
        """.trimIndent()

        return StormDetail(
            id = "SIM2026",
            name = "Simulated Cyclone",
            basin = "Atlantic",
            type = "Hurricane",
            category = 3,
            latitude = baseLat,
            longitude = baseLon,
            windSpeedKts = 105.0,
            pressureMb = 952.0,
            direction = "NE",
            speedMph = 14.0,
            trackPoints = points,
            windRadii = windRadii,
            watchWarningAreas = watchWarningAreas,
            advisoryText = advisoryText,
            discussionText = discussionText,
            detailUrl = "https://www.nhc.noaa.gov/"
        )
    }
}
