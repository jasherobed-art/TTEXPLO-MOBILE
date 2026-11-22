package com.ttexplo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ttexplo.mobile.data.local.entity.*

@Dao
interface EntradaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntrada(entrada: EntradaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<EntradaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSingleItem(item: EntradaItemEntity): Long

    @Query("SELECT * FROM entradas WHERE id = :entradaId LIMIT 1")
    suspend fun getEntradaById(entradaId: Long): EntradaEntity?

    @Query("SELECT * FROM entradas WHERE estado = 'BORRADOR' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveDraft(): EntradaEntity?

    @Query("UPDATE entradas SET estado = :nuevoEstado WHERE id = :entradaId")
    suspend fun updateEstado(entradaId: Long, nuevoEstado: String)

    @Query("DELETE FROM entrada_items WHERE entrada_id = :entradaId AND codigo_barras = :codigo")
    suspend fun deleteItem(entradaId: Long, codigo: String)

    @Query("DELETE FROM entrada_items WHERE entrada_id = :entradaId")
    suspend fun deleteItemsForEntrada(entradaId: Long)

    @Query("DELETE FROM entradas WHERE id = :entradaId")
    suspend fun deleteEntrada(entradaId: Long)

    @Query("SELECT * FROM entradas ORDER BY id DESC")
    suspend fun getAllEntradas(): List<EntradaEntity>

    @Query("SELECT * FROM entrada_items WHERE entrada_id = :entradaId ORDER BY id DESC")
    suspend fun getItemsForEntrada(entradaId: Long): List<EntradaItemEntity>

    @Query("UPDATE entradas SET exportado = 1 WHERE exportado = 0")
    suspend fun markAllExported()
}

@Dao
interface DespachoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDespacho(despacho: DespachoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<DespachoItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSingleItem(item: DespachoItemEntity): Long

    @Query("SELECT * FROM despachos WHERE id = :despachoId LIMIT 1")
    suspend fun getDespachoById(despachoId: Long): DespachoEntity?

    @Query("SELECT * FROM despachos WHERE estado = 'BORRADOR' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveDraft(): DespachoEntity?

    @Query("UPDATE despachos SET estado = :nuevoEstado WHERE id = :despachoId")
    suspend fun updateEstado(despachoId: Long, nuevoEstado: String)

    @Query("DELETE FROM despacho_items WHERE despacho_id = :despachoId AND codigo_barras = :codigo")
    suspend fun deleteItem(despachoId: Long, codigo: String)

    @Query("DELETE FROM despacho_items WHERE despacho_id = :despachoId")
    suspend fun deleteItemsForDespacho(despachoId: Long)

    @Query("DELETE FROM despachos WHERE id = :despachoId")
    suspend fun deleteDespacho(despachoId: Long)

    @Query("SELECT * FROM despachos ORDER BY id DESC")
    suspend fun getAllDespachos(): List<DespachoEntity>

    @Query("SELECT * FROM despacho_items WHERE despacho_id = :despachoId ORDER BY id DESC")
    suspend fun getItemsForDespacho(despachoId: Long): List<DespachoItemEntity>

    @Query("UPDATE despachos SET exportado = 1 WHERE exportado = 0")
    suspend fun markAllExported()
}

@Dao
interface DevolucionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevolucion(devolucion: DevolucionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<DevolucionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSingleItem(item: DevolucionItemEntity): Long

    @Query("SELECT * FROM devoluciones WHERE id = :devolucionId LIMIT 1")
    suspend fun getDevolucionById(devolucionId: Long): DevolucionEntity?

    @Query("SELECT * FROM devoluciones WHERE estado = 'BORRADOR' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveDraft(): DevolucionEntity?

    @Query("UPDATE devoluciones SET estado = :nuevoEstado WHERE id = :devolucionId")
    suspend fun updateEstado(devolucionId: Long, nuevoEstado: String)

    @Query("DELETE FROM devolucion_items WHERE devolucion_id = :devolucionId AND codigo_barras = :codigo")
    suspend fun deleteItem(devolucionId: Long, codigo: String)

    @Query("DELETE FROM devolucion_items WHERE devolucion_id = :devolucionId")
    suspend fun deleteItemsForDevolucion(devolucionId: Long)

    @Query("DELETE FROM devoluciones WHERE id = :devolucionId")
    suspend fun deleteDevolucion(devolucionId: Long)

    @Query("SELECT * FROM devoluciones ORDER BY id DESC")
    suspend fun getAllDevoluciones(): List<DevolucionEntity>

    @Query("SELECT * FROM devolucion_items WHERE devolucion_id = :devolucionId ORDER BY id DESC")
    suspend fun getItemsForDevolucion(devolucionId: Long): List<DevolucionItemEntity>

    @Query("UPDATE devoluciones SET exportado = 1 WHERE exportado = 0")
    suspend fun markAllExported()
}

@Dao
interface AccesorioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVale(vale: AccesorioValeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<AccesorioItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSingleItem(item: AccesorioItemEntity): Long

    @Query("SELECT * FROM accesorios_vales WHERE id = :valeId LIMIT 1")
    suspend fun getValeById(valeId: Long): AccesorioValeEntity?

    @Query("SELECT * FROM accesorios_vales WHERE estado = 'BORRADOR' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveDraft(): AccesorioValeEntity?

    @Query("UPDATE accesorios_vales SET estado = :nuevoEstado WHERE id = :valeId")
    suspend fun updateEstado(valeId: Long, nuevoEstado: String)

    @Query("DELETE FROM accesorio_items WHERE vale_id = :valeId AND codigo_accesorio = :codigo")
    suspend fun deleteItem(valeId: Long, codigo: String)

    @Query("DELETE FROM accesorio_items WHERE vale_id = :valeId")
    suspend fun deleteItemsForVale(valeId: Long)

    @Query("DELETE FROM accesorios_vales WHERE id = :valeId")
    suspend fun deleteVale(valeId: Long)

    @Query("SELECT * FROM accesorios_vales ORDER BY id DESC")
    suspend fun getAllVales(): List<AccesorioValeEntity>

    @Query("SELECT * FROM accesorio_items WHERE vale_id = :valeId ORDER BY id DESC")
    suspend fun getItemsForVale(valeId: Long): List<AccesorioItemEntity>

    @Query("UPDATE accesorios_vales SET exportado = 1 WHERE exportado = 0")
    suspend fun markAllExported()
}
