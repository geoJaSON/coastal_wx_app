package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherConditionIcon(
    weatherCode: Int,
    modifier: Modifier = Modifier.size(64.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "weather_icon_anim")

    val sunRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "sun_rotation"
    )

    val rainOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "rain_drop"
    )

    val snowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "snow_drop"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        when (weatherCode) {

            // ── Clear ───────────────────────────────────────────────────────
            1000 -> {
                drawSun(cx, cy, w, h, sunRotation, rayCount = 8, coreRatio = 0.28f,
                    coreColor = Color(0xFFFBBF24), rayColor = Color(0xFFFCD34D))
            }

            // ── Mostly Clear — sun with a wisp of cloud at lower edge ───────
            1100 -> {
                drawSun(cx, cy - h * 0.08f, w, h, sunRotation, rayCount = 8,
                    coreRatio = 0.24f, coreColor = Color(0xFFFBBF24), rayColor = Color(0xFFFCD34D))
                // small wispy cloud at bottom
                drawCloud(cx - w * 0.05f, cy + h * 0.18f, w * 0.6f, h * 0.45f,
                    colorShadow = Color(0xFFCBD5E1), colorBody = Color(0xFFE2E8F0))
            }

            // ── Partly Cloudy — balanced sun + cloud ────────────────────────
            1101 -> {
                drawCircle(Color(0xFFF59E0B), radius = w * 0.20f,
                    center = Offset(cx + w * 0.18f, cy - h * 0.18f))
                // small rays on the sun peek
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60.0 + sunRotation * 0.5)).toFloat()
                    val inner = w * 0.24f
                    val outer = w * 0.32f
                    drawLine(Color(0xFFFBBF24),
                        start = Offset(cx + w * 0.18f + inner * cos(angle), cy - h * 0.18f + inner * sin(angle)),
                        end   = Offset(cx + w * 0.18f + outer * cos(angle), cy - h * 0.18f + outer * sin(angle)),
                        strokeWidth = w * 0.055f, cap = StrokeCap.Round)
                }
                drawCloud(cx, cy + h * 0.05f, w, h,
                    colorShadow = Color(0xFF94A3B8), colorBody = Color(0xFFCBD5E1))
            }

            // ── Mostly Cloudy — large cloud, sun barely peeking ─────────────
            1102 -> {
                // just the ambient glow of a sun behind, not a visible disc
                drawCircle(Color(0xFFF59E0B).copy(alpha = 0.55f), radius = w * 0.16f,
                    center = Offset(cx + w * 0.22f, cy - h * 0.22f))
                drawCloud(cx, cy + h * 0.04f, w * 1.05f, h * 1.05f,
                    colorShadow = Color(0xFF64748B), colorBody = Color(0xFF94A3B8))
            }

            // ── Overcast / Cloudy — no sun ───────────────────────────────────
            1001 -> {
                drawCloud(cx, cy, w, h,
                    colorShadow = Color(0xFF475569), colorBody = Color(0xFF64748B))
            }

            // ── Drizzle — very light drops ───────────────────────────────────
            4000 -> {
                drawCloud(cx, cy - h * 0.06f, w, h,
                    colorShadow = Color(0xFF64748B), colorBody = Color(0xFF94A3B8))
                drawRainDrops(cx, cy, w, h, rainOffset,
                    dropColor = Color(0xFF7DD3FC), count = 2, heaviness = 0.7f)
            }

            // ── Light Rain ───────────────────────────────────────────────────
            4200 -> {
                drawCloud(cx, cy - h * 0.06f, w, h,
                    colorShadow = Color(0xFF475569), colorBody = Color(0xFF64748B))
                drawRainDrops(cx, cy, w, h, rainOffset,
                    dropColor = Color(0xFF38BDF8), count = 3, heaviness = 0.85f)
            }

            // ── Rain ─────────────────────────────────────────────────────────
            4001 -> {
                drawCloud(cx, cy - h * 0.06f, w, h,
                    colorShadow = Color(0xFF334155), colorBody = Color(0xFF475569))
                drawRainDrops(cx, cy, w, h, rainOffset,
                    dropColor = Color(0xFF38BDF8), count = 3, heaviness = 1.0f)
            }

            // ── Heavy Rain ───────────────────────────────────────────────────
            4201 -> {
                drawCloud(cx, cy - h * 0.08f, w, h,
                    colorShadow = Color(0xFF1E293B), colorBody = Color(0xFF334155))
                drawRainDrops(cx, cy, w, h, rainOffset,
                    dropColor = Color(0xFF0EA5E9), count = 4, heaviness = 1.2f)
            }

            // ── Thunderstorm ─────────────────────────────────────────────────
            8000 -> {
                drawCloud(cx, cy - h * 0.08f, w, h,
                    colorShadow = Color(0xFF0F172A), colorBody = Color(0xFF1E293B))
                val boltPath = Path().apply {
                    moveTo(cx + w * 0.06f, cy + h * 0.10f)
                    lineTo(cx - w * 0.10f, cy + h * 0.28f)
                    lineTo(cx + w * 0.01f, cy + h * 0.28f)
                    lineTo(cx - w * 0.06f, cy + h * 0.46f)
                    lineTo(cx + w * 0.16f, cy + h * 0.22f)
                    lineTo(cx + w * 0.04f, cy + h * 0.22f)
                    close()
                }
                drawPath(boltPath, Color(0xFFFACC15))
            }

            // ── Flurries ─────────────────────────────────────────────────────
            5001 -> {
                drawCloud(cx, cy - h * 0.04f, w, h,
                    colorShadow = Color(0xFF94A3B8), colorBody = Color(0xFFCBD5E1))
                drawSnowDots(cx, cy, w, h, snowOffset, count = 2, size = 0.048f,
                    color = Color(0xFFE2E8F0))
            }

            // ── Light Snow ───────────────────────────────────────────────────
            5100 -> {
                drawCloud(cx, cy - h * 0.04f, w, h,
                    colorShadow = Color(0xFF94A3B8), colorBody = Color(0xFFCBD5E1))
                drawSnowDots(cx, cy, w, h, snowOffset, count = 3, size = 0.052f,
                    color = Color(0xFFF1F5F9))
            }

            // ── Snow ─────────────────────────────────────────────────────────
            5000 -> {
                drawCloud(cx, cy - h * 0.04f, w, h,
                    colorShadow = Color(0xFF7C8FA6), colorBody = Color(0xFFB8C5D0))
                drawSnowDots(cx, cy, w, h, snowOffset, count = 4, size = 0.058f,
                    color = Color(0xFFFFFFFF))
            }

            // ── Heavy Snow ───────────────────────────────────────────────────
            5101 -> {
                drawCloud(cx, cy - h * 0.06f, w, h,
                    colorShadow = Color(0xFF64748B), colorBody = Color(0xFF94A3B8))
                drawSnowDots(cx, cy, w, h, snowOffset, count = 5, size = 0.062f,
                    color = Color(0xFFFFFFFF))
            }

            // ── Freezing Drizzle / Freezing Rain ─────────────────────────────
            6000, 6200 -> {
                drawCloud(cx, cy - h * 0.06f, w, h,
                    colorShadow = Color(0xFF475569), colorBody = Color(0xFF64748B))
                drawRainDrops(cx, cy, w, h, rainOffset,
                    dropColor = Color(0xFFBAE6FD), count = 2, heaviness = 0.8f)
                drawSnowDots(cx, cy + h * 0.10f, w, h, snowOffset, count = 2,
                    size = 0.042f, color = Color(0xFFE0F2FE))
            }

            6001, 6201 -> {
                drawCloud(cx, cy - h * 0.06f, w, h,
                    colorShadow = Color(0xFF334155), colorBody = Color(0xFF475569))
                drawRainDrops(cx, cy, w, h, rainOffset,
                    dropColor = Color(0xFF7DD3FC), count = 3, heaviness = 1.0f)
                drawSnowDots(cx, cy + h * 0.08f, w, h, snowOffset, count = 2,
                    size = 0.046f, color = Color(0xFFBAE6FD))
            }

            // ── Ice Pellets / Sleet ───────────────────────────────────────────
            7000, 7100, 7101 -> {
                drawCloud(cx, cy - h * 0.06f, w, h,
                    colorShadow = Color(0xFF475569), colorBody = Color(0xFF64748B))
                // solid ice ball dots
                val iceCenters = listOf(
                    Offset(cx - w * 0.16f, cy + h * 0.28f),
                    Offset(cx,             cy + h * 0.33f + snowOffset * 0.4f),
                    Offset(cx + w * 0.16f, cy + h * 0.28f)
                )
                iceCenters.forEach { pos ->
                    drawCircle(Color(0xFFBAE6FD), radius = w * 0.065f, center = pos)
                    drawCircle(Color.White.copy(alpha = 0.6f), radius = w * 0.03f,
                        center = pos - Offset(w * 0.02f, h * 0.02f))
                }
            }

            // ── Fog ───────────────────────────────────────────────────────────
            2000 -> {
                drawFogBands(cx, cy, w, h, count = 3,
                    color = Color(0xFF94A3B8), alpha = 0.85f)
            }

            // ── Light Fog ─────────────────────────────────────────────────────
            2100 -> {
                drawFogBands(cx, cy, w, h, count = 2,
                    color = Color(0xFF94A3B8), alpha = 0.65f)
            }

            // ── Fallback — generic cloudy ─────────────────────────────────────
            else -> {
                drawCloud(cx, cy, w, h,
                    colorShadow = Color(0xFF64748B), colorBody = Color(0xFF94A3B8))
            }
        }
    }
}

// ── Draw helpers ─────────────────────────────────────────────────────────────

private fun DrawScope.drawSun(
    cx: Float, cy: Float, w: Float, h: Float,
    rotation: Float, rayCount: Int, coreRatio: Float,
    coreColor: Color, rayColor: Color
) {
    drawCircle(coreColor, radius = w * coreRatio, center = Offset(cx, cy))
    val inner = w * (coreRatio + 0.07f)
    val outer = w * (coreRatio + 0.18f)
    for (i in 0 until rayCount) {
        val angle = Math.toRadians((i * (360.0 / rayCount) + rotation)).toFloat()
        drawLine(
            color = rayColor,
            start = Offset(cx + inner * cos(angle), cy + inner * sin(angle)),
            end   = Offset(cx + outer * cos(angle), cy + outer * sin(angle)),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawRainDrops(
    cx: Float, cy: Float, w: Float, h: Float,
    offset: Float, dropColor: Color, count: Int, heaviness: Float
) {
    val spacing = w * 0.16f
    val startX = cx - spacing * (count - 1) / 2f
    val strokeW = w * 0.05f * heaviness
    val length = h * 0.14f * heaviness
    for (i in 0 until count) {
        val rx = startX + i * spacing
        val ry = cy + h * 0.16f + offset
        drawLine(dropColor,
            start = Offset(rx, ry),
            end   = Offset(rx - w * 0.035f, ry + length),
            strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSnowDots(
    cx: Float, cy: Float, w: Float, h: Float,
    offset: Float, count: Int, size: Float, color: Color
) {
    val spacing = w * 0.18f
    val startX = cx - spacing * (count - 1) / 2f
    for (i in 0 until count) {
        val px = startX + i * spacing
        val py = cy + h * 0.20f + offset * (if (i % 2 == 0) 0.8f else 1.0f)
        drawCircle(color, radius = w * size, center = Offset(px, py))
        // small glint
        drawCircle(Color.White.copy(alpha = 0.55f), radius = w * size * 0.4f,
            center = Offset(px - w * 0.012f, py - h * 0.012f))
    }
}

private fun DrawScope.drawFogBands(
    cx: Float, cy: Float, w: Float, h: Float,
    count: Int, color: Color, alpha: Float
) {
    val bandColor = color.copy(alpha = alpha)
    val spacing = h * 0.18f
    val lengths = listOf(0.80f, 0.60f, 0.70f)
    val topY = cy - spacing * (count - 1) / 2f
    for (i in 0 until count) {
        val bx = cx - w * (lengths.getOrElse(i) { 0.7f } / 2f)
        val ex = cx + w * (lengths.getOrElse(i) { 0.7f } / 2f)
        val by = topY + i * spacing
        drawLine(bandColor, start = Offset(bx, by), end = Offset(ex, by),
            strokeWidth = h * 0.085f, cap = StrokeCap.Round)
    }
}

private fun buildCloudPath(cx: Float, cy: Float, w: Float, h: Float): Path =
    Path().apply {
        moveTo(cx - w * 0.20f, cy + h * 0.15f)
        lineTo(cx + w * 0.20f, cy + h * 0.15f)
        arcTo(Rect(Offset(cx - w * 0.125f, cy - h * 0.125f), Size(w * 0.25f, h * 0.25f)),
            90f, -180f, false)
        arcTo(Rect(Offset(cx - w * 0.175f, cy - h * 0.175f), Size(w * 0.35f, h * 0.35f)),
            180f, -135f, false)
        arcTo(Rect(Offset(cx - w * 0.11f, cy - h * 0.11f), Size(w * 0.22f, h * 0.22f)),
            270f, -135f, false)
        close()
    }

private fun DrawScope.drawCloud(
    cx: Float, cy: Float, w: Float, h: Float,
    colorShadow: Color, colorBody: Color
) {
    // shadow layer — slightly down-right
    drawPath(buildCloudPath(cx + w * 0.025f, cy + h * 0.025f, w, h), colorShadow)
    // body layer — on top, slightly lighter
    drawPath(buildCloudPath(cx, cy, w, h), colorBody)
}
