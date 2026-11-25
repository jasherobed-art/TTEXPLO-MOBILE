package com.ttexplo.mobile.domain.scanner

import com.ttexplo.mobile.domain.model.ProductInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TipoEmpaque(val label: String) {
    CAJA("Caja"),
    SACO("Saco"),
    PIEZA("Cartucho"),
    ACCESORIO("Accesorio"),
    DESCONOCIDO("Desconocido")
}

data class BarcodeItem(
    val code: String,
    val timestamp: String,
    val productInfo: ProductInfo,
    val empaque: TipoEmpaque = TipoEmpaque.DESCONOCIDO,
    val isDuplicateRead: Boolean = false
)

data class ScanResult(
    val rawBarcode: String,
    val timestamp: String,
    val productInfo: ProductInfo,
    val isDuplicate: Boolean,
    val isValid: Boolean = true,
    val empaque: TipoEmpaque = TipoEmpaque.DESCONOCIDO,
    val unknownId: String? = null,
    val message: String,
    val detectedCartridgesPerBox: Int? = null,
    val calculatedUnitWeightKg: Double? = null,
    val invalidDate: String? = null
)

object DateTimeUtils {
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun currentTimestamp(): String {
        return DATE_FORMAT.format(Date())
    }
}
