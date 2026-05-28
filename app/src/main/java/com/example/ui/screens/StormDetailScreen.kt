package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferencesHelper
import com.example.ui.MainViewModel
import com.example.ui.StormUIState
import com.example.ui.components.CustomRadarMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StormDetailScreen(
    stormId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stormState by viewModel.stormState.collectAsState()
    val savedLocations by viewModel.savedLocations.collectAsState()
    val scrollState = rememberScrollState()
    val proximityRadius = remember { PreferencesHelper.getProximityAlertRadiusMiles(context) }
    val mapStyle = remember { PreferencesHelper.getMapStyle(context) }

    val storm = remember(stormState, stormId) {
        if (stormState is StormUIState.Active) {
            (stormState as StormUIState.Active).activeStorms.firstOrNull { it.id == stormId }
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(storm?.let { "${it.type} ${it.name}" } ?: "Storm Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        if (storm == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: Selected Storm details are not loaded.", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive track map
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    CustomRadarMap(
                        storm = storm,
                        savedLocations = savedLocations,
                        proximityAlertRadiusMiles = proximityRadius,
                        mapStyle = mapStyle,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Storm Banner Metrics Card
                val catColor = when (storm.category) {
                    1 -> Color(0xFFEAB308)
                    2 -> Color(0xFFF97316)
                    3 -> Color(0xFFEF4444)
                    4 -> Color(0xFFEC4899)
                    5 -> Color(0xFFA855F7)
                    else -> Color(0xFF84CC16)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(catColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (storm.category > 0) "H${storm.category}" else "TS",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Column {
                                Text(
                                    "${storm.type} ${storm.name.uppercase()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "Basin: ${storm.basin} ocean  •  ID: ${storm.id}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                        // High fidelity key value rows
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StormGauge(label = "MAX WINDS", valStr = "${storm.windSpeedKts.toInt()} kts")
                            StormGauge(label = "PRESSURE", valStr = "${storm.pressureMb.toInt()} mb")
                            StormGauge(label = "MOVEMENT", valStr = "${storm.direction} @ ${storm.speedMph.toInt()} mph")
                        }
                    }
                }

                // Official Link Trigger Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(storm.detailUrl))
                        context.startActivity(browserIntent)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "NHC Web", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "View Official NOAA NHC Public Advisory Page",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                var tabIndex by remember { mutableStateOf(0) }
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                        Text("Public Advisory", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                        Text("Forecast Discussion", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable monospaced telemetry body texts
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    val bodyText = if (tabIndex == 0) storm.advisoryText else storm.discussionText
                    Text(
                        text = bodyText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
fun StormGauge(label: String, valStr: String) {
    Column {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valStr, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
    }
}
