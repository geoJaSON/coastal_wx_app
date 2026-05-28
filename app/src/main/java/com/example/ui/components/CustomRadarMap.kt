package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SavedLocation
import com.example.data.StormDetail
import com.example.data.TrackPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * A beautiful, real-time custom geographical vector map rendering tropical storm track points,
 * cones of uncertainty, and concentric wind-radii overlapping rings on an interactive Canvas.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun CustomRadarMap(
    storm: StormDetail?,
    savedLocations: List<SavedLocation>,
    proximityAlertRadiusMiles: Int,
    mapStyle: String, // standard, satellite, dark
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableStateOf(1.0f) }
    var panX by remember { mutableStateOf(0.0f) }
    var panY by remember { mutableStateOf(0.0f) }

    // Center the map coordinates default (centered on Bahamas / Florida tracking zone)
    val centerLat = 26.5
    val centerLon = -75.0
    val latSpan = 24.0
    val lonSpan = 32.0

    val textMeasurer = rememberTextMeasurer()

    // stylized shoreline vectors modeling Gulf of Mexico, Florida and Eastern US
    val coastlines = remember {
        listOf(
            // Eastern Shore and Gulf Coast
            listOf(
                Offset(-97.0f, 21.0f), Offset(-97.0f, 26.0f), Offset(-95.0f, 29.5f),
                Offset(-90.0f, 30.0f), Offset(-85.0f, 30.0f), Offset(-83.0f, 29.0f),
                Offset(-82.6f, 27.5f), Offset(-81.5f, 25.0f), Offset(-80.3f, 25.2f),
                Offset(-80.1f, 27.0f), Offset(-81.0f, 30.0f), Offset(-79.0f, 33.0f),
                Offset(-75.5f, 35.2f), Offset(-74.0f, 40.0f), Offset(-71.0f, 42.5f),
                Offset(-66.0f, 44.5f), Offset(-60.0f, 47.0f)
            ),
            // Florida Keys
            listOf(Offset(-81.8f, 24.5f), Offset(-80.5f, 24.8f)),
            // Cuba
            listOf(Offset(-84.5f, 22.0f), Offset(-82.5f, 23.1f), Offset(-80.0f, 22.5f), Offset(-74.2f, 20.2f)),
            // Hispaniola
            listOf(Offset(-74.0f, 19.8f), Offset(-71.0f, 19.5f), Offset(-68.5f, 18.5f)),
            // Puerto Rico
            listOf(Offset(-67.2f, 18.0f), Offset(-65.6f, 18.3f)),
            // Bahamas Outer Arc
            listOf(Offset(-78.5f, 26.6f), Offset(-76.0f, 24.5f), Offset(-74.5f, 23.0f), Offset(-71.5f, 21.0f))
        )
    }

    val isDark = when (mapStyle) {
        "dark" -> true
        "satellite" -> true
        else -> !MaterialTheme.colorScheme.background.luminance().plus(0.5f).minus(1.0f).isNaN() // adaptive
    }

    // Set map backdrops
    val mapBg = when (mapStyle) {
        "dark" -> Color(0xFF10141D)
        "satellite" -> Color(0xFF0D1B2A)
        else -> if (isDark) Color(0xFF1A1F26) else Color(0xFFF1F5F9)
    }

    val gridColor = if (isDark) Color(0xFF2E3B4E) else Color(0xFFCBD5E1)
    val landstrokeColor = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)
    val textStyle = TextStyle(
        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
        fontSize = 11.sp
    )

    Box(
        modifier = modifier
            .background(mapBg, shape = RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomAmount, _ ->
                    zoom = (zoom * zoomAmount).coerceIn(0.5f, 8.0f)
                    panX += pan.x
                    panY += pan.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Coordinate translators
            fun getPixelX(lon: Double): Float {
                val relativeLon = lon - centerLon
                val xPos = width / 2f + (relativeLon.toFloat() / lonSpan.toFloat()) * width * zoom
                return xPos + panX
            }

            fun getPixelY(lat: Double): Float {
                val relativeLat = lat - centerLat
                val yPos = height / 2f - (relativeLat.toFloat() / latSpan.toFloat()) * height * zoom
                return yPos + panY
            }

            // Converter for radii based on latitude degrees distance
            // 1 degree latitude is approx 69 miles (or approx 60 nautical miles)
            fun getRadiusInPixels(miles: Double): Float {
                val latSecDegrees = miles / 69.0
                return ((latSecDegrees / latSpan) * height * zoom).toFloat()
            }

            // 1. Draw coordinate grids
            val stepLon = 5
            val stepLat = 5
            val startLon = ((centerLon - lonSpan) / stepLon).toInt() * stepLon
            val endLon = ((centerLon + lonSpan) / stepLon).toInt() * stepLon
            val startLat = ((centerLat - latSpan) / stepLat).toInt() * stepLat
            val endLat = ((centerLat + latSpan) / stepLat).toInt() * stepLat

            for (lon in startLon..endLon step stepLon) {
                val x = getPixelX(lon.toDouble())
                if (x in 0f..width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
                    drawText(
                        textMeasurer,
                        "${lon}°W",
                        Offset(x + 4f, height - 20f),
                        style = textStyle
                    )
                }
            }

            for (lat in startLat..endLat step stepLat) {
                val y = getPixelY(lat.toDouble())
                if (y in 0f..height) {
                    drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                    drawText(
                        textMeasurer,
                        "${lat}°N",
                        Offset(12f, y - 16f),
                        style = textStyle
                    )
                }
            }

            // 2. Draw styled land coast paths
            val coastPath = Path()
            coastlines.forEach { line ->
                if (line.isNotEmpty()) {
                    val p1 = line.first()
                    coastPath.moveTo(getPixelX(p1.x.toDouble()), getPixelY(p1.y.toDouble()))
                    for (i in 1 until line.size) {
                        val pt = line[i]
                        coastPath.lineTo(getPixelX(pt.x.toDouble()), getPixelY(pt.y.toDouble()))
                    }
                }
            }
            drawPath(
                coastPath,
                color = landstrokeColor,
                style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f))
            )

            // 3. Draw severe storm attributes if provided
            if (storm != null) {
                val track = storm.trackPoints

                // A. Draw Cone of Uncertainty polygon
                val forecastPoints = track.filter { it.isForecast }
                if (forecastPoints.size >= 2) {
                    val conePathLeft = Path()
                    val conePathRight = Path()
                    val startPt = track.firstOrNull { !it.isForecast && !track[track.indexOf(it) + 1].isForecast } ?: storm.trackPoints.first()

                    val startX = getPixelX(startPt.longitude)
                    val startY = getPixelY(startPt.latitude)
                    
                    conePathLeft.moveTo(startX, startY)
                    conePathRight.moveTo(startX, startY)

                    // Compute expanding cone boundary vertices perpendicular to track heading
                    for (i in forecastPoints.indices) {
                        val curr = forecastPoints[i]
                        val prev = if (i == 0) startPt else forecastPoints[i - 1]
                        
                        val dx = getPixelX(curr.longitude) - getPixelX(prev.longitude)
                        val dy = getPixelY(curr.latitude) - getPixelY(prev.latitude)
                        
                        val len = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (len > 0) {
                            val nx = -dy / len
                            val ny = dx / len
                            
                            // Width expands progressively by day (approx 60 miles per forecast step index)
                            val expansionRadiusMiles = 45.0 + (i * 45.0)
                            val rPx = getRadiusInPixels(expansionRadiusMiles)

                            val lx = getPixelX(curr.longitude) + nx * rPx
                            val ly = getPixelY(curr.latitude) + ny * rPx
                            val rx = getPixelX(curr.longitude) - nx * rPx
                            val ry = getPixelY(curr.latitude) - ny * rPx

                            conePathLeft.lineTo(lx, ly)
                            conePathRight.lineTo(rx, ry)
                        }
                    }

                    // Complete closed cone loop
                    val finalConePath = Path().apply {
                        addPath(conePathLeft)
                        for (i in forecastPoints.indices.reversed()) {
                            val curr = forecastPoints[i]
                            val prev = if (i == 0) startPt else forecastPoints[i - 1]
                            val dx = getPixelX(curr.longitude) - getPixelX(prev.longitude)
                            val dy = getPixelY(curr.latitude) - getPixelY(prev.latitude)
                            val len = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (len > 0) {
                                val nx = -dy / len
                                val ny = dx / len
                                val expansionRadiusMiles = 45.0 + (i * 45.0)
                                val rPx = getRadiusInPixels(expansionRadiusMiles)
                                val rx = getPixelX(curr.longitude) - nx * rPx
                                val ry = getPixelY(curr.latitude) - ny * rPx
                                lineTo(rx, ry)
                            }
                        }
                        close()
                    }

                    // Render translucent cone fill
                    drawPath(
                        finalConePath,
                        color = Color(0x3338BDF8), // Translucent light blue
                        style = Fill
                    )
                    // Draw cone border line
                    drawPath(
                        finalConePath,
                        color = Color(0x8838BDF8),
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f))
                    )
                }

                // B. Draw Wind Radii Rings around CURRENT center
                val currentX = getPixelX(storm.longitude)
                val currentY = getPixelY(storm.latitude)

                // 34 knot radius
                val radius34Px = getRadiusInPixels(storm.windRadii.radius34ktNm.toDouble())
                drawCircle(
                    color = Color(0x19EF4444), // very translucent red
                    radius = radius34Px,
                    center = Offset(currentX, currentY),
                    style = Fill
                )
                drawCircle(
                    color = Color(0x7FED4444),
                    radius = radius34Px,
                    center = Offset(currentX, currentY),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                )

                // 50 knot radius
                val radius50Px = getRadiusInPixels(storm.windRadii.radius50ktNm.toDouble())
                drawCircle(
                    color = Color(0x2CEF4444),
                    radius = radius50Px,
                    center = Offset(currentX, currentY),
                    style = Fill
                )
                drawCircle(
                    color = Color(0xC0EF4444),
                    radius = radius50Px,
                    center = Offset(currentX, currentY),
                    style = Stroke(width = 1.5f)
                )

                // 64 knot (hurricane force) core
                val radius64Px = getRadiusInPixels(storm.windRadii.radius64ktNm.toDouble())
                drawCircle(
                    color = Color(0x40F43F5E), // stronger transparent red
                    radius = radius64Px,
                    center = Offset(currentX, currentY),
                    style = Fill
                )
                drawCircle(
                    color = Color(0xFFF43F5E),
                    radius = radius64Px,
                    center = Offset(currentX, currentY),
                    style = Stroke(width = 2.5f)
                )

                // C. Draw Past & Forecast tracks
                val pathPast = Path()
                val pathForecast = Path()
                
                var firstPast = true
                var firstForecast = true

                track.forEach { pt ->
                    val px = getPixelX(pt.longitude)
                    val py = getPixelY(pt.latitude)
                    if (pt.isForecast) {
                        if (firstForecast) {
                            // Link up with last past spot
                            track.lastOrNull { !it.isForecast }?.let { lastPast ->
                                pathForecast.moveTo(getPixelX(lastPast.longitude), getPixelY(lastPast.latitude))
                            } ?: pathForecast.moveTo(px, py)
                            firstForecast = false
                        }
                        pathForecast.lineTo(px, py)
                    } else {
                        if (firstPast) {
                            pathPast.moveTo(px, py)
                            firstPast = false
                        } else {
                            pathPast.lineTo(px, py)
                        }
                    }
                }

                // Draw past tracks lines (Black/Slate solid)
                drawPath(
                    pathPast,
                    color = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E293B),
                    style = Stroke(width = 3f)
                )
                // Draw forecast track line (Dashed)
                drawPath(
                    pathForecast,
                    color = if (isDark) Color(0xFF94D3FF) else Color(0xFF2563EB),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f))
                )

                // Draw dots at track points
                val catColors = listOf(
                    Color(0xFF84CC16), // TD/TS (lime)
                    Color(0xFFEAB308), // Cat 1 (yellow)
                    Color(0xFFF97316), // Cat 2 (orange)
                    Color(0xFFEF4444), // Cat 3 (red)
                    Color(0xFFEC4899), // Cat 4 (pink)
                    Color(0xFFA855F7)  // Cat 5 (purple/magenta)
                )

                track.forEach { pt ->
                    val px = getPixelX(pt.longitude)
                    val py = getPixelY(pt.latitude)

                    // Find category styling index
                    val pointCategory = if (pt.windSpeedKt >= 137) 5
                        else if (pt.windSpeedKt >= 113) 4
                        else if (pt.windSpeedKt >= 96) 3
                        else if (pt.windSpeedKt >= 83) 2
                        else if (pt.windSpeedKt >= 64) 1
                        else 0

                    val c = catColors.getOrElse(pointCategory) { Color.Gray }

                    if (pt.isForecast) {
                        // Open circle for forecast
                        drawCircle(
                            color = c,
                            radius = 6f,
                            center = Offset(px, py),
                            style = Stroke(width = 3f)
                        )
                        // Label forecast interval
                        if (pt.timeLabel.contains("h")) {
                            drawText(
                                textMeasurer,
                                pt.timeLabel,
                                Offset(px + 8f, py - 6f),
                                style = textStyle.copy(fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            )
                        }
                    } else {
                        // Solid circle for recorded locations
                        drawCircle(
                            color = c,
                            radius = 8f,
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(px, py)
                        )
                    }
                }

                // D. Active Hurricane Vortex Indicator
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 16f,
                    center = Offset(currentX, currentY),
                    style = Stroke(width = 2f)
                )
                drawText(
                    textMeasurer,
                    "HURRICANE ${storm.name.uppercase()}",
                    Offset(currentX + 16f, currentY + 12f),
                    style = textStyle.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        color = Color.Yellow,
                        fontSize = 12.sp
                    )
                )
            }

            // 4. Draw saved locations & Proximity Alerts Radius!
            savedLocations.forEach { loc ->
                val lx = getPixelX(loc.longitude)
                val ly = getPixelY(loc.latitude)

                if (lx in 0f..width && ly in 0f..height) {
                    // Saved location dot
                    drawCircle(
                        color = Color(0xFF10B981), // Emerald green
                        radius = 8f,
                        center = Offset(lx, ly)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3f,
                        center = Offset(lx, ly)
                    )

                    // Glow circle representing Proximity Alert Zone
                    val alarmPx = getRadiusInPixels(proximityAlertRadiusMiles.toDouble())
                    drawCircle(
                        color = Color(0x1510B981),
                        radius = alarmPx,
                        center = Offset(lx, ly),
                        style = Fill
                    )
                    drawCircle(
                        color = Color(0x6010B981),
                        radius = alarmPx,
                        center = Offset(lx, ly),
                        style = Stroke(width = 1.0f)
                    )

                    // Draw name
                    drawText(
                        textMeasurer,
                        loc.name,
                        Offset(lx - 25f, ly - 22f),
                        style = textStyle.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    )
                }
            }
        }

        // Overlay Map Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { zoom = (zoom * 1.3f).coerceAtMost(8.0f) }) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom In")
            }
            IconButton(onClick = { zoom = (zoom / 1.3f).coerceAtLeast(0.5f) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom Out")
            }
            IconButton(onClick = {
                zoom = 1.0f
                panX = 0f
                panY = 0f
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Recenter")
            }
        }

        // Color coding scale overlay
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scale:", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                val labels = listOf("TS" to Color(0xFF84CC16), "H1" to Color(0xFFEAB308), "H3" to Color(0xFFEF4444), "H5" to Color(0xFFA855F7))
                labels.forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(item.second, RoundedCornerShape(2.dp))
                    )
                    Text(item.first, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
    }
}
