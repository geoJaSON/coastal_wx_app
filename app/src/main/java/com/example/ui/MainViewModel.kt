package com.example.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.*
import com.example.widget.WeatherWidgetProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = WeatherDatabase.getDatabase(context)

    // JSON adapters for cache persistence
    private val moshi = Moshi.Builder().build()
    private val hourlyType = Types.newParameterizedType(List::class.java, HourlyForecastItem::class.java)
    private val hourlyAdapter = moshi.adapter<List<HourlyForecastItem>>(hourlyType)
    private val dailyType = Types.newParameterizedType(List::class.java, DailyForecastItem::class.java)
    private val dailyAdapter = moshi.adapter<List<DailyForecastItem>>(dailyType)
    private val trackType = Types.newParameterizedType(List::class.java, TrackPoint::class.java)
    private val trackAdapter = moshi.adapter<List<TrackPoint>>(trackType)
    private val stationListAdapter = moshi.adapter(TideStationListResponse::class.java)

    // Tomorrow.io Retrofit Client
    private val tomorrowApi: TomorrowApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.tomorrow.io/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TomorrowApi::class.java)
    }

    // 1. Saved locations state
    val savedLocations: StateFlow<List<SavedLocation>> = db.savedLocationDao.getAllLocationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Active location tracking
    private val _activeLocationId = MutableStateFlow<Long>(-1L)
    val activeLocationId: StateFlow<Long> = _activeLocationId.asStateFlow()

    private val _currentLocation = MutableStateFlow<SavedLocation?>(null)
    val currentLocation: StateFlow<SavedLocation?> = _currentLocation.asStateFlow()

    // 3. Current Weather and Forecast UI States
    private val _weatherState = MutableStateFlow<WeatherUIState>(WeatherUIState.Loading)
    val weatherState: StateFlow<WeatherUIState> = _weatherState.asStateFlow()

    // 4. Alerts and Tropics
    val activeAlerts: StateFlow<List<ActiveAlert>> = db.activeAlertDao.getActiveAlertsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _stormState = MutableStateFlow<StormUIState>(StormUIState.NoStorms)
    val stormState: StateFlow<StormUIState> = _stormState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _showConnectionTestResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val showConnectionTestResult: StateFlow<Pair<Boolean, String>?> = _showConnectionTestResult.asStateFlow()

    // 5. Tides
    private val _tideSummary = MutableStateFlow<TideSummary?>(null)
    val tideSummary: StateFlow<TideSummary?> = _tideSummary.asStateFlow()

    @Volatile private var cachedTideStations: List<TideStation>? = null

    // 6. Geocoding / city search
    private val _geocodeResults = MutableStateFlow<List<NominatimResult>>(emptyList())
    val geocodeResults: StateFlow<List<NominatimResult>> = _geocodeResults.asStateFlow()

    private val _geocodeSearching = MutableStateFlow(false)
    val geocodeSearching: StateFlow<Boolean> = _geocodeSearching.asStateFlow()

    private var geocodeJob: Job? = null

    // 7. GPS request state
    private val _gpsRequestState = MutableStateFlow<GpsRequestState>(GpsRequestState.Idle)
    val gpsRequestState: StateFlow<GpsRequestState> = _gpsRequestState.asStateFlow()

    init {
        createAlertNotificationChannel()

        // Setup initial default database elements if none exist
        viewModelScope.launch(Dispatchers.IO) {
            val count = db.savedLocationDao.getAllLocationsFlow().first().size
            if (count == 0) {
                val primaryId = db.savedLocationDao.insertLocation(
                    SavedLocation(name = "Miami, FL", latitude = 25.7617, longitude = -80.1918)
                )
                db.savedLocationDao.insertLocation(
                    SavedLocation(name = "Outer Banks, NC", latitude = 35.5585, longitude = -75.4665)
                )
                db.savedLocationDao.insertLocation(
                    SavedLocation(name = "New York, NY", latitude = 40.7128, longitude = -74.0060)
                )
                PreferencesHelper.saveActiveLocationId(context, primaryId)
                _activeLocationId.value = primaryId
            } else {
                var activeId = PreferencesHelper.getActiveLocationId(context)
                if (activeId == -1L) {
                    val first = db.savedLocationDao.getAllLocationsFlow().first().firstOrNull()
                    if (first != null) {
                        PreferencesHelper.saveActiveLocationId(context, first.id)
                        activeId = first.id
                    }
                }
                _activeLocationId.value = activeId
            }

            // If GPS permission is already granted, refresh the GPS row coords silently.
            if (LocationProvider.hasPermission(context)) {
                refreshGpsCoordinatesSilently()
            }

            syncActiveLocationData()
            startPeriodicRefresh()
        }
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val intervalMins = PreferencesHelper.getRefreshIntervalMinutes(context)
                delay(TimeUnit.MINUTES.toMillis(intervalMins.toLong()))
                if (LocationProvider.hasPermission(context)) {
                    refreshGpsCoordinatesSilently()
                }
                refreshAllData(silent = true)
            }
        }
    }

    fun selectActiveLocation(locationId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            PreferencesHelper.saveActiveLocationId(context, locationId)
            _activeLocationId.value = locationId
            _tideSummary.value = null
            syncActiveLocationData()
        }
    }

    suspend fun syncActiveLocationData() {
        val activeId = _activeLocationId.value
        if (activeId == -1L) return

        val location = db.savedLocationDao.getLocationById(activeId) ?: return
        _currentLocation.value = location

        val cache = db.weatherCacheDao.getWeatherCacheByLocation(activeId)
        if (cache != null) {
            val hourly = try { hourlyAdapter.fromJson(cache.hourlyJson) ?: emptyList() } catch (e: Exception) { emptyList() }
            val daily = try { dailyAdapter.fromJson(cache.dailyJson) ?: emptyList() } catch (e: Exception) { emptyList() }

            _weatherState.value = WeatherUIState.Success(
                currentTemp = cache.currentTemp,
                feelsLike = cache.feelsLike,
                humidity = cache.humidity,
                windSpeed = cache.windSpeed,
                windDirection = cache.windDirection,
                uvIndex = cache.uvIndex,
                visibility = cache.visibility,
                pressure = cache.pressure,
                weatherCode = cache.weatherCode,
                lastUpdatedMillis = cache.lastUpdatedMillis,
                hourlyForecast = hourly,
                dailyForecast = daily,
                isCached = true
            )
        } else {
            _weatherState.value = WeatherUIState.Loading
        }

        refreshAllData(silent = true)
    }

    fun refreshAllData(silent: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!silent) _isRefreshing.value = true

            val activeId = _activeLocationId.value
            val loc = _currentLocation.value
            val apiKey = PreferencesHelper.getApiKey(context)

            if (loc != null && apiKey.isNotBlank() && activeId != -1L) {
                try {
                    val response = tomorrowApi.getForecast(
                        location = "${loc.latitude},${loc.longitude}",
                        apiKey = apiKey,
                        units = "metric"
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val minutelyItems = body.timelines?.minutely ?: emptyList()
                        val hourlyItems = body.timelines?.hourly ?: emptyList()
                        val dailyItems = body.timelines?.daily ?: emptyList()

                        val current = hourlyItems.firstOrNull()?.values

                        if (current != null) {
                            val temp = current.temperature ?: 0.0
                            val feels = current.temperatureApparent ?: temp
                            val hum = current.humidity ?: 0.0
                            val windS = current.windSpeed ?: 0.0
                            val windD = current.windDirection ?: 0.0
                            val uv = current.uvIndex ?: 0.0
                            val vis = current.visibility ?: 10.0
                            val press = current.pressureSurfaceLevel ?: 1013.25
                            val code = current.weatherCode ?: 1000

                            val updatedCache = WeatherCache(
                                locationId = activeId,
                                currentTemp = temp,
                                feelsLike = feels,
                                humidity = hum,
                                windSpeed = windS,
                                windDirection = windD,
                                uvIndex = uv,
                                visibility = vis,
                                pressure = press,
                                weatherCode = code,
                                lastUpdatedMillis = System.currentTimeMillis(),
                                hourlyJson = hourlyAdapter.toJson(hourlyItems.take(24)),
                                dailyJson = dailyAdapter.toJson(dailyItems.take(5))
                            )

                            db.weatherCacheDao.insertWeatherCache(updatedCache)

                            _weatherState.value = WeatherUIState.Success(
                                currentTemp = temp,
                                feelsLike = feels,
                                humidity = hum,
                                windSpeed = windS,
                                windDirection = windD,
                                uvIndex = uv,
                                visibility = vis,
                                pressure = press,
                                weatherCode = code,
                                lastUpdatedMillis = updatedCache.lastUpdatedMillis,
                                hourlyForecast = hourlyItems.take(24),
                                dailyForecast = dailyItems.take(5),
                                minutelyForecast = minutelyItems.take(60),
                                isCached = false
                            )

                            WeatherWidgetProvider.triggerUpdate(context)
                        }
                    } else {
                        markActiveStateAsCachedOrError()
                    }
                } catch (e: Exception) {
                    markActiveStateAsCachedOrError()
                }
            } else if (apiKey.isBlank()) {
                _weatherState.value = WeatherUIState.NoApiKey
            }

            // Run side-channel syncs in parallel; failures here shouldn't surface as main errors.
            launch { syncNWSAlerts(loc, activeId) }
            launch { syncTides(loc) }
            launch { syncHurricaneTracking() }

            _isRefreshing.value = false
        }
    }

    private suspend fun markActiveStateAsCachedOrError() {
        val state = _weatherState.value
        if (state is WeatherUIState.Success) {
            _weatherState.value = state.copy(isCached = true)
        } else {
            val activeId = _activeLocationId.value
            val cache = db.weatherCacheDao.getWeatherCacheByLocation(activeId)
            if (cache != null) {
                val hourly = try { hourlyAdapter.fromJson(cache.hourlyJson) ?: emptyList() } catch (e: Exception) { emptyList() }
                val daily = try { dailyAdapter.fromJson(cache.dailyJson) ?: emptyList() } catch (e: Exception) { emptyList() }

                _weatherState.value = WeatherUIState.Success(
                    currentTemp = cache.currentTemp,
                    feelsLike = cache.feelsLike,
                    humidity = cache.humidity,
                    windSpeed = cache.windSpeed,
                    windDirection = cache.windDirection,
                    uvIndex = cache.uvIndex,
                    visibility = cache.visibility,
                    pressure = cache.pressure,
                    weatherCode = cache.weatherCode,
                    lastUpdatedMillis = cache.lastUpdatedMillis,
                    hourlyForecast = hourly,
                    dailyForecast = daily,
                    isCached = true
                )
            } else {
                _weatherState.value = WeatherUIState.Error("Offline. Enter Tomorrow.io key inside settings to restore connection.")
            }
        }
    }

    // ---------- NWS alerts ----------
    private suspend fun syncNWSAlerts(loc: SavedLocation?, activeId: Long) {
        if (loc == null || activeId == -1L) return
        try {
            val resp = NWSService.api.getActiveAlerts(point = "${loc.latitude},${loc.longitude}")
            if (!resp.isSuccessful) return
            val features = resp.body()?.features ?: emptyList()

            // Stable per-location id namespace so we can prune stale alerts.
            val prefix = "nws_${activeId}_"
            val existing = db.activeAlertDao.getAllAlerts().filter { it.id.startsWith(prefix) }
            val incomingIds = features.mapNotNull { f -> f.properties.id?.let { prefix + it } }.toSet()

            // Remove alerts that NWS no longer reports as active for this location.
            existing.filter { it.id !in incomingIds }.forEach { stale ->
                db.activeAlertDao.deleteAlertById(stale.id)
            }

            // Insert new alerts; skip ones the user already dismissed.
            val dismissedIds = existing.filter { it.dismissed }.map { it.id }.toSet()
            val toInsert = features.mapNotNull { f ->
                val props = f.properties
                val rawId = props.id ?: return@mapNotNull null
                val storedId = prefix + rawId
                if (storedId in dismissedIds) return@mapNotNull null
                ActiveAlert(
                    id = storedId,
                    title = props.event ?: props.headline ?: "Weather Alert",
                    severity = NWSService.severityRank(props.severity),
                    description = listOfNotNull(
                        props.headline?.takeIf { it.isNotBlank() },
                        props.areaDesc?.takeIf { it.isNotBlank() }?.let { "Area: $it" },
                        props.instruction?.takeIf { it.isNotBlank() }
                    ).joinToString("\n").ifBlank { props.description ?: "" }.take(500)
                )
            }
            if (toInsert.isNotEmpty()) {
                // Filter out ones that already exist (avoid re-notifying for unchanged alerts).
                val newOnes = toInsert.filter { incoming ->
                    existing.none { it.id == incoming.id }
                }
                db.activeAlertDao.insertAlerts(toInsert)
                val mostSevere = newOnes.maxByOrNull { severityWeight(it.severity) }
                if (mostSevere != null && severityWeight(mostSevere.severity) >= 3) {
                    sendSystemNotification(mostSevere.title, mostSevere.description.take(120))
                }
            }
        } catch (e: Exception) {
            // Network/parse failures are non-fatal; keep stale cached alerts.
        }
    }

    private fun severityWeight(s: String): Int = when (s.lowercase()) {
        "extreme" -> 4
        "severe" -> 3
        "moderate" -> 2
        "minor" -> 1
        else -> 0
    }

    // ---------- Tides ----------
    private suspend fun ensureTideStations(): List<TideStation>? {
        cachedTideStations?.let { return it }
        val cacheFile = File(context.filesDir, "tide_stations.json")
        if (cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val resp = stationListAdapter.fromJson(json)
                val list = resp?.stations
                if (list != null && list.isNotEmpty()) {
                    cachedTideStations = list
                    return list
                }
            } catch (e: Exception) {
                // fall through to network fetch
            }
        }
        return try {
            val resp = TidesService.stationApi.getStations()
            if (resp.isSuccessful) {
                val list = resp.body()?.stations ?: emptyList()
                if (list.isNotEmpty()) {
                    cachedTideStations = list
                    try {
                        cacheFile.writeText(stationListAdapter.toJson(TideStationListResponse(list)))
                    } catch (e: Exception) { /* cache write best-effort */ }
                }
                list.ifEmpty { null }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun syncTides(loc: SavedLocation?) {
        if (loc == null) {
            _tideSummary.value = null
            return
        }
        val stations = ensureTideStations() ?: return
        val station = TidesService.findNearestStation(stations, loc.latitude, loc.longitude)
        if (station == null) {
            _tideSummary.value = null
            return
        }
        try {
            val resp = TidesService.predictionsApi.getPredictions(
                stationId = station.id,
                beginDate = TidesService.todayBeginDate()
            )
            if (resp.isSuccessful) {
                val preds = resp.body()?.predictions ?: emptyList()
                _tideSummary.value = if (preds.isNotEmpty()) {
                    TideSummary(station.name, station.id, preds)
                } else null
            }
        } catch (e: Exception) {
            // leave existing summary as-is
        }
    }

    // ---------- Hurricane / NHC ----------
    private suspend fun syncHurricaneTracking() {
        val isSimulationEnforced = PreferencesHelper.getThemeOverride(context) == "simulated_hurricane"

        try {
            val response = NHCService.api.getCurrentStorms()
            if (response.isSuccessful && response.body() != null && !isSimulationEnforced) {
                val stormsList = response.body()?.storms ?: emptyList()
                if (stormsList.isEmpty()) {
                    _stormState.value = StormUIState.NoStorms
                } else {
                    val details = stormsList.map { ns ->
                        StormDetail(
                            id = ns.id,
                            name = ns.name,
                            basin = when (ns.basin) {
                                "AT" -> "Atlantic"
                                "EP" -> "Eastern Pacific"
                                else -> "Central Pacific"
                            },
                            type = when (ns.type) {
                                "HU" -> "Hurricane"
                                "TS" -> "Tropical Storm"
                                else -> "Tropical Depression"
                            },
                            category = ns.category,
                            latitude = ns.latitude,
                            longitude = ns.longitude,
                            windSpeedKts = ns.windSpeedKts,
                            pressureMb = ns.pressureMb,
                            direction = ns.direction ?: "WNW",
                            speedMph = ns.speedMph ?: 12.0,
                            trackPoints = listOf(
                                TrackPoint("Current Center", ns.latitude, ns.longitude, ns.windSpeedKts.toInt(), ns.pressureMb.toInt(), false, "Record")
                            ),
                            windRadii = WindRadii(100.0, 60.0, 30.0),
                            watchWarningAreas = emptyList(),
                            advisoryText = "Advisory text missing inside NHC advisory payload.",
                            discussionText = "Discussion text missing.",
                            detailUrl = "https://www.nhc.noaa.gov/"
                        )
                    }
                    _stormState.value = StormUIState.Active(details)
                    checkLocationsProximityAlert(details)
                }
            } else if (isSimulationEnforced) {
                val simulated = NHCService.getSimulatedStorm()
                _stormState.value = StormUIState.Active(listOf(simulated))
                checkLocationsProximityAlert(listOf(simulated))
            } else {
                _stormState.value = StormUIState.NoStorms
            }
        } catch (e: Exception) {
            if (isSimulationEnforced) {
                val simulated = NHCService.getSimulatedStorm()
                _stormState.value = StormUIState.Active(listOf(simulated))
                checkLocationsProximityAlert(listOf(simulated))
            } else {
                _stormState.value = StormUIState.NoStorms
            }
        }
    }

    private suspend fun checkLocationsProximityAlert(storms: List<StormDetail>) {
        val locations = db.savedLocationDao.getAllLocationsFlow().first()
        val radiusLimit = PreferencesHelper.getProximityAlertRadiusMiles(context)

        storms.forEach { storm ->
            locations.forEach { loc ->
                val dist = calculateDistanceMiles(loc.latitude, loc.longitude, storm.latitude, storm.longitude)
                if (dist <= radiusLimit) {
                    val alertId = "storm_prox_${storm.id}_${loc.id}"
                    val exists = db.activeAlertDao.getAllAlerts().any { it.id == alertId }
                    if (!exists) {
                        val newAlert = ActiveAlert(
                            id = alertId,
                            title = "URGENT: ${storm.type} ${storm.name} APPROACHING",
                            severity = "extreme",
                            description = "Storm core estimated at ${dist.toInt()} miles from ${loc.name}. Sustained winds: ${storm.windSpeedKts.toInt()} knots. Secure your surroundings immediately."
                        )
                        db.activeAlertDao.insertAlerts(listOf(newAlert))
                        sendSystemNotification("URGENT TROPICAL WARNING", newAlert.description)
                    }
                }
            }
        }
    }

    private fun calculateDistanceMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMiles = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMiles * c
    }

    private fun sendSystemNotification(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            999,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, "aero_life_safety_channel")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "aero_life_safety_channel",
                "Severe Weather Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Severe lightning, wind, and hurricane hazard proximity alarms"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // ---------- Location modifiers ----------
    fun addNewLocation(name: String, lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = db.savedLocationDao.insertLocation(
                SavedLocation(name = name, latitude = lat, longitude = lon)
            )
            selectActiveLocation(newId)
        }
    }

    fun addLocationFromGeocode(result: NominatimResult) {
        val lat = result.lat.toDoubleOrNull() ?: return
        val lon = result.lon.toDoubleOrNull() ?: return
        val nice = result.address?.shortLabel().orEmpty().ifBlank {
            result.displayName.substringBefore(",")
        }
        addNewLocation(nice, lat, lon)
        clearGeocodeResults()
    }

    fun deleteLocation(loc: SavedLocation) {
        viewModelScope.launch(Dispatchers.IO) {
            db.savedLocationDao.deleteLocation(loc)
            db.weatherCacheDao.deleteWeatherCache(loc.id)
            db.activeAlertDao.deleteAlertsByPrefix("nws_${loc.id}_")
            if (_activeLocationId.value == loc.id) {
                val remains = db.savedLocationDao.getAllLocationsFlow().first().firstOrNull()
                if (remains != null) {
                    selectActiveLocation(remains.id)
                } else {
                    _activeLocationId.value = -1L
                    _currentLocation.value = null
                    _weatherState.value = WeatherUIState.NoLocations
                }
            }
        }
    }

    fun dismissAlertBanner(alertId: String) {
        viewModelScope.launch {
            db.activeAlertDao.dismissAlert(alertId)
        }
    }

    /**
     * Caller (UI) is responsible for ensuring ACCESS_FINE/COARSE_LOCATION is granted before calling this.
     */
    fun useCurrentLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            _gpsRequestState.value = GpsRequestState.Loading

            if (!LocationProvider.hasPermission(context)) {
                _gpsRequestState.value = GpsRequestState.Failed("Location permission not granted.")
                return@launch
            }
            if (!LocationProvider.isLocationEnabled(context)) {
                _gpsRequestState.value = GpsRequestState.Failed("Location services are disabled. Enable them in system settings.")
                return@launch
            }

            val fix = LocationProvider.getCurrentLocation(context)
            if (fix == null) {
                _gpsRequestState.value = GpsRequestState.Failed("Couldn't get a location fix. Try again outdoors or with location services on.")
                return@launch
            }

            val niceLabel = try {
                val resp = GeocodingService.api.reverse(fix.latitude, fix.longitude)
                if (resp.isSuccessful) {
                    val label = resp.body()?.address?.shortLabel().orEmpty()
                    if (label.isNotBlank()) "Current Location ($label)" else "Current Location"
                } else "Current Location"
            } catch (e: Exception) {
                "Current Location"
            }

            val existing = db.savedLocationDao.getGPSLocation()
            val newId = if (existing != null) {
                db.savedLocationDao.insertLocation(
                    existing.copy(
                        name = niceLabel,
                        latitude = fix.latitude,
                        longitude = fix.longitude
                    )
                )
            } else {
                db.savedLocationDao.insertLocation(
                    SavedLocation(
                        name = niceLabel,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        isGPS = true
                    )
                )
            }

            PreferencesHelper.saveActiveLocationId(context, newId)
            _activeLocationId.value = newId
            _gpsRequestState.value = GpsRequestState.Success
            syncActiveLocationData()
        }
    }

    fun clearGpsRequestState() {
        _gpsRequestState.value = GpsRequestState.Idle
    }

    /**
     * Refresh the cached GPS location's coordinates without prompting the user.
     * Only updates if a GPS row already exists and permissions are still granted.
     */
    private suspend fun refreshGpsCoordinatesSilently() {
        if (!LocationProvider.hasPermission(context)) return
        val existing = db.savedLocationDao.getGPSLocation() ?: return
        val fix = LocationProvider.getCurrentLocation(context) ?: return

        // Skip update if the device hasn't moved meaningfully (avoids hammering reverse-geocode).
        val moved = calculateDistanceMiles(
            existing.latitude, existing.longitude, fix.latitude, fix.longitude
        )
        if (moved < 1.0 && existing.name.startsWith("Current Location")) return

        db.savedLocationDao.insertLocation(
            existing.copy(latitude = fix.latitude, longitude = fix.longitude)
        )
        if (_activeLocationId.value == existing.id) {
            _currentLocation.value = db.savedLocationDao.getLocationById(existing.id)
        }
    }

    // ---------- City search (forward geocoding) ----------
    fun searchCity(query: String) {
        geocodeJob?.cancel()
        if (query.isBlank() || query.length < 2) {
            _geocodeResults.value = emptyList()
            _geocodeSearching.value = false
            return
        }
        geocodeJob = viewModelScope.launch(Dispatchers.IO) {
            _geocodeSearching.value = true
            // Light debounce -- Nominatim's policy asks for ~1 req/sec.
            delay(450)
            try {
                val resp = GeocodingService.api.search(query = query)
                if (resp.isSuccessful) {
                    _geocodeResults.value = resp.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _geocodeResults.value = emptyList()
            } finally {
                _geocodeSearching.value = false
            }
        }
    }

    fun clearGeocodeResults() {
        geocodeJob?.cancel()
        _geocodeResults.value = emptyList()
        _geocodeSearching.value = false
    }

    // ---------- API key test ----------
    fun testTomorrowApiKeyConnection(testKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = tomorrowApi.getForecast(
                    location = "40.7128,-74.0060",
                    apiKey = testKey,
                    units = "metric"
                )
                if (response.isSuccessful) {
                    _showConnectionTestResult.value = Pair(true, "Connection Successful! Key is valid.")
                } else {
                    _showConnectionTestResult.value = Pair(false, "Connection Failed with code: ${response.code()}")
                }
            } catch (e: Exception) {
                _showConnectionTestResult.value = Pair(false, "Network connection error: ${e.localizedMessage}")
            }
        }
    }

    fun clearConnectionTestResult() {
        _showConnectionTestResult.value = null
    }
}

// UI States
sealed interface WeatherUIState {
    data object Loading : WeatherUIState
    data object NoApiKey : WeatherUIState
    data object NoLocations : WeatherUIState
    data class Success(
        val currentTemp: Double,
        val feelsLike: Double,
        val humidity: Double,
        val windSpeed: Double,
        val windDirection: Double,
        val uvIndex: Double,
        val visibility: Double,
        val pressure: Double,
        val weatherCode: Int,
        val lastUpdatedMillis: Long,
        val hourlyForecast: List<HourlyForecastItem>,
        val dailyForecast: List<DailyForecastItem>,
        // Minutely is intentionally not cached — stale minute-by-minute data is useless.
        // Empty for cached/offline state; populated only on successful fresh fetches.
        val minutelyForecast: List<MinutelyForecastItem> = emptyList(),
        val isCached: Boolean
    ) : WeatherUIState
    data class Error(val message: String) : WeatherUIState
}

sealed interface StormUIState {
    data object NoStorms : StormUIState
    data class Active(val activeStorms: List<StormDetail>) : StormUIState
}

sealed interface GpsRequestState {
    data object Idle : GpsRequestState
    data object Loading : GpsRequestState
    data object Success : GpsRequestState
    data class Failed(val reason: String) : GpsRequestState
}
