package com.ttexplo.mobile.domain.scanner

import com.ttexplo.mobile.data.repository.CatalogRepository
import com.ttexplo.mobile.domain.model.ProductType
import java.time.DateTimeException
import java.time.LocalDate

fun isValidManufacturingDate(dateStr: String): Boolean {
    if (dateStr.length != 6 || !dateStr.all { it.isDigit() }) return false
    val day = dateStr.substring(0, 2).toIntOrNull() ?: return false
    val month = dateStr.substring(2, 4).toIntOrNull() ?: return false
    val year = 2000 + (dateStr.substring(4, 6).toIntOrNull() ?: return false)
    if (year !in 2020..2030) return false
    return try {
        LocalDate.of(year, month, day)
        true
    } catch (_: DateTimeException) {
        false
    }
}

fun correctBarcodeDate(rawBarcode: String, correctedDate: String): String? {
    if (!isValidManufacturingDate(correctedDate)) return null
    val offset = when (rawBarcode.length) {
        18, 30 -> 3
        16 -> 4
        else -> return null
    }
    return rawBarcode.substring(0, offset) + correctedDate + rawBarcode.substring(offset + 6)
}

class ScanValidator(
    private val catalogRepository: CatalogRepository
) {
    suspend fun processScan(
        rawBarcode: String,
        currentSessionCodes: Set<String>,
        allowAccessories: Boolean = false
    ): ScanResult {
        val trimmed = rawBarcode.trim().replace("\r", "").replace("\n", "")
        val timestamp = DateTimeUtils.currentTimestamp()

        if (trimmed.isEmpty()) {
            return ScanResult(
                rawBarcode = "",
                timestamp = timestamp,
                productInfo = catalogRepository.lookupBarcode(""),
                isDuplicate = false,
                isValid = false,
                empaque = TipoEmpaque.DESCONOCIDO,
                message = "Lectura vacía"
            )
        }

        // Accessory check (X prefix)
        if (trimmed.startsWith("X", ignoreCase = true)) {
            if (!allowAccessories) {
                return ScanResult(
                    rawBarcode = trimmed,
                    timestamp = timestamp,
                    productInfo = catalogRepository.lookupBarcode(trimmed),
                    isDuplicate = false,
                    isValid = false,
                    empaque = TipoEmpaque.ACCESORIO,
                    message = "Los accesorios (código X) solo se permiten en el módulo de Salida de Accesorios."
                )
            }
            val isDuplicate = currentSessionCodes.contains(trimmed)
            return ScanResult(
                rawBarcode = trimmed,
                timestamp = timestamp,
                productInfo = catalogRepository.lookupBarcode(trimmed),
                isDuplicate = isDuplicate,
                isValid = true,
                empaque = TipoEmpaque.ACCESORIO,
                message = if (isDuplicate) "¡CÓDIGO REPETIDO! Este accesorio ya fue escaneado." else "Lectura correcta: Accesorio"
            )
        }

        // Strict length check for Alto Explosivo / Agente:
        // Cajas: 30 dígitos, Sacos: 18 dígitos, Cartuchos: 16 dígitos (compatible con 16 y 17)
        val empaque = when (trimmed.length) {
            30 -> TipoEmpaque.CAJA
            18 -> TipoEmpaque.SACO
            16, 17 -> TipoEmpaque.PIEZA
            else -> null
        }

        if (empaque == null || !trimmed.all { it.isDigit() }) {
            return ScanResult(
                rawBarcode = trimmed,
                timestamp = timestamp,
                productInfo = catalogRepository.lookupBarcode(trimmed),
                isDuplicate = false,
                isValid = false,
                empaque = TipoEmpaque.DESCONOCIDO,
                unknownId = null,
                message = "Código inválido (${trimmed.length} dígitos). Solo se permiten Cajas (30 dígitos), Sacos (18 dígitos) o Cartuchos (16 dígitos)."
            )
        }

        // Validate prefix exists in product catalog
        val prefix = trimmed.take(3)
        val productInfo = catalogRepository.lookupBarcode(trimmed)
        if (productInfo.type == ProductType.DESCONOCIDO || productInfo.id.isEmpty() || productInfo.id == "???" || productInfo.name.contains("no catalogado", ignoreCase = true)) {
            return ScanResult(
                rawBarcode = trimmed,
                timestamp = timestamp,
                productInfo = productInfo,
                isDuplicate = false,
                isValid = false,
                empaque = empaque,
                unknownId = prefix,
                message = "Material no reconocido. ID '$prefix' no existe en el catálogo. Por favor agréguela al catálogo para continuar."
            )
        }

        // CANDADO DE COHERENCIA DE EMPAQUE:
        // Alto Explosivo solo existe en Cajas (30 dígitos) o Cartuchos (16/17 dígitos), NUNCA en Sacos (18 dígitos).
        if (productInfo.type == ProductType.ALTO_EXPLOSIVO && empaque == TipoEmpaque.SACO) {
            return ScanResult(
                rawBarcode = trimmed,
                timestamp = timestamp,
                productInfo = productInfo,
                isDuplicate = false,
                isValid = false,
                empaque = empaque,
                unknownId = null,
                message = "Código inválido (18 dígitos). '${productInfo.name}' es Alto Explosivo y solo se presenta en Cajas (30 dígitos) o Cartuchos (16 dígitos), nunca en Sacos."
            )
        }

        // Agente de Voladura solo existe en Sacos (18 dígitos) o Cajas (30 dígitos), NUNCA en Cartuchos (16/17 dígitos).
        if (productInfo.type == ProductType.AGENTE && empaque == TipoEmpaque.PIEZA) {
            return ScanResult(
                rawBarcode = trimmed,
                timestamp = timestamp,
                productInfo = productInfo,
                isDuplicate = false,
                isValid = false,
                empaque = empaque,
                unknownId = null,
                message = "Código inválido (${trimmed.length} dígitos). '${productInfo.name}' es Agente de Voladura y solo se presenta en Sacos (18 dígitos) o Cajas (30 dígitos), no en Cartuchos."
            )
        }

        // CANDADO DE ESTRUCTURA INTERNA DE CAJAS (30 dígitos):
        // [3 ID][6 Fecha][6 Inicial][6 Final][9 Serie]
        if (empaque == TipoEmpaque.CAJA) {
            val inicialStr = trimmed.substring(9, 15)
            val finalStr = trimmed.substring(15, 21)
            val inicial = inicialStr.toLongOrNull()
            val final = finalStr.toLongOrNull()

            if (inicial != null && final != null) {
                if (final < inicial) {
                    return ScanResult(
                        rawBarcode = trimmed,
                        timestamp = timestamp,
                        productInfo = productInfo,
                        isDuplicate = false,
                        isValid = false,
                        empaque = empaque,
                        unknownId = null,
                        message = "Estructura de caja inválida: código inicial ($inicialStr) no puede ser mayor al final ($finalStr)."
                    )
                }
                val countCartuchos = final - inicial + 1
                if (countCartuchos > 500) {
                    return ScanResult(
                        rawBarcode = trimmed,
                        timestamp = timestamp,
                        productInfo = productInfo,
                        isDuplicate = false,
                        isValid = false,
                        empaque = empaque,
                        unknownId = null,
                        message = "Estructura de caja inválida: rango de $countCartuchos cartuchos excede la capacidad máxima de embalaje."
                    )
                }
            }
        }

        // CANDADO DE FECHA DE FABRICACIÓN PLAUSIBLE (6 dígitos):
        val fechaStr = when (empaque) {
            TipoEmpaque.CAJA, TipoEmpaque.SACO -> trimmed.substring(3, 9)
            TipoEmpaque.PIEZA -> if (trimmed.length == 16) trimmed.substring(4, 10) else null
            else -> null
        }
        if (fechaStr != null && !isValidManufacturingDate(fechaStr)) {
            return ScanResult(
                rawBarcode = trimmed,
                timestamp = timestamp,
                productInfo = productInfo,
                isDuplicate = false,
                isValid = false,
                empaque = empaque,
                unknownId = null,
                message = "Fecha de fabricación inválida en el código (${formatManufacturingDate(fechaStr)}). Debe ser entre 2020 y 2030 y un día válido.",
                invalidDate = fechaStr
            )
        }

        val isDuplicate = currentSessionCodes.contains(trimmed)

        // CÁLCULO AUTOMÁTICO DE PESO UNITARIO PARA CAJAS (30 DÍGITOS):
        // [3 ID][6 Fecha][6 Inicial][6 Final][9 Serie] -> cartuchos = (Final - Inicial) + 1; pesoUnitario = pesoCajaKg / cartuchos
        var resolvedProduct = productInfo
        var detectedCartridges: Int? = null
        var calculatedUnitWeight: Double? = null

        if (empaque == TipoEmpaque.CAJA) {
            val parsedBox = com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator.computeFromBoxBarcode(
                barcode = trimmed,
                boxWeightKg = productInfo.weightKg
            )
            if (parsedBox != null) {
                detectedCartridges = parsedBox.cartridgesPerBox
                calculatedUnitWeight = parsedBox.unitWeightKg
                catalogRepository.updateUnitWeightFromBox(
                    productId = productInfo.id,
                    cartridgesPerBox = parsedBox.cartridgesPerBox,
                    unitWeightKg = parsedBox.unitWeightKg
                )
                resolvedProduct = productInfo.copy(
                    unitWeightKg = parsedBox.unitWeightKg,
                    cartridgesPerBox = parsedBox.cartridgesPerBox
                )
            }
        }

        val unitWeightSuffix = if (detectedCartridges != null && calculatedUnitWeight != null) {
            val formattedUnit = com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator.formatUnitWeight3(calculatedUnitWeight)
            " • $detectedCartridges cart. (${formattedUnit} kg/u)"
        } else {
            ""
        }

        val message = if (isDuplicate) {
            "¡CÓDIGO REPETIDO! Este material ya fue escaneado."
        } else {
            "Lectura correcta: ${resolvedProduct.name} (${resolvedProduct.presentation})$unitWeightSuffix"
        }

        return ScanResult(
            rawBarcode = trimmed,
            timestamp = timestamp,
            productInfo = resolvedProduct,
            isDuplicate = isDuplicate,
            isValid = true,
            empaque = empaque,
            message = message,
            detectedCartridgesPerBox = detectedCartridges,
            calculatedUnitWeightKg = calculatedUnitWeight
        )
    }

}

private fun formatManufacturingDate(dateStr: String): String =
    "${dateStr.substring(0, 2)}/${dateStr.substring(2, 4)}/${dateStr.substring(4, 6)}"
