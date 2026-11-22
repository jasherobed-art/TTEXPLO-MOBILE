package com.ttexplo.mobile.domain.inventory

import com.ttexplo.mobile.domain.model.ProductType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

data class BoxUnitWeightParseResult(
    val productId: String,
    val initialCartridge: Long,
    val finalCartridge: Long,
    val cartridgesPerBox: Int,
    val boxWeightKg: Double,
    val unitWeightKg: Double
)

data class PackagingBreakdown(
    val fullPackages: Int,
    val looseCartridges: Int,
    val remainderKg: Double,
    val isNegative: Boolean
)

/**
 * Calculadora de dominio para:
 * 1. Cálculo automático de peso unitario a partir de códigos SEDENA de Caja (30 dígitos):
 *    [3 ID][6 Fecha][6 Inicial][6 Final][9 Serie]
 *    cartuchos = (Final - Inicial) + 1
 *    pesoUnitario = pesoCajaKg / cartuchos
 * 2. Cálculo de peso total en captura física rápida (cajas + cartuchos sueltos, sacos, PZA, MTS).
 * 3. Formateo de equivalencia visual en tarjetas de inventario (ej. "1,003.00 kg · 40 cajas + 12 cart.").
 */
object BoxUnitWeightCalculator {

    const val MAX_CARTRIDGES_PER_BOX = 500
    private val US_SYMBOLS = DecimalFormatSymbols(Locale.US)

    /**
     * Extrae la cantidad de cartuchos de una Caja de 30 dígitos SEDENA.
     * Retorna null si el código no cumple estructura de 30 dígitos o el rango es inválido.
     */
    fun parseBoxCartridgeCount(barcode: String): Int? {
        val trimmed = barcode.trim()
        if (trimmed.length != 30 || !trimmed.all { it.isDigit() }) return null
        val inicial = trimmed.substring(9, 15).toLongOrNull() ?: return null
        val finalNum = trimmed.substring(15, 21).toLongOrNull() ?: return null
        if (finalNum < inicial) return null
        val count = finalNum - inicial + 1L
        if (count !in 1L..MAX_CARTRIDGES_PER_BOX.toLong()) return null
        return count.toInt()
    }

    /**
     * Calcula el peso unitario en kilogramos por cartucho redondeado a 6 decimales para evitar
     * ruido de punto flotante IEEE-754 manteniendo precisión exacta al multiplicar.
     */
    fun calculateUnitWeightKg(boxWeightKg: Double, cartridgesCount: Int): Double? {
        if (boxWeightKg <= 0.0 || cartridgesCount <= 0) return null
        val raw = boxWeightKg / cartridgesCount.toDouble()
        return round(raw * 1_000_000.0) / 1_000_000.0
    }

    /**
     * Analiza un código de caja completo junto con el peso de caja del catálogo.
     */
    fun computeFromBoxBarcode(barcode: String, boxWeightKg: Double): BoxUnitWeightParseResult? {
        val trimmed = barcode.trim()
        val count = parseBoxCartridgeCount(trimmed) ?: return null
        val unitWeight = calculateUnitWeightKg(boxWeightKg, count) ?: return null
        val inicial = trimmed.substring(9, 15).toLong()
        val finalNum = trimmed.substring(15, 21).toLong()
        return BoxUnitWeightParseResult(
            productId = trimmed.substring(0, 3),
            initialCartridge = inicial,
            finalCartridge = finalNum,
            cartridgesPerBox = count,
            boxWeightKg = boxWeightKg,
            unitWeightKg = unitWeight
        )
    }

    /**
     * Calcula el peso total en kg a partir de cajas completas + cartuchos sueltos.
     */
    fun calculateExplosiveWeightKg(
        fullBoxes: Int,
        looseCartridges: Int,
        boxWeightKg: Double,
        unitWeightKg: Double
    ): Double {
        val safeBoxes = fullBoxes.coerceAtLeast(0)
        val safeCartridges = looseCartridges.coerceAtLeast(0)
        val total = (safeBoxes * boxWeightKg.coerceAtLeast(0.0)) +
            (safeCartridges * unitWeightKg.coerceAtLeast(0.0))
        return roundTo4Decimals(total)
    }

    /**
     * Calcula el peso total en kg a partir de sacos completos + kg sueltos opcionales.
     */
    fun calculateAgentWeightKg(
        fullSacks: Int,
        extraKg: Double,
        sackWeightKg: Double
    ): Double {
        val safeSacks = fullSacks.coerceAtLeast(0)
        val total = (safeSacks * sackWeightKg.coerceAtLeast(0.0)) + extraKg.coerceAtLeast(0.0)
        return roundTo4Decimals(total)
    }

    /**
     * Descompone una existencia en kg en empaques completos y cartuchos sueltos.
     */
    fun decomposeKgStock(
        stockKg: Double,
        boxWeightKg: Double,
        unitWeightKg: Double,
        cartridgesPerBox: Int
    ): PackagingBreakdown {
        val isNegative = stockKg < -1e-6
        val absKg = abs(stockKg)
        if (absKg < 1e-6 || boxWeightKg <= 0.0) {
            val looseOnly = if (unitWeightKg > 0.0 && absKg >= 1e-6) {
                (absKg / unitWeightKg).roundToInt()
            } else {
                0
            }
            return PackagingBreakdown(
                fullPackages = 0,
                looseCartridges = looseOnly,
                remainderKg = if (unitWeightKg > 0.0) 0.0 else absKg,
                isNegative = isNegative
            )
        }

        var fullBoxes = floor((absKg + 1e-6) / boxWeightKg).toInt()
        var remKg = (absKg - fullBoxes * boxWeightKg).coerceAtLeast(0.0)

        var looseCarts = 0
        if (unitWeightKg > 0.0) {
            looseCarts = (remKg / unitWeightKg).roundToInt()
            if (cartridgesPerBox > 0 && looseCarts >= cartridgesPerBox) {
                fullBoxes += looseCarts / cartridgesPerBox
                looseCarts %= cartridgesPerBox
            }
            remKg = 0.0
        }

        return PackagingBreakdown(
            fullPackages = fullBoxes,
            looseCartridges = looseCarts,
            remainderKg = roundTo4Decimals(remKg),
            isNegative = isNegative
        )
    }

    /**
     * Formatea la equivalencia visual para las tarjetas de inventario en vivo.
     * Ejemplos:
     * - Alto Explosivo: "1,003.00 kg · 40 cajas + 12 cart."
     * - Agente:         "625.00 kg · 25 sacos"
     * - Accesorio PZA:  "350 PZA"
     * - Accesorio MTS:  "1,250.50 MTS"
     */
    fun formatVisualEquivalence(
        stock: Double,
        unit: String,
        productType: ProductType,
        boxWeightKg: Double,
        unitWeightKg: Double,
        cartridgesPerBox: Int
    ): String {
        val normalizedUnit = unit.trim().uppercase()
        if (normalizedUnit == "PZA") {
            return "${formatPieces(stock)} PZA"
        }
        if (normalizedUnit == "MTS") {
            return "${formatDecimal2(stock)} MTS"
        }

        val kgFormatted = "${formatDecimal2(stock)} kg"
        if (abs(stock) < 1e-6) {
            return "$kgFormatted · Sin existencia"
        }

        val signPrefix = if (stock < -1e-6) "-" else ""
        if (productType == ProductType.AGENTE) {
            if (boxWeightKg <= 0.0) return kgFormatted
            val absKg = abs(stock)
            val fullSacks = floor((absKg + 1e-6) / boxWeightKg).toInt()
            val remKg = (absKg - fullSacks * boxWeightKg).coerceAtLeast(0.0)
            val sackWord = if (fullSacks == 1) "saco" else "sacos"
            val detail = when {
                fullSacks > 0 && remKg >= 0.01 -> "$signPrefix$fullSacks $sackWord + ${formatDecimal2(remKg)} kg"
                fullSacks > 0 -> "$signPrefix$fullSacks $sackWord"
                else -> "$signPrefix${formatDecimal2(remKg)} kg sueltos"
            }
            return "$kgFormatted · $detail"
        }

        // Alto Explosivo (o KG genérico)
        if (boxWeightKg <= 0.0 && unitWeightKg <= 0.0) return kgFormatted
        val breakdown = decomposeKgStock(stock, boxWeightKg, unitWeightKg, cartridgesPerBox)
        val boxWord = if (breakdown.fullPackages == 1) "caja" else "cajas"
        val detail = when {
            breakdown.fullPackages > 0 && breakdown.looseCartridges > 0 ->
                "$signPrefix${breakdown.fullPackages} $boxWord + ${breakdown.looseCartridges} cart."
            breakdown.fullPackages > 0 && breakdown.remainderKg >= 0.01 ->
                "$signPrefix${breakdown.fullPackages} $boxWord + ${formatDecimal2(breakdown.remainderKg)} kg"
            breakdown.fullPackages > 0 ->
                "$signPrefix${breakdown.fullPackages} $boxWord"
            breakdown.looseCartridges > 0 ->
                "$signPrefix${breakdown.looseCartridges} cart."
            else ->
                "$signPrefix${formatDecimal2(breakdown.remainderKg)} kg"
        }
        return "$kgFormatted · $detail"
    }

    fun formatDecimal2(value: Double): String {
        val df = DecimalFormat("#,##0.00", US_SYMBOLS)
        val clean = if (abs(value) < 1e-6) 0.0 else value
        return df.format(clean)
    }

    fun formatUnitWeight3(value: Double): String {
        val df = DecimalFormat("0.000", US_SYMBOLS)
        return df.format(value)
    }

    fun formatPieces(value: Double): String {
        val clean = if (abs(value) < 1e-6) 0.0 else value
        val isWhole = abs(clean - round(clean)) < 1e-4
        val df = if (isWhole) {
            DecimalFormat("#,##0", US_SYMBOLS)
        } else {
            DecimalFormat("#,##0.##", US_SYMBOLS)
        }
        return df.format(clean)
    }

    fun roundTo4Decimals(value: Double): Double = round(value * 10_000.0) / 10_000.0
}
