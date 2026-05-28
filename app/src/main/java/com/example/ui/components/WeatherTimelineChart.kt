package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HourlyForecastItem
import com.example.data.WeatherCodeMapper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalTextApi::class)
@Composable
fun WeatherTimelineChart(
    hourlyList: List<HourlyForecastItem>,
    isCelsius: Boolean,
    modifier: Modifier = Modifier
) {
    if (hourlyList.isEmpty()) return

    val scrollState = rememberScrollState()
    val hourWidth = 72.dp
    val totalWidth = hourWidth * hourlyList.size
    val textMeasurer = rememberTextMeasurer()

    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val curveBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.horizontalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .width(totalWidth)
                    .height(180.dp)
            ) {
                // Drawing continuous temperature curves & precipitation bars.
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val w = size.width
                    val h = size.height

                    // Constants
                    val paddingBottom = 28f
                    val paddingTop = 45f
                    val chartHeight = h - paddingBottom - paddingTop

                    // Parse hourly temps to determine range
                    val temps = hourlyList.map { item ->
                        val temp = item.values.temperature ?: 0.0
                        if (isCelsius) temp else (temp * 1.8 + 32)
                    }

                    val maxTemp = (temps.maxOrNull() ?: 100.0) + 2.0
                    val minTemp = (temps.minOrNull() ?: 0.0) - 2.0
                    val range = if (maxTemp - minTemp == 0.0) 1.0 else maxTemp - minTemp

                    // Draw loops
                    val points = ArrayList<Offset>()
                    val hourWidthPx = (w / hourlyList.size)

                    hourlyList.forEachIndexed { index, item ->
                        val t = temps[index]
                        val x = index * hourWidthPx + hourWidthPx / 2f
                        // Project Temp -> Y coord (inverted because Canvas 0,0 is Top-Left)
                        val y = (h - paddingBottom - ((t - minTemp) / range) * chartHeight).toFloat()
                        points.add(Offset(x, y))

                        // Draw precipitation bars (at the bottom of the timeline)
                        val prob = item.values.precipitationProbability ?: 0.0
                        if (prob > 0.0) {
                            val barMaxHeight = 28f
                            val barHeightValue = (prob / 100.0) * barMaxHeight
                            val barX = x - 12f
                            val barY = h - paddingBottom - barHeightValue.toFloat()
                            drawRect(
                                color = Color(0x9938BDF8), // Translucent sky blue
                                topLeft = Offset(barX, barY),
                                size = androidx.compose.ui.geometry.Size(24f, barHeightValue.toFloat())
                            )
                            // Draw probability text above the bar
                            if (prob >= 15.0) {
                                drawText(
                                    textMeasurer,
                                    "${prob.toInt()}%",
                                    Offset(x - 14f, barY - 14f),
                                    style = TextStyle(color = Color(0xFF0284C7), fontSize = 8.sp)
                                )
                            }
                        }

                        // Draw Hour Text
                        val timeString = try {
                            val sdfSource = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            val date = sdfSource.parse(item.time)
                            val sdfDest = SimpleDateFormat("h a", Locale.getDefault())
                            sdfDest.format(date ?: Date())
                        } catch (e: Exception) {
                            "NA"
                        }
                        
                        drawText(
                            textMeasurer,
                            timeString,
                            Offset(x - 22f, h - 22f),
                            style = TextStyle(color = labelColor, fontSize = 9.sp)
                        )
                    }

                    // Draw smooth Bezier spline for temperatures
                    if (points.size >= 2) {
                        val path = Path()
                        val fillPath = Path()

                        path.moveTo(points[0].x, points[0].y)
                        fillPath.moveTo(points[0].x, h - paddingBottom)
                        fillPath.lineTo(points[0].x, points[0].y)

                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlPointX1 = p0.x + hourWidthPx / 2.5f
                            val controlPointX2 = p1.x - hourWidthPx / 2.5f

                            path.cubicTo(
                                controlPointX1, p0.y,
                                controlPointX2, p1.y,
                                p1.x, p1.y
                            )
                            fillPath.cubicTo(
                                controlPointX1, p0.y,
                                controlPointX2, p1.y,
                                p1.x, p1.y
                            )
                        }

                        fillPath.lineTo(points.last().x, h - paddingBottom)
                        fillPath.close()

                        // Fill translucent gradient under temp curve
                        drawPath(
                            fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(curveBgColor, Color.Transparent),
                                startY = 10f,
                                endY = h - paddingBottom
                            )
                        )

                        // Draw Curve Stroke
                        drawPath(
                            path,
                            color = primaryColor,
                            style = Stroke(width = 3.5f)
                        )
                    }

                    // Annotate values along the temperature curve
                    points.forEachIndexed { idx, pt ->
                        val tempVal = temps[idx].toInt()
                        drawCircle(
                            color = primaryColor,
                            radius = 4f,
                            center = pt
                        )
                        drawText(
                            textMeasurer,
                            "${tempVal}°",
                            Offset(pt.x - 12f, pt.y - 18f),
                            style = TextStyle(color = primaryColor, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        )
                    }
                }

                // Superimpose custom icons above the curves using Compose layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    hourlyList.forEach { item ->
                        Box(
                            modifier = Modifier
                                .width(hourWidth)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            WeatherConditionIcon(
                                weatherCode = item.values.weatherCode ?: 1000,
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .size(28.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    )
                                    .padding(3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
