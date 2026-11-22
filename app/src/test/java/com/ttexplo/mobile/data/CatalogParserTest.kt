package com.ttexplo.mobile.data

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.domain.model.ProductClassifier
import com.ttexplo.mobile.domain.model.ProductType
import org.junit.Test

class CatalogParserTest {

    @Test
    fun testParseCsvLine_handlesQuotesAndCommas() {
        val sampleLine = "\"001\",\"MAGNAFRAC \",\"1\"\" X 5\"\"\",\"EMFN25X125BKGLA\",\"KG\",25,\"001\",25,\"0119\",0"
        val tokens = CatalogRepositoryImpl.parseCsvLine(sampleLine)

        assertThat(tokens).hasSize(10)
        assertThat(tokens[0]).isEqualTo("001")
        assertThat(tokens[1]).isEqualTo("MAGNAFRAC ")
        assertThat(tokens[2]).isEqualTo("1\" X 5\"")
        assertThat(tokens[3]).isEqualTo("EMFN25X125BKGLA")
        assertThat(tokens[4]).isEqualTo("KG")
        assertThat(tokens[5]).isEqualTo("25")
        assertThat(tokens[6]).isEqualTo("001")
        assertThat(tokens[7]).isEqualTo("25")
        assertThat(tokens[8]).isEqualTo("0119")
        assertThat(tokens[9]).isEqualTo("0")
    }

    @Test
    fun testInferType_identifiesAgenteCorrectly() {
        assertThat(ProductClassifier.inferType("AUSTINITE 15 CA-1")).isEqualTo(ProductType.AGENTE)
        assertThat(ProductClassifier.inferType("AMEX REG")).isEqualTo(ProductType.AGENTE)
        assertThat(ProductClassifier.inferType("MEXAMON G")).isEqualTo(ProductType.AGENTE)
        assertThat(ProductClassifier.inferType("ANFOOVIEDO SACO 25KG")).isEqualTo(ProductType.AGENTE)
        assertThat(ProductClassifier.inferType("NITRATO DE AMONIO PRILADO")).isEqualTo(ProductType.AGENTE)
    }

    @Test
    fun testInferType_identifiesAltoExplosivoCorrectly() {
        assertThat(ProductClassifier.inferType("EMULEX C")).isEqualTo(ProductType.ALTO_EXPLOSIVO)
        assertThat(ProductClassifier.inferType("MAGNAFRAC 1 1/4 X 16")).isEqualTo(ProductType.ALTO_EXPLOSIVO)
        assertThat(ProductClassifier.inferType("TOVEX 100")).isEqualTo(ProductType.ALTO_EXPLOSIVO)
        assertThat(ProductClassifier.inferType("SENATEL POWERSPLIT")).isEqualTo(ProductType.ALTO_EXPLOSIVO)
        assertThat(ProductClassifier.inferType("FORTEL PRO")).isEqualTo(ProductType.ALTO_EXPLOSIVO)
    }

    @Test
    fun testExtractPrefix_handlesAllBarcodeFormats() {
        // 18-digit piece code
        assertThat(ProductClassifier.extractPrefix("298020926004801028")).isEqualTo("298")
        // 30-digit box code
        assertThat(ProductClassifier.extractPrefix("337150826014346014401005323061")).isEqualTo("337")
        // Accessory code
        assertThat(ProductClassifier.extractPrefix("X100182000000009")).isEqualTo("X")
        // Edge cases
        assertThat(ProductClassifier.extractPrefix("")).isEqualTo("")
        assertThat(ProductClassifier.extractPrefix("42")).isEqualTo("42")
    }
}
