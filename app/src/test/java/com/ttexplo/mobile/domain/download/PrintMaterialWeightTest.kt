package com.ttexplo.mobile.domain.download

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import org.junit.Test

class PrintMaterialWeightTest {

    private fun product(
        weightKg: Double = 0.0,
        unitWeightKg: Double = 0.0,
        cartridgesPerBox: Int = 0
    ) = ProductInfo(
        id = "310",
        name = "EMULEX 1",
        presentation = "1 X 8",
        noSap = "",
        unit = "KG",
        weightKg = weightKg,
        type = ProductType.ALTO_EXPLOSIVO,
        pg = "",
        unitWeightKg = unitWeightKg,
        cartridgesPerBox = cartridgesPerBox
    )

    @Test
    fun boxUsesBoxWeight() {
        assertThat(PrintMaterialWeight.totalKg(product(weightKg = 25.0), "CAJA", 2))
            .isWithin(1e-9).of(50.0)
    }

    @Test
    fun boxFallsBackToUnitWeightTimesCartridges() {
        val p = product(unitWeightKg = 0.5, cartridgesPerBox = 48)
        assertThat(PrintMaterialWeight.totalKg(p, "CAJA", 2))
            .isWithin(1e-9).of(48.0)
    }

    @Test
    fun sackUsesSackWeight() {
        assertThat(PrintMaterialWeight.totalKg(product(weightKg = 25.0), "SACO", 1))
            .isWithin(1e-9).of(25.0)
    }

    @Test
    fun cartridgeUsesCalibratedUnitWeight() {
        assertThat(PrintMaterialWeight.totalKg(product(unitWeightKg = 0.446429), "CARTUCHO", 2))
            .isWithin(1e-9).of(0.892858)
    }

    @Test
    fun cartridgeFallsBackToBoxWeightDividedByCartridges() {
        val p = product(weightKg = 25.0, cartridgesPerBox = 56)
        assertThat(PrintMaterialWeight.totalKg(p, "CARTUCHO", 2))
            .isWithin(1e-9).of(25.0 / 56.0 * 2.0)
    }

    @Test
    fun unknownWeightReturnsNull() {
        assertThat(PrintMaterialWeight.totalKg(product(), "CAJA", 1)).isNull()
        assertThat(PrintMaterialWeight.totalKg(product(), "CARTUCHO", 1)).isNull()
    }

    @Test
    fun zeroQuantityOrNullProductReturnsNull() {
        assertThat(PrintMaterialWeight.totalKg(product(weightKg = 25.0), "CAJA", 0)).isNull()
        assertThat(PrintMaterialWeight.totalKg(null, "CAJA", 1)).isNull()
    }
}
