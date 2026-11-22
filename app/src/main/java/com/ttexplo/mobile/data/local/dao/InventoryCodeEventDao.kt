package com.ttexplo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ttexplo.mobile.data.local.entity.InventoryCodeEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryCodeEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: InventoryCodeEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<InventoryCodeEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventsReturningRowIds(events: List<InventoryCodeEventEntity>): List<Long>

    @Query("SELECT * FROM inventory_code_events WHERE is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    fun observeAllActiveCodeEvents(): Flow<List<InventoryCodeEventEntity>>

    @Query("SELECT * FROM inventory_code_events WHERE product_id = :productId AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    fun observeCodeEventsForProduct(productId: String): Flow<List<InventoryCodeEventEntity>>

    @Query("SELECT * FROM inventory_code_events WHERE is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getAllActiveCodeEvents(): List<InventoryCodeEventEntity>

    @Query("SELECT * FROM inventory_code_events WHERE sync_status = 'PENDING' AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getPendingSyncCodeEvents(): List<InventoryCodeEventEntity>

    @Query("SELECT * FROM inventory_code_events WHERE timestamp_epoch_ms >= :sinceEpochMs AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getCodeEventsSince(sinceEpochMs: Long): List<InventoryCodeEventEntity>

    @Query("UPDATE inventory_code_events SET sync_status = 'SYNCED' WHERE event_uuid IN (:uuids)")
    suspend fun markCodeEventsAsSynced(uuids: List<String>)

    @Query("SELECT * FROM inventory_code_events WHERE product_id = :productId AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getCodeEventsForProduct(productId: String): List<InventoryCodeEventEntity>

    @Query("SELECT * FROM inventory_code_events WHERE reference_type = :referenceType AND reference_id = :referenceId AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getCodeEventsByReference(referenceType: String, referenceId: Long): List<InventoryCodeEventEntity>
}
