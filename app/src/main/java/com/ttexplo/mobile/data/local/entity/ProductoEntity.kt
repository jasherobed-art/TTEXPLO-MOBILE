package com.ttexplo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.model.StandardUnits

@Entity(tableName = "productos")
data class ProductoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_producto")
    val idProducto: String,

    @ColumnInfo(name = "producto")
    val producto: String,

    @ColumnInfo(name = "presentacion")
    val presentacion: String,

    @ColumnInfo(name = "no_sap")
    val noSap: String,

    @ColumnInfo(name = "unidad")
    val unidad: String,

    @ColumnInfo(name = "cantidad")
    val cantidad: Double,

    @ColumnInfo(name = "cod_barras")
    val codBarras: String,

    @ColumnInfo(name = "peso")
    val peso: Double,

    @ColumnInfo(name = "pg")
    val pg: String,

    @ColumnInfo(name = "pu")
    val pu: Double,

    @ColumnInfo(name = "tipo")
    val tipo: String, // "ALTO_EXPLOSIVO" | "AGENTE" | "ACCESORIO"

    @ColumnInfo(name = "peso_unitario_kg", defaultValue = "0.0")
    val pesoUnitarioKg: Double = 0.0,

    @ColumnInfo(name = "cartuchos_por_caja", defaultValue = "0")
    val cartuchosPorCaja: Int = 0,

    @ColumnInfo(name = "categoria_inventario", defaultValue = "ALTO_EXPLOSIVO")
    val categoriaInventario: String = InventoryCategory.inferCategory(
        productType = try {
            ProductType.valueOf(tipo)
        } catch (_: Exception) {
            ProductType.DESCONOCIDO
        },
        codeOrId = idProducto,
        productName = producto,
        unit = unidad
    ).name
) {
    fun resolvedCategory(): InventoryCategory {
        val parsedType = try {
            ProductType.valueOf(tipo)
        } catch (_: Exception) {
            ProductType.DESCONOCIDO
        }
        return InventoryCategory.fromStringOrNull(categoriaInventario)
            ?: InventoryCategory.inferCategory(parsedType, idProducto, producto, unidad)
    }

    fun toDomain(): ProductInfo {
        val parsedType = try {
            ProductType.valueOf(tipo)
        } catch (_: Exception) {
            ProductType.DESCONOCIDO
        }
        val category = resolvedCategory()
        return ProductInfo(
            id = idProducto,
            name = producto.trim(),
            presentation = presentacion.trim(),
            noSap = noSap.trim(),
            unit = StandardUnits.normalize(unidad, tipo, idProducto, producto),
            weightKg = peso,
            type = parsedType,
            pg = pg.trim(),
            unitWeightKg = pesoUnitarioKg,
            cartridgesPerBox = cartuchosPorCaja,
            inventoryCategory = category
        )
    }
}
