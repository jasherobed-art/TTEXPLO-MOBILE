package com.ttexplo.mobile.domain.inventory

import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import java.util.Locale
import kotlin.math.abs

/**
 * Formato y resolución de peso únicos (regla de 3 decimales exactos).
 * No inventa valores: si un cartucho no está calibrado, devuelve 0.0.
 */
object WeightFormat {

    /** Formatea kg con separador de miles y exactamente 3 decimales (25.000, 0.217). */
    fun kg3(value: Double): String {
        val safe = if (abs(value) < 1e-6) 0.0 else value
        return String.format(Locale.US, "%,.3f", safe)
    }

    /**
     * Peso de un cartucho (PIEZA):
     * 1. producto calibrado (unitWeightKg)
     * 2. peso de caja / cartuchos por caja
     * 3. sin calibrar -> 0.0
     */
    fun resolveCartridgeUnitWeightKg(product: ProductInfo): Double = when {
        product.unitWeightKg > 0.0 -> product.unitWeightKg
        product.weightKg > 0.0 && product.cartridgesPerBox > 0 ->
            product.weightKg / product.cartridgesPerBox
        else -> 0.0
    }

    /** Peso de una lectura según su empaque. */
    fun itemWeightKg(item: BarcodeItem): Double = when (item.empaque) {
        TipoEmpaque.CAJA, TipoEmpaque.SACO -> item.productInfo.weightKg.coerceAtLeast(0.0)
        TipoEmpaque.PIEZA -> resolveCartridgeUnitWeightKg(item.productInfo)
        else -> 0.0
    }
}
