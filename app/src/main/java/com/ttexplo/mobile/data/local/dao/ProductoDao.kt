package com.ttexplo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {

    @Query("SELECT * FROM productos WHERE id_producto = :id LIMIT 1")
    suspend fun getById(id: String): ProductoEntity?

    @Query("SELECT * FROM productos WHERE cod_barras = :codBarras LIMIT 1")
    suspend fun getByCodBarras(codBarras: String): ProductoEntity?

    @Query("SELECT * FROM productos ORDER BY id_producto ASC")
    fun getAllFlow(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos ORDER BY id_producto ASC")
    suspend fun getAll(): List<ProductoEntity>

    @Query("SELECT COUNT(*) FROM productos")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(producto: ProductoEntity)

    @Query("DELETE FROM productos WHERE id_producto = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM productos WHERE id_producto LIKE '%' || :query || '%' OR producto LIKE '%' || :query || '%' ORDER BY id_producto ASC")
    suspend fun search(query: String): List<ProductoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(productos: List<ProductoEntity>)

    @Query("UPDATE productos SET peso_unitario_kg = :unitWeightKg, cartuchos_por_caja = :cartridgesPerBox WHERE id_producto = :id")
    suspend fun updateUnitWeight(id: String, unitWeightKg: Double, cartridgesPerBox: Int)

    @Query("DELETE FROM productos")
    suspend fun deleteAll()
}
