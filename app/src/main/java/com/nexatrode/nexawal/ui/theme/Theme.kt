package com.nexatrode.nexawal.ui.theme

import android.os.Build
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
    primary = Color(0xFF36C7D6),
    onPrimary = Color(0xFF04151C),
    secondary = Color(0xFF36C7D6),
    onSecondary = Color(0xFF04151C),
    tertiary = Color(0xFF24B88A),
    background = Color(0xFF081522),
    onBackground = Color(0xFFE6F2F7),
    surface = Color(0xFF102433),
    onSurface = Color(0xFFE6F2F7),
    error = Color(0xFFF05F6D),
    onError = Color(0xFF081522),
)

private val LightColorScheme = lightColorScheme(
    primary = NexaTeal,
    onPrimary = NexaTealOn,
    secondary = NexaTeal,
    onSecondary = NexaTealOn,
    tertiary = Color(0xFF006F58),
    background = Color(0xFFF2F6F8),
    onBackground = Color(0xFF152B37),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF152B37),
    error = Color(0xFFB32636),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun NexawalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic/system wallpaper colors so neon green isn't overridden by Material You blue.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
