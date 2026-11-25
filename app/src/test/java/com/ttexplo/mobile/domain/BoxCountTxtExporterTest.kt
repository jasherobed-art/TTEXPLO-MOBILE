package com.ttexplo.mobile.domain

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.domain.inventory.BoxCountRecord
import com.ttexplo.mobile.domain.inventory.BoxCountTxtExporter
import org.junit.Test

class BoxCountTxtExporterTest {

    @Test
    fun rendersExactTxtFormat() {
        val record = BoxCountRecord(
            code = "310071026008799008854005399631",
            kp = "2",
            pieces = 55,
            timestamp = "2026-11-25 12:41:56",
            rangeCount = 55,
            unitWeightKg = 0.454545
        )

        assertThat(BoxCountTxtExporter.render(listOf(record)))
            .isEqualTo("CAJ\n310071026008799008854005399631,2,55,2026-11-25 12:41:56\n\n")
        assertThat(BoxCountTxtExporter.fileName("2026-11-25")).isEqualTo("ttexplo2026-11-25.txt")
    }
}
