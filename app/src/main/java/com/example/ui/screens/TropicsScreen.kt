package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferencesHelper
import com.example.data.StormDetail
import com.example.ui.MainViewModel
import com.example.ui.StormUIState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TropicsScreen(
    viewModel: MainViewModel,
    onNavigateToStormDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = viewModel.getApplication<android.app.Application>()
    val stormState by viewModel.stormState.collectAsState()
    var forceSimulationMode by remember { mutableStateOf(PreferencesHelper.getThemeOverride(context) == "simulated_hurricane") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Tropics", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                actions = {
                    IconButton(onClick = {
                        val next = !forceSimulationMode
                        forceSimulationMode = next
                        PreferencesHelper.saveThemeOverride(context, if (next) "simulated_hurricane" else "system")
                        viewModel.refreshAllData(silent = true)
                    }) {
                        Icon(
                            imageVector = if (forceSimulationMode) Icons.Filled.Animation else Icons.Filled.Cyclone,
                            contentDescription = "Toggle Simulation",
                            tint = if (forceSimulationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        when (val state = stormState) {
            is StormUIState.NoStorms -> QuietBasinsContent(
                modifier = modifier.padding(innerPadding),
                onLaunchSimulation = {
                    forceSimulationMode = true
                    PreferencesHelper.saveThemeOverride(context, "simulated_hurricane")
                    viewModel.refreshAllData(silent = true)
                }
            )

            is StormUIState.Active -> ActiveStormsContent(
                modifier = modifier.padding(innerPadding),
                storms = state.activeStorms,
                onStormClick = onNavigateToStormDetail
            )
        }
    }
}

// ── No activity screen ──────────────────────────────────────────────────────

@Composable
private fun QuietBasinsContent(modifier: Modifier = Modifier, onLaunchSimulation: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Filled.BeachAccess,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No Tropical Activity",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "All monitored basins are clear of active advisories.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "SEASON SCHEDULE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                SeasonRow("Atlantic Basin", "Jun 1 – Nov 30")
                Spacer(modifier = Modifier.height(6.dp))
                SeasonRow("Eastern Pacific Basin", "May 15 – Nov 30")
                Spacer(modifier = Modifier.height(6.dp))
                SeasonRow("Central Pacific Basin", "Jun 1 – Nov 30")
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))

        TextButton(onClick = onLaunchSimulation) {
            Icon(Icons.Filled.Cyclone, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Launch simulation", fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SeasonRow(basin: String, dates: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(basin, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            dates,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ── Active storms list ───────────────────────────────────────────────────────

@Composable
private fun ActiveStormsContent(
    modifier: Modifier = Modifier,
    storms: List<StormDetail>,
    onStormClick: (String) -> Unit
) {
    val sorted = storms.sortedWith(
        compareBy(
            { stormSortTier(it) },
            { if (it.type == "Hurricane") -it.category else 0 },
            { -it.windSpeedKts }
        )
    )

    val grouped = sorted.groupBy { stormGroupLabel(it) }
    val groupOrder = listOf("HURRICANES", "TROPICAL STORMS", "TROPICAL DEPRESSIONS", "AREAS OF INTEREST")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Active count chip
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Text(
                        "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Text(
                "${storms.size} active system${if (storms.size != 1) "s" else ""}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        groupOrder.forEach { groupName ->
            val groupStorms = grouped[groupName] ?: return@forEach

            // Section header
            Text(
                groupName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
            )

            groupStorms.forEach { storm ->
                StormListCard(
                    storm = storm,
                    onClick = { onStormClick(storm.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StormListCard(storm: StormDetail, onClick: () -> Unit) {
    val badgeColor = stormBadgeColor(storm)
    val badgeLabel = stormBadgeLabel(storm)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: badge + name + basin pill + chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.3.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${storm.type} ${storm.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${storm.basin} Basin  •  Moving ${storm.direction} at ${storm.speedMph.toInt()} mph",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "View Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StormMetricCol(
                    label = "MAX WINDS",
                    value = "${storm.windSpeedKts.toInt()} kts"
                )
                StormMetricCol(
                    label = "PRESSURE",
                    value = "${storm.pressureMb.toInt()} mb"
                )
                StormMetricCol(
                    label = "POSITION",
                    value = "${storm.latitude}°N"
                )
            }
        }
    }
}

@Composable
private fun StormMetricCol(label: String, value: String) {
    Column {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.4.sp
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun stormSortTier(storm: StormDetail): Int = when (storm.type) {
    "Hurricane" -> 0
    "Tropical Storm", "Subtropical Storm" -> 1
    "Tropical Depression", "Subtropical Depression" -> 2
    else -> 3
}

private fun stormGroupLabel(storm: StormDetail): String = when (storm.type) {
    "Hurricane" -> "HURRICANES"
    "Tropical Storm", "Subtropical Storm" -> "TROPICAL STORMS"
    "Tropical Depression", "Subtropical Depression" -> "TROPICAL DEPRESSIONS"
    else -> "AREAS OF INTEREST"
}

private fun stormBadgeLabel(storm: StormDetail): String = when {
    storm.type == "Hurricane" && storm.category >= 1 -> "CAT ${storm.category}"
    storm.type == "Tropical Storm" -> "T·STORM"
    storm.type == "Subtropical Storm" -> "SUB·S"
    storm.type == "Tropical Depression" -> "T·DEPR"
    storm.type == "Subtropical Depression" -> "SUB·D"
    else -> "INVEST"
}

private fun stormBadgeColor(storm: StormDetail): Color = when {
    storm.type == "Hurricane" && storm.category == 5 -> Color(0xFFA855F7)
    storm.type == "Hurricane" && storm.category == 4 -> Color(0xFFEC4899)
    storm.type == "Hurricane" && storm.category == 3 -> Color(0xFFEF4444)
    storm.type == "Hurricane" && storm.category == 2 -> Color(0xFFF97316)
    storm.type == "Hurricane" && storm.category == 1 -> Color(0xFFD97706)
    storm.type == "Tropical Storm" || storm.type == "Subtropical Storm" -> Color(0xFF0284C7)
    storm.type == "Tropical Depression" || storm.type == "Subtropical Depression" -> Color(0xFF16A34A)
    else -> Color(0xFF64748B)
}
