package com.ttexplo.mobile.data.repository

import com.ttexplo.mobile.data.local.dao.ProductoDao
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductClassifier
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType

interface CatalogRepository {
    suspend fun importCsv(csvContent: String): Int
    suspend fun lookupBarcode(barcode: String): ProductInfo
    suspend fun countProducts(): Int
    suspend fun getAllProducts(): List<ProductInfo>
    suspend fun updateUnitWeightFromBox(productId: String, cartridgesPerBox: Int, unitWeightKg: Double) {}
    suspend fun upsertProduct(entity: ProductoEntity) {}
}

class CatalogRepositoryImpl(
    private val productoDao: ProductoDao
) : CatalogRepository {

    override suspend fun importCsv(csvContent: String): Int {
        val lines = csvContent.lines()
        val entities = mutableListOf<ProductoEntity>()
        val existingById = productoDao.getAll().associateBy { it.idProducto }

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (index == 0 && (trimmed.startsWith("\"IdProducto\"") || trimmed.startsWith("IdProducto"))) {
                continue // Skip header row
            }

            val tokens = parseCsvLine(trimmed)
            if (tokens.size >= 8) {
                val id = tokens.getOrNull(0)?.trim() ?: ""
                val producto = tokens.getOrNull(1)?.trim() ?: ""
                val presentacion = tokens.getOrNull(2)?.trim() ?: ""
                val noSap = tokens.getOrNull(3)?.trim() ?: ""
                val unidad = tokens.getOrNull(4)?.trim()?.ifBlank { "KG" } ?: "KG"
                val cantidad = tokens.getOrNull(5)?.trim()?.toDoubleOrNull() ?: 0.0
                val codBarras = tokens.getOrNull(6)?.trim() ?: id
                val peso = tokens.getOrNull(7)?.trim()?.toDoubleOrNull() ?: 0.0
                val pg = tokens.getOrNull(8)?.trim() ?: ""
                val pu = tokens.getOrNull(9)?.trim()?.toDoubleOrNull() ?: 0.0

                // Infer tipo: Check if there's an 11th column with explicit type, else infer from name
                val explicitTipo = tokens.getOrNull(10)?.trim()?.uppercase()
                val tipo = when (explicitTipo) {
                    "AGENTE", "ALTO_EXPLOSIVO", "ACCESORIO" -> explicitTipo
                    else -> if (id.startsWith("X", ignoreCase = true)) {
                        ProductType.ACCESORIO.name
                    } else {
                        ProductClassifier.inferType(producto).name
                    }
                }

                // Preserve previously calibrated unit weight / cartridges per box if CSV does not provide them
                val existing = existingById[id]
                val csvUnitWeight = tokens.getOrNull(11)?.trim()?.toDoubleOrNull() ?: 0.0
                val csvCartridges = tokens.getOrNull(12)?.trim()?.toIntOrNull() ?: 0
                val csvCategory = InventoryCategory.fromStringOrNull(tokens.getOrNull(13))

                val resolvedCartridges = when {
                    csvCartridges > 0 -> csvCartridges
                    existing != null && existing.cartuchosPorCaja > 0 -> existing.cartuchosPorCaja
                    else -> 0
                }
                val resolvedUnitWeight = when {
                    csvUnitWeight > 0.0 -> csvUnitWeight
                    existing != null && existing.pesoUnitarioKg > 0.0 -> existing.pesoUnitarioKg
                    resolvedCartridges > 0 && peso > 0.0 ->
                        BoxUnitWeightCalculator.calculateUnitWeightKg(peso, resolvedCartridges) ?: 0.0
                    pu > 0.0 -> pu
                    else -> 0.0
                }

                val parsedType = try {
                    ProductType.valueOf(tipo)
                } catch (_: Exception) {
                    ProductType.ALTO_EXPLOSIVO
                }
                val resolvedCategory = csvCategory
                    ?: existing?.let { InventoryCategory.fromStringOrNull(it.categoriaInventario) }
                    ?: InventoryCategory.inferCategory(parsedType, id, producto, unidad)

                if (id.isNotEmpty()) {
                    entities.add(
                        ProductoEntity(
                            idProducto = id,
                            producto = producto,
                            presentacion = presentacion,
                            noSap = noSap,
                            unidad = unidad,
                            cantidad = cantidad,
                            codBarras = codBarras,
                            peso = peso,
                            pg = pg,
                            pu = pu,
                            tipo = tipo,
                            pesoUnitarioKg = resolvedUnitWeight,
                            cartuchosPorCaja = resolvedCartridges,
                            categoriaInventario = resolvedCategory.name
                        )
                    )
                }
            }
        }

        if (entities.isNotEmpty()) {
            productoDao.insertAll(entities)
        }
        return entities.size
    }

    override suspend fun lookupBarcode(barcode: String): ProductInfo {
        val trimmed = barcode.trim()
        if (trimmed.startsWith("X", ignoreCase = true)) {
            val normalizedCode = trimmed.uppercase()
            val registered = productoDao.getById(normalizedCode)
                ?: productoDao.getByCodBarras(normalizedCode)
                ?: productoDao.getById(trimmed)
                ?: productoDao.getByCodBarras(trimmed)
            if (registered != null) {
                return registered.toDomain()
            }
            val suggestedCategory = InventoryCategory.inferCategory(
                productType = ProductType.ACCESORIO,
                codeOrId = normalizedCode,
                productName = "",
                unit = ""
            )
            val suggestedUnit = suggestedCategory.defaultUnit
            val suggestedPresentation = if (suggestedUnit == "MTS") "Metros" else "Pieza"
            return ProductInfo(
                id = normalizedCode,
                name = "${suggestedCategory.label} ($normalizedCode)",
                presentation = suggestedPresentation,
                noSap = "",
                unit = suggestedUnit,
                weightKg = 0.0,
                type = ProductType.ACCESORIO,
                pg = "",
                inventoryCategory = suggestedCategory
            )
        }

        val prefix = ProductClassifier.extractPrefix(trimmed)
        if (prefix.isNotEmpty()) {
            val entity = productoDao.getById(prefix) ?: productoDao.getByCodBarras(prefix)
            if (entity != null) {
                return entity.toDomain()
            }
        }

        return ProductInfo(
            id = if (prefix.isNotEmpty()) prefix else "???",
            name = "Material no catalogado ($prefix)",
            presentation = "Desconocida",
            noSap = "",
            unit = "KG",
            weightKg = 0.0,
            type = ProductType.DESCONOCIDO,
            pg = "",
            inventoryCategory = InventoryCategory.ALTO_EXPLOSIVO
        )
    }

    override suspend fun countProducts(): Int = productoDao.count()

    override suspend fun getAllProducts(): List<ProductInfo> =
        productoDao.getAll().map { it.toDomain() }

    override suspend fun updateUnitWeightFromBox(
        productId: String,
        cartridgesPerBox: Int,
        unitWeightKg: Double
    ) {
        if (productId.isBlank() || cartridgesPerBox <= 0 || unitWeightKg <= 0.0) return
        productoDao.updateUnitWeight(
            id = productId,
            unitWeightKg = unitWeightKg,
            cartridgesPerBox = cartridgesPerBox
        )
    }

    override suspend fun upsertProduct(entity: ProductoEntity) {
        productoDao.insert(entity)
    }

    companion object {
        fun parseCsvLine(line: String): List<String> {
            val tokens = mutableListOf<String>()
            val sb = java.lang.StringBuilder()
            var inQuotes = false
            var i = 0

            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                } else if (c == ',' && !inQuotes) {
                    tokens.add(sb.toString())
                    sb.setLength(0)
                } else {
                    sb.append(c)
                }
                i++
            }
            tokens.add(sb.toString())
            return tokens
        }
    }
}
