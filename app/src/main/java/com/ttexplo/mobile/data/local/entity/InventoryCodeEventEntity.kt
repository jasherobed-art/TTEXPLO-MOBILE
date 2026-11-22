package com.ttexplo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Libro mayor inmutable (append-only) de trazabilidad individual de códigos de barras SEDENA
 * para Alto Explosivo y Agente Explosivo (Cajas 30d, Sacos 18d y Cartuchos 16-17d).
 *
 * Cada evento posee un UUID v4 único globalmente y soporta `reverses_event_uuid` para revertir
 * de forma exacta e idempotente el estado de un código cuando un vale se edita o elimina,
 * manteniendo compatibilidad total con sincronización offline multi-escáner.
 */
@Entity(
    tableName = "inventory_code_events",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["product_id"]),
        Index(value = ["reference_type", "reference_id"]),
        Index(value = ["timestamp_epoch_ms"])
    ]
)
data class InventoryCodeEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_uuid")
    val eventUuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "reverses_event_uuid", defaultValue = "")
    val reversesEventUuid: String = "",

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "polvorin")
    val polvorin: String,

    @ColumnInfo(name = "action_type")
    val actionType: String, // "ENTRADA" | "DESPACHO" | "DEVOLUCION" | "REVERSAL"

    @ColumnInfo(name = "status")
    val status: String, // "EN_POLVORIN" | "DESPACHADO" | "REVERSAL"

    @ColumnInfo(name = "barcode")
    val barcode: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "presentation")
    val presentation: String,

    @ColumnInfo(name = "inventory_category")
    val inventoryCategory: String, // "ALTO_EXPLOSIVO" | "AGENTE_EXPLOSIVO"

    @ColumnInfo(name = "empaque")
    val empaque: String, // "CAJA" | "SACO" | "PIEZA"

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double = 0.0,

    @ColumnInfo(name = "reference_type")
    val referenceType: String,

    @ColumnInfo(name = "reference_id")
    val referenceId: Long = 0L,

    @ColumnInfo(name = "reference_folio")
    val referenceFolio: String = "",

    @ColumnInfo(name = "timestamp_iso")
    val timestampIso: String,

    @ColumnInfo(name = "timestamp_epoch_ms")
    val timestampEpochMs: Long,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING",

    @ColumnInfo(name = "is_voided")
    val isVoided: Boolean = false
)
