package com.ttexplo.mobile.ui.screens.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ttexplo.mobile.domain.inventory.QrMatrixGenerator
import com.ttexplo.mobile.ui.theme.MineAgentCyan
import com.ttexplo.mobile.ui.theme.MineErrorRed
import kotlin.math.floor

/**
 * Visor QR ISO/IEC 18004 de ultra-alto contraste (#000000 sobre #FFFFFF con quiet zone de 4 módulos)
 * optimizado para lectura instantánea con lectores láser/2D Zebra incluso en polvorines con poca luz.
 */
@Composable
fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier,
    useLowErrorCorrection: Boolean = true,
    accessibilityLabel: String = "Código QR de sincronización de inventario"
) {
    val ecLevel = if (useLowErrorCorrection) ErrorCorrectionLevel.L else ErrorCorrectionLevel.M
    val matrixResult = remember(content, ecLevel) {
        runCatching { QrMatrixGenerator.encode(content, errorCorrection = ecLevel) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(2.dp, MineAgentCyan, RoundedCornerShape(14.dp))
            .padding(10.dp)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center
    ) {
        val matrix = matrixResult.getOrNull()
        if (matrix == null) {
            Text(
                text = "No se pudo renderizar el QR",
                color = MineErrorRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Fondo blanco puro para máximo contraste óptico
                drawRect(color = Color.White, size = size)

                val quietModules = 4
                val totalModules = matrix.size + (quietModules * 2)
                val minSide = minOf(size.width, size.height)
                val modulePx = floor(minSide / totalModules.toFloat()).coerceAtLeast(1f)
                val drawnSide = modulePx * totalModules
                val offsetX = (size.width - drawnSide) / 2f
                val offsetY = (size.height - drawnSide) / 2f

                for (y in 0 until matrix.size) {
                    val top = offsetY + (y + quietModules) * modulePx
                    for (x in 0 until matrix.size) {
                        if (matrix.get(x, y)) {
                            val left = offsetX + (x + quietModules) * modulePx
                            // +0.5f evita micro-líneas blancas por subpixelado en pantallas de alta densidad
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(left, top),
                                size = Size(modulePx + 0.4f, modulePx + 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
