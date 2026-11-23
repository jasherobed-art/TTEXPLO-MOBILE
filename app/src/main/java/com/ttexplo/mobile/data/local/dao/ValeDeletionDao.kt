package com.ttexplo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ttexplo.mobile.data.local.entity.InventoryCodeEventEntity
import com.ttexplo.mobile.data.local.entity.InventoryEventEntity
import com.ttexplo.mobile.domain.inventory.InventoryReferenceType

/**
 * Punto único de escritura para borrado de vales: agrupa en una sola transacción los eventos
 * compensatorios y el borrado del vale (cabecera + hijos) para que un fallo parcial no deje
 * el libro mayor descuadrado.
 */
@Dao
interface ValeDeletionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQtyReversals(events: List<InventoryEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCodeReversals(events: List<InventoryCodeEventEntity>)

    @Query("DELETE FROM entrada_items WHERE entrada_id = :id")
    suspend fun deleteEntradaItems(id: Long)

    @Query("DELETE FROM entradas WHERE id = :id")
    suspend fun deleteEntrada(id: Long)

    @Query("DELETE FROM despacho_items WHERE despacho_id = :id")
    suspend fun deleteDespachoItems(id: Long)

    @Query("DELETE FROM despachos WHERE id = :id")
    suspend fun deleteDespacho(id: Long)

    @Query("DELETE FROM accesorio_items WHERE vale_id = :id")
    suspend fun deleteAccesorioItems(id: Long)

    @Query("DELETE FROM accesorios_vales WHERE id = :id")
    suspend fun deleteAccesorio(id: Long)

    @Query("DELETE FROM devolucion_items WHERE devolucion_id = :id")
    suspend fun deleteDevolucionItems(id: Long)

    @Query("DELETE FROM devoluciones WHERE id = :id")
    suspend fun deleteDevolucion(id: Long)

    @Transaction
    suspend fun deleteValeAndReverse(
        referenceType: String,
        referenceId: Long,
        qtyReversals: List<InventoryEventEntity>,
        codeReversals: List<InventoryCodeEventEntity>
    ) {
        // Los reversos van primero: si cualquier borrado falla, la transacción los deshace.
        if (qtyReversals.isNotEmpty()) insertQtyReversals(qtyReversals)
        if (codeReversals.isNotEmpty()) insertCodeReversals(codeReversals)

        when (referenceType) {
            InventoryReferenceType.ENTRADA -> {
                deleteEntradaItems(referenceId)
                deleteEntrada(referenceId)
            }
            InventoryReferenceType.DESPACHO -> {
                deleteDespachoItems(referenceId)
                deleteDespacho(referenceId)
            }
            InventoryReferenceType.ACCESORIO_VALE -> {
                deleteAccesorioItems(referenceId)
                deleteAccesorio(referenceId)
            }
            InventoryReferenceType.DEVOLUCION -> {
                deleteDevolucionItems(referenceId)
                deleteDevolucion(referenceId)
            }
        }
    }
}
