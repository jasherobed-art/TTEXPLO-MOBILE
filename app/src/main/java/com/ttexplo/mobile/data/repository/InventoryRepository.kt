package com.ttexplo.mobile.data.repository

import com.ttexplo.mobile.data.local.dao.InventoryCodeEventDao
import com.ttexplo.mobile.data.local.dao.InventoryEventDao
import com.ttexplo.mobile.data.local.dao.ProductoDao
import com.ttexplo.mobile.data.local.dao.SyncSessionDao
import com.ttexplo.mobile.data.local.dao.ValeDeletionDao
import com.ttexplo.mobile.data.local.entity.InventoryCodeEventEntity
import com.ttexplo.mobile.data.local.entity.InventoryEventEntity
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.local.entity.SyncSessionDirection
import com.ttexplo.mobile.data.local.entity.SyncSessionEntity
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.inventory.InventoryCodeItem
import com.ttexplo.mobile.domain.inventory.InventoryCodeStatus
import com.ttexplo.mobile.domain.inventory.InventoryConflictAnalyzer
import com.ttexplo.mobile.domain.inventory.InventoryDashboardTotals
import com.ttexplo.mobile.domain.inventory.InventoryEventType
import com.ttexplo.mobile.domain.inventory.InventoryReferenceType
import com.ttexplo.mobile.domain.inventory.InventoryStockItem
import com.ttexplo.mobile.domain.inventory.InventorySyncJsonExporter
import com.ttexplo.mobile.domain.inventory.InventorySyncJsonParser
import com.ttexplo.mobile.domain.inventory.InventorySyncStatus
import com.ttexplo.mobile.domain.inventory.KardexEntry
import com.ttexplo.mobile.domain.inventory.QrDensityMode
import com.ttexplo.mobile.domain.inventory.QrSyncCodec
import com.ttexplo.mobile.domain.inventory.QuickStockMode
import com.ttexplo.mobile.domain.inventory.SyncConflict
import com.ttexplo.mobile.domain.inventory.SyncExportPackage
import com.ttexplo.mobile.domain.inventory.SyncExportScope
import com.ttexplo.mobile.domain.inventory.SyncMergeReport
import com.ttexplo.mobile.domain.inventory.SyncTabularV3Codec
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Repositorio central de Inventario en Polvorín basado en doble libro mayor inmutable con UUID v4:
 *
 * 1. `inventory_events`: Libro de cantidades separado en las 5 familias regulatorias SEDENA
 *    (Alto Explosivo KG, Agente Explosivo KG, Cordón Detonante MTS, Cañuelas PZA, Detonadores PZA).
 * 2. `inventory_code_events`: Libro individual de códigos de barras SEDENA (30d, 18d, 16-17d)
 *    para Alto Explosivo y Agente Explosivo (`EN_POLVORIN` vs `DESPACHADO`).
 *
 * Las ediciones o eliminaciones de vales emiten eventos compensatorios `REVERSAL` con nuevos UUIDs
 * en ambos libros, preservando trazabilidad exacta y sincronización 100% offline multi-escáner (QR Zebra / JSON).
 */
class InventoryRepository(
    private val eventDao: InventoryEventDao,
    private val codeEventDao: InventoryCodeEventDao,
    private val productoDao: ProductoDao,
    private val deviceIdProvider: () -> String,
    private val polvorinProvider: () -> String,
    private val valeDeletionDao: ValeDeletionDao,
    private val syncSessionDao: SyncSessionDao? = null
) {

    /**
     * Flujo reactivo en vivo para la pantalla de Inventario:
     * combina el libro de cantidades, el libro de códigos SEDENA y el catálogo para entregar
     * los 5 totales separados por familia + tarjetas por producto con equivalencia visual y códigos.
     */
    fun observeInventoryOverview(): Flow<Pair<InventoryDashboardTotals, List<InventoryStockItem>>> {
        return combine(
            eventDao.observeAllActiveEvents(),
            codeEventDao.observeAllActiveCodeEvents(),
            productoDao.getAllFlow()
        ) { events, codeEvents, catalogProducts ->
            buildOverview(events, codeEvents, catalogProducts)
        }
    }

    /**
     * Flujo reactivo del Kardex de un producto con saldo corrido cronológico (retornado del más reciente al más antiguo).
     */
    fun observeProductKardex(productId: String): Flow<List<KardexEntry>> {
        return eventDao.observeEventsForProduct(productId).map { chronologicalEvents ->
            var running = 0.0
            val entries = chronologicalEvents.map { e ->
                running = BoxUnitWeightCalculator.roundTo4Decimals(running + e.deltaQuantity)
                val cat = InventoryCategory.fromStringOrNull(e.inventoryCategory)
                    ?: InventoryCategory.inferCategory(
                        productType = safeProductType(e.productType, e.productId),
                        codeOrId = e.productId,
                        productName = e.productName,
                        unit = e.unit
                    )
                KardexEntry(
                    eventUuid = e.eventUuid,
                    deviceId = e.deviceId,
                    polvorin = e.polvorin,
                    eventType = InventoryEventType.fromString(e.eventType),
                    productId = e.productId,
                    inventoryCategory = cat,
                    unit = e.unit,
                    deltaQuantity = e.deltaQuantity,
                    runningBalance = running,
                    cajasDelta = e.cajasDelta,
                    sacosDelta = e.sacosDelta,
                    cartuchosDelta = e.cartuchosDelta,
                    unitWeightKgUsed = e.unitWeightKgUsed,
                    referenceType = e.referenceType,
                    referenceFolio = e.referenceFolio,
                    notes = e.notes,
                    timestampIso = e.timestampIso,
                    timestampEpochMs = e.timestampEpochMs,
                    syncStatus = e.syncStatus
                )
            }
            entries.asReversed()
        }
    }

    /**
     * Flujo reactivo del inventario individual de códigos SEDENA para un producto
     * (Alto Explosivo o Agente Explosivo), derivando el estado actual (`EN_POLVORIN` / `DESPACHADO`).
     */
    fun observeProductCodes(productId: String): Flow<List<InventoryCodeItem>> {
        return codeEventDao.observeCodeEventsForProduct(productId.trim().uppercase()).map { events ->
            deriveActiveCodeItems(events)
        }
    }

    suspend fun getProductCodes(productId: String): List<InventoryCodeItem> {
        return deriveActiveCodeItems(codeEventDao.getCodeEventsForProduct(productId.trim().uppercase()))
    }

    suspend fun getCurrentStockForProduct(productId: String): Double {
        return BoxUnitWeightCalculator.roundTo4Decimals(eventDao.getCurrentStockForProduct(productId))
    }

    /**
     * Carga rápida de Inventario Inicial o Ajuste Físico en piso.
     * Permite fijar existencia exacta (calculando la diferencia automática) o sumar existencias
     * dentro de cualquiera de las 5 familias SEDENA.
     */
    suspend fun recordQuickPhysicalCount(
        productId: String,
        productName: String,
        presentation: String,
        productType: ProductType,
        unit: String,
        mode: QuickStockMode,
        targetQuantity: Double,
        inventoryCategory: InventoryCategory? = null,
        cajasCount: Int = 0,
        sacosCount: Int = 0,
        cartuchosCount: Int = 0,
        boxWeightKg: Double = 0.0,
        unitWeightKg: Double = 0.0,
        cartridgesPerBox: Int = 0,
        notes: String = "",
        polvorinOverride: String? = null
    ): InventoryEventEntity? {
        val cleanId = productId.trim().uppercase()
        if (cleanId.isEmpty()) return null

        val resolvedCategory = inventoryCategory ?: InventoryCategory.inferCategory(
            productType = productType,
            codeOrId = cleanId,
            productName = productName,
            unit = unit
        )
        val resolvedType = resolvedCategory.toProductType()
        val normalizedUnit = unit.trim().uppercase().ifBlank { resolvedCategory.defaultUnit }

        // Asegura que el producto exista o actualice sus parámetros de familia, peso y unidad en catálogo
        val existingProd = productoDao.getById(cleanId)
        if (existingProd == null) {
            productoDao.insert(
                ProductoEntity(
                    idProducto = cleanId,
                    producto = productName.trim().ifBlank { cleanId },
                    presentacion = presentation.trim().ifBlank { if (resolvedType == ProductType.ACCESORIO) normalizedUnit else "" },
                    noSap = "",
                    unidad = normalizedUnit,
                    cantidad = if (cartridgesPerBox > 0) cartridgesPerBox.toDouble() else 0.0,
                    codBarras = cleanId,
                    peso = boxWeightKg,
                    pg = "",
                    pu = 0.0,
                    tipo = resolvedType.name,
                    pesoUnitarioKg = unitWeightKg,
                    cartuchosPorCaja = cartridgesPerBox,
                    categoriaInventario = resolvedCategory.name
                )
            )
        } else {
            val updatedWeight = if (boxWeightKg > 0.0) boxWeightKg else existingProd.peso
            val updatedCarts = if (cartridgesPerBox > 0) cartridgesPerBox else existingProd.cartuchosPorCaja
            val updatedUnitW = when {
                unitWeightKg > 0.0 -> unitWeightKg
                updatedCarts > 0 && updatedWeight > 0.0 ->
                    BoxUnitWeightCalculator.calculateUnitWeightKg(updatedWeight, updatedCarts) ?: existingProd.pesoUnitarioKg
                else -> existingProd.pesoUnitarioKg
            }
            val updatedUnit = if (resolvedType == ProductType.ACCESORIO) normalizedUnit else existingProd.unidad
            val isGenericAccName = existingProd.producto.startsWith("Accesorio (") ||
                existingProd.producto.startsWith("Detonadores (") ||
                existingProd.producto.startsWith("Cordón Detonante (") ||
                existingProd.producto.startsWith("Cañuelas (")
            val updatedName = if (productName.isNotBlank() && isGenericAccName) productName.trim() else existingProd.producto
            val updatedCategory = resolvedCategory.name

            if (updatedWeight != existingProd.peso ||
                updatedCarts != existingProd.cartuchosPorCaja ||
                updatedUnitW != existingProd.pesoUnitarioKg ||
                updatedUnit != existingProd.unidad ||
                updatedName != existingProd.producto ||
                updatedCategory != existingProd.categoriaInventario
            ) {
                productoDao.insert(
                    existingProd.copy(
                        producto = updatedName,
                        unidad = updatedUnit,
                        peso = updatedWeight,
                        pesoUnitarioKg = updatedUnitW,
                        cartuchosPorCaja = updatedCarts,
                        categoriaInventario = updatedCategory
                    )
                )
            }
        }

        val currentStock = getCurrentStockForProduct(cleanId)
        val priorEventCount = eventDao.countEventsForProduct(cleanId)
        val roundedTarget = BoxUnitWeightCalculator.roundTo4Decimals(targetQuantity.coerceAtLeast(0.0))

        val (eventType, delta) = when (mode) {
            QuickStockMode.ADD_INITIAL_STOCK -> {
                if (roundedTarget <= 0.0) return null
                InventoryEventType.INITIAL_STOCK to roundedTarget
            }
            QuickStockMode.SET_PHYSICAL_COUNT -> {
                val diff = BoxUnitWeightCalculator.roundTo4Decimals(roundedTarget - currentStock)
                if (abs(diff) < 1e-6 && priorEventCount > 0) return null
                val type = if (priorEventCount == 0) InventoryEventType.INITIAL_STOCK else InventoryEventType.PHYSICAL_ADJUSTMENT
                type to diff
            }
        }

        val breakdownText = buildQuickCountSummaryNote(
            category = resolvedCategory,
            unit = normalizedUnit,
            cajas = cajasCount,
            sacos = sacosCount,
            cartuchos = cartuchosCount,
            total = roundedTarget,
            mode = mode,
            userNote = notes
        )

        val nowMs = System.currentTimeMillis()
        val event = InventoryEventEntity(
            eventUuid = UUID.randomUUID().toString(),
            deviceId = deviceIdProvider(),
            polvorin = polvorinOverride?.takeIf { it.isNotBlank() } ?: polvorinProvider(),
            eventType = eventType.name,
            productId = cleanId,
            productName = productName.trim().ifBlank { existingProd?.producto ?: cleanId },
            presentation = presentation.trim().ifBlank { existingProd?.presentacion ?: "" },
            productType = resolvedType.name,
            inventoryCategory = resolvedCategory.name,
            unit = normalizedUnit,
            deltaQuantity = delta,
            cajasDelta = if (mode == QuickStockMode.ADD_INITIAL_STOCK) cajasCount else 0,
            sacosDelta = if (mode == QuickStockMode.ADD_INITIAL_STOCK) sacosCount else 0,
            cartuchosDelta = if (mode == QuickStockMode.ADD_INITIAL_STOCK) cartuchosCount else 0,
            unitWeightKgUsed = unitWeightKg,
            boxWeightKgUsed = boxWeightKg,
            referenceType = InventoryReferenceType.MANUAL,
            referenceId = 0L,
            referenceFolio = if (eventType == InventoryEventType.INITIAL_STOCK) "INV-INICIAL" else "AJUSTE-FISICO",
            notes = breakdownText,
            timestampIso = DateTimeUtils.currentTimestamp(),
            timestampEpochMs = nowMs,
            syncStatus = InventorySyncStatus.PENDING
        )

        eventDao.insertEvent(event)
        return event
    }

    /**
     * Registra automáticamente las existencias sumadas por una Entrada finalizada
     * y da de alta cada código SEDENA (30d/18d/16-17d) como `EN_POLVORIN`.
     */
    suspend fun recordEntrada(
        entradaId: Long,
        proveedor: String,
        polvorin: String,
        factura: String,
        items: List<BarcodeItem>
    ) {
        val folio = buildString {
            append("PRV").append(proveedor.trim())
            if (factura.isNotBlank()) append(" / F:").append(factura.trim())
        }
        val (plannedQtyEvents, plannedCodeEvents) = buildEventsAndCodesFromScans(
            items = items,
            sign = +1.0,
            eventType = InventoryEventType.ENTRADA,
            codeTargetStatus = InventoryCodeStatus.EN_POLVORIN,
            referenceType = InventoryReferenceType.ENTRADA,
            referenceId = entradaId,
            referenceFolio = folio,
            polvorinOverride = polvorin
        )
        val editReason = "Edición de Entrada $folio"
        reconcileReferenceEvents(
            referenceType = InventoryReferenceType.ENTRADA,
            referenceId = entradaId,
            newEvents = plannedQtyEvents,
            editReason = editReason
        )
        reconcileReferenceCodeEvents(
            referenceType = InventoryReferenceType.ENTRADA,
            referenceId = entradaId,
            newCodeEvents = plannedCodeEvents,
            editReason = editReason
        )
    }

    /**
     * Descuenta automáticamente existencias al finalizar un Despacho (cajas/sacos por peso empaque
     * y cartuchos sueltos por su peso unitario en kg) y marca cada código SEDENA como `DESPACHADO`.
     */
    suspend fun recordDespacho(
        despachoId: Long,
        vale: String,
        empresa: String,
        lugar: String,
        items: List<BarcodeItem>
    ) {
        val folio = if (vale.startsWith("REF", ignoreCase = true)) vale else "REF$vale"
        val contextNote = listOf(empresa.trim(), lugar.trim()).filter { it.isNotBlank() }.joinToString(" • ")
        val (plannedQtyEvents, plannedCodeEvents) = buildEventsAndCodesFromScans(
            items = items,
            sign = -1.0,
            eventType = InventoryEventType.DESPACHO,
            codeTargetStatus = InventoryCodeStatus.DESPACHADO,
            referenceType = InventoryReferenceType.DESPACHO,
            referenceId = despachoId,
            referenceFolio = folio,
            extraNote = contextNote
        )
        val editReason = "Edición de Despacho $folio"
        reconcileReferenceEvents(
            referenceType = InventoryReferenceType.DESPACHO,
            referenceId = despachoId,
            newEvents = plannedQtyEvents,
            editReason = editReason
        )
        reconcileReferenceCodeEvents(
            referenceType = InventoryReferenceType.DESPACHO,
            referenceId = despachoId,
            newCodeEvents = plannedCodeEvents,
            editReason = editReason
        )
    }

    /**
     * Suma automáticamente existencias al guardar una Devolución a Polvorín (KG, PZA o MTS)
     * y reingresa cada código SEDENA de Alto/Agente al estado `EN_POLVORIN`.
     */
    suspend fun recordDevolucion(
        devolucionId: Long,
        empleadoEntrega: String,
        empleadoRecibe: String,
        items: List<BarcodeItem>
    ) {
        val folio = if (empleadoEntrega.startsWith("DEV", ignoreCase = true)) empleadoEntrega else "DEV$empleadoEntrega"
        val note = if (empleadoRecibe.isNotBlank()) "Recibe: ${empleadoRecibe.trim()}" else ""
        val (plannedQtyEvents, plannedCodeEvents) = buildEventsAndCodesFromScans(
            items = items,
            sign = +1.0,
            eventType = InventoryEventType.DEVOLUCION,
            codeTargetStatus = InventoryCodeStatus.EN_POLVORIN,
            referenceType = InventoryReferenceType.DEVOLUCION,
            referenceId = devolucionId,
            referenceFolio = folio,
            extraNote = note
        )
        val editReason = "Edición de Devolución $folio"
        reconcileReferenceEvents(
            referenceType = InventoryReferenceType.DEVOLUCION,
            referenceId = devolucionId,
            newEvents = plannedQtyEvents,
            editReason = editReason
        )
        reconcileReferenceCodeEvents(
            referenceType = InventoryReferenceType.DEVOLUCION,
            referenceId = devolucionId,
            newCodeEvents = plannedCodeEvents,
            editReason = editReason
        )
    }

    /**
     * Descuenta (Salida) o suma (Devolución) accesorios en su familia exacta:
     * Cordón Detonante (`MTS`), Cañuelas (`PZA`) o Detonadores (`PZA`).
     * En AccesoriosViewModel: salida tiene quantity > 0 -> delta = -quantity;
     * devolución tiene quantity < 0 -> delta = +abs(quantity) = -quantity.
     */
    suspend fun recordAccesoriosVale(
        valeId: Long,
        vale: String,
        empresa: String,
        lugar: String,
        isDevolucion: Boolean,
        items: List<AccessoryItemRecord>
    ) {
        val folio = if (vale.startsWith("ACC", ignoreCase = true)) vale else "ACC$vale"
        val cleanEmpresa = empresa.removePrefix("DEV:").trim()
        val extraNote = listOf(cleanEmpresa, lugar.trim()).filter { it.isNotBlank() }.joinToString(" • ")

        val nowMs = System.currentTimeMillis()
        val nowIso = DateTimeUtils.currentTimestamp()
        val deviceId = deviceIdProvider()
        val polvorin = polvorinProvider()

        val grouped = items.groupBy { it.code.trim().uppercase() }
        val planned = grouped.mapNotNull { (code, group) ->
            val netValeQty = group.sumOf { it.quantity }
            if (netValeQty == 0) return@mapNotNull null

            val inventoryDelta = -netValeQty.toDouble()
            val catalogProd = productoDao.getById(code) ?: productoDao.getByCodBarras(code)
            val resolvedCategory = catalogProd?.resolvedCategory()?.takeIf { !it.supportsBarcodeLedger }
                ?: InventoryCategory.inferCategory(
                    productType = ProductType.ACCESORIO,
                    codeOrId = code,
                    productName = catalogProd?.producto.orEmpty(),
                    unit = catalogProd?.unidad.orEmpty()
                )
            val resolvedUnit = catalogProd?.unidad?.trim()?.uppercase()
                ?.takeIf { it == "PZA" || it == "MTS" } ?: resolvedCategory.defaultUnit
            val resolvedName = catalogProd?.producto?.trim()?.ifBlank { "${resolvedCategory.label} ($code)" }
                ?: "${resolvedCategory.label} ($code)"
            val resolvedPres = catalogProd?.presentacion?.trim()?.ifBlank { resolvedUnit } ?: resolvedUnit

            val eventType = if (inventoryDelta > 0) {
                InventoryEventType.DEVOLUCION_ACCESORIOS
            } else {
                InventoryEventType.SALIDA_ACCESORIOS
            }

            val qtyText = if (resolvedUnit == "MTS") {
                "${BoxUnitWeightCalculator.formatDecimal2(abs(inventoryDelta))} MTS"
            } else {
                "${BoxUnitWeightCalculator.formatPieces(abs(inventoryDelta))} PZA"
            }
            val note = listOf(qtyText, extraNote).filter { it.isNotBlank() }.joinToString(" · ")

            InventoryEventEntity(
                eventUuid = UUID.randomUUID().toString(),
                deviceId = deviceId,
                polvorin = polvorin,
                eventType = eventType.name,
                productId = code,
                productName = resolvedName,
                presentation = resolvedPres,
                productType = ProductType.ACCESORIO.name,
                inventoryCategory = resolvedCategory.name,
                unit = resolvedUnit,
                deltaQuantity = BoxUnitWeightCalculator.roundTo4Decimals(inventoryDelta),
                referenceType = InventoryReferenceType.ACCESORIO_VALE,
                referenceId = valeId,
                referenceFolio = folio,
                notes = note,
                timestampIso = nowIso,
                timestampEpochMs = nowMs,
                syncStatus = InventorySyncStatus.PENDING
            )
        }

        reconcileReferenceEvents(
            referenceType = InventoryReferenceType.ACCESORIO_VALE,
            referenceId = valeId,
            newEvents = planned,
            editReason = if (isDevolucion) "Edición de Dev. Accesorios $folio" else "Edición de Accesorios $folio"
        )
    }

    /**
     * Emite eventos compensatorios `REVERSAL` con nuevos UUIDs tanto en el libro de cantidades
     * como en el libro de códigos SEDENA cuando se elimina un vale desde Historial.
     */
    suspend fun reverseReferenceEvents(
        referenceType: String,
        referenceId: Long,
        reason: String
    ) {
        if (referenceId <= 0L) return

        val (reversals, codeReversals) = buildReversalsForReference(referenceType, referenceId, reason)
        if (reversals.isNotEmpty()) eventDao.insertEvents(reversals)
        if (codeReversals.isNotEmpty()) codeEventDao.insertEvents(codeReversals)
    }

    /**
     * Única entrada atómica para borrar un vale: construye los reversos y los escribe junto con
     * el borrado de cabecera+hijos en una sola transacción de Room. Si falla cualquier borrado,
     * ningún evento compensatorio queda insertado y el vale conserva sus hijos.
     */
    suspend fun deleteValeAndReverse(
        referenceType: String,
        referenceId: Long,
        reason: String
    ) {
        if (referenceId <= 0L) return
        val (reversals, codeReversals) = buildReversalsForReference(referenceType, referenceId, reason)
        valeDeletionDao.deleteValeAndReverse(referenceType, referenceId, reversals, codeReversals)
    }

    private suspend fun buildReversalsForReference(
        referenceType: String,
        referenceId: Long,
        reason: String
    ): Pair<List<InventoryEventEntity>, List<InventoryCodeEventEntity>> {
        val reversals = buildReversalEvents(eventDao.getEventsByReference(referenceType, referenceId), reason)
        val codeReversals = buildCodeReversalEvents(
            codeEventDao.getCodeEventsByReference(referenceType, referenceId),
            reason
        )
        return reversals to codeReversals
    }

    /**
     * Exporta ambos libros mayores de eventos con UUID (`events` + `code_events`) en JSON v2
     * listo para sincronización offline entre múltiples terminales Zebra.
     */
    suspend fun exportEventsAsSyncJson(): String {
        val allEvents = eventDao.getAllActiveEvents()
        val allCodeEvents = codeEventDao.getAllActiveCodeEvents()
        return InventorySyncJsonExporter.toSyncBundleJson(
            deviceId = deviceIdProvider(),
            polvorin = polvorinProvider(),
            exportedAtIso = DateTimeUtils.currentTimestamp(),
            events = allEvents,
            codeEvents = allCodeEvents,
            prettyPrint = true
        )
    }

    /**
     * Observa el historial persistido de cruces de sincronización (QR y JSON).
     */
    fun observeSyncSessions(): Flow<List<SyncSessionEntity>> {
        return syncSessionDao?.observeRecentSessions() ?: flowOf(emptyList())
    }

    /**
     * Observa en vivo todos los conflictos operativos detectados en el libro combinado
     * (doble despacho de serie SEDENA, conteos físicos paralelos y saldos negativos).
     */
    fun observeActiveConflicts(): Flow<List<SyncConflict>> {
        return combine(
            eventDao.observeAllActiveEvents(),
            codeEventDao.observeAllActiveCodeEvents(),
            productoDao.getAllFlow()
        ) { events, codeEvents, catalogProducts ->
            val (_, stockItems) = buildOverview(events, codeEvents, catalogProducts)
            InventoryConflictAnalyzer.analyze(events, codeEvents, stockItems)
        }
    }

    /**
     * Construye un paquete listo para compartir vía QR de Alta Capacidad V3
     * (esquema tabular + GZIP nivel 9 + Base45 Alfanumérico, ~1,400 chars/QR por defecto)
     * y archivo `.JSON` estándar v2 según el alcance elegido.
     */
    suspend fun buildSyncExportPackage(
        scope: SyncExportScope,
        densityMode: QrDensityMode = QrDensityMode.HIGH_CAPACITY,
        maxChunkChars: Int = densityMode.maxChunkChars
    ): SyncExportPackage {
        val nowMs = System.currentTimeMillis()
        val since24h = nowMs - 24L * 60L * 60L * 1000L

        val events = when (scope) {
            SyncExportScope.PENDING_ONLY -> eventDao.getPendingSyncEvents()
            SyncExportScope.LAST_24_HOURS -> eventDao.getEventsSince(since24h)
            SyncExportScope.FULL_HISTORY -> eventDao.getAllActiveEvents()
        }
        val codeEvents = when (scope) {
            SyncExportScope.PENDING_ONLY -> codeEventDao.getPendingSyncCodeEvents()
            SyncExportScope.LAST_24_HOURS -> codeEventDao.getCodeEventsSince(since24h)
            SyncExportScope.FULL_HISTORY -> codeEventDao.getAllActiveCodeEvents()
        }

        val deviceId = deviceIdProvider()
        val polvorin = polvorinProvider()
        val exportedAtIso = DateTimeUtils.currentTimestamp()

        val compactJson = InventorySyncJsonExporter.toSyncBundleJson(
            deviceId = deviceId,
            polvorin = polvorin,
            exportedAtIso = exportedAtIso,
            events = events,
            codeEvents = codeEvents,
            prettyPrint = false
        )
        val prettyJson = InventorySyncJsonExporter.toSyncBundleJson(
            deviceId = deviceId,
            polvorin = polvorin,
            exportedAtIso = exportedAtIso,
            events = events,
            codeEvents = codeEvents,
            prettyPrint = true
        )
        val binaryV3 = SyncTabularV3Codec.encodeBinary(
            deviceId = deviceId,
            polvorin = polvorin,
            exportedAtIso = exportedAtIso,
            events = events,
            codeEvents = codeEvents
        )
        val qrPackage = QrSyncCodec.encodeV4(
            binaryPayload = binaryV3,
            rawJsonBytes = compactJson.toByteArray(Charsets.UTF_8).size,
            eventCount = events.size,
            codeEventCount = codeEvents.size,
            maxChunkChars = maxChunkChars,
            useLowErrorCorrection = densityMode.useLowErrorCorrection
        )

        return SyncExportPackage(
            scope = scope,
            jsonCompact = compactJson,
            jsonPretty = prettyJson,
            qrPackage = qrPackage,
            eventUuids = events.map { it.eventUuid },
            codeEventUuids = codeEvents.map { it.eventUuid },
            deviceId = deviceId,
            polvorin = polvorin
        )
    }

    /**
     * Registra en bitácora que un paquete fue compartido (QR o JSON).
     *
     * `markPendingAsSynced` se conserva por compatibilidad de firma pero NO cambia el estado:
     * `SyncMergeReport` sólo trae contadores, no UUIDs, así que una confirmación del operador
     * no es un ACK real. Los eventos siguen `PENDING` y se reexportan; la dedupe por UUID en el
     * receptor hace idempotente el reenvío y evita pérdida silenciosa del libro mayor.
     */
    suspend fun markExportSessionCompleted(
        pkg: SyncExportPackage,
        direction: String,
        @Suppress("UNUSED_PARAMETER") markPendingAsSynced: Boolean
    ) {
        val nowMs = System.currentTimeMillis()
        syncSessionDao?.insertSession(
            SyncSessionEntity(
                sessionUuid = UUID.randomUUID().toString(),
                direction = direction,
                remoteDeviceId = pkg.deviceId,
                polvorin = pkg.polvorin,
                eventsMerged = pkg.eventUuids.size,
                eventsSkippedDuplicate = 0,
                codeEventsMerged = pkg.codeEventUuids.size,
                codeEventsSkipped = 0,
                conflictsDetected = 0,
                summaryNote = "Enviado (${pkg.scope.label}) • ${pkg.qrPackage.chunks.size} QR(s) • Sesión ${pkg.qrPackage.sessionId}",
                timestampIso = DateTimeUtils.currentTimestamp(),
                timestampEpochMs = nowMs
            )
        )
    }

    /**
     * Motor de fusión idempotente por UUID v4 para paquetes recibidos vía QR multi-parte o archivo `.JSON`.
     *
     * - Inserta únicamente los eventos cuyo `event_uuid` no exista localmente (`OnConflictStrategy.IGNORE`).
     * - Siembra SKUs desconocidos y propaga calibraciones de `pesoUnitarioKg` entre escáneres.
     * - Ejecuta `InventoryConflictAnalyzer` sobre el libro combinado y registra la sesión en `sync_sessions`.
     */
    suspend fun importSyncJson(
        json: String,
        sourceDirection: String = SyncSessionDirection.IMPORT_JSON
    ): Result<SyncMergeReport> {
        val parsed = InventorySyncJsonParser.parse(json)
        val bundle = parsed.getOrElse { return Result.failure(it) }

        val eventRowIds = if (bundle.events.isNotEmpty()) {
            eventDao.insertEventsReturningRowIds(bundle.events)
        } else {
            emptyList()
        }
        val eventsMerged = eventRowIds.count { it != -1L }
        val eventsSkipped = eventRowIds.count { it == -1L }

        val codeRowIds = if (bundle.codeEvents.isNotEmpty()) {
            codeEventDao.insertEventsReturningRowIds(bundle.codeEvents)
        } else {
            emptyList()
        }
        val codeEventsMerged = codeRowIds.count { it != -1L }
        val codeEventsSkipped = codeRowIds.count { it == -1L }

        // Propagar productos nuevos y calibraciones de peso unitario descubiertas en otra terminal
        val calibrationsUpdated = propagateCatalogFromImportedEvents(bundle.events)

        // Recalcular libro vivo combinado y detectar conflictos
        val allEvents = eventDao.getAllActiveEvents()
        val allCodeEvents = codeEventDao.getAllActiveCodeEvents()
        val catalogProducts = productoDao.getAll()
        val (_, stockItems) = buildOverview(allEvents, allCodeEvents, catalogProducts)
        val conflicts = InventoryConflictAnalyzer.analyze(allEvents, allCodeEvents, stockItems)

        val sessionUuid = UUID.randomUUID().toString()
        val nowIso = DateTimeUtils.currentTimestamp()
        val nowMs = System.currentTimeMillis()

        val summary = buildString {
            append("+${eventsMerged} mov.")
            if (codeEventsMerged > 0) append(", +${codeEventsMerged} cód.")
            val totalDup = eventsSkipped + codeEventsSkipped
            if (totalDup > 0) append(" ($totalDup dup. omitidos)")
            if (conflicts.isNotEmpty()) append(" • ${conflicts.size} conflicto(s)")
        }

        syncSessionDao?.insertSession(
            SyncSessionEntity(
                sessionUuid = sessionUuid,
                direction = sourceDirection,
                remoteDeviceId = bundle.remoteDeviceId,
                polvorin = bundle.polvorin,
                eventsMerged = eventsMerged,
                eventsSkippedDuplicate = eventsSkipped,
                codeEventsMerged = codeEventsMerged,
                codeEventsSkipped = codeEventsSkipped,
                conflictsDetected = conflicts.size,
                summaryNote = summary,
                timestampIso = nowIso,
                timestampEpochMs = nowMs
            )
        )

        return Result.success(
            SyncMergeReport(
                sessionUuid = sessionUuid,
                sourceDirection = sourceDirection,
                remoteDeviceId = bundle.remoteDeviceId,
                polvorin = bundle.polvorin,
                eventsMerged = eventsMerged,
                eventsSkippedDuplicate = eventsSkipped,
                codeEventsMerged = codeEventsMerged,
                codeEventsSkippedDuplicate = codeEventsSkipped,
                catalogCalibrationsUpdated = calibrationsUpdated,
                conflicts = conflicts,
                timestampIso = nowIso
            )
        )
    }

    /**
     * Genera un paquete QR multi-parte realista de prueba (simulando una segunda terminal `ZBR-POLV-02`)
     * para validar la recepción óptica multi-bloque y el detector de conflictos sin necesitar 2 teléfonos físicos.
     */
    suspend fun generateDemoRemoteSyncQrChunks(
        includeConflictSample: Boolean = true,
        forceMultiPart: Boolean = false
    ): List<String> {
        val nowMs = System.currentTimeMillis()
        val nowIso = DateTimeUtils.currentTimestamp()
        val polvorin = polvorinProvider()
        val remoteDevice = "ZBR-POLV-02"

        val demoEvents = mutableListOf(
            InventoryEventEntity(
                eventUuid = UUID.randomUUID().toString(),
                deviceId = remoteDevice,
                polvorin = polvorin,
                eventType = InventoryEventType.ENTRADA.name,
                productId = "315",
                productName = "EMULEX 1",
                presentation = "1 1/4\" X 16\"",
                productType = ProductType.ALTO_EXPLOSIVO.name,
                inventoryCategory = InventoryCategory.ALTO_EXPLOSIVO.name,
                unit = "KG",
                deltaQuantity = 50.0,
                cajasDelta = 2,
                unitWeightKgUsed = 0.25,
                boxWeightKgUsed = 25.0,
                referenceType = InventoryReferenceType.ENTRADA,
                referenceId = 9001L,
                referenceFolio = "PRV-REMOTO-02",
                notes = "2 cajas (50.00 kg) • Recibido en $remoteDevice",
                timestampIso = nowIso,
                timestampEpochMs = nowMs - 120_000L,
                syncStatus = InventorySyncStatus.PENDING
            ),
            InventoryEventEntity(
                eventUuid = UUID.randomUUID().toString(),
                deviceId = remoteDevice,
                polvorin = polvorin,
                eventType = InventoryEventType.ENTRADA.name,
                productId = "298",
                productName = "AUSTINITE 15 CA-1",
                presentation = "SACO 25 KG",
                productType = ProductType.AGENTE.name,
                inventoryCategory = InventoryCategory.AGENTE_EXPLOSIVO.name,
                unit = "KG",
                deltaQuantity = 75.0,
                sacosDelta = 3,
                boxWeightKgUsed = 25.0,
                referenceType = InventoryReferenceType.ENTRADA,
                referenceId = 9001L,
                referenceFolio = "PRV-REMOTO-02",
                notes = "3 sacos (75.00 kg) • Recibido en $remoteDevice",
                timestampIso = nowIso,
                timestampEpochMs = nowMs - 110_000L,
                syncStatus = InventorySyncStatus.PENDING
            ),
            InventoryEventEntity(
                eventUuid = UUID.randomUUID().toString(),
                deviceId = remoteDevice,
                polvorin = polvorin,
                eventType = InventoryEventType.SALIDA_ACCESORIOS.name,
                productId = "XCOR-01",
                productName = "CORDÓN DETONANTE 5G",
                presentation = "MTS",
                productType = ProductType.ACCESORIO.name,
                inventoryCategory = InventoryCategory.CORDON_DETONANTE.name,
                unit = "MTS",
                deltaQuantity = -40.0,
                referenceType = InventoryReferenceType.ACCESORIO_VALE,
                referenceId = 9002L,
                referenceFolio = "ACC-REM-02",
                notes = "40.00 MTS · Frente Norte ($remoteDevice)",
                timestampIso = nowIso,
                timestampEpochMs = nowMs - 90_000L,
                syncStatus = InventorySyncStatus.PENDING
            )
        )

        val demoCodeEvents = mutableListOf(
            InventoryCodeEventEntity(
                eventUuid = UUID.randomUUID().toString(),
                deviceId = remoteDevice,
                polvorin = polvorin,
                actionType = InventoryEventType.ENTRADA.name,
                status = InventoryCodeStatus.EN_POLVORIN.name,
                barcode = "315150826000001000100990000001",
                productId = "315",
                productName = "EMULEX 1",
                presentation = "1 1/4\" X 16\"",
                inventoryCategory = InventoryCategory.ALTO_EXPLOSIVO.name,
                empaque = TipoEmpaque.CAJA.name,
                weightKg = 25.0,
                referenceType = InventoryReferenceType.ENTRADA,
                referenceId = 9001L,
                referenceFolio = "PRV-REMOTO-02",
                timestampIso = nowIso,
                timestampEpochMs = nowMs - 120_000L,
                syncStatus = InventorySyncStatus.PENDING
            ),
            InventoryCodeEventEntity(
                eventUuid = UUID.randomUUID().toString(),
                deviceId = remoteDevice,
                polvorin = polvorin,
                actionType = InventoryEventType.ENTRADA.name,
                status = InventoryCodeStatus.EN_POLVORIN.name,
                barcode = "298150826990000001",
                productId = "298",
                productName = "AUSTINITE 15 CA-1",
                presentation = "SACO 25 KG",
                inventoryCategory = InventoryCategory.AGENTE_EXPLOSIVO.name,
                empaque = TipoEmpaque.SACO.name,
                weightKg = 25.0,
                referenceType = InventoryReferenceType.ENTRADA,
                referenceId = 9001L,
                referenceFolio = "PRV-REMOTO-02",
                timestampIso = nowIso,
                timestampEpochMs = nowMs - 110_000L,
                syncStatus = InventorySyncStatus.PENDING
            )
        )

        if (includeConflictSample) {
            // Si ya existe un código despachado localmente, simulamos que ZBR-POLV-02 también lo despachó
            val existingCodes = deriveActiveCodeItems(codeEventDao.getAllActiveCodeEvents())
            val dispatchedLocal = existingCodes.firstOrNull { it.status == InventoryCodeStatus.DESPACHADO }
            val conflictBarcode = dispatchedLocal?.barcode ?: "315150826000001000100990000001"
            val conflictProdId = dispatchedLocal?.productId ?: "315"
            val conflictProdName = dispatchedLocal?.productName ?: "EMULEX 1"
            val conflictCategory = dispatchedLocal?.category ?: InventoryCategory.ALTO_EXPLOSIVO

            if (dispatchedLocal == null) {
                // Inyectamos un despacho local + un despacho remoto sobre la misma serie para demostrar la alerta crítica
                demoCodeEvents.add(
                    InventoryCodeEventEntity(
                        eventUuid = UUID.randomUUID().toString(),
                        deviceId = deviceIdProvider(),
                        polvorin = polvorin,
                        actionType = InventoryEventType.DESPACHO.name,
                        status = InventoryCodeStatus.DESPACHADO.name,
                        barcode = conflictBarcode,
                        productId = conflictProdId,
                        productName = conflictProdName,
                        presentation = "1 1/4\" X 16\"",
                        inventoryCategory = conflictCategory.name,
                        empaque = TipoEmpaque.CAJA.name,
                        weightKg = 25.0,
                        referenceType = InventoryReferenceType.DESPACHO,
                        referenceId = 9003L,
                        referenceFolio = "REF-LOC-DEMO",
                        timestampIso = nowIso,
                        timestampEpochMs = nowMs - 60_000L,
                        syncStatus = InventorySyncStatus.PENDING
                    )
                )
            }
            demoCodeEvents.add(
                InventoryCodeEventEntity(
                    eventUuid = UUID.randomUUID().toString(),
                    deviceId = remoteDevice,
                    polvorin = polvorin,
                    actionType = InventoryEventType.DESPACHO.name,
                    status = InventoryCodeStatus.DESPACHADO.name,
                    barcode = conflictBarcode,
                    productId = conflictProdId,
                    productName = conflictProdName,
                    presentation = dispatchedLocal?.presentation ?: "1 1/4\" X 16\"",
                    inventoryCategory = conflictCategory.name,
                    empaque = (dispatchedLocal?.empaque ?: TipoEmpaque.CAJA).name,
                    weightKg = dispatchedLocal?.weightKg ?: 25.0,
                    referenceType = InventoryReferenceType.DESPACHO,
                    referenceId = 9004L,
                    referenceFolio = "REF-REM-CONFLICTO",
                    timestampIso = nowIso,
                    timestampEpochMs = nowMs - 30_000L,
                    syncStatus = InventorySyncStatus.PENDING
                )
            )
        }

        val binaryV3 = SyncTabularV3Codec.encodeBinary(
            deviceId = remoteDevice,
            polvorin = polvorin,
            exportedAtIso = nowIso,
            events = demoEvents,
            codeEvents = demoCodeEvents
        )
        val pkg = QrSyncCodec.encodeV3Binary(
            binaryPayload = binaryV3,
            eventCount = demoEvents.size,
            codeEventCount = demoCodeEvents.size,
            maxChunkChars = if (forceMultiPart) 180 else QrDensityMode.HIGH_CAPACITY.maxChunkChars
        )
        return pkg.chunks.map { it.rawText }
    }

    private suspend fun propagateCatalogFromImportedEvents(
        importedEvents: List<InventoryEventEntity>
    ): Int {
        if (importedEvents.isEmpty()) return 0
        var updatedCount = 0
        val byProduct = importedEvents.groupBy { it.productId.trim().uppercase() }
        for ((productId, list) in byProduct) {
            if (productId.isEmpty()) continue
            val latest = list.maxByOrNull { it.timestampEpochMs } ?: continue
            val bestUnitWeight = list.map { it.unitWeightKgUsed }.firstOrNull { it > 0.0 } ?: 0.0
            val bestBoxWeight = list.map { it.boxWeightKgUsed }.firstOrNull { it > 0.0 } ?: 0.0
            val inferredCartridges = if (bestUnitWeight > 0.0 && bestBoxWeight > 0.0) {
                (bestBoxWeight / bestUnitWeight).roundToInt().coerceAtLeast(0)
            } else {
                0
            }

            val existing = productoDao.getById(productId)
            val resolvedCategory = InventoryCategory.fromStringOrNull(latest.inventoryCategory)
                ?: InventoryCategory.inferCategory(
                    productType = safeProductType(latest.productType, productId),
                    codeOrId = productId,
                    productName = latest.productName,
                    unit = latest.unit
                )

            if (existing == null) {
                productoDao.insert(
                    ProductoEntity(
                        idProducto = productId,
                        producto = latest.productName.ifBlank { productId },
                        presentacion = latest.presentation,
                        noSap = "",
                        unidad = latest.unit.ifBlank { resolvedCategory.defaultUnit },
                        cantidad = if (inferredCartridges > 0) inferredCartridges.toDouble() else 0.0,
                        codBarras = productId,
                        peso = bestBoxWeight,
                        pg = "",
                        pu = 0.0,
                        tipo = resolvedCategory.toProductType().name,
                        pesoUnitarioKg = bestUnitWeight,
                        cartuchosPorCaja = inferredCartridges,
                        categoriaInventario = resolvedCategory.name
                    )
                )
                if (bestUnitWeight > 0.0) updatedCount++
            } else if (existing.pesoUnitarioKg <= 0.0 && bestUnitWeight > 0.0) {
                val finalCarts = if (existing.cartuchosPorCaja > 0) existing.cartuchosPorCaja else inferredCartridges
                productoDao.updateUnitWeight(productId, bestUnitWeight, finalCarts)
                updatedCount++
            }
        }
        return updatedCount
    }

    private suspend fun buildEventsAndCodesFromScans(
        items: List<BarcodeItem>,
        sign: Double,
        eventType: InventoryEventType,
        codeTargetStatus: InventoryCodeStatus,
        referenceType: String,
        referenceId: Long,
        referenceFolio: String,
        polvorinOverride: String? = null,
        extraNote: String = ""
    ): Pair<List<InventoryEventEntity>, List<InventoryCodeEventEntity>> {
        if (items.isEmpty()) return emptyList<InventoryEventEntity>() to emptyList()

        val nowMs = System.currentTimeMillis()
        val nowIso = DateTimeUtils.currentTimestamp()
        val deviceId = deviceIdProvider()
        val polvorin = polvorinOverride?.takeIf { it.isNotBlank() } ?: polvorinProvider()

        val grouped = items.groupBy { item ->
            if (item.empaque == TipoEmpaque.ACCESORIO || item.code.startsWith("X", ignoreCase = true)) {
                item.code.trim().uppercase()
            } else {
                item.productInfo.id.trim().uppercase()
            }
        }

        val qtyResult = mutableListOf<InventoryEventEntity>()
        val codeResult = mutableListOf<InventoryCodeEventEntity>()
        var codeTickOffset = 0L

        for ((productId, group) in grouped) {
            if (productId.isBlank() || productId == "???") continue
            val sample = group.first()
            val freshCatalog = productoDao.getById(productId) ?: productoDao.getByCodBarras(productId)

            val isAccessory = sample.empaque == TipoEmpaque.ACCESORIO ||
                sample.productInfo.type == ProductType.ACCESORIO ||
                productId.startsWith("X", ignoreCase = true)

            if (isAccessory) {
                val resolvedCategory = freshCatalog?.resolvedCategory()?.takeIf { !it.supportsBarcodeLedger }
                    ?: InventoryCategory.inferCategory(
                        productType = ProductType.ACCESORIO,
                        codeOrId = productId,
                        productName = freshCatalog?.producto ?: sample.productInfo.name,
                        unit = freshCatalog?.unidad ?: sample.productInfo.unit
                    )
                val resolvedUnit = freshCatalog?.unidad?.trim()?.uppercase()
                    ?.takeIf { it == "PZA" || it == "MTS" } ?: resolvedCategory.defaultUnit
                val count = group.size.toDouble()
                val delta = BoxUnitWeightCalculator.roundTo4Decimals(sign * count)
                val note = listOf("${group.size} $resolvedUnit", extraNote).filter { it.isNotBlank() }.joinToString(" · ")
                qtyResult.add(
                    InventoryEventEntity(
                        eventUuid = UUID.randomUUID().toString(),
                        deviceId = deviceId,
                        polvorin = polvorin,
                        eventType = eventType.name,
                        productId = productId,
                        productName = freshCatalog?.producto?.trim()?.ifBlank { sample.productInfo.name } ?: sample.productInfo.name,
                        presentation = freshCatalog?.presentacion?.trim()?.ifBlank { resolvedUnit } ?: sample.productInfo.presentation,
                        productType = ProductType.ACCESORIO.name,
                        inventoryCategory = resolvedCategory.name,
                        unit = resolvedUnit,
                        deltaQuantity = delta,
                        referenceType = referenceType,
                        referenceId = referenceId,
                        referenceFolio = referenceFolio,
                        notes = note,
                        timestampIso = nowIso,
                        timestampEpochMs = nowMs,
                        syncStatus = InventorySyncStatus.PENDING
                    )
                )
                continue
            }

            val resolvedType = try {
                ProductType.valueOf(freshCatalog?.tipo ?: sample.productInfo.type.name)
            } catch (_: Exception) {
                ProductType.ALTO_EXPLOSIVO
            }
            val resolvedCategory = if (resolvedType == ProductType.AGENTE) {
                InventoryCategory.AGENTE_EXPLOSIVO
            } else {
                InventoryCategory.ALTO_EXPLOSIVO
            }
            val resolvedName = freshCatalog?.producto?.trim()?.ifBlank { sample.productInfo.name } ?: sample.productInfo.name
            val resolvedPres = freshCatalog?.presentacion?.trim()?.ifBlank { sample.productInfo.presentation } ?: sample.productInfo.presentation

            val boxWeightKg = freshCatalog?.peso?.takeIf { it > 0.0 } ?: sample.productInfo.weightKg
            var unitWeightKg = freshCatalog?.pesoUnitarioKg?.takeIf { it > 0.0 } ?: sample.productInfo.unitWeightKg

            // Si viene alguna caja de 30d en el lote, también nos aseguramos de tomar su peso unitario más reciente
            if (unitWeightKg <= 0.0) {
                val firstBox = group.firstOrNull { it.empaque == TipoEmpaque.CAJA || it.code.length == 30 }
                if (firstBox != null && boxWeightKg > 0.0) {
                    val computed = BoxUnitWeightCalculator.computeFromBoxBarcode(firstBox.code, boxWeightKg)
                    if (computed != null) {
                        unitWeightKg = computed.unitWeightKg
                        productoDao.updateUnitWeight(productId, computed.unitWeightKg, computed.cartridgesPerBox)
                    }
                }
            }

            var cajas = 0
            var sacos = 0
            var cartuchos = 0
            for (item in group) {
                val resolvedEmpaque = when (item.empaque) {
                    TipoEmpaque.CAJA, TipoEmpaque.SACO, TipoEmpaque.PIEZA -> item.empaque
                    else -> when (item.code.trim().length) {
                        30 -> TipoEmpaque.CAJA
                        18 -> TipoEmpaque.SACO
                        16, 17 -> TipoEmpaque.PIEZA
                        else -> TipoEmpaque.DESCONOCIDO
                    }
                }
                val itemWeightKg = when (resolvedEmpaque) {
                    TipoEmpaque.CAJA -> {
                        cajas++
                        boxWeightKg
                    }
                    TipoEmpaque.SACO -> {
                        sacos++
                        boxWeightKg
                    }
                    TipoEmpaque.PIEZA -> {
                        cartuchos++
                        unitWeightKg
                    }
                    else -> 0.0
                }

                // Libro individual de códigos SEDENA para Alto Explosivo y Agente Explosivo
                val cleanBarcode = item.code.trim()
                if (cleanBarcode.isNotEmpty() && resolvedEmpaque != TipoEmpaque.DESCONOCIDO) {
                    codeResult.add(
                        InventoryCodeEventEntity(
                            eventUuid = UUID.randomUUID().toString(),
                            reversesEventUuid = "",
                            deviceId = deviceId,
                            polvorin = polvorin,
                            actionType = eventType.name,
                            status = codeTargetStatus.name,
                            barcode = cleanBarcode,
                            productId = productId,
                            productName = resolvedName,
                            presentation = resolvedPres,
                            inventoryCategory = resolvedCategory.name,
                            empaque = resolvedEmpaque.name,
                            weightKg = BoxUnitWeightCalculator.roundTo4Decimals(itemWeightKg),
                            referenceType = referenceType,
                            referenceId = referenceId,
                            referenceFolio = referenceFolio,
                            timestampIso = item.timestamp.ifBlank { nowIso },
                            timestampEpochMs = nowMs + codeTickOffset++,
                            syncStatus = InventorySyncStatus.PENDING
                        )
                    )
                }
            }

            val totalAbsKg = BoxUnitWeightCalculator.roundTo4Decimals(
                (cajas * boxWeightKg) + (sacos * boxWeightKg) + (cartuchos * unitWeightKg)
            )
            val deltaKg = BoxUnitWeightCalculator.roundTo4Decimals(sign * totalAbsKg)

            val parts = mutableListOf<String>()
            if (cajas > 0) parts.add("$cajas ${if (cajas == 1) "caja" else "cajas"}")
            if (sacos > 0) parts.add("$sacos ${if (sacos == 1) "saco" else "sacos"}")
            if (cartuchos > 0) {
                if (unitWeightKg > 0.0) {
                    parts.add("$cartuchos cart. (${BoxUnitWeightCalculator.formatUnitWeight3(unitWeightKg)} kg/u)")
                } else {
                    parts.add("$cartuchos cart. (peso unit. pendiente)")
                }
            }
            if (extraNote.isNotBlank()) parts.add(extraNote)

            val intSign = if (sign >= 0) 1 else -1
            qtyResult.add(
                InventoryEventEntity(
                    eventUuid = UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    polvorin = polvorin,
                    eventType = eventType.name,
                    productId = productId,
                    productName = resolvedName,
                    presentation = resolvedPres,
                    productType = resolvedType.name,
                    inventoryCategory = resolvedCategory.name,
                    unit = "KG",
                    deltaQuantity = deltaKg,
                    cajasDelta = intSign * cajas,
                    sacosDelta = intSign * sacos,
                    cartuchosDelta = intSign * cartuchos,
                    unitWeightKgUsed = unitWeightKg,
                    boxWeightKgUsed = boxWeightKg,
                    referenceType = referenceType,
                    referenceId = referenceId,
                    referenceFolio = referenceFolio,
                    notes = parts.joinToString(" · "),
                    timestampIso = nowIso,
                    timestampEpochMs = nowMs,
                    syncStatus = InventorySyncStatus.PENDING
                )
            )
        }
        return qtyResult to codeResult
    }

    private suspend fun reconcileReferenceEvents(
        referenceType: String,
        referenceId: Long,
        newEvents: List<InventoryEventEntity>,
        editReason: String
    ) {
        val existing = if (referenceId > 0L) {
            eventDao.getEventsByReference(referenceType, referenceId)
        } else {
            emptyList()
        }

        if (existing.isEmpty()) {
            if (newEvents.isNotEmpty()) {
                eventDao.insertEvents(newEvents)
            }
            return
        }

        // Verificar si los deltas netos por producto realmente cambiaron (idempotencia en re-guardado)
        val existingNetByProduct = existing
            .groupBy { it.productId }
            .mapValues { (_, list) -> BoxUnitWeightCalculator.roundTo4Decimals(list.sumOf { it.deltaQuantity }) }
            .filterValues { abs(it) > 1e-6 }

        val newNetByProduct = newEvents
            .groupBy { it.productId }
            .mapValues { (_, list) -> BoxUnitWeightCalculator.roundTo4Decimals(list.sumOf { it.deltaQuantity }) }
            .filterValues { abs(it) > 1e-6 }

        if (existingNetByProduct == newNetByProduct) {
            return // Sin cambios respecto a lo ya contabilizado
        }

        // La semilla de generación viene de los eventos NUEVOS: el mínimo del histórico se
        // mantiene igual entre ediciones y haría colisionar reversos sucesivos (INSERT IGNORE).
        val generationSeed = newEvents.map { it.eventUuid }.minOrNull()
        val reversals = buildReversalEvents(existing, editReason, generationSeed)
        val baseTime = System.currentTimeMillis()
        val orderedReversals = reversals.mapIndexed { idx, ev ->
            ev.copy(timestampEpochMs = baseTime + idx)
        }
        val orderedNew = newEvents.mapIndexed { idx, ev ->
            ev.copy(timestampEpochMs = baseTime + orderedReversals.size + 1 + idx)
        }

        eventDao.insertEvents(orderedReversals + orderedNew)
    }

    private suspend fun reconcileReferenceCodeEvents(
        referenceType: String,
        referenceId: Long,
        newCodeEvents: List<InventoryCodeEventEntity>,
        editReason: String
    ) {
        val existing = if (referenceId > 0L) {
            codeEventDao.getCodeEventsByReference(referenceType, referenceId)
        } else {
            emptyList()
        }

        if (existing.isEmpty()) {
            if (newCodeEvents.isNotEmpty()) {
                codeEventDao.insertEvents(newCodeEvents)
            }
            return
        }

        val activeExisting = filterUnreversedCodeEvents(existing)
        val existingSignature = activeExisting.map { it.barcode to it.status }.sortedBy { it.first }
        val newSignature = newCodeEvents.map { it.barcode to it.status }.sortedBy { it.first }

        if (existingSignature == newSignature) {
            return // Idempotente: mismos códigos SEDENA ya trazados para este vale
        }

        val reversals = buildCodeReversalEvents(existing, editReason)
        val baseTime = System.currentTimeMillis()
        val orderedReversals = reversals.mapIndexed { idx, ev ->
            ev.copy(timestampEpochMs = baseTime + idx)
        }
        val orderedNew = newCodeEvents.mapIndexed { idx, ev ->
            ev.copy(timestampEpochMs = baseTime + orderedReversals.size + 1 + idx)
        }
        codeEventDao.insertEvents(orderedReversals + orderedNew)
    }

    private fun buildReversalEvents(
        existing: List<InventoryEventEntity>,
        reason: String,
        generationSeed: String? = null
    ): List<InventoryEventEntity> {
        val nowMs = System.currentTimeMillis()
        val nowIso = DateTimeUtils.currentTimestamp()
        val deviceId = deviceIdProvider()

        return existing
            .groupBy { it.productId }
            .mapNotNull { (productId, list) ->
                val netDelta = BoxUnitWeightCalculator.roundTo4Decimals(list.sumOf { it.deltaQuantity })
                val netCajas = list.sumOf { it.cajasDelta }
                val netSacos = list.sumOf { it.sacosDelta }
                val netCarts = list.sumOf { it.cartuchosDelta }
                if (abs(netDelta) < 1e-6 && netCajas == 0 && netSacos == 0 && netCarts == 0) {
                    return@mapNotNull null
                }
                val last = list.last()
                // UUID determinista por generación: en ediciones la semilla son los eventos nuevos;
                // en borrado/duplicado se usa el mínimo histórico (el neto ya evita duplicar).
                val seed = generationSeed ?: list.minBy { it.eventUuid }.eventUuid
                InventoryEventEntity(
                    eventUuid = UUID.nameUUIDFromBytes(
                        "REVERSAL:${last.referenceType}:${last.referenceId}:$reason:$productId:$seed".toByteArray()
                    ).toString(),
                    deviceId = deviceId,
                    polvorin = last.polvorin,
                    eventType = InventoryEventType.REVERSAL.name,
                    productId = productId,
                    productName = last.productName,
                    presentation = last.presentation,
                    productType = last.productType,
                    inventoryCategory = last.inventoryCategory,
                    unit = last.unit,
                    deltaQuantity = BoxUnitWeightCalculator.roundTo4Decimals(-netDelta),
                    cajasDelta = -netCajas,
                    sacosDelta = -netSacos,
                    cartuchosDelta = -netCarts,
                    unitWeightKgUsed = last.unitWeightKgUsed,
                    boxWeightKgUsed = last.boxWeightKgUsed,
                    referenceType = last.referenceType,
                    referenceId = last.referenceId,
                    referenceFolio = last.referenceFolio,
                    notes = reason,
                    timestampIso = nowIso,
                    timestampEpochMs = nowMs,
                    syncStatus = InventorySyncStatus.PENDING
                )
            }
    }

    private fun buildCodeReversalEvents(
        existing: List<InventoryCodeEventEntity>,
        reason: String
    ): List<InventoryCodeEventEntity> {
        val activeUnreversed = filterUnreversedCodeEvents(existing)
        if (activeUnreversed.isEmpty()) return emptyList()

        val nowMs = System.currentTimeMillis()
        val nowIso = DateTimeUtils.currentTimestamp()
        val deviceId = deviceIdProvider()

        return activeUnreversed.mapIndexed { idx, original ->
            InventoryCodeEventEntity(
                eventUuid = UUID.nameUUIDFromBytes(
                    "REVERSAL:${original.referenceType}:${original.referenceId}:$reason:${original.eventUuid}".toByteArray()
                ).toString(),
                reversesEventUuid = original.eventUuid,
                deviceId = deviceId,
                polvorin = original.polvorin,
                actionType = InventoryEventType.REVERSAL.name,
                status = InventoryEventType.REVERSAL.name,
                barcode = original.barcode,
                productId = original.productId,
                productName = original.productName,
                presentation = original.presentation,
                inventoryCategory = original.inventoryCategory,
                empaque = original.empaque,
                weightKg = original.weightKg,
                referenceType = original.referenceType,
                referenceId = original.referenceId,
                referenceFolio = original.referenceFolio,
                timestampIso = nowIso,
                timestampEpochMs = nowMs + idx,
                syncStatus = InventorySyncStatus.PENDING
            )
        }
    }

    private fun buildQuickCountSummaryNote(
        category: InventoryCategory,
        unit: String,
        cajas: Int,
        sacos: Int,
        cartuchos: Int,
        total: Double,
        mode: QuickStockMode,
        userNote: String
    ): String {
        val parts = mutableListOf<String>()
        val prefix = if (mode == QuickStockMode.SET_PHYSICAL_COUNT) "Conteo físico:" else "Carga inicial:"
        when (category) {
            InventoryCategory.CORDON_DETONANTE ->
                parts.add("$prefix ${BoxUnitWeightCalculator.formatDecimal2(total)} $unit")
            InventoryCategory.CANUELAS, InventoryCategory.DETONADORES ->
                parts.add("$prefix ${BoxUnitWeightCalculator.formatPieces(total)} $unit")
            InventoryCategory.AGENTE_EXPLOSIVO ->
                parts.add("$prefix $sacos sacos (${BoxUnitWeightCalculator.formatDecimal2(total)} kg)")
            InventoryCategory.ALTO_EXPLOSIVO -> {
                val detail = buildString {
                    append("$cajas cajas")
                    if (cartuchos > 0) append(" + $cartuchos cart.")
                    append(" (${BoxUnitWeightCalculator.formatDecimal2(total)} kg)")
                }
                parts.add("$prefix $detail")
            }
        }
        if (userNote.isNotBlank()) parts.add(userNote.trim())
        return parts.joinToString(" · ")
    }

    companion object {
        private fun safeProductType(rawType: String?, productId: String): ProductType {
            return try {
                if (!rawType.isNullOrBlank()) ProductType.valueOf(rawType)
                else if (productId.startsWith("X", ignoreCase = true)) ProductType.ACCESORIO else ProductType.ALTO_EXPLOSIVO
            } catch (_: Exception) {
                if (productId.startsWith("X", ignoreCase = true)) ProductType.ACCESORIO else ProductType.ALTO_EXPLOSIVO
            }
        }

        private fun safeEmpaque(raw: String, barcode: String): TipoEmpaque {
            return try {
                TipoEmpaque.valueOf(raw)
            } catch (_: Exception) {
                when (barcode.trim().length) {
                    30 -> TipoEmpaque.CAJA
                    18 -> TipoEmpaque.SACO
                    16, 17 -> TipoEmpaque.PIEZA
                    else -> TipoEmpaque.DESCONOCIDO
                }
            }
        }

        /**
         * Filtra eventos de código cuya operación original ya fue anulada mediante `reversesEventUuid`.
         */
        fun filterUnreversedCodeEvents(
            events: List<InventoryCodeEventEntity>
        ): List<InventoryCodeEventEntity> {
            val reversedUuids = events
                .mapNotNull { it.reversesEventUuid.takeIf { uuid -> uuid.isNotBlank() } }
                .toSet()
            return events.filter { ev ->
                ev.actionType != InventoryEventType.REVERSAL.name &&
                    ev.eventUuid !in reversedUuids
            }
        }

        /**
         * Deriva el inventario vivo de códigos de barras SEDENA (`EN_POLVORIN` vs `DESPACHADO`)
         * a partir del libro mayor append-only `inventory_code_events`.
         */
        fun deriveActiveCodeItems(
            codeEvents: List<InventoryCodeEventEntity>
        ): List<InventoryCodeItem> {
            val effective = filterUnreversedCodeEvents(codeEvents)
            if (effective.isEmpty()) return emptyList()

            return effective
                .groupBy { it.barcode.trim() }
                .map { (barcode, history) ->
                    val sorted = history.sortedWith(
                        compareBy<InventoryCodeEventEntity> { it.timestampEpochMs }
                            .thenBy { it.eventUuid }
                    )
                    val latest = sorted.last()
                    val cat = InventoryCategory.fromStringOrNull(latest.inventoryCategory)
                        ?: if (latest.empaque == TipoEmpaque.SACO.name) {
                            InventoryCategory.AGENTE_EXPLOSIVO
                        } else {
                            InventoryCategory.ALTO_EXPLOSIVO
                        }
                    InventoryCodeItem(
                        barcode = barcode,
                        productId = latest.productId.uppercase(),
                        productName = latest.productName,
                        presentation = latest.presentation,
                        category = cat,
                        empaque = safeEmpaque(latest.empaque, barcode),
                        status = InventoryCodeStatus.fromString(latest.status),
                        weightKg = latest.weightKg,
                        lastActionType = InventoryEventType.fromString(latest.actionType),
                        lastReferenceFolio = latest.referenceFolio,
                        lastUpdatedIso = latest.timestampIso,
                        lastEventUuid = latest.eventUuid,
                        deviceId = latest.deviceId,
                        polvorin = latest.polvorin,
                        movementCount = sorted.size
                    )
                }
                .sortedWith(
                    compareBy<InventoryCodeItem> { if (it.status == InventoryCodeStatus.EN_POLVORIN) 0 else 1 }
                        .thenByDescending { it.lastUpdatedIso }
                        .thenBy { it.barcode }
                )
        }

        fun buildOverview(
            events: List<InventoryEventEntity>,
            codeEvents: List<InventoryCodeEventEntity> = emptyList(),
            catalogProducts: List<ProductoEntity>
        ): Pair<InventoryDashboardTotals, List<InventoryStockItem>> {
            val catalogById = catalogProducts.associateBy { it.idProducto.uppercase() }
            val eventsByProduct = events.groupBy { it.productId.uppercase() }

            val derivedCodes = deriveActiveCodeItems(codeEvents)
            val codesByProduct = derivedCodes.groupBy { it.productId.uppercase() }

            val allProductIds = (eventsByProduct.keys + codesByProduct.keys).toSet()

            val stockItems = allProductIds.map { productId ->
                val prodEvents = eventsByProduct[productId].orEmpty()
                val prodCodes = codesByProduct[productId].orEmpty()
                val cat = catalogById[productId]
                val latestEvent = prodEvents.lastOrNull()
                val latestCode = prodCodes.firstOrNull()

                val sumQty = BoxUnitWeightCalculator.roundTo4Decimals(prodEvents.sumOf { it.deltaQuantity })
                val type = safeProductType(cat?.tipo ?: latestEvent?.productType, productId)
                val resolvedCategory = cat?.resolvedCategory()
                    ?: InventoryCategory.fromStringOrNull(latestEvent?.inventoryCategory)
                    ?: latestCode?.category
                    ?: InventoryCategory.inferCategory(
                        productType = type,
                        codeOrId = productId,
                        productName = latestEvent?.productName ?: latestCode?.productName.orEmpty(),
                        unit = latestEvent?.unit.orEmpty()
                    )

                val unit = (cat?.unidad?.takeIf { it.isNotBlank() }
                    ?: latestEvent?.unit?.takeIf { it.isNotBlank() }
                    ?: resolvedCategory.defaultUnit).uppercase()

                val inStockCodes = prodCodes.count { it.status == InventoryCodeStatus.EN_POLVORIN }
                val dispatchedCodes = prodCodes.count { it.status == InventoryCodeStatus.DESPACHADO }

                InventoryStockItem(
                    productId = productId,
                    productName = cat?.producto?.trim()?.ifBlank { latestEvent?.productName ?: latestCode?.productName ?: productId }
                        ?: latestEvent?.productName
                        ?: latestCode?.productName
                        ?: productId,
                    presentation = cat?.presentacion?.trim()?.ifBlank { latestEvent?.presentation ?: latestCode?.presentation.orEmpty() }
                        ?: latestEvent?.presentation
                        ?: latestCode?.presentation
                        ?: "",
                    productType = resolvedCategory.toProductType(),
                    category = resolvedCategory,
                    unit = unit,
                    stockQuantity = sumQty,
                    boxWeightKg = cat?.peso?.takeIf { it > 0.0 } ?: latestEvent?.boxWeightKgUsed ?: 0.0,
                    unitWeightKg = cat?.pesoUnitarioKg?.takeIf { it > 0.0 } ?: latestEvent?.unitWeightKgUsed ?: 0.0,
                    cartridgesPerBox = cat?.cartuchosPorCaja ?: 0,
                    lastUpdatedIso = latestEvent?.timestampIso ?: latestCode?.lastUpdatedIso ?: "",
                    eventCount = prodEvents.size,
                    codesInPolvorinCount = inStockCodes,
                    codesDispatchedCount = dispatchedCodes
                )
            }.sortedWith(
                compareBy<InventoryStockItem> { it.category.ordinal }
                    .thenByDescending { abs(it.stockQuantity) > 1e-6 }
                    .thenBy { it.productId }
            )

            var altoKg = 0.0
            var agenteKg = 0.0
            var cordonMts = 0.0
            var canuelasPza = 0.0
            var detonadoresPza = 0.0
            var altoCodes = 0
            var agenteCodes = 0

            for (item in stockItems) {
                when (item.category) {
                    InventoryCategory.ALTO_EXPLOSIVO -> {
                        altoKg += item.stockQuantity
                        altoCodes += item.codesInPolvorinCount
                    }
                    InventoryCategory.AGENTE_EXPLOSIVO -> {
                        agenteKg += item.stockQuantity
                        agenteCodes += item.codesInPolvorinCount
                    }
                    InventoryCategory.CORDON_DETONANTE -> cordonMts += item.stockQuantity
                    InventoryCategory.CANUELAS -> canuelasPza += item.stockQuantity
                    InventoryCategory.DETONADORES -> detonadoresPza += item.stockQuantity
                }
            }

            val calibratedCount = catalogProducts.count { it.pesoUnitarioKg > 0.0 }
            val pendingSyncCount = events.count { it.syncStatus == InventorySyncStatus.PENDING } +
                codeEvents.count { it.syncStatus == InventorySyncStatus.PENDING }

            val totals = InventoryDashboardTotals(
                altoExplosivoKg = BoxUnitWeightCalculator.roundTo4Decimals(altoKg),
                agenteExplosivoKg = BoxUnitWeightCalculator.roundTo4Decimals(agenteKg),
                cordonDetonanteMts = BoxUnitWeightCalculator.roundTo4Decimals(cordonMts),
                canuelasPza = BoxUnitWeightCalculator.roundTo4Decimals(canuelasPza),
                detonadoresPza = BoxUnitWeightCalculator.roundTo4Decimals(detonadoresPza),
                altoCodesInStock = altoCodes,
                agenteCodesInStock = agenteCodes,
                activeProductCount = stockItems.size,
                calibratedUnitWeightCount = calibratedCount,
                totalEventCount = events.size,
                totalCodeEventCount = codeEvents.size,
                pendingSyncEventCount = pendingSyncCount
            )

            return totals to stockItems
        }
    }
}
