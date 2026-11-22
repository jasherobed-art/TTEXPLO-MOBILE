package com.ttexplo.mobile.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MineDarkColorScheme = darkColorScheme(
    primary = MineSafetyYellow,
    onPrimary = MineDarkBackground,
    primaryContainer = MineSafetyAmber,
    onPrimaryContainer = MineTextPrimary,
    secondary = MineSafetyYellowDark,
    onSecondary = MineTextPrimary,
    background = MineDarkBackground,
    onBackground = MineTextPrimary,
    surface = MineDarkSurface,
    onSurface = MineTextPrimary,
    surfaceVariant = MineDarkSurfaceElevated,
    onSurfaceVariant = MineTextSecondary,
    error = MineErrorRed,
    onError = MineTextPrimary,
    errorContainer = MineErrorRedBackground,
    onErrorContainer = MineErrorRedLight
)

@Composable
fun TTExploTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = MineDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
