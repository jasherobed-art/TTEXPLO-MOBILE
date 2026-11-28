package com.ttexplo.mobile.data.repository

import com.ttexplo.mobile.data.local.dao.ProductoDao
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductClassifier
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.model.StandardUnits

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
        var columns: Map<String, Int>? = null
        var headerChecked = false

        for (line in lines) {
            val trimmed = line.trim().removePrefix("\uFEFF")
            if (trimmed.isEmpty()) continue
            val tokens = parseCsvLine(trimmed)
            if (!headerChecked) {
                headerChecked = true
                val names = tokens.map(::normalizeHeader)
                if (names.any { it == "idproducto" || it == "id" }) {
                    columns = names.withIndex()
                        .filter { it.value.isNotEmpty() }
                        .associate { it.value to it.index }
                    continue
                }
            }
            val cols = columns

            // Con encabezado se busca por nombre; sin encabezado, por posición (formato original).
            fun col(position: Int, vararg names: String): String {
                if (cols != null) {
                    for (name in names) {
                        val at = cols[name] ?: continue
                        return tokens.getOrNull(at)?.trim() ?: ""
                    }
                    return ""
                }
                return tokens.getOrNull(position)?.trim() ?: ""
            }

            if (cols == null && tokens.size < 8) continue
            val id = col(0, "idproducto", "id")
            if (id.isEmpty()) continue
            val existing = existingById[id]

            val producto = col(1, "producto").ifBlank { existing?.producto ?: "" }
            val presentacion = col(2, "presentacion").ifBlank { existing?.presentacion ?: "" }
            val noSap = col(3, "nosap").ifBlank { existing?.noSap ?: "" }
            val unidadRaw = col(4, "unidad").ifBlank { existing?.unidad ?: "" }
            val cantidad = col(5, "cantidad").toDoubleOrNull() ?: existing?.cantidad ?: 0.0
            val codBarras = col(6, "codbarras").ifBlank { existing?.codBarras ?: id }
            val peso = col(7, "peso").toDoubleOrNull() ?: existing?.peso ?: 0.0
            val pg = col(8, "pg").ifBlank { existing?.pg ?: "" }
            val pu = col(9, "pu").toDoubleOrNull() ?: existing?.pu ?: 0.0

            // Tipo explícito si viene; si no, se conserva el del producto ya cargado o se infiere.
            val explicitTipo = col(10, "tipo").uppercase()
            val tipo = when {
                explicitTipo == "AGENTE" || explicitTipo == "ALTO_EXPLOSIVO" ||
                    explicitTipo == "ACCESORIO" -> explicitTipo
                cols != null && explicitTipo.isEmpty() && existing != null -> existing.tipo
                id.startsWith("X", ignoreCase = true) -> ProductType.ACCESORIO.name
                else -> ProductClassifier.inferType(producto).name
            }

            // Kilos, metros o piezas: la unidad se estandariza aunque el CSV traiga "KGS", "METRO" o venga vacía.
            val unidad = StandardUnits.normalize(unidadRaw, tipo, id, producto)

            // Preserve previously calibrated unit weight / cartridges per box if CSV does not provide them
            val csvUnitWeight = col(11, "pesounitario").toDoubleOrNull() ?: 0.0
            val csvCartridges = col(12, "cartuchoscaja", "cartuchosporcaja").toIntOrNull() ?: 0
            val csvCategory = InventoryCategory.fromStringOrNull(col(13, "categoria", "categoriainventario"))

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

        if (entities.isNotEmpty()) {
            productoDao.insertAll(entities)
        }
        return entities.size
    }

    override suspend fun lookupBarcode(barcode: String): ProductInfo {
        val trimmed = barcode.trim()
        if (trimmed.startsWith("X", ignoreCase = true)) {
            val normalizedCode = trimmed.uppercase()
            val productKey = normalizedCode
                .takeIf { it.length > 7 && it.take(7).matches(Regex("^X\\d{6}$")) }
                ?.take(7)
            val registered = productoDao.getById(normalizedCode)
                ?: productoDao.getByCodBarras(normalizedCode)
                ?: productKey?.let { productoDao.getById(it) ?: productoDao.getByCodBarras(it) }
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
        /** Encabezado legible: sin BOM, comillas, espacios ni guiones bajos. */
        fun normalizeHeader(value: String): String =
            value.trim().removePrefix("\uFEFF").removeSurrounding("\"")
                .lowercase().replace(" ", "").replace("_", "")

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
