package com.ttexplo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ttexplo.mobile.data.local.entity.ConteoCajaEntity

@Dao
interface BoxCountDao {
    @Insert
    suspend fun insertAll(items: List<ConteoCajaEntity>)

    @Query("SELECT * FROM conteos_caja ORDER BY id DESC")
    suspend fun getAll(): List<ConteoCajaEntity>

    @Query("SELECT * FROM conteos_caja WHERE folio = :folio ORDER BY id DESC")
    suspend fun getByFolio(folio: String): List<ConteoCajaEntity>

    @Query("SELECT COUNT(DISTINCT folio) FROM conteos_caja WHERE folio LIKE :prefix || '%'")
    suspend fun countFolios(prefix: String): Int

    @Query("DELETE FROM conteos_caja WHERE folio = :folio")
    suspend fun deleteByFolio(folio: String)
}
