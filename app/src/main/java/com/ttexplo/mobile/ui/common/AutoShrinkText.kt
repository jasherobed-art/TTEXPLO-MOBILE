package com.ttexplo.mobile.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.ui.theme.MineTextPrimary

/**
 * Composable reutilizable de texto auto-escalable que reduce su tamaño de fuente
 * progresivamente si el contenido excede el ancho disponible para evitar recortes o desbordes.
 */
@Composable
fun AutoShrinkText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MineTextPrimary,
    maxLines: Int = 1,
    softWrap: Boolean = true,
    minTextSize: TextUnit = 8.sp,
    initialTextSize: TextUnit = 13.sp,
    targetTextSize: TextUnit = initialTextSize,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val startSize = if (targetTextSize != initialTextSize) targetTextSize else initialTextSize
    var textSize by remember(text, startSize) { mutableStateOf(startSize) }
    var readyToDraw by remember(text, startSize) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        softWrap = softWrap,
        fontWeight = fontWeight,
        fontSize = textSize,
        textAlign = textAlign,
        overflow = TextOverflow.Ellipsis,
        style = style,
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && textSize > minTextSize) {
                val nextSize = (textSize.value - 1f).coerceAtLeast(minTextSize.value).sp
                textSize = nextSize
            } else {
                readyToDraw = true
            }
        }
    )
}
