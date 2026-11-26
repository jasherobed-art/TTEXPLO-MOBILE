package com.ttexplo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ttexplo.mobile.domain.inventory.BoxCountItem

@Entity(tableName = "conteos_caja")
data class ConteoCajaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "folio") val folio: String,
    @ColumnInfo(name = "codigo") val codigo: String,
    @ColumnInfo(name = "descripcion") val descripcion: String,
    @ColumnInfo(name = "kp") val kp: String,
    @ColumnInfo(name = "piezas") val piezas: Int,
    @ColumnInfo(name = "fecha_hora") val fechaHora: String
) {
    fun toDomain(): BoxCountItem = BoxCountItem(codigo, descripcion, kp, piezas, fechaHora)
}
