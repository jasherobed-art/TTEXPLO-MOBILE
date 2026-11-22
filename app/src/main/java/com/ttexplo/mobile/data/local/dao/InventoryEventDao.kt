package com.ttexplo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ttexplo.mobile.data.local.entity.InventoryEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: InventoryEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<InventoryEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventsReturningRowIds(events: List<InventoryEventEntity>): List<Long>

    @Query("SELECT * FROM inventory_events WHERE is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    fun observeAllActiveEvents(): Flow<List<InventoryEventEntity>>

    @Query("SELECT * FROM inventory_events WHERE product_id = :productId AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    fun observeEventsForProduct(productId: String): Flow<List<InventoryEventEntity>>

    @Query("SELECT * FROM inventory_events WHERE is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getAllActiveEvents(): List<InventoryEventEntity>

    @Query("SELECT * FROM inventory_events WHERE sync_status = 'PENDING' AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getPendingSyncEvents(): List<InventoryEventEntity>

    @Query("SELECT * FROM inventory_events WHERE timestamp_epoch_ms >= :sinceEpochMs AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getEventsSince(sinceEpochMs: Long): List<InventoryEventEntity>

    @Query("UPDATE inventory_events SET sync_status = 'SYNCED' WHERE event_uuid IN (:uuids)")
    suspend fun markEventsAsSynced(uuids: List<String>)

    @Query("SELECT * FROM inventory_events WHERE product_id = :productId AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC, event_uuid ASC")
    suspend fun getEventsForProduct(productId: String): List<InventoryEventEntity>

    @Query("SELECT * FROM inventory_events WHERE reference_type = :referenceType AND reference_id = :referenceId AND is_voided = 0 ORDER BY timestamp_epoch_ms ASC")
    suspend fun getEventsByReference(referenceType: String, referenceId: Long): List<InventoryEventEntity>

    @Query("SELECT COALESCE(SUM(delta_quantity), 0.0) FROM inventory_events WHERE product_id = :productId AND is_voided = 0")
    suspend fun getCurrentStockForProduct(productId: String): Double

    @Query("SELECT COUNT(*) FROM inventory_events WHERE product_id = :productId AND is_voided = 0")
    suspend fun countEventsForProduct(productId: String): Int

    @Query("SELECT COUNT(*) FROM inventory_events WHERE sync_status = 'PENDING' AND is_voided = 0")
    suspend fun countPendingSyncEvents(): Int
}
