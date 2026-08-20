package com.halanoi.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberEmerald,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF022C22),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF064E3B),
    onPrimaryContainer = CyberEmerald,
    secondary = RoyalViolet,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF4C1D95),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFDDD6FE),
    tertiary = NeonCyan,
    onTertiary = androidx.compose.ui.graphics.Color(0xFF083344),
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder,
    error = DangerCrimson,
    onError = androidx.compose.ui.graphics.Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = LightEmerald,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD1FAE5),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF065F46),
    secondary = LightViolet,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFEDE9FE),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF5B21B6),
    tertiary = LightCyan,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    error = LightCrimson,
    onError = androidx.compose.ui.graphics.Color.White
)

@Composable
fun HalanoiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}