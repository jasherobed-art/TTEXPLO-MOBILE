package com.ttexplo.mobile.domain

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.domain.inventory.BoxCountParser
import org.junit.Test

class BoxCountParserTest {

    @Test
    fun parsesRealBoxCountCode() {
        val record = BoxCountParser.parse("CAJ310071026008799008854005399631,2,55,2026-11-25 12:41:56")

        assertThat(record).isNotNull()
        assertThat(record!!.code).isEqualTo("310071026008799008854005399631")
        assertThat(record.kp).isEqualTo("2")
        assertThat(record.pieces).isEqualTo(55)
        assertThat(record.timestamp).isEqualTo("2026-11-25 12:41:56")
        assertThat(record.rangeCount).isEqualTo(55)
        assertThat(record.unitWeightKg).isWithin(1e-9).of(0.454545)
    }

    @Test
    fun parsesCodeOnlyWithEmptyKpAndRangePieces() {
        val record = BoxCountParser.parseCodeOnly("CAJ310071026008799008854005399631")

        assertThat(record).isNotNull()
        assertThat(record!!.code).isEqualTo("310071026008799008854005399631")
        assertThat(record.kp).isEmpty()
        assertThat(record.pieces).isEqualTo(55)
        assertThat(record.rangeCount).isEqualTo(55)
    }

    @Test
    fun acceptsFourDigitPiecesButOnlySingleDigitKp() {
        val record = BoxCountParser.parse("CAJ310071026008799008854005399631,9,0500,2026-11-25 12:41:56")
        assertThat(record).isNotNull()
        assertThat(record!!.pieces).isEqualTo(500)

        assertThat(BoxCountParser.parse("CAJ310071026008799008854005399631,22,55,2026-11-25 12:41:56")).isNull()
        assertThat(BoxCountParser.parse("CAJ310071026008799008854005399631,A,55,2026-11-25 12:41:56")).isNull()
    }

    @Test
    fun rejectsBlankKpOutOfRangePiecesAndNonDigitCode() {
        assertThat(BoxCountParser.parse("CAJ310071026008799008854005399631,,55,2026-11-25 12:41:56")).isNull()
        assertThat(BoxCountParser.parse("CAJ310071026008799008854005399631,2,0,2026-11-25 12:41:56")).isNull()
        assertThat(BoxCountParser.parse("CAJ310071026008799008854005399631,2,501,2026-11-25 12:41:56")).isNull()
        assertThat(BoxCountParser.parse("CAJ31007102600879900885400539963X,2,55,2026-11-25 12:41:56")).isNull()
    }
}
