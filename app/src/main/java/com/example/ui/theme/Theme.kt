package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SleekDarkPrimary,
    onPrimary = SleekDarkOnPrimary,
    primaryContainer = SleekDarkPrimaryContainer,
    onPrimaryContainer = SleekDarkOnPrimaryContainer,
    background = SleekDarkBg,
    surface = SleekDarkSurface,
    onBackground = SleekDarkOnSurface,
    onSurface = SleekDarkOnSurface,
    onSurfaceVariant = SleekDarkOnSurfaceVariant,
    secondaryContainer = SleekDarkSecondaryContainer,
    onSecondaryContainer = SleekDarkOnSecondaryContainer,
    outline = SleekDarkOutline,
    outlineVariant = SleekDarkOutlineVariant,
    error = SleekAlertBorder,
    errorContainer = SleekDarkSecondaryContainer,
    onErrorContainer = SleekDarkOnSecondaryContainer
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SleekLightPrimary,
    onPrimary = SleekLightOnPrimary,
    primaryContainer = SleekLightPrimaryContainer,
    onPrimaryContainer = SleekLightOnPrimaryContainer,
    background = SleekLightBg,
    surface = SleekLightSurface,
    onBackground = SleekLightOnSurface,
    onSurface = SleekLightOnSurface,
    onSurfaceVariant = SleekLightOnSurfaceVariant,
    secondaryContainer = SleekLightSecondaryContainer,
    onSecondaryContainer = SleekLightOnSecondaryContainer,
    outline = SleekLightOutline,
    outlineVariant = SleekLightOutlineVariant,
    error = SleekAlertBorder,
    errorContainer = SleekAlertBg,
    onErrorContainer = SleekAlertText
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic wallpaper color by default so the custom Sleek Interface palette is highlighted
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
