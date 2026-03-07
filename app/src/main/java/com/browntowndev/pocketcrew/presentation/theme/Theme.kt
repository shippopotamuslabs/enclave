package com.browntowndev.pocketcrew.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PeachAccent,
    onPrimary = Color.Black,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = PeachAccent,

    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkOnBackground,

    tertiary = PeachVariant,
    onTertiary = DarkOnBackground,

    background = DarkBackground,
    onBackground = DarkOnBackground,

    surface = DarkSurface,
    onSurface = DarkOnBackground,

    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,

    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = PurpleLightPrimary,
    secondary = Color(0xFF64748B),
    tertiary = GoldVariant,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = LightOnBackground,
    onSurface = LightOnBackground,
)

@Composable
fun PocketCrewTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDarkTheme = darkTheme ?: isSystemInDarkTheme()

    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}