package com.ttexplo.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// Safety Industrial Mine Theme Tokens
val MineSafetyYellow = Color(0xFFFFB300)
val MineSafetyYellowDark = Color(0xFFC68400)
val MineSafetyAmber = Color(0xFFFF8F00)

val MineDarkBackground = Color(0xFF121212)
val MineDarkSurface = Color(0xFF1E1E1E)
val MineDarkSurfaceElevated = Color(0xFF2C2C2C)
val MineBorderGray = Color(0xFF383838)
// El diseño proponía #6E6E6E (3,27:1, solo apto para borde no textual); el gate pide 4,5:1.
val MineOutline = Color(0xFF8A8A8A) // 4,83:1 sobre MineDarkSurface

val MineTextPrimary = Color(0xFFFFFFFF)
val MineTextSecondary = Color(0xFFB0B0B0)
val MineTextMuted = Color(0xFF9E9E9E)

val MineSuccessGreen = Color(0xFF2E7D32) // fill, no para texto
val MineSuccessGreenLight = Color(0xFF81C784)
val MineSuccessText = Color(0xFF81C784) // texto de estado: AA sobre superficies oscuras

val MineErrorRed = Color(0xFFD32F2F) // fill, no para texto
val MineErrorRedLight = Color(0xFFE57373)
val MineErrorText = Color(0xFFE57373) // texto de estado: AA sobre superficies oscuras
val MineErrorRedBackground = Color(0xFF3E1A1A)

val MineAgentBlue = Color(0xFF1976D2) // fill, no para texto
val MineInfoText = Color(0xFF64B5F6) // texto informativo: AA sobre superficies oscuras
val MineAgentCyan = Color(0xFF00ACC1)
val MineHighExplosiveOrange = Color(0xFFF57C00)
val MineHighExplosiveOrangeLight = Color(0xFFFFB74D)
val MineAccessoryPurple = Color(0xFF7B1FA2)
val MineAccessoryPurpleLight = Color(0xFFBA68C8)
val MineWarningAmber = MineSafetyAmber

// Standardized Packaging Color Tokens (Caja, Saco, Cartucho)
val EmpaqueCajaColor = Color(0xFFFFD600)        // Amarillo Oro brillante
val EmpaqueSacoColor = Color(0xFF00E5FF)        // Cian eléctrico
val EmpaqueCartuchoColor = Color(0xFFFF916F)    // Naranja rojizo / Coral (#FF916F)
val EmpaqueAccesorioColor = Color(0xFFBA68C8)   // Morado / Lavanda
val EmpaqueTotalColor = Color(0xFFFFFFFF)       // Blanco neutro
