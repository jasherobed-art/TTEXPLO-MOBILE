package com.ttexplo.mobile.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ttexplo.mobile.ui.theme.MineErrorRed
import com.ttexplo.mobile.ui.theme.MineSuccessGreenLight
import com.ttexplo.mobile.ui.theme.MineWarningAmber
import kotlinx.coroutines.delay

enum class ScanFeedbackType {
    SUCCESS,
    WARNING,
    ERROR
}

data class ScanFlashEvent(
    val id: Long = System.nanoTime(),
    val type: ScanFeedbackType
)

/**
 * Superposición de destello visual perimetral no bloqueante (~280 ms) para confirmación
 * periférica inmediata en ambientes con mucho ruido o uso de protección auditiva.
 */
@Composable
fun ScanFlashOverlay(
    event: ScanFlashEvent?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(event?.id, enabled) {
        if (event != null && enabled) {
            alphaAnim.snapTo(1f)
            val holdMs = when (event.type) {
                ScanFeedbackType.SUCCESS -> 55L
                ScanFeedbackType.WARNING -> 90L
                ScanFeedbackType.ERROR -> 120L
            }
            val fadeMs = when (event.type) {
                ScanFeedbackType.SUCCESS -> 210
                ScanFeedbackType.WARNING -> 260
                ScanFeedbackType.ERROR -> 320
            }
            delay(holdMs)
            alphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = fadeMs, easing = FastOutSlowInEasing)
            )
        } else {
            alphaAnim.snapTo(0f)
        }
    }

    val currentAlpha = alphaAnim.value
    if (event != null && enabled && currentAlpha > 0.005f) {
        val baseColor = when (event.type) {
            ScanFeedbackType.SUCCESS -> MineSuccessGreenLight
            ScanFeedbackType.WARNING -> MineWarningAmber
            ScanFeedbackType.ERROR -> MineErrorRed
        }
        val fillMaxAlpha = when (event.type) {
            ScanFeedbackType.SUCCESS -> 0.16f
            ScanFeedbackType.WARNING -> 0.22f
            ScanFeedbackType.ERROR -> 0.28f
        }

        Canvas(
            modifier = modifier
                .fillMaxSize()
                .testTag("scan_flash_overlay")
        ) {
            val strokePx = 10.dp.toPx()
            // Velo translúcido breve sobre la pantalla
            drawRect(
                color = baseColor.copy(alpha = fillMaxAlpha * currentAlpha),
                size = size
            )
            // Marco perimetral luminoso de alto contraste
            drawRect(
                color = baseColor.copy(alpha = 0.92f * currentAlpha),
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = Size(
                    width = (size.width - strokePx).coerceAtLeast(0f),
                    height = (size.height - strokePx).coerceAtLeast(0f)
                ),
                style = Stroke(width = strokePx)
            )
        }
    }
}
