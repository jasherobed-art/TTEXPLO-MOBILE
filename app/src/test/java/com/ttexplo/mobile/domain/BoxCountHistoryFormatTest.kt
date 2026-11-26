package com.ttexplo.mobile.domain

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.domain.inventory.BoxCountFolio
import com.ttexplo.mobile.domain.inventory.BoxCountItem
import com.ttexplo.mobile.domain.inventory.BoxCountTxtExporter
import com.ttexplo.mobile.domain.inventory.BoxCountVale
import com.ttexplo.mobile.printer.PaperWidth
import com.ttexplo.mobile.printer.TicketGenerator
import org.junit.Test

class BoxCountHistoryFormatTest {

    private val vale = BoxCountVale(
        folio = "CC-20261125-001",
        items = listOf(
            BoxCountItem(
                code = "310071026008799008854005399631",
                descripcion = "EMULEX 1 (2.54 CM X 20.32 CM)",
                kp = "2",
                piezas = 54,
                fechaHora = "2026-11-25 18:24:14"
            )
        )
    )

    @Test
    fun folioUsesDateAndThreeDigitConsecutive() {
        assertThat(BoxCountFolio.build("20261125", 7)).isEqualTo("CC-20261125-007")
    }

    @Test
    fun consolidatedUsesCajHeaderAndEditableValues() {
        assertThat(BoxCountTxtExporter.renderItems(vale.items))
            .isEqualTo("CAJ\n310071026008799008854005399631,2,54,2026-11-25 18:24:14\n\n")
    }

    @Test
    fun escPosTicketHasHeaderFolioDescriptionAndTotalWithoutSignatures() {
        val text = TicketGenerator.generateBoxCountEscPos(vale).toString(Charsets.US_ASCII)
        assertThat(text).contains("CONTEO EN CAJA")
        assertThat(text).contains("CC-20261125-001")
        assertThat(text).contains("310071026008799008854005399631")
        assertThat(text).contains("EMULEX 1")
        assertThat(text).contains("KP 2")
        assertThat(text).contains("54 PZAS")
        assertThat(text).contains("TOTAL DE PIEZAS: 54")
        assertThat(text).doesNotContain("ENTREGO")
        assertThat(text).doesNotContain("RECIBIO")
    }

    @Test
    fun plainAndCpclTicketsCarryTheSameData() {
        val plain = TicketGenerator.generateBoxCountPlain(vale, PaperWidth.WIDTH_3_INCH)
            .toString(Charsets.US_ASCII)
        assertThat(plain).contains("CONTEO EN CAJA")
        assertThat(plain).contains("CC-20261125-001")
        assertThat(plain).contains("KP 2")

        val cpcl = TicketGenerator.generateBoxCountCpcl(vale, PaperWidth.WIDTH_4_INCH)
            .toString(Charsets.US_ASCII)
        assertThat(cpcl).contains("CONTEO EN CAJA")
        assertThat(cpcl).contains("CC-20261125-001")
        assertThat(cpcl).contains("KP 2")
        assertThat(cpcl).contains("PRINT")
    }
}
