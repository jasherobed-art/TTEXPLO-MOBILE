package com.ttexplo.mobile.printer

import com.ttexplo.mobile.domain.model.AccesoriosRecord
import com.ttexplo.mobile.domain.model.DespachoRecord
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import java.io.ByteArrayOutputStream

enum class PaperWidth(val lineLength: Int) {
    WIDTH_3_INCH(42), // 80mm
    WIDTH_4_INCH(56)  // 104mm (Zebra standard)
}

object TicketGenerator {

    private val CHARSET = Charsets.US_ASCII

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)

    private val ESC_DOUBLE_SIZE = byteArrayOf(0x1B, 0x21, 0x30)
    private val ESC_NORMAL_SIZE = byteArrayOf(0x1B, 0x21, 0x00)
    private val ESC_FEED_6_LINES = byteArrayOf(0x1B, 0x64, 0x06)

    fun stripAccents(text: String): String {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

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
            out.write(ESC_BOLD_ON)
            out.write("LISTADO DE CODIGOS ESCANEADOS (${record.items.size}):\r\n".toByteArray(CHARSET))
            out.write(ESC_BOLD_OFF)
            out.write("$thinDivider\r\n".toByteArray(CHARSET))
            record.items.forEachIndexed { index, item ->
                val num = String.format("%02d", index + 1)
                val timeStr = if (item.timestamp.isNotBlank()) item.timestamp else ""
                out.write("[$num] ${item.code}".toByteArray(CHARSET))
                if (timeStr.isNotBlank()) {
                    val pad = " ".repeat((w - num.length - item.code.length - timeStr.length - 4).coerceAtLeast(1))
                    out.write("$pad$timeStr\r\n".toByteArray(CHARSET))
                } else {
                    out.write("\r\n".toByteArray(CHARSET))
                }
                val desc = buildString {
                    if (item.productName.isNotBlank()) append(stripAccents(item.productName))
                    if (item.presentation.isNotBlank()) append(" (${stripAccents(item.presentation)})")
                }
                if (desc.isNotBlank()) {
                    out.write("     $desc\r\n".toByteArray(CHARSET))
                }
            }
            out.write("$thinDivider\r\n".toByteArray(CHARSET))
        }

        val cajas = record.items.count { it.code.length == 30 }
        val sacos = record.items.count { it.code.length == 18 }
        val cartuchos = record.items.count { it.code.length == 16 || it.code.length == 17 }

        out.write(ESC_BOLD_ON)
        out.write("TOTAL DE PIEZAS / BULTOS: ${record.items.size}\r\n".toByteArray(CHARSET))
        if (includeBreakdown) {
            out.write("CAJAS: $cajas  SACOS: $sacos  CARTUCHOS: $cartuchos\r\n".toByteArray(CHARSET))
        }
        out.write(ESC_BOLD_OFF)
        out.write("$divider\r\n\r\n".toByteArray(CHARSET))

        if (includeSignatures) {
            out.write("ENTREGO:\r\n\r\n\r\n".toByteArray(CHARSET))
            out.write("___________________________\r\n".toByteArray(CHARSET))
            out.write("Firma Responsable Polvorin\r\n\r\n\r\n".toByteArray(CHARSET))

            out.write("RECIBIO:\r\n\r\n\r\n".toByteArray(CHARSET))
            out.write("___________________________\r\n".toByteArray(CHARSET))
            out.write("${stripAccents(record.transportista)}\r\n".toByteArray(CHARSET))
            out.write("$divider\r\n\r\n\r\n".toByteArray(CHARSET))
        }

        out.write(ESC_FEED_6_LINES)
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
            sb.append("LISTADO DE CODIGOS ESCANEADOS (${record.items.size}):\r\n")
            sb.append(thinDivider).append("\r\n")
            record.items.forEachIndexed { index, item ->
                val num = String.format("%02d", index + 1)
                val timeStr = if (item.timestamp.isNotBlank()) item.timestamp else ""
                sb.append("[$num] ${item.code}")
                if (timeStr.isNotBlank()) {
                    val pad = " ".repeat((w - num.length - item.code.length - timeStr.length - 4).coerceAtLeast(1))
                    sb.append(pad).append(timeStr).append("\r\n")
                } else {
                    sb.append("\r\n")
                }
                val desc = buildString {
                    if (item.productName.isNotBlank()) append(stripAccents(item.productName))
                    if (item.presentation.isNotBlank()) append(" (${stripAccents(item.presentation)})")
                }
                if (desc.isNotBlank()) {
                    sb.append("     ").append(desc).append("\r\n")
                }
            }
            sb.append(thinDivider).append("\r\n")
        }

        val cajas = record.items.count { it.code.length == 30 }
        val sacos = record.items.count { it.code.length == 18 }
        val cartuchos = record.items.count { it.code.length == 16 || it.code.length == 17 }

        sb.append("TOTAL DE PIEZAS / BULTOS: ").append(record.items.size).append("\r\n")
        if (includeBreakdown) {
            sb.append("CAJAS: ").append(cajas).append("  SACOS: ").append(sacos).append("  CARTUCHOS: ").append(cartuchos).append("\r\n")
        }
        sb.append(divider).append("\r\n\r\n")

        if (includeSignatures) {
            sb.append("ENTREGO:\r\n\r\n\r\n")
            sb.append("___________________________\r\n")
            sb.append("Firma Responsable Polvorin\r\n\r\n\r\n")

            sb.append("RECIBIO:\r\n\r\n\r\n")
            sb.append("___________________________\r\n")
            sb.append(stripAccents(record.transportista)).append("\r\n")
            sb.append(divider).append("\r\n\r\n")
        }

        sb.append("\r\n\r\n\r\n\r\n\r\n\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateDespachoTicket(
        despacho: DespachoRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH
    ): ByteArray = generateDespachoEscPos(despacho, paperWidth)

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
        out.write("ANCHO: ${if (paperWidth == PaperWidth.WIDTH_4_INCH) "4 Pulgadas (104mm)" else "3 Pulgadas (80mm)"}\r\n".toByteArray(CHARSET))
        out.write("FORMATO: ESC/POS Termico Estandar\r\n".toByteArray(CHARSET))
        if (permitNumber.isNotBlank()) {
            out.write("PERMISO: ${stripAccents(permitNumber)}\r\n".toByteArray(CHARSET))
        }
        out.write("FECHA/HORA: ${DateTimeUtils.currentTimestamp()}\r\n".toByteArray(CHARSET))
        out.write("$thinDivider\r\n".toByteArray(CHARSET))
        out.write("COMUNICACION BLUETOOTH OK\r\n".toByteArray(CHARSET))
        out.write("T&T EXPLO MOBILE - Control en Sitio\r\n".toByteArray(CHARSET))
        out.write("$divider\r\n".toByteArray(CHARSET))
        out.write(ESC_FEED_6_LINES)
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
        sb.append("FORMATO: Texto Plano Universal (Sin comandos ESC)\r\n")
        sb.append("FECHA/HORA: ").append(DateTimeUtils.currentTimestamp()).append("\r\n")
        sb.append("Si puede leer esto, su impresora funciona en modo RAW.\r\n")
        sb.append(divider).append("\r\n")
        sb.append("\r\n\r\n\r\n\r\n\r\n\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateCpclTestTicket(
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA"
    ): ByteArray {
        val pageWidth = if (paperWidth == PaperWidth.WIDTH_4_INCH) 832 else 576
        val lineEnd = pageWidth - 20
        val sb = StringBuilder()
        var y = 20

        val lines = mutableListOf<String>()
        lines.add("CENTER")
        lines.add("TEXT 4 0 0 $y ${stripAccents(mineName.ifEmpty { "ARIA" })}")
        y += 48
        lines.add("TEXT 5 0 0 $y PRUEBA DE IMPRESION ZEBRA CPCL")
        y += 38
        lines.add("LEFT")
        lines.add("LINE 10 $y $lineEnd $y 3")
        y += 20
        lines.add("TEXT 7 0 10 $y ESTADO: CONEXION BLUETOOTH OK")
        y += 30
        lines.add("TEXT 7 0 10 $y PROTOCOLO: ZEBRA CPCL INDUSTRIAL")
        y += 30
        lines.add("TEXT 7 0 10 $y ANCHO DE PAPEL: ${if (paperWidth == PaperWidth.WIDTH_4_INCH) "4 Pulgadas (104mm)" else "3 Pulgadas (80mm)"}")
        y += 30
        lines.add("TEXT 7 0 10 $y FECHA/HORA: ${DateTimeUtils.currentTimestamp()}")
        y += 30
        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 20
        lines.add("TEXT 7 0 10 $y COMUNICACION INSTANTANEA OK")
        y += 30
        lines.add("TEXT 7 0 10 $y T&T EXPLO MOBILE - Control en Sitio")
        y += 35
        lines.add("LINE 10 $y $lineEnd $y 3")
        y += 40

        val totalHeight = y + 20
        sb.append("! 0 200 200 $totalHeight 1\r\n")
        sb.append("PAGE-WIDTH $pageWidth\r\n")
        for (line in lines) {
            sb.append(line).append("\r\n")
        }
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateAccesoriosEscPos(
        record: AccesoriosRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        includeSignatures: Boolean = true
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
        out.write("$thinDivider\r\n".toByteArray(CHARSET))

        out.write(ESC_BOLD_ON)
        val sectionLabel = if (isDev) "ACCESORIOS DEVUELTOS:\r\n" else "ACCESORIOS DESPACHADOS:\r\n"
        out.write(sectionLabel.toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)

        var totalPieces = 0
        for (item in record.items) {
            out.write("- ${item.code}   CANT: ${item.quantity}\r\n".toByteArray(CHARSET))
            totalPieces += item.quantity
        }

        out.write("$thinDivider\r\n".toByteArray(CHARSET))
        out.write(ESC_BOLD_ON)
        val totalLabel = if (isDev) "TOTAL DEVUELTO: $totalPieces PZAS\r\n" else "TOTAL ACCESORIOS: $totalPieces PZAS\r\n"
        out.write(totalLabel.toByteArray(CHARSET))
        out.write(ESC_BOLD_OFF)
        out.write("$divider\r\n\r\n".toByteArray(CHARSET))

        if (includeSignatures) {
            out.write("ENTREGO:\r\n\r\n\r\n".toByteArray(CHARSET))
            out.write("___________________________\r\n".toByteArray(CHARSET))
            out.write("${stripAccents(record.entrego)}\r\n\r\n\r\n".toByteArray(CHARSET))

            out.write("RECIBIO:\r\n\r\n\r\n".toByteArray(CHARSET))
            out.write("___________________________\r\n".toByteArray(CHARSET))
            out.write("${stripAccents(record.recibio)}\r\n".toByteArray(CHARSET))
            out.write("$divider\r\n\r\n\r\n".toByteArray(CHARSET))
        }

        out.write(ESC_FEED_6_LINES)
        out.write("\r\n\r\n\r\n\r\n".toByteArray(CHARSET))
        return out.toByteArray()
    }

    fun generateAccesoriosPlain(
        record: AccesoriosRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        includeSignatures: Boolean = true
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
        sb.append(thinDivider).append("\r\n")

        val sectionLabel = if (isDev) "ACCESORIOS DEVUELTOS:\r\n" else "ACCESORIOS DESPACHADOS:\r\n"
        sb.append(sectionLabel)
        var totalPieces = 0
        for (item in record.items) {
            sb.append("- ").append(item.code).append("   CANT: ").append(item.quantity).append("\r\n")
            totalPieces += item.quantity
        }

        sb.append(thinDivider).append("\r\n")
        val totalLabel = if (isDev) "TOTAL DEVUELTO: $totalPieces PZAS\r\n" else "TOTAL ACCESORIOS: $totalPieces PZAS\r\n"
        sb.append(totalLabel)
        sb.append(divider).append("\r\n\r\n")

        if (includeSignatures) {
            sb.append("ENTREGO:\r\n\r\n\r\n")
            sb.append("___________________________\r\n")
            sb.append(stripAccents(record.entrego)).append("\r\n\r\n\r\n")

            sb.append("RECIBIO:\r\n\r\n\r\n")
            sb.append("___________________________\r\n")
            sb.append(stripAccents(record.recibio)).append("\r\n")
            sb.append(divider).append("\r\n\r\n")
        }

        sb.append("\r\n\r\n\r\n\r\n\r\n\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateAccesoriosTicket(
        accesorios: AccesoriosRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_3_INCH
    ): ByteArray = generateAccesoriosEscPos(accesorios, paperWidth)

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
        val cajas = despacho.items.count { it.code.length == 30 }
        val sacos = despacho.items.count { it.code.length == 18 }
        val cartuchos = despacho.items.count { it.code.length == 16 || it.code.length == 17 }

        val grouped = despacho.items.groupBy {
            val name = it.productName.ifEmpty { "Material ID ${it.code.take(3)}" }
            val pres = it.presentation.ifEmpty { "Pieza" }
            "$name ($pres)"
        }

        val pageWidth = if (paperWidth == PaperWidth.WIDTH_4_INCH) 832 else 576
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
        lines.add("TEXT 5 0 0 $y VALE DE DESPACHO")
        y += 34

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
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 16
            lines.add("TEXT 5 0 10 $y LISTADO DE CODIGOS ESCANEADOS ($totalItems):")
            y += 30
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 14

            despacho.items.forEachIndexed { index, item ->
                val num = String.format("%02d", index + 1)
                val timeStr = if (item.timestamp.isNotBlank()) item.timestamp else "S/H"

                if (paperWidth == PaperWidth.WIDTH_4_INCH) {
                    lines.add("TEXT 7 0 10 $y [$num] ${item.code}")
                    lines.add("TEXT 7 0 570 $y $timeStr")
                    y += 24

                    val desc = buildString {
                        if (item.productName.isNotBlank()) append(stripAccents(item.productName))
                        if (item.presentation.isNotBlank()) append(" (${stripAccents(item.presentation)})")
                    }
                    if (desc.isNotBlank()) {
                        val descFont = selectCpclFont(desc, lineEnd - 45, preferLarge = false)
                        lines.add("TEXT ${descFont.fontNumber} ${descFont.fontSize} 45 $y $desc")
                        y += descFont.lineHeightDots + 4
                    } else {
                        y += 6
                    }
                } else {
                    lines.add("TEXT 7 0 10 $y [$num] ${item.code}")
                    y += 24
                    val desc = buildString {
                        if (item.productName.isNotBlank()) append(stripAccents(item.productName))
                        if (item.presentation.isNotBlank()) append(" (${stripAccents(item.presentation)})")
                    }
                    val line2 = if (desc.isNotBlank()) "$desc - $timeStr" else "Hora: $timeStr"
                    val descFont = selectCpclFont(line2, lineEnd - 35, preferLarge = false)
                    lines.add("TEXT ${descFont.fontNumber} ${descFont.fontSize} 35 $y $line2")
                    y += descFont.lineHeightDots + 4
                }
            }
        }

        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 16

        lines.add("TEXT 5 0 10 $y TOTAL MATERIALES: $totalItems PIEZAS")
        y += 28

        if (includeBreakdown) {
            lines.add("TEXT 7 0 10 $y CAJAS: $cajas  SACOS: $sacos  CARTUCHOS: $cartuchos")
            y += 28
        }

        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 20

        if (includeSignatures) {
            lines.add("TEXT 7 0 10 $y ENTREGO:")
            y += 50
            lines.add("LINE 10 $y 350 $y 1")
            y += 15
            lines.add("TEXT 7 0 10 $y Firma Responsable Polvorin")
            y += 35

            lines.add("TEXT 7 0 10 $y RECIBIO:")
            y += 50
            lines.add("LINE 10 $y 350 $y 1")
            y += 15
            lines.add("TEXT 7 0 10 $y ${stripAccents(despacho.transportista)}")
            y += 30
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 20
        }

        val totalHeight = y + 40
        val sb = StringBuilder()
        sb.append("! 0 200 200 $totalHeight 1\r\n")
        sb.append("PAGE-WIDTH $pageWidth\r\n")
        for (line in lines) {
            sb.append(line).append("\r\n")
        }
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(CHARSET)
    }

    fun generateAccesoriosCpcl(
        accesorios: AccesoriosRecord,
        paperWidth: PaperWidth = PaperWidth.WIDTH_4_INCH,
        mineName: String = "ARIA",
        permitNumber: String = "",
        includeSignatures: Boolean = true,
        includeCodeDetails: Boolean = true
    ): ByteArray {
        val totalPieces = accesorios.items.sumOf { it.quantity }
        val pageWidth = if (paperWidth == PaperWidth.WIDTH_4_INCH) 832 else 576
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
        lines.add("TEXT 5 0 0 $y $titleText")
        y += 34

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

        lines.add("LINE 10 $y $lineEnd $y 1")
        y += 16

        if (includeCodeDetails && accesorios.items.isNotEmpty()) {
            val sectionText = if (isDev) "ACCESORIOS DEVUELTOS:" else "ACCESORIOS DESPACHADOS:"
            lines.add("TEXT 5 0 10 $y $sectionText")
            y += 28

            for (item in accesorios.items) {
                lines.add("TEXT 7 0 10 $y - ${item.code}   CANT: ${item.quantity}")
                if (item.timestamp.isNotBlank() && paperWidth == PaperWidth.WIDTH_4_INCH) {
                    lines.add("TEXT 7 0 570 $y ${item.timestamp}")
                }
                y += 26
            }

            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 16
        }

        val totalLabel = if (isDev) "TOTAL DEVUELTO: $totalPieces PZAS" else "TOTAL ACCESORIOS: $totalPieces PZAS"
        lines.add("TEXT 5 0 10 $y $totalLabel")
        y += 28

        lines.add("LINE 10 $y $lineEnd $y 2")
        y += 20

        if (includeSignatures) {
            lines.add("TEXT 7 0 10 $y ENTREGO:")
            y += 50
            lines.add("LINE 10 $y 350 $y 1")
            y += 15
            lines.add("TEXT 7 0 10 $y ${stripAccents(accesorios.entrego)}")
            y += 35

            lines.add("TEXT 7 0 10 $y RECIBIO:")
            y += 50
            lines.add("LINE 10 $y 350 $y 1")
            y += 15
            lines.add("TEXT 7 0 10 $y ${stripAccents(accesorios.recibio)}")
            y += 30
            lines.add("LINE 10 $y $lineEnd $y 1")
            y += 20
        }

        val totalHeight = y + 40
        val sb = StringBuilder()
        sb.append("! 0 200 200 $totalHeight 1\r\n")
        sb.append("PAGE-WIDTH $pageWidth\r\n")
        for (line in lines) {
            sb.append(line).append("\r\n")
        }
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(CHARSET)
    }
}
