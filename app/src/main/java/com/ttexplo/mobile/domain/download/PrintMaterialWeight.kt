package com.ttexplo.mobile.domain.download

import com.ttexplo.mobile.domain.model.ProductInfo

/**
 * Peso de un renglón impreso, calculado con los datos del catálogo.
 * Devuelve null cuando el catálogo no permite conocerlo; no inventa valores.
 */
object PrintMaterialWeight {

    fun totalKg(product: ProductInfo?, empaque: String, cantidad: Int): Double? {
        if (product == null || cantidad <= 0) return null
        val unit = when (empaque.trim().uppercase()) {
            "CAJA" -> product.weightKg.takeIf { it > 0.0 }
                ?: (product.unitWeightKg * product.cartridgesPerBox).takeIf { it > 0.0 }
            "SACO" -> product.weightKg.takeIf { it > 0.0 }
            "CARTUCHO" -> product.unitWeightKg.takeIf { it > 0.0 }
                ?: if (product.cartridgesPerBox > 0) {
                    (product.weightKg / product.cartridgesPerBox).takeIf { it > 0.0 }
                } else {
                    null
                }
            else -> null
        }
        return unit?.times(cantidad)
    }
}
