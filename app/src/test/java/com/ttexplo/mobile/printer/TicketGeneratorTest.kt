package com.ttexplo.mobile.printer

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.domain.model.AccesoriosRecord
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.model.DespachoRecord
import com.ttexplo.mobile.domain.model.DevolucionRecord
import com.ttexplo.mobile.domain.model.ScannedCodeRecord
import org.junit.Test
import java.nio.charset.Charset

class TicketGeneratorTest {

    private val charset = Charset.forName("CP437")

    private val despacho = DespachoRecord(
        vale = "7712",
        empresa = "MPRG",
        lugar = "576-S/RMP/576/PIBOTEO",
        transportista = "GILBERTO MENDOZA AGUIRRE",
        turno = "3",
        voladura = "1",
        items = listOf(
            ScannedCodeRecord("272120826024843282", "2026-10-07 04:41:01", "AUSTINITE 15 CA-1", "SACO")
        )
    )

    private val accesorios = AccesoriosRecord(
        vale = "7712",
        empresa = "MPRG",
        lugar = "576-S/RMP/576/PIBOTEO",
        transportista = "GILBERTO MENDOZA AGUIRRE",
        turno = "3",
        recibio = "MPRG",
        entrego = "GILBERTO MENDOZA AGUIRRE",
        voladura = "1",
        items = listOf(AccessoryItemRecord("X100182000000009", 2, "2026-10-07 06:21:57"))
    )

    private val devolucion = DevolucionRecord(
        empleadoEntrega = "GILBERTO MENDOZA AGUIRRE",
        empleadoRecibe = "MPRG",
        items = listOf(
            ScannedCodeRecord("272120826024843282", "2026-10-07 04:41:01", "AUSTINITE 15 CA-1", "SACO")
        )
    )

    @Test
    fun signatureLineEscPosMatches58MmWidth() {
        val text = String(TicketGenerator.generateDespachoEscPos(
            record = despacho, paperWidth = PaperWidth.WIDTH_2_INCH
        ), charset)
        assertThat(text).contains("ENTREGO:\r\n${"_".repeat(28)}\r\n")
        assertThat(text).contains("RECIBIO:\r\n${"_".repeat(28)}\r\n")
    }

    @Test
    fun signatureLineGrowsWithPaperWidth() {
        val wide = String(TicketGenerator.generateDespachoPlain(
            record = despacho, paperWidth = PaperWidth.WIDTH_4_INCH
        ), charset)
        assertThat(wide).contains("ENTREGO:\r\n${"_".repeat(52)}\r\n")
        assertThat(wide).contains("RECIBIO:\r\n${"_".repeat(52)}\r\n")
    }

    @Test
    fun headerKeepsDataWhenSignatureNamesRemoved() {
        val text = String(TicketGenerator.generateAccesoriosEscPos(
            accesorios, PaperWidth.WIDTH_3_INCH
        ), charset)
        assertThat(text).contains("EMPRESA: MPRG")
        assertThat(text).doesNotContain("Firma Responsable")
        assertThat(text).doesNotContain("GILBERTO MENDOZA AGUIRRE\r\n\r\n")
    }

    @Test
    fun codeListHeaderUsesAsciiCodigos() {
        val esc = String(TicketGenerator.generateDespachoEscPos(
            record = despacho, paperWidth = PaperWidth.WIDTH_3_INCH
        ), charset)
        assertThat(esc).contains("LISTADO DE CODIGOS (1):")
        assertThat(esc).doesNotContain("C?DIGOS")

        val cpcl = String(TicketGenerator.generateDespachoCpcl(
            despacho = despacho, paperWidth = PaperWidth.WIDTH_3_INCH
        ), charset)
        assertThat(cpcl).contains("LISTADO DE CODIGOS (1):")
        assertThat(cpcl).doesNotContain("C?DIGOS")
    }

    @Test
    fun cpclSignatureLineUsesFullPaperWidth() {
        val cpcl = String(TicketGenerator.generateDespachoCpcl(
            despacho = despacho, paperWidth = PaperWidth.WIDTH_4_INCH
        ), charset)
        assertThat(Regex("LINE 10 \\d+ 812 \\d+ 1").containsMatchIn(cpcl)).isTrue()
        assertThat(cpcl).contains("TRANSPORTE: GILBERTO MENDOZA")
        assertThat(cpcl.indexOf("GILBERTO MENDOZA")).isEqualTo(cpcl.lastIndexOf("GILBERTO MENDOZA"))
        assertThat(cpcl).doesNotContain("Firma Responsable Polvorin")
    }

    @Test
    fun signaturesDisabledStillSkipsSignatureBlock() {
        val text = String(TicketGenerator.generateDevolucionPlain(
            record = devolucion, paperWidth = PaperWidth.WIDTH_3_INCH,
            includeSignatures = false
        ), charset)
        assertThat(text).doesNotContain("ENTREGO:")
        assertThat(text).doesNotContain("RECIBIO:")
    }

    @Test
    fun ticketShowsTerminalIdWhenProvided() {
        val plain = String(TicketGenerator.generateDespachoPlain(
            record = despacho, paperWidth = PaperWidth.WIDTH_3_INCH, terminalId = "ZBR-POLV-01"
        ), charset)
        val esc = String(TicketGenerator.generateDespachoEscPos(
            record = despacho, paperWidth = PaperWidth.WIDTH_3_INCH, terminalId = "ZBR-POLV-01"
        ), charset)
        val cpcl = String(TicketGenerator.generateDespachoCpcl(
            despacho = despacho, paperWidth = PaperWidth.WIDTH_3_INCH, terminalId = "ZBR-POLV-01"
        ), charset)
        assertThat(plain).contains("TERMINAL: ZBR-POLV-01")
        assertThat(esc).contains("TERMINAL: ZBR-POLV-01")
        assertThat(cpcl).contains("TERMINAL: ZBR-POLV-01")
    }

    @Test
    fun ticketOmitsTerminalLineWhenBlank() {
        val text = String(TicketGenerator.generateDespachoPlain(
            record = despacho, paperWidth = PaperWidth.WIDTH_3_INCH
        ), charset)
        assertThat(text).doesNotContain("TERMINAL:")
    }

    @Test
    fun testGenerateDespachoTicket_withCustomMineAndPermit() {
        val despacho = DespachoRecord(
            vale = "7712",
            empresa = "MPRG",
            lugar = "576-S/RMP/576/PIBOTEO",
            transportista = "GILBERTO MENDOZA AGUIRRE",
            turno = "3",
            voladura = "1",
            items = listOf(
                ScannedCodeRecord("272120826024843282", "2026-10-07 04:41:01", "AUSTINITE 15 CA-1", "SACO"),
                ScannedCodeRecord("272120826024842743", "2026-10-07 04:41:06", "AUSTINITE 15 CA-1", "SACO"),
                ScannedCodeRecord("337130626009586009641005228514", "2026-10-07 06:20:07", "EMULEX C", "1\" X 39.3\"")
            )
        )

        val bytes = TicketGenerator.generateDespachoEscPos(
            record = despacho,
            paperWidth = PaperWidth.WIDTH_3_INCH,
            mineName = "MINA T&T EXPLÓ",
            permitNumber = "SEDENA-PG-9988",
            headerNote = "POLVORÍN SUR"
        )
        assertThat(bytes.size).isGreaterThan(50)

        // Check init command 0x1B, 0x40
        assertThat(bytes[0]).isEqualTo(0x1B.toByte())
        assertThat(bytes[1]).isEqualTo(0x40.toByte())

        val text = String(bytes, charset)
        assertThat(text).contains("MINA T&T EXPLO")
        assertThat(text).contains("PERMISO GENERAL: SEDENA-PG-9988")
        assertThat(text).contains("POLVORIN SUR")
        assertThat(text).contains("VALE DE DESPACHO")
        assertThat(text).contains("REF7712")
        assertThat(text).contains("MPRG")
        assertThat(text).contains("GILBERTO MENDOZA")
        assertThat(text).contains("AUSTINITE 15 CA-1")
        assertThat(text).contains("CANTIDAD: 2")
        assertThat(text).contains("EMULEX C")
        assertThat(text).contains("CANTIDAD: 1")
        assertThat(text).contains("TOTAL DE MATERIALES: 3 PZAS")
        assertThat(text).contains("ENTREGO:")
        assertThat(text).contains("RECIBIO:")
    }

    @Test
    fun testGenerateAccesoriosTicket_containsItemsAndTotal() {
        val accesorios = AccesoriosRecord(
            vale = "7712",
            empresa = "MPRG",
            lugar = "576-S/RMP/576/PIBOTEO",
            transportista = "GILBERTO MENDOZA AGUIRRE",
            turno = "3",
            recibio = "MPRG",
            entrego = "GILBERTO MENDOZA AGUIRRE",
            voladura = "1",
            items = listOf(
                AccessoryItemRecord("X100182000000009", 2, "2026-10-07 06:21:57"),
                AccessoryItemRecord("X100183000000010", 5, "2026-10-07 06:22:00")
            )
        )

        val bytes = TicketGenerator.generateAccesoriosEscPos(accesorios, PaperWidth.WIDTH_3_INCH)
        val text = String(bytes, charset)

        assertThat(text).contains("ACC7712")
        assertThat(text).contains("X100182000000009   CANT: 2")
        assertThat(text).contains("X100183000000010   CANT: 5")
        assertThat(text).contains("TOTAL DE MATERIALES: 7 PZAS")
        assertThat(text).contains("ENTREGO:")
        assertThat(text).contains("RECIBIO:")
    }

    @Test
    fun testGenerateDespachoCpcl_withAutoShrinkAndScannedCodesList() {
        val despacho = DespachoRecord(
            vale = "4",
            empresa = "MINERA T&T S.A. DE C.V.",
            lugar = "POLVORIN CENTRAL",
            transportista = "GILBERTO MENDOZA",
            turno = "1",
            voladura = "3",
            items = listOf(
                ScannedCodeRecord(
                    code = "272120826024843282112233445566",
                    timestamp = "2026-11-17 00:49:13",
                    productName = "EMULEX 1",
                    presentation = "2.54 CM X 20.32 CM (1\" X 8\")"
                ),
                ScannedCodeRecord(
                    code = "337130626009586009641005228514",
                    timestamp = "2026-11-17 00:49:15",
                    productName = "SUPER MEXAMON D",
                    presentation = "Pieza"
                )
            )
        )

        val bytes = TicketGenerator.generateDespachoCpcl(
            despacho = despacho,
            paperWidth = PaperWidth.WIDTH_4_INCH,
            mineName = "MINERA T&T S.A. DE C.V.",
            permitNumber = "SEDENA NO. 1234",
            headerNote = "POLVORIN SUR",
            includeCodeDetails = true
        )

        val cpclText = String(bytes, charset)
        assertThat(cpclText).startsWith("! 0 200 200")
        assertThat(cpclText).contains("PAGE-WIDTH 832")
        assertThat(cpclText).contains("MINERA T&T S.A. DE C.V.")
        assertThat(cpclText).contains("PERMISO GENERAL: SEDENA NO. 1234")
        assertThat(cpclText).contains("VALE DE DESPACHO")
        assertThat(cpclText).contains("LISTADO DE CODIGOS (2):")
        assertThat(cpclText).contains("272120826024843282112233445566")
        assertThat(cpclText).contains("2026-11-17 00:49:13")
        assertThat(cpclText).contains("337130626009586009641005228514")
        assertThat(cpclText).contains("2026-11-17 00:49:15")
        assertThat(cpclText).contains("PRINT")

        // Verify non-overlapping Y values in CPCL
        val lines = cpclText.lines()
        val textLines = lines.filter { it.startsWith("TEXT ") }
        val yValues = textLines.map {
            val parts = it.split(" ")
            parts[4].toInt()
        }
        for (i in 0 until yValues.size - 1) {
            assertThat(yValues[i + 1]).isAtLeast(yValues[i])
        }
    }

    @Test
    fun testSelectCpclFont_autoShrinksLongText() {
        // Short text in 4" width gets prominent font 4
        val fontShort = TicketGenerator.selectCpclFont("ARIA", 800, preferLarge = true)
        assertThat(fontShort.fontNumber).isEqualTo(4)

        // Longer text shrinks to font 5 or 7
        val fontMedium = TicketGenerator.selectCpclFont("MINERA Y EXPLOSIVOS DE MEXICO S.A. DE C.V.", 300, preferLarge = true)
        assertThat(fontMedium.fontNumber).isNotEqualTo(4)

        // Very long text shrinks to compact font 0
        val fontLong = TicketGenerator.selectCpclFont("UN TEXTO EXTREMADAMENTE LARGO QUE EXCEDE CIEN CARACTERES PARA PROBAR EL AUTO SHRINK DE IMPRESION TERMICA", 400)
        assertThat(fontLong.fontNumber).isEqualTo(0)
    }
}
