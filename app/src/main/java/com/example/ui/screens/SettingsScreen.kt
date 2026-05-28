package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferencesHelper
import com.example.service.StatusBarTempService
import com.example.ui.GpsRequestState
import com.example.ui.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 1. Settings state values
    var apiKey by remember { mutableStateOf(PreferencesHelper.getApiKey(context)) }
    var tempUnit by remember { mutableStateOf(PreferencesHelper.getTempUnit(context)) }
    var windUnit by remember { mutableStateOf(PreferencesHelper.getWindSpeedUnit(context)) }
    var measurementSystem by remember { mutableStateOf(PreferencesHelper.getMeasurementSystem(context)) }
    var refreshInterval by remember { mutableStateOf(PreferencesHelper.getRefreshIntervalMinutes(context)) }
    var alarmRadius by remember { mutableStateOf(PreferencesHelper.getProximityAlertRadiusMiles(context)) }
    var themeOverride by remember { mutableStateOf(PreferencesHelper.getThemeOverride(context)) }
    var mapStyle by remember { mutableStateOf(PreferencesHelper.getMapStyle(context)) }
    var statusBarTemp by remember { mutableStateOf(PreferencesHelper.getStatusBarTempToggle(context)) }

    var isKeyVisible by remember { mutableStateOf(false) }

    // Station Inputs state
    var newStationName by remember { mutableStateOf("") }
    var newStationLat by remember { mutableStateOf("") }
    var newStationLon by remember { mutableStateOf("") }

    var citySearchQuery by remember { mutableStateOf("") }

    val savedLocations by viewModel.savedLocations.collectAsState()
    val testConnectionResult by viewModel.showConnectionTestResult.collectAsState()
    val gpsState by viewModel.gpsRequestState.collectAsState()
    val geocodeResults by viewModel.geocodeResults.collectAsState()
    val geocodeSearching by viewModel.geocodeSearching.collectAsState()

    val locationPerms = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    // Connection success prompt trigger
    LaunchedEffect(testConnectionResult) {
        testConnectionResult?.let { (success, msg) ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearConnectionTestResult()
        }
    }

    // GPS request feedback
    LaunchedEffect(gpsState) {
        when (val s = gpsState) {
            is GpsRequestState.Success -> {
                Toast.makeText(context, "Location set to your current position.", Toast.LENGTH_SHORT).show()
                viewModel.clearGpsRequestState()
            }
            is GpsRequestState.Failed -> {
                Toast.makeText(context, s.reason, Toast.LENGTH_LONG).show()
                viewModel.clearGpsRequestState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App Preferences", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 0. Current Location (GPS) card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "CURRENT LOCATION (GPS)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Uses on-device location (no Google Play Services). Location is reverse-geocoded for a friendly name.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val isLoading = gpsState is GpsRequestState.Loading
                    Button(
                        onClick = {
                            if (locationPerms.allPermissionsGranted) {
                                viewModel.useCurrentLocation()
                            } else {
                                locationPerms.launchMultiplePermissionRequest()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Locating...")
                        } else {
                            Icon(Icons.Filled.MyLocation, contentDescription = "Use Current Location")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (locationPerms.allPermissionsGranted) "Use My Current Location"
                                else "Grant Location & Use Current Position"
                            )
                        }
                    }
                }
            }

            // A. Tomorrow.io API Credentials safe section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("TOMORROW.IO API SERVICE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 1.sp)
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            PreferencesHelper.saveApiKey(context, it)
                            viewModel.refreshAllData(silent = true)
                        },
                        label = { Text("Tomorrow.io Personal API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Mask credentials"
                                )
                            }
                        }
                    )
                    
                    Button(
                        onClick = { viewModel.testTomorrowApiKeyConnection(apiKey) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.NetworkCheck, contentDescription = "Test Connection")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test API Connection")
                    }
                }
            }

            // B. Add new station / location manual tracker
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("ADD STATION COORDINATES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 1.sp)

                    // City search (forward geocoding via Nominatim/OSM)
                    OutlinedTextField(
                        value = citySearchQuery,
                        onValueChange = {
                            citySearchQuery = it
                            viewModel.searchCity(it)
                        },
                        label = { Text("Search by city or place name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (geocodeSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else if (citySearchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    citySearchQuery = ""
                                    viewModel.clearGeocodeResults()
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        }
                    )

                    if (geocodeResults.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            geocodeResults.forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addLocationFromGeocode(result)
                                            citySearchQuery = ""
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.AddLocation,
                                        contentDescription = "Add",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        result.displayName,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }

                    Text(
                        "Or enter coordinates manually:",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newStationName,
                        onValueChange = { newStationName = it },
                        label = { Text("Station Label (e.g. New Orleans)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newStationLat,
                            onValueChange = { newStationLat = it },
                            label = { Text("Latitude") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = newStationLon,
                            onValueChange = { newStationLon = it },
                            label = { Text("Longitude") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Button(
                        onClick = {
                            val latVal = newStationLat.toDoubleOrNull()
                            val lonVal = newStationLon.toDoubleOrNull()
                            if (newStationName.isNotBlank() && latVal != null && lonVal != null) {
                                viewModel.addNewLocation(newStationName, latVal, lonVal)
                                newStationName = ""
                                newStationLat = ""
                                newStationLon = ""
                                Toast.makeText(context, "Coordinates saved to station index", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid coordinates. Use decimals.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AddLocation, contentDescription = "Insert")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Coordination Station")
                    }
                }
            }

            // C. Meteorological units panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("METEOROLOGICAL UNITS CONFIG", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 1.sp)
                    
                    // Temp units row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Thermal Unit Scales", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Units utilized inside forecasts and overlays", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(if (tempUnit == "F") MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        tempUnit = "F"
                                        PreferencesHelper.saveTempUnit(context, "F")
                                        viewModel.refreshAllData(silent = true)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("°F", color = if (tempUnit == "F") Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .background(if (tempUnit == "C") MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        tempUnit = "C"
                                        PreferencesHelper.saveTempUnit(context, "C")
                                        viewModel.refreshAllData(silent = true)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("°C", color = if (tempUnit == "C") Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                    // Wind speed units
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Wind Velocity Scales", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Knots, miles / hr, or km / hr selection", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf("mph", "km/h", "knots").forEach { unit ->
                                val active = windUnit == unit
                                Box(
                                    modifier = Modifier
                                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            windUnit = unit
                                            PreferencesHelper.saveWindSpeedUnit(context, unit)
                                            viewModel.refreshAllData(silent = true)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(unit, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                    // Measurement system (pressure, distance, rainfall, tide height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Measurement System", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Pressure (hPa/inHg), distance (km/mi), rainfall (mm/in), tide (m/ft)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf("imperial" to "IMP", "metric" to "MET").forEach { (sys, label) ->
                                val active = measurementSystem == sys
                                Box(
                                    modifier = Modifier
                                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            measurementSystem = sys
                                            PreferencesHelper.saveMeasurementSystem(context, sys)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        label,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // D. Polling and map parameters
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("TRACKING ENGINE INTERVALS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 1.sp)
                    
                    // Interval slider
                    Text("Auto Refresh Cycle: $refreshInterval minutes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Slider(
                        value = refreshInterval.toFloat(),
                        onValueChange = {
                            val rounded = (it / 15).toInt() * 15
                            refreshInterval = rounded.coerceIn(15, 60)
                            PreferencesHelper.saveRefreshIntervalMinutes(context, refreshInterval)
                        },
                        valueRange = 15f..60f,
                        steps = 2
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                    // Radius slider
                    Text("Hurricane Alarm Radius: $alarmRadius Miles", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Slider(
                        value = alarmRadius.toFloat(),
                        onValueChange = {
                            alarmRadius = it.toInt().coerceIn(50, 300)
                            PreferencesHelper.saveProximityAlertRadiusMiles(context, alarmRadius)
                        },
                        valueRange = 50f..300f
                    )
                }
            }

            // E. Theme settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("VISUAL MAP TILES & BACKGROUNDS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 1.sp)
                    
                    // Map Style preference
                    Text("Map Rendering Backdrop", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("standard", "satellite", "dark").forEach { style ->
                            val active = mapStyle == style
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        mapStyle = style
                                        PreferencesHelper.saveMapStyle(context, style)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(style.uppercase(), color = if (active) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                    // Status Bar Temperature Switch Service
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Status Bar Temperature", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Runs persistent background layout service displaying active temperature", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = statusBarTemp,
                            onCheckedChange = {
                                statusBarTemp = it
                                PreferencesHelper.saveStatusBarTempToggle(context, it)
                                if (it) {
                                    StatusBarTempService.startService(context)
                                } else {
                                    StatusBarTempService.stopService(context)
                                }
                            }
                        )
                    }
                }
            }

            // F. Active Track Locations Directory List
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("SAVED FORECAST STATION NODES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 1.sp)
                    
                    savedLocations.forEach { loc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(loc.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Lat: ${loc.latitude}  •  Lon: ${loc.longitude}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!loc.isGPS) {
                                IconButton(onClick = { viewModel.deleteLocation(loc) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Location", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
