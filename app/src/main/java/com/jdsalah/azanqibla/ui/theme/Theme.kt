package com.jdsalah.azanqibla.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = AppPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = AppPrimaryLight.copy(alpha = 0.05f),
    onPrimaryContainer = AppPrimaryLight,
    secondary = AppAccentGold,
    background = AppBackgroundLight,
    surface = AppCardLight,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B)
)

private val DarkColors = darkColorScheme(
    primary = AppPrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = AppPrimaryDark.copy(alpha = 0.1f),
    onPrimaryContainer = AppPrimaryDark,
    secondary = AppAccentGold,
    background = AppBackgroundDark,
    surface = AppCardDark,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = AppCardDark,
    onSurfaceVariant = Color(0xFF94A3B8)
)

@Composable
fun JdSalahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
