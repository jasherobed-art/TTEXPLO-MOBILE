package com.ttexplo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entradas")
data class EntradaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "proveedor") val proveedor: String,
    @ColumnInfo(name = "polvorin") val polvorin: String,
    @ColumnInfo(name = "factura") val factura: String,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: String,
    @ColumnInfo(name = "estado") val estado: String = "FINALIZADO",
    @ColumnInfo(name = "exportado") val exportado: Boolean = false
)

@Entity(tableName = "entrada_items")
data class EntradaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entrada_id") val entradaId: Long,
    @ColumnInfo(name = "codigo_barras") val codigoBarras: String,
    @ColumnInfo(name = "fecha_escaneo") val fechaEscaneo: String,
    @ColumnInfo(name = "producto_id") val productoId: String = "",
    @ColumnInfo(name = "producto_nombre") val productoNombre: String = "",
    @ColumnInfo(name = "presentacion") val presentacion: String = "",
    @ColumnInfo(name = "tipo") val tipo: String = ""
)

@Entity(tableName = "despachos")
data class DespachoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "vale") val vale: String,
    @ColumnInfo(name = "empresa") val empresa: String,
    @ColumnInfo(name = "lugar") val lugar: String,
    @ColumnInfo(name = "transportista") val transportista: String,
    @ColumnInfo(name = "turno") val turno: String,
    @ColumnInfo(name = "voladura") val voladura: String,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: String,
    @ColumnInfo(name = "estado") val estado: String = "FINALIZADO",
    @ColumnInfo(name = "exportado") val exportado: Boolean = false
)

@Entity(tableName = "despacho_items")
data class DespachoItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "despacho_id") val despachoId: Long,
    @ColumnInfo(name = "codigo_barras") val codigoBarras: String,
    @ColumnInfo(name = "fecha_escaneo") val fechaEscaneo: String,
    @ColumnInfo(name = "producto_id") val productoId: String,
    @ColumnInfo(name = "producto_nombre") val productoNombre: String,
    @ColumnInfo(name = "presentacion") val presentacion: String,
    @ColumnInfo(name = "tipo") val tipo: String
)

@Entity(tableName = "devoluciones")
data class DevolucionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "empleado_entrega") val empleadoEntrega: String,
    @ColumnInfo(name = "empleado_recibe") val empleadoRecibe: String,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: String,
    @ColumnInfo(name = "estado") val estado: String = "FINALIZADO",
    @ColumnInfo(name = "exportado") val exportado: Boolean = false
)

@Entity(tableName = "devolucion_items")
data class DevolucionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "devolucion_id") val devolucionId: Long,
    @ColumnInfo(name = "codigo_barras") val codigoBarras: String,
    @ColumnInfo(name = "fecha_escaneo") val fechaEscaneo: String
)

@Entity(tableName = "accesorios_vales")
data class AccesorioValeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "vale") val vale: String,
    @ColumnInfo(name = "empresa") val empresa: String,
    @ColumnInfo(name = "lugar") val lugar: String,
    @ColumnInfo(name = "transportista") val transportista: String,
    @ColumnInfo(name = "turno") val turno: String,
    @ColumnInfo(name = "recibio") val recibio: String,
    @ColumnInfo(name = "entrego") val entrego: String,
    @ColumnInfo(name = "voladura") val voladura: String,
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: String,
    @ColumnInfo(name = "estado") val estado: String = "FINALIZADO",
    @ColumnInfo(name = "exportado") val exportado: Boolean = false
)

@Entity(tableName = "accesorio_items")
data class AccesorioItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "vale_id") val valeId: Long,
    @ColumnInfo(name = "codigo_accesorio") val codigoAccesorio: String,
    @ColumnInfo(name = "cantidad") val cantidad: Int,
    @ColumnInfo(name = "fecha_escaneo") val fechaEscaneo: String
)
