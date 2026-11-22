package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorContainerGold = Color(0xFF332B00)
private val ColorContainerCyan = Color(0xFF002E36)

private val TTExploDarkColorScheme = darkColorScheme(
    primary = EmpaqueCajaColor,
    onPrimary = IndustrialObsidian,
    primaryContainer = ColorContainerGold,
    onPrimaryContainer = EmpaqueCajaColor,
    secondary = EmpaqueSacoColor,
    onSecondary = IndustrialObsidian,
    secondaryContainer = ColorContainerCyan,
    onSecondaryContainer = EmpaqueSacoColor,
    tertiary = EmpaqueCartuchoColor,
    onTertiary = IndustrialObsidian,
    background = IndustrialObsidian,
    onBackground = TextPrimary,
    surface = IndustrialPanel,
    onSurface = TextPrimary,
    surfaceVariant = IndustrialCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = IndustrialPanel,
    surfaceContainer = IndustrialCard,
    surfaceContainerHigh = IndustrialCardElevated,
    outline = IndustrialBorder,
    outlineVariant = IndustrialBorderStrong,
    error = SignalError,
    onError = TextPrimary,
    errorContainer = SignalErrorSurface,
    onErrorContainer = SignalError
)

val TTExploShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun TTExploTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TTExploDarkColorScheme,
        typography = TTExploTypography,
        shapes = TTExploShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    TTExploTheme(content = content)
}
