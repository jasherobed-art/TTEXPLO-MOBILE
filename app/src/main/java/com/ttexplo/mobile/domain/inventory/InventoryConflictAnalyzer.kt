package com.ttexplo.mobile.domain.inventory

import com.ttexplo.mobile.data.local.entity.InventoryCodeEventEntity
import com.ttexplo.mobile.data.local.entity.InventoryEventEntity
import com.ttexplo.mobile.data.repository.InventoryRepository
import com.ttexplo.mobile.domain.model.InventoryCategory
import kotlin.math.abs

enum class SyncConflictSeverity(val label: String) {
    CRITICAL("CRÍTICO"),
    WARNING("ADVERTENCIA"),
    ALERT("ALERTA")
}

enum class SyncConflictType(val label: String) {
    DOUBLE_DISPATCH_SEDENA_CODE("Doble Despacho de Serie SEDENA"),
    CONCURRENT_PHYSICAL_COUNT("Conteo Físico Concurrente"),
    NEGATIVE_STOCK_BALANCE("Saldo Negativo Resultante")
}

data class SyncConflict(
    val conflictId: String,
    val type: SyncConflictType,
    val severity: SyncConflictSeverity,
    val productId: String,
    val productName: String,
    val category: InventoryCategory,
    val title: String,
    val detail: String,
    val involvedDevices: List<String>,
    val barcode: String? = null,
    val detectedAtIso: String = ""
)

data class SyncExportPackage(
    val scope: SyncExportScope,
    val jsonCompact: String,
    val jsonPretty: String,
    val qrPackage: QrSyncPackage,
    val eventUuids: List<String>,
    val codeEventUuids: List<String>,
    val deviceId: String,
    val polvorin: String
)

data class SyncMergeReport(
    val sessionUuid: String,
    val sourceDirection: String,
    val remoteDeviceId: String,
    val polvorin: String,
    val eventsMerged: Int,
    val eventsSkippedDuplicate: Int,
    val codeEventsMerged: Int,
    val codeEventsSkippedDuplicate: Int,
    val catalogCalibrationsUpdated: Int,
    val conflicts: List<SyncConflict>,
    val timestampIso: String
)

/**
 * Analizador determinista de conflictos multi-escáner sobre el doble libro mayor de inventario.
 *
 * Nunca descarta datos reales de campo: fusiona por UUID sin duplicar y reporta de forma explícita:
 * 1. Doble despacho del mismo código SEDENA en 2 terminales (o sin devolución intermedia).
 * 2. Conteos físicos / ajustes concurrentes hechos por 2 terminales distintas sobre el mismo SKU en <= 24h.
 * 3. Saldos negativos resultantes tras fusión parcial (típico cuando falta importar una Entrada).
 */
object InventoryConflictAnalyzer {

    private const val CONCURRENT_COUNT_WINDOW_MS = 24L * 60L * 60L * 1000L

    fun analyze(
        events: List<InventoryEventEntity>,
        codeEvents: List<InventoryCodeEventEntity>,
        stockItems: List<InventoryStockItem>
    ): List<SyncConflict> {
        val conflicts = mutableListOf<SyncConflict>()

        // 1. DOBLE DESPACHO DE SERIE SEDENA (CRÍTICO)
        val activeCodes = InventoryRepository.filterUnreversedCodeEvents(codeEvents)
        val byBarcode = activeCodes.groupBy { it.barcode.trim() }

        for ((barcode, history) in byBarcode) {
            if (barcode.isEmpty()) continue
            val chronological = history.sortedWith(
                compareBy<InventoryCodeEventEntity> { it.timestampEpochMs }
                    .thenBy { it.eventUuid }
            )

            var lastDispatch: InventoryCodeEventEntity? = null
            for (ev in chronological) {
                val status = InventoryCodeStatus.fromString(ev.status)
                if (status == InventoryCodeStatus.EN_POLVORIN) {
                    // Una entrada o devolución intermedia habilita un nuevo despacho legítimo
                    lastDispatch = null
                } else if (status == InventoryCodeStatus.DESPACHADO) {
                    if (lastDispatch != null) {
                        val first = lastDispatch
                        val devices = listOf(first.deviceId, ev.deviceId).distinct()
                        val cat = InventoryCategory.fromStringOrNull(ev.inventoryCategory)
                            ?: InventoryCategory.ALTO_EXPLOSIVO
                        val crossTerminalText = if (devices.size > 1) {
                            "Despachado en 2 terminales (${first.deviceId} y ${ev.deviceId})"
                        } else {
                            "Despachado 2 veces en ${ev.deviceId} sin devolución intermedia"
                        }
                        val detail = "$crossTerminalText • Folios: ${first.referenceFolio.ifBlank { "S/F" }} (${first.timestampIso.take(16)}) y ${ev.referenceFolio.ifBlank { "S/F" }} (${ev.timestampIso.take(16)}). Verifique físicamente cuál vale debe corregirse."
                        conflicts.add(
                            SyncConflict(
                                conflictId = "DOUBLE_DISPATCH|${barcode}|${first.eventUuid.take(8)}|${ev.eventUuid.take(8)}",
                                type = SyncConflictType.DOUBLE_DISPATCH_SEDENA_CODE,
                                severity = SyncConflictSeverity.CRITICAL,
                                productId = ev.productId,
                                productName = ev.productName,
                                category = cat,
                                title = "Serie SEDENA con doble despacho: $barcode",
                                detail = detail,
                                involvedDevices = devices,
                                barcode = barcode,
                                detectedAtIso = ev.timestampIso
                            )
                        )
                    }
                    lastDispatch = ev
                }
            }
        }

        // 2. CONTEOS FÍSICOS CONCURRENTES EN 2+ TERMINALES (ADVERTENCIA)
        val physicalCounts = events.filter {
            it.eventType == InventoryEventType.INITIAL_STOCK.name ||
                it.eventType == InventoryEventType.PHYSICAL_ADJUSTMENT.name
        }
        val countsByProduct = physicalCounts.groupBy { it.productId.trim().uppercase() }
        for ((productId, prodCounts) in countsByProduct) {
            val sorted = prodCounts.sortedWith(
                compareBy<InventoryEventEntity> { it.timestampEpochMs }
                    .thenBy { it.eventUuid }
            )
            var foundForProduct = false
            for (i in 0 until sorted.size) {
                if (foundForProduct) break
                for (j in i + 1 until sorted.size) {
                    val a = sorted[i]
                    val b = sorted[j]
                    if (a.deviceId != b.deviceId && abs(b.timestampEpochMs - a.timestampEpochMs) <= CONCURRENT_COUNT_WINDOW_MS) {
                        val cat = InventoryCategory.fromStringOrNull(b.inventoryCategory)
                            ?: InventoryCategory.ALTO_EXPLOSIVO
                        val devices = listOf(a.deviceId, b.deviceId).distinct()
                        val deltaA = formatDeltaWithUnit(a.deltaQuantity, a.unit)
                        val deltaB = formatDeltaWithUnit(b.deltaQuantity, b.unit)
                        conflicts.add(
                            SyncConflict(
                                conflictId = "CONCURRENT_COUNT|$productId|${a.eventUuid.take(8)}|${b.eventUuid.take(8)}",
                                type = SyncConflictType.CONCURRENT_PHYSICAL_COUNT,
                                severity = SyncConflictSeverity.WARNING,
                                productId = productId,
                                productName = b.productName,
                                category = cat,
                                title = "Conteo físico paralelo en 2 escáneres (${b.productName})",
                                detail = "${a.deviceId} registró $deltaA (${a.timestampIso.take(16)}) y ${b.deviceId} registró $deltaB (${b.timestampIso.take(16)}) en menos de 24h. Confirme la existencia física real con un ajuste final.",
                                involvedDevices = devices,
                                detectedAtIso = b.timestampIso
                            )
                        )
                        foundForProduct = true
                        break
                    }
                }
            }
        }

        // 3. SALDO NEGATIVO RESULTANTE (ALERTA)
        for (item in stockItems) {
            if (item.stockQuantity < -1e-6) {
                val formatted = if (item.unit == "PZA") {
                    "${BoxUnitWeightCalculator.formatPieces(item.stockQuantity)} PZA"
                } else {
                    "${BoxUnitWeightCalculator.formatDecimal2(item.stockQuantity)} ${item.unit}"
                }
                conflicts.add(
                    SyncConflict(
                        conflictId = "NEGATIVE_STOCK|${item.productId}",
                        type = SyncConflictType.NEGATIVE_STOCK_BALANCE,
                        severity = SyncConflictSeverity.ALERT,
                        productId = item.productId,
                        productName = item.productName,
                        category = item.category,
                        title = "Saldo negativo tras fusión: ${item.productName} ($formatted)",
                        detail = "El saldo neto cayó por debajo de cero. Es común cuando se importa un Despacho antes de sincronizar la Entrada o Carga Inicial de otro escáner.",
                        involvedDevices = emptyList(),
                        detectedAtIso = item.lastUpdatedIso
                    )
                )
            }
        }

        return conflicts.sortedWith(
            compareBy<SyncConflict> { it.severity.ordinal }
                .thenBy { it.productId }
        )
    }

    private fun formatDeltaWithUnit(delta: Double, unit: String): String {
        val sign = if (delta >= 0) "+" else ""
        val num = if (unit.equals("PZA", ignoreCase = true)) {
            BoxUnitWeightCalculator.formatPieces(delta)
        } else {
            BoxUnitWeightCalculator.formatDecimal2(delta)
        }
        return "$sign$num $unit"
    }
}
