package com.ttexplo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ttexplo.mobile.domain.model.InventoryCategory
import java.util.UUID

/**
 * Libro mayor inmutable (append-only) de eventos de inventario en polvorín.
 * Cada evento posee un UUID v4 único globalmente, device_id, inventory_category (5 familias)
 * y sync_status para soportar futura sincronización offline entre múltiples escáneres Zebra.
 */
@Entity(
    tableName = "inventory_events",
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["reference_type", "reference_id"]),
        Index(value = ["timestamp_epoch_ms"])
    ]
)
data class InventoryEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_uuid")
    val eventUuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "polvorin")
    val polvorin: String,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "presentation")
    val presentation: String,

    @ColumnInfo(name = "product_type")
    val productType: String,

    @ColumnInfo(name = "inventory_category", defaultValue = "ALTO_EXPLOSIVO")
    val inventoryCategory: String = InventoryCategory.ALTO_EXPLOSIVO.name,

    @ColumnInfo(name = "unit")
    val unit: String,

    @ColumnInfo(name = "delta_quantity")
    val deltaQuantity: Double,

    @ColumnInfo(name = "cajas_delta")
    val cajasDelta: Int = 0,

    @ColumnInfo(name = "sacos_delta")
    val sacosDelta: Int = 0,

    @ColumnInfo(name = "cartuchos_delta")
    val cartuchosDelta: Int = 0,

    @ColumnInfo(name = "unit_weight_kg_used")
    val unitWeightKgUsed: Double = 0.0,

    @ColumnInfo(name = "box_weight_kg_used")
    val boxWeightKgUsed: Double = 0.0,

    @ColumnInfo(name = "reference_type")
    val referenceType: String,

    @ColumnInfo(name = "reference_id")
    val referenceId: Long = 0L,

    @ColumnInfo(name = "reference_folio")
    val referenceFolio: String = "",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "timestamp_iso")
    val timestampIso: String,

    @ColumnInfo(name = "timestamp_epoch_ms")
    val timestampEpochMs: Long,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING",

    @ColumnInfo(name = "is_voided")
    val isVoided: Boolean = false
)
