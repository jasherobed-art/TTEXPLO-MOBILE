package com.ttexplo.mobile

import androidx.compose.ui.graphics.Color
import com.ttexplo.mobile.ui.theme.MineDarkSurface
import com.ttexplo.mobile.ui.theme.MineDarkSurfaceElevated
import com.ttexplo.mobile.ui.theme.MineErrorText
import com.ttexplo.mobile.ui.theme.MineInfoText
import com.ttexplo.mobile.ui.theme.MineOutline
import com.ttexplo.mobile.ui.theme.MineSuccessText
import com.ttexplo.mobile.ui.theme.MineTextMuted
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Cálculo WCAG 2.1 sobre los hex de los tokens, no medición en pantalla.
 * La función vive aquí a propósito: es la única definición del umbral que se verifica.
 */
private fun relativeLuminance(color: Color): Double {
    fun channel(srgb: Float): Double {
        val v = srgb.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}

private fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

class HardeningUiTest {

    @Test
    fun statusTokensMeetWcagAaOnDarkSurfaces() {
        val tokens = listOf(
            "MineTextMuted" to MineTextMuted,
            "MineOutline" to MineOutline,
            "MineErrorText" to MineErrorText,
            "MineSuccessText" to MineSuccessText,
            "MineInfoText" to MineInfoText
        )

        tokens.forEach { (name, token) ->
            val onSurface = contrastRatio(token, MineDarkSurface)
            val onElevated = contrastRatio(token, MineDarkSurfaceElevated)
            assertTrue(
                "$name on MineDarkSurface: $onSurface < 4.5:1",
                onSurface >= 4.5
            )
            assertTrue(
                "$name on MineDarkSurfaceElevated: $onElevated < 3.0:1",
                onElevated >= 3.0
            )
        }
    }
}
