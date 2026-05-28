package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class NominatimResult(
    @Json(name = "place_id") val placeId: Long?,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "lat") val lat: String,
    @Json(name = "lon") val lon: String,
    @Json(name = "type") val type: String?,
    @Json(name = "addresstype") val addressType: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "address") val address: NominatimAddress?
)

@JsonClass(generateAdapter = true)
data class NominatimAddress(
    @Json(name = "city") val city: String?,
    @Json(name = "town") val town: String?,
    @Json(name = "village") val village: String?,
    @Json(name = "hamlet") val hamlet: String?,
    @Json(name = "county") val county: String?,
    @Json(name = "state") val state: String?,
    @Json(name = "country_code") val countryCode: String?
) {
    fun shortLabel(): String {
        val locality = city ?: town ?: village ?: hamlet ?: county
        val region = state
        return when {
            locality != null && region != null -> "$locality, $region"
            locality != null -> locality
            region != null -> region
            else -> ""
        }
    }
}

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 5
    ): Response<List<NominatimResult>>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("zoom") zoom: Int = 10
    ): Response<NominatimResult>
}

object GeocodingService {
    // Nominatim's usage policy requires an identifying User-Agent.
    private const val USER_AGENT = "CoastalWX/1.0 (personal use)"

    private val userAgentInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        chain.proceed(req)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val api: NominatimApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build()
        .create(NominatimApi::class.java)
}
