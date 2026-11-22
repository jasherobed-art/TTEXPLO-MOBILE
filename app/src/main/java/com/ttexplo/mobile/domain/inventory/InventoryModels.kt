package com.ttexplo.mobile.domain.inventory

import com.ttexplo.mobile.data.local.entity.InventoryCodeEventEntity
import com.ttexplo.mobile.data.local.entity.InventoryEventEntity
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import org.json.JSONArray
import org.json.JSONObject

enum class InventoryEventType(val label: String, val shortBadge: String) {
    INITIAL_STOCK("Inventario Inicial", "INV. INICIAL"),
    PHYSICAL_ADJUSTMENT("Ajuste Físico", "AJUSTE"),
    ENTRADA("Entrada a Polvorín", "ENTRADA"),
    DEVOLUCION("Devolución a Polvorín", "DEVOLUCIÓN"),
    DESPACHO("Despacho / Salida", "DESPACHO"),
    SALIDA_ACCESORIOS("Salida Accesorios", "SAL. ACC."),
    DEVOLUCION_ACCESORIOS("Dev. Accesorios", "DEV. ACC."),
    REVERSAL("Reverso / Edición", "REVERSO");

    companion object {
        fun fromString(raw: String): InventoryEventType =
            entries.find { it.name == raw } ?: PHYSICAL_ADJUSTMENT
    }
}

enum class InventoryCodeStatus(val label: String) {
    EN_POLVORIN("En Polvorín"),
    DESPACHADO("Despachado");

    companion object {
        fun fromString(raw: String): InventoryCodeStatus =
            entries.find { it.name == raw } ?: EN_POLVORIN
    }
}

object InventoryReferenceType {
    const val MANUAL = "MANUAL"
    const val ENTRADA = "ENTRADA"
    const val DESPACHO = "DESPACHO"
    const val DEVOLUCION = "DEVOLUCION"
    const val ACCESORIO_VALE = "ACCESORIO_VALE"
}

object InventorySyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
}

enum class QuickStockMode(val label: String) {
    SET_PHYSICAL_COUNT("Fijar Conteo Físico (=)"),
    ADD_INITIAL_STOCK("Sumar Existencia (+)")
}

/**
 * Vista agregada de existencia actual por producto en el polvorín, clasificada en una de las 5 familias SEDENA.
 */
data class InventoryStockItem(
    val productId: String,
    val productName: String,
    val presentation: String,
    val productType: ProductType,
    val category: InventoryCategory = InventoryCategory.inferCategory(productType, productId, productName),
    val unit: String, // "KG", "PZA", "MTS"
    val stockQuantity: Double,
    val boxWeightKg: Double,
    val unitWeightKg: Double,
    val cartridgesPerBox: Int,
    val lastUpdatedIso: String,
    val eventCount: Int,
    val codesInPolvorinCount: Int = 0,
    val codesDispatchedCount: Int = 0
) {
    val visualEquivalence: String
        get() = BoxUnitWeightCalculator.formatVisualEquivalence(
            stock = stockQuantity,
            unit = unit,
            productType = productType,
            boxWeightKg = boxWeightKg,
            unitWeightKg = unitWeightKg,
            cartridgesPerBox = cartridgesPerBox
        )
}

/**
 * Estado derivado actual de un código de barras SEDENA individual en Alto Explosivo o Agente Explosivo.
 */
data class InventoryCodeItem(
    val barcode: String,
    val productId: String,
    val productName: String,
    val presentation: String,
    val category: InventoryCategory,
    val empaque: TipoEmpaque,
    val status: InventoryCodeStatus,
    val weightKg: Double,
    val lastActionType: InventoryEventType,
    val lastReferenceFolio: String,
    val lastUpdatedIso: String,
    val lastEventUuid: String,
    val deviceId: String,
    val polvorin: String,
    val movementCount: Int
)

/**
 * Tablero superior con los 5 totales separados por familia regulatoria SEDENA
 * + contadores de códigos SEDENA vigentes en polvorín.
 */
data class InventoryDashboardTotals(
    val altoExplosivoKg: Double = 0.0,
    val agenteExplosivoKg: Double = 0.0,
    val cordonDetonanteMts: Double = 0.0,
    val canuelasPza: Double = 0.0,
    val detonadoresPza: Double = 0.0,
    val altoCodesInStock: Int = 0,
    val agenteCodesInStock: Int = 0,
    val activeProductCount: Int = 0,
    val calibratedUnitWeightCount: Int = 0,
    val totalEventCount: Int = 0,
    val totalCodeEventCount: Int = 0,
    val pendingSyncEventCount: Int = 0
) {
    val totalKg: Double
        get() = BoxUnitWeightCalculator.roundTo4Decimals(altoExplosivoKg + agenteExplosivoKg)
    val totalPza: Double
        get() = BoxUnitWeightCalculator.roundTo4Decimals(canuelasPza + detonadoresPza)
    val totalMts: Double
        get() = cordonDetonanteMts
}

/**
 * Registro enriquecido del Kardex por producto con saldo corrido.
 */
data class KardexEntry(
    val eventUuid: String,
    val deviceId: String,
    val polvorin: String,
    val eventType: InventoryEventType,
    val productId: String,
    val inventoryCategory: InventoryCategory,
    val unit: String,
    val deltaQuantity: Double,
    val runningBalance: Double,
    val cajasDelta: Int,
    val sacosDelta: Int,
    val cartuchosDelta: Int,
    val unitWeightKgUsed: Double,
    val referenceType: String,
    val referenceFolio: String,
    val notes: String,
    val timestampIso: String,
    val timestampEpochMs: Long,
    val syncStatus: String
)

/**
 * Exportador JSON del doble libro mayor de eventos con UUID (`events` + `code_events`)
 * listo para futura sincronización offline entre múltiples terminales Zebra.
 */
object InventorySyncJsonExporter {
    const val SCHEMA_VERSION = "ttexplo-inventory-events-v2"

    fun toSyncBundleJson(
        deviceId: String,
        polvorin: String,
        exportedAtIso: String,
        events: List<InventoryEventEntity>,
        codeEvents: List<InventoryCodeEventEntity> = emptyList(),
        prettyPrint: Boolean = true
    ): String {
        val root = JSONObject()
        root.put("schema_version", SCHEMA_VERSION)
        root.put("device_id", deviceId)
        root.put("polvorin", polvorin)
        root.put("exported_at", exportedAtIso)
        root.put("event_count", events.size)
        root.put("code_event_count", codeEvents.size)

        val arr = JSONArray()
        for (e in events) {
            val obj = JSONObject()
            obj.put("event_uuid", e.eventUuid)
            obj.put("device_id", e.deviceId)
            obj.put("polvorin", e.polvorin)
            obj.put("event_type", e.eventType)
            obj.put("product_id", e.productId)
            obj.put("product_name", e.productName)
            obj.put("presentation", e.presentation)
            obj.put("product_type", e.productType)
            obj.put("inventory_category", e.inventoryCategory)
            obj.put("unit", e.unit)
            obj.put("delta_quantity", e.deltaQuantity)
            obj.put("cajas_delta", e.cajasDelta)
            obj.put("sacos_delta", e.sacosDelta)
            obj.put("cartuchos_delta", e.cartuchosDelta)
            obj.put("unit_weight_kg_used", e.unitWeightKgUsed)
            obj.put("box_weight_kg_used", e.boxWeightKgUsed)
            obj.put("reference_type", e.referenceType)
            obj.put("reference_id", e.referenceId)
            obj.put("reference_folio", e.referenceFolio)
            obj.put("notes", e.notes)
            obj.put("timestamp_iso", e.timestampIso)
            obj.put("timestamp_epoch_ms", e.timestampEpochMs)
            obj.put("sync_status", e.syncStatus)
            arr.put(obj)
        }
        root.put("events", arr)

        val codeArr = JSONArray()
        for (c in codeEvents) {
            val obj = JSONObject()
            obj.put("event_uuid", c.eventUuid)
            obj.put("reverses_event_uuid", c.reversesEventUuid)
            obj.put("device_id", c.deviceId)
            obj.put("polvorin", c.polvorin)
            obj.put("action_type", c.actionType)
            obj.put("status", c.status)
            obj.put("barcode", c.barcode)
            obj.put("product_id", c.productId)
            obj.put("product_name", c.productName)
            obj.put("presentation", c.presentation)
            obj.put("inventory_category", c.inventoryCategory)
            obj.put("empaque", c.empaque)
            obj.put("weight_kg", c.weightKg)
            obj.put("reference_type", c.referenceType)
            obj.put("reference_id", c.referenceId)
            obj.put("reference_folio", c.referenceFolio)
            obj.put("timestamp_iso", c.timestampIso)
            obj.put("timestamp_epoch_ms", c.timestampEpochMs)
            obj.put("sync_status", c.syncStatus)
            codeArr.put(obj)
        }
        root.put("code_events", codeArr)

        return if (prettyPrint) root.toString(2) else root.toString()
    }
}
