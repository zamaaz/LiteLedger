package com.liteledger.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Fallback Schemes
private val DarkColorScheme = darkColorScheme(
    primary = SeedGreen,
    secondary = SeedRed,
    tertiary = MoneyGreen
)

private val LightColorScheme = lightColorScheme(
    primary = SeedGreen,
    secondary = SeedRed,
    tertiary = MoneyGreen
)

@Composable
fun LiteLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // 1. Determine Color Scheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 2. Handle Status Bar (PixelPlay Logic)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Make status bar transparent so content can draw behind it (Edge-to-Edge)
            // Or match the background color if you prefer
            window.statusBarColor = Color.Transparent.toArgb()

            // Set icon color (Dark icons on light background, Light icons on dark background)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography, // Our Custom Font
        shapes = AppShapes,
        content = content
    )
}