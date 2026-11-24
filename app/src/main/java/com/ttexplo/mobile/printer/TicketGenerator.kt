package com.ttexplo.mobile.printer

import com.ttexplo.mobile.domain.model.AccesoriosRecord
import com.ttexplo.mobile.domain.model.DespachoRecord
import com.ttexplo.mobile.domain.model.DevolucionRecord
import com.ttexplo.mobile.domain.model.EntradaRecord
import com.ttexplo.mobile.domain.model.ScannedCodeRecord
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import java.io.ByteArrayOutputStream

enum class PaperWidth(
    val lineLength: Int,
    val cpclDots: Int,
    val shortLabel: String,
    val inchesLabel: String,
    val inchesText: String,
    val displayLabel: String
) {
    WIDTH_2_INCH(32, 384, "58mm", "2\"", "2 pulg.", "2 Pulgadas (58mm)"),
    WIDTH_3_INCH(42, 576, "80mm", "3\"", "3 pulg.", "3 Pulgadas (80mm)"),
    WIDTH_4_INCH(56, 832, "104mm", "4\"", "4 pulg.", "4 Pulgadas (104mm)");

    val combinedLabel: String
        get() = "$shortLabel ($inchesLabel)"

    companion object {
        fun fromNameOrDefault(name: String?): PaperWidth {
            return entries.firstOrNull { it.name == name } ?: WIDTH_4_INCH
        }
    }
}

object TicketGenerator {

    private val CHARSET = Charsets.US_ASCII

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)

    // Modo inverso ESC/POS (fondo negro sólido con texto blanco): GS B 1 / GS B 0
    val ESC_INVERSE_ON = byteArrayOf(0x1D, 0x42, 0x01)
    val ESC_INVERSE_OFF = byteArrayOf(0x1D, 0x42, 0x00)

    private val ESC_DOUBLE_SIZE = byteArrayOf(0x1B, 0x21, 0x30)
    private val ESC_NORMAL_SIZE = byteArrayOf(0x1B, 0x21, 0x00)
    private val ESC_FEED_6_LINES = byteArrayOf(0x1B, 0x64, 0x06)

    // Margen inferior ~3 cm en papel térmico de 80 mm (≈4 mm por línea).
    const val BOTTOM_MARGIN_ESC_LINES = 7
    const val BOTTOM_MARGIN_CPCL_DOTS = 220
    private val ESC_FEED_BOTTOM_MARGIN = byteArrayOf(0x1B, 0x64, BOTTOM_MARGIN_ESC_LINES.toByte())

    private fun writeEscPosBottomMargin(out: ByteArrayOutputStream) {
        out.write(ESC_FEED_BOTTOM_MARGIN)
    }

    private fun appendPlainBottomMargin(sb: StringBuilder) {
        sb.append("\r\n".repeat(BOTTOM_MARGIN_ESC_LINES))
    }

    fun stripAccents(text: String): String {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    private fun signatureLine(paperWidth: PaperWidth): String =
        "_".repeat((paperWidth.lineLength - 4).coerceAtLeast(1))

    data class CpclFont(
        val fontNumber: Int,
        val fontSize: Int,
        val charWidthDots: Int,
        val lineHeightDots: Int
    )

    /**
     * AutoShrink para impresión térmica CPCL:
     * Selecciona dinámicamente la fuente y tamaño óptimo según el ancho disponible en dots
     * para evitar truncamientos, traslapes de líneas o desbordes en papel continuo.
     */
    fun selectCpclFont(text: String, maxWidthDots: Int, preferLarge: Boolean = false): CpclFont {
        val len = text.length.coerceAtLeast(1)
        if (preferLarge && len * 16 <= maxWidthDots) {
            return CpclFont(fontNumber = 4, fontSize = 0, charWidthDots = 16, lineHeightDots = 42)
        }
        if (len * 14 <= maxWidthDots) {
            return CpclFont(fontNumber = 5, fontSize = 0, charWidthDots = 14, lineHeightDots = 32)
        }
        if (len * 11 <= maxWidthDots) {
            return CpclFont(fontNumber = 7, fontSize = 0, charWidthDots = 11, lineHeightDots = 26)
        }
        return CpclFont(fontNumber = 0, fontSize = 2, charWidthDots = 9, lineHeightDots = 22)
    }

    /**
     * Escribe un bloque de texto con fondo negro sólido y letras blancas en ESC/POS (GS B 1).
     * Cada línea se rellena al ancho exacto del papel para lograr una franja uniforme de borde a borde.
     */
    private fun writeEscPosInvertedBlock(
        out: ByteArrayOutputStream,
        lineWidth: Int,
        lines: List<String>
    ) {
        if (lines.isEmpty()) return
        out.write(ESC_ALIGN_LEFT)
        out.write(ESC_INVERSE_ON)
        out.write(ESC_BOLD_ON)
        for (rawLine in lines) {
            val clean = stripAccents(rawLine)
            val padded = if (clean.length >= lineWidth) {
                clean.take(lineWidth)
            } else {
                val withLead = if (clean.length + 1 <= lineWidth) " $clean" else clean
                withLead.padEnd(lineWidth, ' ')
            }
            out.write("$padded\r\n".toByteArray(CHARSET))
        }
        out.write(ESC_BOLD_OFF)
        out.write(ESC_INVERSE_OFF)
    }

    /**
     * Emite el bloque TOTAL DE MATERIALES en CPCL con fondo negro sólido y letras blancas
     * utilizando el comando nativo INVERSE-LINE sobre el alto exacto de la banda.
     * Retorna la nueva coordenada Y debajo del bloque.
     */
    private fun addCpclInvertedBlock(
        lines: MutableList<String>,
        startY: Int,
        lineEnd: Int,
        blockLines: List<Pair<String, Boolean>> // (texto, preferMediumFont)
    ): Int {
        if (blockLines.isEmpty()) return startY
        val topPadding = 8
        val bottomPadding = 8
        val boxTopY = startY
        var y = startY + topPadding
        val usableWidth = (lineEnd - 28).coerceAtLeast(180)

        for ((rawText, preferMedium) in blockLines) {
            val clean = stripAccents(rawText)
            val font = if (preferMedium) {
                selectCpclFont(clean, usableWidth, preferLarge = false)
            } else {
                if (clean.length * 11 <= usableWidth) {
                    CpclFont(fontNumber = 7, fontSize = 0, charWidthDots = 11, lineHeightDots = 26)
                } else {
                    CpclFont(fontNumber = 0, fontSize = 2, charWidthDots = 9, lineHeightDots = 22)
                }
            }
            lines.add("TEXT ${font.fontNumber} ${font.fontSize} 18 $y $clean")
            y += font.lineHeightDots
        }

        val boxHeight = (y - boxTopY + bottomPadding).coerceAtLeast(36)
        // INVERSE-LINE invierte (XOR negro/blanco) el rectángulo exacto que cubre las líneas de texto
        lines.add("INVERSE-LINE 10 $boxTopY $lineEnd $boxTopY $boxHeight")
        return boxTopY + boxHeight + 10
    }

    private fun breakdownLine(items: List<ScannedCodeRecord>, lineWidth: Int): String {
        val cajas = items.count { it.code.length == 30 }
        val sacos = items.count { it.code.length == 18 }
        val cartuchos = items.count { it.code.length == 16 || it.code.length == 17 }
        return if (lineWidth <= 32) {
            "CAJ:$cajas  SAC:$sacos  CART:$cartuchos"
        } else {
            "CAJAS: $cajas  SACOS: $sacos  CARTUCHOS: $cartuchos"
        }
    }

    private fun buildCodeDescription(item: ScannedCodeRecord): String {
        val name = stripAccents(item.productName.ifBlank { "Material ID ${item.code.take(3)}" })
        val pres = stripAccents(item.presentation.ifBlank { "Pieza" })
        return "$name ($pres)"
    }

    /**
     * Imprime el listado detallado de códigos escaneados en ESC/POS con el mismo formato estándar
     * de Salidas de Alto Explosivo y Agente: índice [01], código completo, fecha/hora y producto (presentación).
     */
    private fun writeEscPosCodeDetails(
        out: ByteArrayOutputStream,
        w: Int,
        thinDivider: String,
        sectionTitle: String,
        items: List<ScannedCodeRecord>
    ) {
        out.write(ESC_BOLD_ON)
        out.write("$sectionTitle (${items.size}):\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        out.write("$thinDivider\r\n".toByteArray(CHARSET))
        items.forEachIndexed { index, item ->
            val num = String.format("%02d", index + 1)
            val prefix = "[$num] ${item.code}"
            val timeStr = item.timestamp.trim()
            if (timeStr.isNotBlank() && prefix.length + 1 + timeStr.length <= w) {
                val pad = " ".repeat((w - prefix.length - timeStr.length).coerceAtLeast(1))
                out.write("$prefix$pad$timeStr\r\n".toByteArray(CHARSET))
            } else {
                out.write("$prefix\r\n".toByteArray(CHARSET))
                if (timeStr.isNotBlank()) {
                    out.write("     HORA: $timeStr\r\n".toByteArray(CHARSET))
                }
            }
            val desc = buildCodeDescription(item)
            out.write("     $desc\r\n".toByteArray(CHARSET))
        }
        out.write("$thinDivider\r\n".toByteArray(CHARSET))
    }

    /**
     * Versión texto plano (RAW) del listado detallado de códigos escaneados.
     */
    private fun appendPlainCodeDetails(
        sb: StringBuilder,
        w: Int,
        thinDivider: String,
        sectionTitle: String,
        items: List<ScannedCodeRecord>
    ) {
        sb.append("$sectionTitle (${items.size}):\r\n")
        sb.append(thinDivider).append("\r\n")
        items.forEachIndexed { index, item ->
            val num = String.format("%02d", index + 1)
            val prefix = "[$num] ${item.code}"
            val timeStr = item.timestamp.trim()
            if (timeStr.isNotBlank() && prefix.length + 1 + timeStr.length <= w) {
                val pad = " ".repeat((w - prefix.length - timeStr.length).coerceAtLeast(1))
                sb.append(prefix).append(pad).append(timeStr).append("\r\n")
            } else {
                sb.append(prefix).append("\r\n")
                if (timeStr.isNotBlank()) {
                    sb.append("     HORA: ").append(timeStr).append("\r\n")
                }
            }
            val desc = buildCodeDescription(item)
            sb.append("     ").append(desc).append("\r\n")
        }
        sb.append(thinDivider).append("\r\n")
    }

    /**
     * Versión Zebra CPCL del listado detallado de códigos escaneados con AutoShrink para 58mm, 80mm y 104mm.
     * Garantiza coordenadas Y estrictamente crecientes y sin recortes laterales.
     */
    private fun addCpclCodeDetails(
        lines: MutableList<String>,
        startY: Int,
        lineEnd: Int,
        paperWidth: PaperWidth,
        sectionTitle: String,
        items: List<ScannedCodeRecord>
    ): Int {
        var y = startY
        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 16
        val hdr = "$sectionTitle (${items.size}):"
        val hdrFont = selectCpclFont(hdr, lineEnd - 20, preferLarge = false)
        lines.add("TEXT ${hdrFont.fontNumber} ${hdrFont.fontSize} 10 $y $hdr")
        y += hdrFont.lineHeightDots
        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 14

        items.forEachIndexed { index, item ->
            val num = String.format("%02d", index + 1)
            val timeStr = item.timestamp.trim().ifBlank { "S/H" }
            val desc = buildCodeDescription(item)

            if (paperWidth == PaperWidth.WIDTH_4_INCH) {
                lines.add("TEXT 7 0 10 $y [$num] ${item.code}")
                lines.add("TEXT 7 0 570 $y $timeStr")
                y += 24

                val descFont = selectCpclFont(desc, lineEnd - 45, preferLarge = false)
                lines.add("TEXT ${descFont.fontNumber} ${descFont.fontSize} 45 $y $desc")
                y += descFont.lineHeightDots + 4
            } else {
                val codeLine = "[$num] ${item.code}"
                val codeFont = selectCpclFont(codeLine, lineEnd - 20, preferLarge = false)
                lines.add("TEXT ${codeFont.fontNumber} ${codeFont.fontSize} 10 $y $codeLine")
                y += codeFont.lineHeightDots

                val combined = "$desc - $timeStr"
                val usable = (lineEnd - 35).coerceAtLeast(180)
                if (paperWidth == PaperWidth.WIDTH_3_INCH && combined.length * 9 <= usable) {
                    val combinedFont = selectCpclFont(combined, usable, preferLarge = false)
                    lines.add("TEXT ${combinedFont.fontNumber} ${combinedFont.fontSize} 30 $y $combined")
                    y += combinedFont.lineHeightDots + 4
                } else {
                    val descFont = selectCpclFont(desc, usable, preferLarge = false)
                    lines.add("TEXT ${descFont.fontNumber} ${descFont.fontSize} 30 $y $desc")
                    y += descFont.lineHeightDots
                    val timeLine = "HORA: $timeStr"
                    val timeFont = selectCpclFont(timeLine, usable, preferLarge = false)
                    lines.add("TEXT ${timeFont.fontNumber} ${timeFont.fontSize} 30 $y $timeLine")
                    y += timeFont.lineHeightDots + 4
                }
            }
        }
        return y
    }

    // =========================================================================
    // 1. VALE DE DESPACHO (ESC/POS, PLAIN, CPCL)
    // =========================================================================

    fun generateDespachoEscPos(
        record: DespachoRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)

        out.write(ESC_INIT)

        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        val cleanMine = stripAccents(mineName.ifEmpty { "ARIA" })
        out.write("$cleanMine\r\n".toByteArray(CHARSET))
        if (permitNumber.isNotBlank()) {
            val cleanPermit = stripAccents(permitNumber)
            out.write("PERMISO GENERAL: $cleanPermit\r\n".toByteArray(CHARSET))
        }
        if (headerNote.isNotBlank()) {
            val cleanNote = stripAccents(headerNote)
            out.write("$cleanNote\r\n".toByteArray(CHARSET))
        }
        out.write("VALE DE DESPACHO\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        out.write("$divider\r\n".toByteArray(CHARSET))

        out.write(ESC_ALIGN_LEFT)
        val ref = if (record.vale.startsWith("REF", ignoreCase = true)) record.vale else "REF${record.vale}"
        out.write("VALE: $ref\r\n".toByteArray(CHARSET))
        out.write("EMPRESA: ${stripAccents(record.empresa)}\r\n".toByteArray(CHARSET))
        out.write("LUGAR: ${stripAccents(record.lugar)}\r\n".toByteArray(CHARSET))
        out.write("TRANSPORTE: ${stripAccents(record.transportista)}\r\n".toByteArray(CHARSET))
        out.write("TURNO: ${record.turno}    VOLADURA: ${record.voladura}\r\n".toByteArray(CHARSET))
        val timestamp = record.items.firstOrNull()?.timestamp ?: ""
        if (timestamp.isNotEmpty()) {
            out.write("FECHA: $timestamp\r\n".toByteArray(CHARSET))
        }
        out.write("$thinDivider\r\n".toByteArray(CHARSET))

        out.write(ESC_BOLD_ON)
        out.write("RESUMEN DE MATERIALES:\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)

        val grouped = record.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }

        for ((productDesc, items) in grouped) {
            val count = items.size
            out.write("- ${stripAccents(productDesc)}\r\n".toByteArray(CHARSET))
            out.write("  CANTIDAD: $count\r\n".toByteArray(CHARSET))
        }

        out.write("$thinDivider\r\n".toByteArray(CHARSET))

        if (includeCodeDetails && record.items.isNotEmpty()) {
            writeEscPosCodeDetails(
                out = out,
                w = w,
                thinDivider = thinDivider,
                sectionTitle = "LISTADO DE CODIGOS",
                items = record.items
            )
        }

        // Bloque TOTAL DE MATERIALES en negro con letras blancas
        val totalLines = mutableListOf(
            "TOTAL DE MATERIALES: ${record.items.size} PZAS"
        )
        if (includeBreakdown) {
            totalLines.add(breakdownLine(record.items, w))
        }
        writeEscPosInvertedBlock(out, w, totalLines)
        out.write("$divider\r\n\r\n".toByteArray(CHARSET))

        if (includeSignatures) {
            val sigLine = signatureLine(paperWidth)
            out.write("ENTREGO:\r\n".toByteArray(CHARSET))
            out.write("$sigLine\r\n\r\n".toByteArray(CHARSET))
            out.write("RECIBIO:\r\n".toByteArray(CHARSET))
            out.write("$sigLine\r\n".toByteArray(CHARSET))
            out.write("$divider\r\n\r\n\r\n".toByteArray(CHARSET))
        }

        out.write(ESC_FEED_6_LINES)
        writeEscPosBottomMargin(out)
        out.write("\r\n\r\n\r\n\r\n".toByteArray(CHARSET))
        return out.toByteArray()
    }

    fun generateDespachoPlain(
        record: DespachoRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)
        val sb = StringBuilder()

        sb.append("\r\n")
        sb.append(stripAccents(mineName.ifEmpty { "ARIA" })).append("\r\n")
        if (permitNumber.isNotBlank()) {
            sb.append("PERMISO GENERAL: ").append(stripAccents(permitNumber)).append("\r\n")
        }
        if (headerNote.isNotBlank()) {
            sb.append(stripAccents(headerNote)).append("\r\n")
        }
        sb.append("VALE DE DESPACHO\r\n")
        sb.append(divider).append("\r\n")

        val ref = if (record.vale.startsWith("REF", ignoreCase = true)) record.vale else "REF${record.vale}"
        sb.append("VALE: ").append(ref).append("\r\n")
        sb.append("EMPRESA: ").append(stripAccents(record.empresa)).append("\r\n")
        sb.append("LUGAR: ").append(stripAccents(record.lugar)).append("\r\n")
        sb.append("TRANSPORTE: ").append(stripAccents(record.transportista)).append("\r\n")
        sb.append("TURNO: ").append(record.turno).append("    VOLADURA: ").append(record.voladura).append("\r\n")
        val timestamp = record.items.firstOrNull()?.timestamp ?: ""
        if (timestamp.isNotEmpty()) {
            sb.append("FECHA: ").append(timestamp).append("\r\n")
        }
        sb.append(thinDivider).append("\r\n")

        sb.append("RESUMEN DE MATERIALES:\r\n")
        val grouped = record.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }
        for ((productDesc, items) in grouped) {
            sb.append("- ").append(stripAccents(productDesc)).append("\r\n")
            sb.append("  CANTIDAD: ").append(items.size).append("\r\n")
        }
        sb.append(thinDivider).append("\r\n")

        if (includeCodeDetails && record.items.isNotEmpty()) {
            appendPlainCodeDetails(
                sb = sb,
                w = w,
                thinDivider = thinDivider,
                sectionTitle = "LISTADO DE CODIGOS",
                items = record.items
            )
        }

        sb.append("TOTAL DE MATERIALES: ").append(record.items.size).append(" PZAS\r\n")
        if (includeBreakdown) {
            sb.append(breakdownLine(record.items, w)).append("\r\n")
        }
        sb.append(divider).append("\r\n\r\n")

        if (includeSignatures) {
            val sigLine = signatureLine(paperWidth)
            sb.append("ENTREGO:\r\n").append(sigLine).append("\r\n\r\n")
            sb.append("RECIBIO:\r\n").append(sigLine).append("\r\n")
            sb.append(divider).append("\r\n\r\n")
        }

        appendPlainBottomMargin(sb)
        sb.append("\r\n\r\n\r\n\r\n\r\n\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateDespachoTicket(
        despacho: DespachoRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH
    ): ByteArray = generateDespachoEscPos(despacho, paperWidth)

    fun generateDespachoCpcl(
        despacho: DespachoRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_4_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val totalItems = despacho.items.size
        val grouped = despacho.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }

        val pageWidth = paperWidth.cpclDots
        val lineEnd = pageWidth - 20

        val lines = mutableListOf<String>()
        var y = 20

        lines.add("CENTER")
        val cleanMine = stripAccents(mineName.ifEmpty { "ARIA" })
        val mineFont = selectCpclFont(cleanMine, lineEnd - 20, preferLarge = true)
        lines.add("TEXT ${mineFont.fontNumber} ${mineFont.fontSize} 0 $y $cleanMine")
        y += mineFont.lineHeightDots

        if (permitNumber.isNotBlank()) {
            val permitText = "PERMISO GENERAL: ${stripAccents(permitNumber)}"
            val permitFont = selectCpclFont(permitText, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${permitFont.fontNumber} ${permitFont.fontSize} 0 $y $permitText")
            y += permitFont.lineHeightDots
        }
        if (headerNote.isNotBlank()) {
            val noteText = stripAccents(headerNote)
            val noteFont = selectCpclFont(noteText, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${noteFont.fontNumber} ${noteFont.fontSize} 0 $y $noteText")
            y += noteFont.lineHeightDots
        }
        val titleFont = selectCpclFont("VALE DE DESPACHO", lineEnd - 20, preferLarge = false)
        lines.add("TEXT ${titleFont.fontNumber} ${titleFont.fontSize} 0 $y VALE DE DESPACHO")
        y += titleFont.lineHeightDots + 2

        lines.add("LEFT")
        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 16

        val ref = if (despacho.vale.startsWith("REF", ignoreCase = true)) despacho.vale else "REF${despacho.vale}"
        lines.add("TEXT 7 0 10 $y VALE: $ref")
        y += 26
        lines.add("TEXT 7 0 10 $y EMPRESA: ${stripAccents(despacho.empresa)}")
        y += 26
        lines.add("TEXT 7 0 10 $y LUGAR: ${stripAccents(despacho.lugar)}")
        y += 26
        lines.add("TEXT 7 0 10 $y TRANSPORTE: ${stripAccents(despacho.transportista)}")
        y += 26
        lines.add("TEXT 7 0 10 $y TURNO: ${despacho.turno}  VOLADURA: ${despacho.voladura}")
        y += 26

        val firstTimestamp = despacho.items.firstOrNull()?.timestamp ?: ""
        if (firstTimestamp.isNotEmpty()) {
            lines.add("TEXT 7 0 10 $y FECHA: $firstTimestamp")
            y += 26
        }

        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 16

        lines.add("TEXT 5 0 10 $y RESUMEN DE MATERIALES:")
        y += 28

        for ((productDesc, items) in grouped) {
            val count = items.size
            val cleanDesc = "- ${stripAccents(productDesc)}"
            val descFont = selectCpclFont(cleanDesc, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${descFont.fontNumber} ${descFont.fontSize} 10 $y $cleanDesc")
            y += descFont.lineHeightDots
            lines.add("TEXT 7 0 30 $y CANTIDAD: $count")
            y += 26
        }

        if (includeCodeDetails && despacho.items.isNotEmpty()) {
            y = addCpclCodeDetails(
                lines = lines,
                startY = y,
                lineEnd = lineEnd,
                paperWidth = paperWidth,
                sectionTitle = "LISTADO DE CODIGOS",
                items = despacho.items
            )
        }

        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 14

        // Bloque TOTAL DE MATERIALES en fondo negro con letras blancas (INVERSE-LINE)
        val invertedLines = mutableListOf(
            "TOTAL DE MATERIALES: $totalItems PIEZAS" to true
        )
        if (includeBreakdown) {
            invertedLines.add(breakdownLine(despacho.items, paperWidth.lineLength) to false)
        }
        y = addCpclInvertedBlock(lines, y, lineEnd, invertedLines)

        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 20

        if (includeSignatures) {
            lines.add("TEXT 7 0 10 $y ENTREGO:")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 50

            lines.add("TEXT 7 0 10 $y RECIBIO:")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 20
        }

        val totalHeight = y + 40 + BOTTOM_MARGIN_CPCL_DOTS
        val sb = StringBuilder()
        sb.append("! 0 200 200 $totalHeight 1\r\n")
        sb.append("PAGE-WIDTH $pageWidth\r\n")
        for (line in lines) {
            sb.append(line).append("\r\n")
        }
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    // =========================================================================
    // 2. VALE DE ACCESORIOS / DEVOLUCIÓN DE ACCESORIOS (ESC/POS, PLAIN, CPCL)
    // =========================================================================

    fun generateAccesoriosEscPos(
        record: AccesoriosRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        includeSignatures: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)

        out.write(ESC_INIT)

        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write("${stripAccents(mineName.ifEmpty { "ARIA" })}\r\n".toByteArray(CHARSET))
        if (permitNumber.isNotBlank()) {
            out.write("PERMISO GENERAL: ${stripAccents(permitNumber)}\r\n".toByteArray(CHARSET))
        }
        val isDev = record.items.any { it.quantity < 0 }
        val titleText = if (isDev) "DEVOLUCION DE ACCESORIOS" else "SALIDA DE ACCESORIOS"
        out.write("$titleText\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        out.write("$divider\r\n".toByteArray(CHARSET))

        out.write(ESC_ALIGN_LEFT)
        val acc = if (record.vale.startsWith("ACC", ignoreCase = true)) record.vale else "ACC${record.vale}"
        out.write("VALE: $acc\r\n".toByteArray(CHARSET))
        out.write("EMPRESA: ${stripAccents(record.empresa)}\r\n".toByteArray(CHARSET))
        out.write("LUGAR: ${stripAccents(record.lugar)}\r\n".toByteArray(CHARSET))
        out.write("TURNO: ${record.turno}    VOLADURA: ${record.voladura}\r\n".toByteArray(CHARSET))
        out.write("ENTREGO: ${stripAccents(record.entrego)}\r\n".toByteArray(CHARSET))
        out.write("RECIBIO: ${stripAccents(record.recibio)}\r\n".toByteArray(CHARSET))
        val firstTs = record.items.firstOrNull()?.timestamp ?: ""
        if (firstTs.isNotBlank()) {
            out.write("FECHA: $firstTs\r\n".toByteArray(CHARSET))
        }
        out.write("$thinDivider\r\n".toByteArray(CHARSET))

        val totalPieces = record.items.sumOf { it.quantity }
        if (includeCodeDetails && record.items.isNotEmpty()) {
            out.write(ESC_BOLD_ON)
            val sectionLabel = if (isDev) {
                "ACCESORIOS DEVUELTOS (${record.items.size}):\r\n"
            } else {
                "ACCESORIOS DESPACHADOS (${record.items.size}):\r\n"
            }
            out.write(sectionLabel.toByteArray(CHARSET))
            out.write(ESC_BOLD_OFF)
            out.write("$thinDivider\r\n".toByteArray(CHARSET))

            record.items.forEachIndexed { idx, item ->
                val num = String.format("%02d", idx + 1)
                val line = "[$num] ${item.code}   CANT: ${item.quantity}"
                val ts = item.timestamp.trim()
                if (ts.isNotBlank() && line.length + 1 + ts.length <= w) {
                    val pad = " ".repeat((w - line.length - ts.length).coerceAtLeast(1))
                    out.write("$line$pad$ts\r\n".toByteArray(CHARSET))
                } else {
                    out.write("$line\r\n".toByteArray(CHARSET))
                    if (ts.isNotBlank()) {
                        out.write("     HORA: $ts\r\n".toByteArray(CHARSET))
                    }
                }
            }
            out.write("$thinDivider\r\n".toByteArray(CHARSET))
        }
        val totalLabel = if (isDev) {
            "TOTAL DE MATERIALES: $totalPieces PZAS"
        } else {
            "TOTAL DE MATERIALES: $totalPieces PZAS"
        }
        val subLabel = if (isDev) "DEVOLUCION ACCESORIOS" else "SALIDA ACCESORIOS"
        writeEscPosInvertedBlock(out, w, listOf(totalLabel, subLabel))
        out.write("$divider\r\n\r\n".toByteArray(CHARSET))

        if (includeSignatures) {
            val sigLine = signatureLine(paperWidth)
            out.write("ENTREGO:\r\n".toByteArray(CHARSET))
            out.write("$sigLine\r\n\r\n".toByteArray(CHARSET))
            out.write("RECIBIO:\r\n".toByteArray(CHARSET))
            out.write("$sigLine\r\n".toByteArray(CHARSET))
            out.write("$divider\r\n\r\n\r\n".toByteArray(CHARSET))
        }

        out.write(ESC_FEED_6_LINES)
        writeEscPosBottomMargin(out)
        out.write("\r\n\r\n\r\n\r\n".toByteArray(CHARSET))
        return out.toByteArray()
    }

    fun generateAccesoriosPlain(
        record: AccesoriosRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        includeSignatures: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)
        val sb = StringBuilder()

        sb.append("\r\n")
        sb.append(stripAccents(mineName.ifEmpty { "ARIA" })).append("\r\n")
        if (permitNumber.isNotBlank()) {
            sb.append("PERMISO GENERAL: ").append(stripAccents(permitNumber)).append("\r\n")
        }
        val isDev = record.items.any { it.quantity < 0 }
        val titleText = if (isDev) "DEVOLUCION DE ACCESORIOS" else "SALIDA DE ACCESORIOS"
        sb.append(titleText).append("\r\n")
        sb.append(divider).append("\r\n")

        val acc = if (record.vale.startsWith("ACC", ignoreCase = true)) record.vale else "ACC${record.vale}"
        sb.append("VALE: ").append(acc).append("\r\n")
        sb.append("EMPRESA: ").append(stripAccents(record.empresa)).append("\r\n")
        sb.append("LUGAR: ").append(stripAccents(record.lugar)).append("\r\n")
        sb.append("TURNO: ").append(record.turno).append("    VOLADURA: ").append(record.voladura).append("\r\n")
        sb.append("ENTREGO: ").append(stripAccents(record.entrego)).append("\r\n")
        sb.append("RECIBIO: ").append(stripAccents(record.recibio)).append("\r\n")
        val firstTs = record.items.firstOrNull()?.timestamp ?: ""
        if (firstTs.isNotBlank()) {
            sb.append("FECHA: ").append(firstTs).append("\r\n")
        }
        sb.append(thinDivider).append("\r\n")

        val totalPieces = record.items.sumOf { it.quantity }
        if (includeCodeDetails && record.items.isNotEmpty()) {
            val sectionLabel = if (isDev) {
                "ACCESORIOS DEVUELTOS (${record.items.size}):\r\n"
            } else {
                "ACCESORIOS DESPACHADOS (${record.items.size}):\r\n"
            }
            sb.append(sectionLabel)
            sb.append(thinDivider).append("\r\n")
            record.items.forEachIndexed { idx, item ->
                val num = String.format("%02d", idx + 1)
                val line = "[$num] ${item.code}   CANT: ${item.quantity}"
                val ts = item.timestamp.trim()
                if (ts.isNotBlank() && line.length + 1 + ts.length <= w) {
                    val pad = " ".repeat((w - line.length - ts.length).coerceAtLeast(1))
                    sb.append(line).append(pad).append(ts).append("\r\n")
                } else {
                    sb.append(line).append("\r\n")
                    if (ts.isNotBlank()) {
                        sb.append("     HORA: ").append(ts).append("\r\n")
                    }
                }
            }
            sb.append(thinDivider).append("\r\n")
        }
        val totalLabel = "TOTAL DE MATERIALES: $totalPieces PZAS\r\n"
        sb.append(totalLabel)
        sb.append(divider).append("\r\n\r\n")

        if (includeSignatures) {
            val sigLine = signatureLine(paperWidth)
            sb.append("ENTREGO:\r\n").append(sigLine).append("\r\n\r\n")
            sb.append("RECIBIO:\r\n").append(sigLine).append("\r\n")
            sb.append(divider).append("\r\n\r\n")
        }

        appendPlainBottomMargin(sb)
        sb.append("\r\n\r\n\r\n\r\n\r\n\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateAccesoriosTicket(
        accesorios: AccesoriosRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH
    ): ByteArray = generateAccesoriosEscPos(accesorios, paperWidth)

    fun generateAccesoriosCpcl(
        accesorios: AccesoriosRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_4_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        includeSignatures: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val totalPieces = accesorios.items.sumOf { it.quantity }
        val pageWidth = paperWidth.cpclDots
        val lineEnd = pageWidth - 20

        val lines = mutableListOf<String>()
        var y = 20

        lines.add("CENTER")
        val cleanMine = stripAccents(mineName.ifEmpty { "ARIA" })
        val mineFont = selectCpclFont(cleanMine, lineEnd - 20, preferLarge = true)
        lines.add("TEXT ${mineFont.fontNumber} ${mineFont.fontSize} 0 $y $cleanMine")
        y += mineFont.lineHeightDots

        if (permitNumber.isNotBlank()) {
            val permitText = "PERMISO GENERAL: ${stripAccents(permitNumber)}"
            val permitFont = selectCpclFont(permitText, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${permitFont.fontNumber} ${permitFont.fontSize} 0 $y $permitText")
            y += permitFont.lineHeightDots
        }
        val isDev = accesorios.items.any { it.quantity < 0 }
        val titleText = if (isDev) "DEVOLUCION DE ACCESORIOS" else "SALIDA DE ACCESORIOS"
        val titleFont = selectCpclFont(titleText, lineEnd - 20, preferLarge = false)
        lines.add("TEXT ${titleFont.fontNumber} ${titleFont.fontSize} 0 $y $titleText")
        y += titleFont.lineHeightDots + 2

        lines.add("LEFT")
        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 16

        val acc = if (accesorios.vale.startsWith("ACC", ignoreCase = true)) accesorios.vale else "ACC${accesorios.vale}"
        lines.add("TEXT 7 0 10 $y VALE: $acc")
        y += 26
        lines.add("TEXT 7 0 10 $y EMPRESA: ${stripAccents(accesorios.empresa)}")
        y += 26
        lines.add("TEXT 7 0 10 $y LUGAR: ${stripAccents(accesorios.lugar)}")
        y += 26
        lines.add("TEXT 7 0 10 $y TURNO: ${accesorios.turno}  VOLADURA: ${accesorios.voladura}")
        y += 26
        lines.add("TEXT 7 0 10 $y ENTREGO: ${stripAccents(accesorios.entrego)}")
        y += 26
        lines.add("TEXT 7 0 10 $y RECIBIO: ${stripAccents(accesorios.recibio)}")
        y += 26

        val firstTs = accesorios.items.firstOrNull()?.timestamp ?: ""
        if (firstTs.isNotBlank()) {
            lines.add("TEXT 7 0 10 $y FECHA: $firstTs")
            y += 26
        }

        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 16

        if (includeCodeDetails && accesorios.items.isNotEmpty()) {
            val sectionText = if (isDev) {
                "ACCESORIOS DEVUELTOS (${accesorios.items.size}):"
            } else {
                "ACCESORIOS DESPACHADOS (${accesorios.items.size}):"
            }
            val secFont = selectCpclFont(sectionText, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${secFont.fontNumber} ${secFont.fontSize} 10 $y $sectionText")
            y += secFont.lineHeightDots
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 14

            accesorios.items.forEachIndexed { idx, item ->
                val num = String.format("%02d", idx + 1)
                val codeText = "[$num] ${item.code}   CANT: ${item.quantity}"
                val ts = item.timestamp.trim()
                if (paperWidth == PaperWidth.WIDTH_4_INCH) {
                    lines.add("TEXT 7 0 10 $y $codeText")
                    if (ts.isNotBlank()) {
                        lines.add("TEXT 7 0 570 $y $ts")
                    }
                    y += 28
                } else {
                    val codeFont = selectCpclFont(codeText, lineEnd - 20, preferLarge = false)
                    lines.add("TEXT ${codeFont.fontNumber} ${codeFont.fontSize} 10 $y $codeText")
                    y += codeFont.lineHeightDots
                    if (ts.isNotBlank()) {
                        val timeLine = "HORA: $ts"
                        val tf = selectCpclFont(timeLine, lineEnd - 35, preferLarge = false)
                        lines.add("TEXT ${tf.fontNumber} ${tf.fontSize} 30 $y $timeLine")
                        y += tf.lineHeightDots + 4
                    } else {
                        y += 4
                    }
                }
            }

            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 14
        }

        // Bloque TOTAL DE MATERIALES en fondo negro con letras blancas
        val totalLabel = "TOTAL DE MATERIALES: $totalPieces PZAS"
        val subLabel = if (isDev) "DEVOLUCION ACCESORIOS" else "SALIDA ACCESORIOS"
        y = addCpclInvertedBlock(
            lines = lines,
            startY = y,
            lineEnd = lineEnd,
            blockLines = listOf(totalLabel to true, subLabel to false)
        )

        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 20

        if (includeSignatures) {
            lines.add("TEXT 7 0 10 $y ENTREGO:")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 50

            lines.add("TEXT 7 0 10 $y RECIBIO:")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 20
        }

        val totalHeight = y + 40 + BOTTOM_MARGIN_CPCL_DOTS
        val sb = StringBuilder()
        sb.append("! 0 200 200 $totalHeight 1\r\n")
        sb.append("PAGE-WIDTH $pageWidth\r\n")
        for (line in lines) {
            sb.append(line).append("\r\n")
        }
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    // =========================================================================
    // 3. VALE DE ENTRADA A POLVORÍN (ESC/POS, PLAIN, CPCL)
    // =========================================================================

    fun generateEntradaEscPos(
        record: EntradaRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)

        out.write(ESC_INIT)
        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write("${stripAccents(mineName.ifEmpty { "ARIA" })}\r\n".toByteArray(CHARSET))
        if (permitNumber.isNotBlank()) {
            out.write("PERMISO GENERAL: ${stripAccents(permitNumber)}\r\n".toByteArray(CHARSET))
        }
        if (headerNote.isNotBlank()) {
            out.write("${stripAccents(headerNote)}\r\n".toByteArray(CHARSET))
        }
        out.write("VALE DE ENTRADA A POLVORIN\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        out.write("$divider\r\n".toByteArray(CHARSET))

        out.write(ESC_ALIGN_LEFT)
        out.write("PROVEEDOR: ${stripAccents(record.proveedor)}\r\n".toByteArray(CHARSET))
        out.write("POLVORIN: ${stripAccents(record.polvorin)}\r\n".toByteArray(CHARSET))
        out.write("FACTURA / REMISION: ${stripAccents(record.factura)}\r\n".toByteArray(CHARSET))
        val ts = record.items.firstOrNull()?.timestamp ?: DateTimeUtils.currentTimestamp()
        out.write("FECHA: $ts\r\n".toByteArray(CHARSET))
        out.write("$thinDivider\r\n".toByteArray(CHARSET))

        out.write(ESC_BOLD_ON)
        out.write("RESUMEN DE MATERIALES:\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        val grouped = record.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }
        for ((desc, items) in grouped) {
            out.write("- ${stripAccents(desc)}\r\n".toByteArray(CHARSET))
            out.write("  CANTIDAD: ${items.size}\r\n".toByteArray(CHARSET))
        }
        out.write("$thinDivider\r\n".toByteArray(CHARSET))

        if (includeCodeDetails && record.items.isNotEmpty()) {
            writeEscPosCodeDetails(
                out = out,
                w = w,
                thinDivider = thinDivider,
                sectionTitle = "LISTADO DE CODIGOS",
                items = record.items
            )
        }

        val totalLines = mutableListOf("TOTAL DE MATERIALES: ${record.items.size} PZAS")
        if (includeBreakdown) {
            totalLines.add(breakdownLine(record.items, w))
        }
        writeEscPosInvertedBlock(out, w, totalLines)
        out.write("$divider\r\n\r\n".toByteArray(CHARSET))

        if (includeSignatures) {
            val sigLine = signatureLine(paperWidth)
            out.write("RECIBIO EN POLVORIN:\r\n".toByteArray(CHARSET))
            out.write("$sigLine\r\n\r\n".toByteArray(CHARSET))
            out.write("ENTREGO PROVEEDOR:\r\n".toByteArray(CHARSET))
            out.write("$sigLine\r\n".toByteArray(CHARSET))
            out.write("$divider\r\n\r\n\r\n".toByteArray(CHARSET))
        }

        out.write(ESC_FEED_6_LINES)
        writeEscPosBottomMargin(out)
        out.write("\r\n\r\n\r\n\r\n".toByteArray(CHARSET))
        return out.toByteArray()
    }

    fun generateEntradaPlain(
        record: EntradaRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)
        val sb = StringBuilder()

        sb.append("\r\n").append(stripAccents(mineName.ifEmpty { "ARIA" })).append("\r\n")
        if (permitNumber.isNotBlank()) sb.append("PERMISO GENERAL: ").append(stripAccents(permitNumber)).append("\r\n")
        if (headerNote.isNotBlank()) sb.append(stripAccents(headerNote)).append("\r\n")
        sb.append("VALE DE ENTRADA A POLVORIN\r\n").append(divider).append("\r\n")
        sb.append("PROVEEDOR: ").append(stripAccents(record.proveedor)).append("\r\n")
        sb.append("POLVORIN: ").append(stripAccents(record.polvorin)).append("\r\n")
        sb.append("FACTURA: ").append(stripAccents(record.factura)).append("\r\n")
        val ts = record.items.firstOrNull()?.timestamp ?: DateTimeUtils.currentTimestamp()
        sb.append("FECHA: ").append(ts).append("\r\n").append(thinDivider).append("\r\n")

        sb.append("RESUMEN DE MATERIALES:\r\n")
        val grouped = record.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }
        for ((desc, items) in grouped) {
            sb.append("- ").append(stripAccents(desc)).append("\r\n")
            sb.append("  CANTIDAD: ").append(items.size).append("\r\n")
        }
        sb.append(thinDivider).append("\r\n")

        if (includeCodeDetails && record.items.isNotEmpty()) {
            appendPlainCodeDetails(
                sb = sb,
                w = w,
                thinDivider = thinDivider,
                sectionTitle = "LISTADO DE CODIGOS",
                items = record.items
            )
        }

        sb.append("TOTAL DE MATERIALES: ").append(record.items.size).append(" PZAS\r\n")
        if (includeBreakdown) sb.append(breakdownLine(record.items, w)).append("\r\n")
        sb.append(divider).append("\r\n\r\n")

        if (includeSignatures) {
            val sigLine = signatureLine(paperWidth)
            sb.append("RECIBIO EN POLVORIN:\r\n").append(sigLine).append("\r\n\r\n")
            sb.append("ENTREGO PROVEEDOR:\r\n").append(sigLine).append("\r\n$divider\r\n\r\n")
        }
        appendPlainBottomMargin(sb)
        sb.append("\r\n\r\n\r\n\r\n\r\n\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateEntradaCpcl(
        record: EntradaRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_4_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val totalItems = record.items.size
        val pageWidth = paperWidth.cpclDots
        val lineEnd = pageWidth - 20
        val lines = mutableListOf<String>()
        var y = 20

        lines.add("CENTER")
        val cleanMine = stripAccents(mineName.ifEmpty { "ARIA" })
        val mineFont = selectCpclFont(cleanMine, lineEnd - 20, preferLarge = true)
        lines.add("TEXT ${mineFont.fontNumber} ${mineFont.fontSize} 0 $y $cleanMine")
        y += mineFont.lineHeightDots
        if (permitNumber.isNotBlank()) {
            val pt = "PERMISO GENERAL: ${stripAccents(permitNumber)}"
            val pf = selectCpclFont(pt, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${pf.fontNumber} ${pf.fontSize} 0 $y $pt")
            y += pf.lineHeightDots
        }
        if (headerNote.isNotBlank()) {
            val nt = stripAccents(headerNote)
            val nf = selectCpclFont(nt, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${nf.fontNumber} ${nf.fontSize} 0 $y $nt")
            y += nf.lineHeightDots
        }
        val tf = selectCpclFont("VALE DE ENTRADA A POLVORIN", lineEnd - 20, preferLarge = false)
        lines.add("TEXT ${tf.fontNumber} ${tf.fontSize} 0 $y VALE DE ENTRADA A POLVORIN")
        y += tf.lineHeightDots + 2

        lines.add("LEFT")
        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 16
        lines.add("TEXT 7 0 10 $y PROVEEDOR: ${stripAccents(record.proveedor)}")
        y += 26
        lines.add("TEXT 7 0 10 $y POLVORIN: ${stripAccents(record.polvorin)}")
        y += 26
        lines.add("TEXT 7 0 10 $y FACTURA: ${stripAccents(record.factura)}")
        y += 26
        val ts = record.items.firstOrNull()?.timestamp ?: DateTimeUtils.currentTimestamp()
        lines.add("TEXT 7 0 10 $y FECHA: $ts")
        y += 26
        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 16

        lines.add("TEXT 5 0 10 $y RESUMEN DE MATERIALES:")
        y += 28
        val grouped = record.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }
        for ((desc, items) in grouped) {
            val cleanDesc = "- ${stripAccents(desc)}"
            val df = selectCpclFont(cleanDesc, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${df.fontNumber} ${df.fontSize} 10 $y $cleanDesc")
            y += df.lineHeightDots
            lines.add("TEXT 7 0 30 $y CANTIDAD: ${items.size}")
            y += 26
        }

        if (includeCodeDetails && record.items.isNotEmpty()) {
            y = addCpclCodeDetails(
                lines = lines,
                startY = y,
                lineEnd = lineEnd,
                paperWidth = paperWidth,
                sectionTitle = "LISTADO DE CODIGOS",
                items = record.items
            )
        }

        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 14
        val invLines = mutableListOf("TOTAL DE MATERIALES: $totalItems PIEZAS" to true)
        if (includeBreakdown) {
            invLines.add(breakdownLine(record.items, paperWidth.lineLength) to false)
        }
        y = addCpclInvertedBlock(lines, y, lineEnd, invLines)
        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 20

        if (includeSignatures) {
            lines.add("TEXT 7 0 10 $y RECIBIO EN POLVORIN:")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 50
            lines.add("TEXT 7 0 10 $y ENTREGO PROVEEDOR:")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 20
        }

        val totalHeight = y + 40 + BOTTOM_MARGIN_CPCL_DOTS
        val sb = StringBuilder()
        sb.append("! 0 200 200 $totalHeight 1\r\n")
        sb.append("PAGE-WIDTH $pageWidth\r\n")
        lines.forEach { sb.append(it).append("\r\n") }
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    // =========================================================================
    // 4. VALE DE DEVOLUCIÓN DE EXPLOSIVOS (ESC/POS, PLAIN, CPCL)
    // =========================================================================

    fun generateDevolucionEscPos(
        record: DevolucionRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)

        out.write(ESC_INIT)
        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write("${stripAccents(mineName.ifEmpty { "ARIA" })}\r\n".toByteArray(CHARSET))
        if (permitNumber.isNotBlank()) {
            out.write("PERMISO GENERAL: ${stripAccents(permitNumber)}\r\n".toByteArray(CHARSET))
        }
        if (headerNote.isNotBlank()) {
            out.write("${stripAccents(headerNote)}\r\n".toByteArray(CHARSET))
        }
        out.write("VALE DE DEVOLUCION A POLVORIN\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        out.write("$divider\r\n".toByteArray(CHARSET))

        out.write(ESC_ALIGN_LEFT)
        out.write("ENTREGA (MINA): ${stripAccents(record.empleadoEntrega)}\r\n".toByteArray(CHARSET))
        out.write("RECIBE (POLVORIN): ${stripAccents(record.empleadoRecibe)}\r\n".toByteArray(CHARSET))
        val ts = record.items.firstOrNull()?.timestamp ?: DateTimeUtils.currentTimestamp()
        out.write("FECHA: $ts\r\n".toByteArray(CHARSET))
        out.write("$thinDivider\r\n".toByteArray(CHARSET))

        out.write(ESC_BOLD_ON)
        out.write("MATERIALES DEVUELTOS:\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        val grouped = record.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }
        for ((desc, items) in grouped) {
            out.write("- ${stripAccents(desc)}\r\n".toByteArray(CHARSET))
            out.write("  CANTIDAD: ${items.size}\r\n".toByteArray(CHARSET))
        }
        out.write("$thinDivider\r\n".toByteArray(CHARSET))

        if (includeCodeDetails && record.items.isNotEmpty()) {
            writeEscPosCodeDetails(
                out = out,
                w = w,
                thinDivider = thinDivider,
                sectionTitle = "LISTADO DE CODIGOS",
                items = record.items
            )
        }

        val totalLines = mutableListOf("TOTAL DE MATERIALES: ${record.items.size} PZAS")
        if (includeBreakdown) {
            totalLines.add(breakdownLine(record.items, w))
        }
        writeEscPosInvertedBlock(out, w, totalLines)
        out.write("$divider\r\n\r\n".toByteArray(CHARSET))

        if (includeSignatures) {
            val sigLine = signatureLine(paperWidth)
            out.write("ENTREGO (DEVOLUCION):\r\n".toByteArray(CHARSET))
            out.write("$sigLine\r\n\r\n".toByteArray(CHARSET))
            out.write("RECIBIO (POLVORIN):\r\n".toByteArray(CHARSET))
            out.write("$sigLine\r\n".toByteArray(CHARSET))
            out.write("$divider\r\n\r\n\r\n".toByteArray(CHARSET))
        }

        out.write(ESC_FEED_6_LINES)
        writeEscPosBottomMargin(out)
        out.write("\r\n\r\n\r\n\r\n".toByteArray(CHARSET))
        return out.toByteArray()
    }

    fun generateDevolucionPlain(
        record: DevolucionRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)
        val sb = StringBuilder()

        sb.append("\r\n").append(stripAccents(mineName.ifEmpty { "ARIA" })).append("\r\n")
        if (permitNumber.isNotBlank()) sb.append("PERMISO GENERAL: ").append(stripAccents(permitNumber)).append("\r\n")
        if (headerNote.isNotBlank()) sb.append(stripAccents(headerNote)).append("\r\n")
        sb.append("VALE DE DEVOLUCION A POLVORIN\r\n").append(divider).append("\r\n")
        sb.append("ENTREGA: ").append(stripAccents(record.empleadoEntrega)).append("\r\n")
        sb.append("RECIBE: ").append(stripAccents(record.empleadoRecibe)).append("\r\n")
        val ts = record.items.firstOrNull()?.timestamp ?: DateTimeUtils.currentTimestamp()
        sb.append("FECHA: ").append(ts).append("\r\n").append(thinDivider).append("\r\n")

        val grouped = record.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }
        for ((desc, items) in grouped) {
            sb.append("- ").append(stripAccents(desc)).append("\r\n")
            sb.append("  CANTIDAD: ").append(items.size).append("\r\n")
        }
        sb.append(thinDivider).append("\r\n")

        if (includeCodeDetails && record.items.isNotEmpty()) {
            appendPlainCodeDetails(
                sb = sb,
                w = w,
                thinDivider = thinDivider,
                sectionTitle = "LISTADO DE CODIGOS",
                items = record.items
            )
        }

        sb.append("TOTAL DE MATERIALES: ").append(record.items.size).append(" PZAS\r\n")
        if (includeBreakdown) sb.append(breakdownLine(record.items, w)).append("\r\n")
        sb.append(divider).append("\r\n\r\n")

        if (includeSignatures) {
            val sigLine = signatureLine(paperWidth)
            sb.append("ENTREGO:\r\n").append(sigLine).append("\r\n\r\n")
            sb.append("RECIBIO:\r\n").append(sigLine).append("\r\n$divider\r\n\r\n")
        }
        appendPlainBottomMargin(sb)
        sb.append("\r\n\r\n\r\n\r\n\r\n\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateDevolucionCpcl(
        record: DevolucionRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_4_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        headerNote: String = "",
        includeSignatures: Boolean = true,
        includeBreakdown: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val totalItems = record.items.size
        val pageWidth = paperWidth.cpclDots
        val lineEnd = pageWidth - 20
        val lines = mutableListOf<String>()
        var y = 20

        lines.add("CENTER")
        val cleanMine = stripAccents(mineName.ifEmpty { "ARIA" })
        val mineFont = selectCpclFont(cleanMine, lineEnd - 20, preferLarge = true)
        lines.add("TEXT ${mineFont.fontNumber} ${mineFont.fontSize} 0 $y $cleanMine")
        y += mineFont.lineHeightDots
        if (permitNumber.isNotBlank()) {
            val pt = "PERMISO GENERAL: ${stripAccents(permitNumber)}"
            val pf = selectCpclFont(pt, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${pf.fontNumber} ${pf.fontSize} 0 $y $pt")
            y += pf.lineHeightDots
        }
        if (headerNote.isNotBlank()) {
            val nt = stripAccents(headerNote)
            val nf = selectCpclFont(nt, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${nf.fontNumber} ${nf.fontSize} 0 $y $nt")
            y += nf.lineHeightDots
        }
        val tf = selectCpclFont("VALE DE DEVOLUCION A POLVORIN", lineEnd - 20, preferLarge = false)
        lines.add("TEXT ${tf.fontNumber} ${tf.fontSize} 0 $y VALE DE DEVOLUCION A POLVORIN")
        y += tf.lineHeightDots + 2

        lines.add("LEFT")
        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 16
        lines.add("TEXT 7 0 10 $y ENTREGA: ${stripAccents(record.empleadoEntrega)}")
        y += 26
        lines.add("TEXT 7 0 10 $y RECIBE: ${stripAccents(record.empleadoRecibe)}")
        y += 26
        val ts = record.items.firstOrNull()?.timestamp ?: DateTimeUtils.currentTimestamp()
        lines.add("TEXT 7 0 10 $y FECHA: $ts")
        y += 26
        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 16

        lines.add("TEXT 5 0 10 $y MATERIALES DEVUELTOS:")
        y += 28
        val grouped = record.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }
        for ((desc, items) in grouped) {
            val cleanDesc = "- ${stripAccents(desc)}"
            val df = selectCpclFont(cleanDesc, lineEnd - 20, preferLarge = false)
            lines.add("TEXT ${df.fontNumber} ${df.fontSize} 10 $y $cleanDesc")
            y += df.lineHeightDots
            lines.add("TEXT 7 0 30 $y CANTIDAD: ${items.size}")
            y += 26
        }

        if (includeCodeDetails && record.items.isNotEmpty()) {
            y = addCpclCodeDetails(
                lines = lines,
                startY = y,
                lineEnd = lineEnd,
                paperWidth = paperWidth,
                sectionTitle = "LISTADO DE CODIGOS",
                items = record.items
            )
        }

        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 14
        val invLines = mutableListOf("TOTAL DE MATERIALES: $totalItems PIEZAS" to true)
        if (includeBreakdown) {
            invLines.add(breakdownLine(record.items, paperWidth.lineLength) to false)
        }
        y = addCpclInvertedBlock(lines, y, lineEnd, invLines)
        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 20

        if (includeSignatures) {
            lines.add("TEXT 7 0 10 $y ENTREGO:")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 50
            lines.add("TEXT 7 0 10 $y RECIBIO:")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 50
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 20
        }

        val totalHeight = y + 40 + BOTTOM_MARGIN_CPCL_DOTS
        val sb = StringBuilder()
        sb.append("! 0 200 200 $totalHeight 1\r\n")
        sb.append("PAGE-WIDTH $pageWidth\r\n")
        lines.forEach { sb.append(it).append("\r\n") }
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    // =========================================================================
    // 5. TICKETS DE PRUEBA (ESC/POS, PLAIN, CPCL)
    // =========================================================================

    fun generateTestTicket(
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = ""
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val thinDivider = "-".repeat(w)

        out.write(ESC_INIT)
        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write("${stripAccents(mineName.ifEmpty { "ARIA" })}\r\n".toByteArray(CHARSET))
        out.write("PRUEBA DE IMPRESION TERMICA POS\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        out.write("$divider\r\n".toByteArray(CHARSET))
        out.write(ESC_ALIGN_LEFT)
        out.write("ESTADO: Impresora conectada OK\r\n".toByteArray(CHARSET))
        out.write("ANCHO: ${paperWidth.displayLabel}\r\n".toByteArray(CHARSET))
        out.write("FORMATO: ESC/POS Termico Estandar\r\n".toByteArray(CHARSET))
        if (permitNumber.isNotBlank()) {
            out.write("PERMISO: ${stripAccents(permitNumber)}\r\n".toByteArray(CHARSET))
        }
        out.write("FECHA/HORA: ${DateTimeUtils.currentTimestamp()}\r\n".toByteArray(CHARSET))
        out.write("$thinDivider\r\n".toByteArray(CHARSET))
        writeEscPosInvertedBlock(
            out,
            w,
            listOf(
                "TOTAL DE MATERIALES: PRUEBA OK",
                "BLOQUE NEGRO / LETRAS BLANCAS"
            )
        )
        out.write("$thinDivider\r\n".toByteArray(CHARSET))
        out.write("COMUNICACION BLUETOOTH OK\r\n".toByteArray(CHARSET))
        out.write("T&T EXPLO MOBILE - Control en Sitio\r\n".toByteArray(CHARSET))
        out.write("$divider\r\n".toByteArray(CHARSET))
        out.write(ESC_FEED_6_LINES)
        writeEscPosBottomMargin(out)
        out.write("\r\n\r\n\r\n\r\n".toByteArray(CHARSET))
        return out.toByteArray()
    }

    fun generatePlainTestTicket(
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA"
    ): ByteArray {
        val w = paperWidth.lineLength
        val divider = "=".repeat(w)
        val sb = StringBuilder()
        sb.append("\r\n")
        sb.append("${stripAccents(mineName.ifEmpty { "ARIA" })}\r\n")
        sb.append("PRUEBA MODO TEXTO PLANO (RAW)\r\n")
        sb.append(divider).append("\r\n")
        sb.append("ESTADO: Impresora conectada OK\r\n")
        sb.append("ANCHO: ${paperWidth.displayLabel}\r\n")
        sb.append("FORMATO: Texto Plano Universal (Sin comandos ESC)\r\n")
        sb.append("FECHA/HORA: ").append(DateTimeUtils.currentTimestamp()).append("\r\n")
        sb.append("Si puede leer esto, su impresora funciona en modo RAW.\r\n")
        sb.append(divider).append("\r\n")
        appendPlainBottomMargin(sb)
        sb.append("\r\n\r\n\r\n\r\n\r\n\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateCpclTestTicket(
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA"
    ): ByteArray {
        val pageWidth = paperWidth.cpclDots
        val lineEnd = pageWidth - 20
        val sb = StringBuilder()
        var y = 20

        val lines = mutableListOf<String>()
        lines.add("CENTER")
        val cleanMine = stripAccents(mineName.ifEmpty { "ARIA" })
        val mf = selectCpclFont(cleanMine, lineEnd - 20, preferLarge = true)
        lines.add("TEXT ${mf.fontNumber} ${mf.fontSize} 0 $y $cleanMine")
        y += mf.lineHeightDots + 4
        val tf = selectCpclFont("PRUEBA DE IMPRESION ZEBRA CPCL", lineEnd - 20, preferLarge = false)
        lines.add("TEXT ${tf.fontNumber} ${tf.fontSize} 0 $y PRUEBA DE IMPRESION ZEBRA CPCL")
        y += tf.lineHeightDots + 4
        lines.add("LEFT")
        lines.add("LINE 10 $y $lineEnd $y 3")
        y += 20
        lines.add("TEXT 7 0 10 $y ESTADO: CONEXION BLUETOOTH OK")
        y += 30
        lines.add("TEXT 7 0 10 $y PROTOCOLO: ZEBRA CPCL INDUSTRIAL")
        y += 30
        lines.add("TEXT 7 0 10 $y ANCHO: ${paperWidth.displayLabel}")
        y += 30
        lines.add("TEXT 7 0 10 $y FECHA/HORA: ${DateTimeUtils.currentTimestamp()}")
        y += 30
        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 14
        y = addCpclInvertedBlock(
            lines = lines,
            startY = y,
            lineEnd = lineEnd,
            blockLines = listOf(
                "TOTAL DE MATERIALES: PRUEBA OK" to true,
                "BLOQUE NEGRO / LETRAS BLANCAS" to false
            )
        )
        lines.add("TEXT 7 0 10 $y COMUNICACION INSTANTANEA OK")
        y += 30
        lines.add("TEXT 7 0 10 $y T&T EXPLO MOBILE - Control en Sitio")
        y += 35
        lines.add("LINE 10 $y $lineEnd $y 3")
        y += 40

        val totalHeight = y + 20 + BOTTOM_MARGIN_CPCL_DOTS
        sb.append("! 0 200 200 $totalHeight 1\r\n")
        sb.append("PAGE-WIDTH $pageWidth\r\n")
        for (line in lines) {
            sb.append(line).append("\r\n")
        }
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(CHARSET)
    }
}
