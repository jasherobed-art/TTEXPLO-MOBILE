package com.ttexplo.mobile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.data.local.AppDatabase
import com.ttexplo.mobile.data.local.entity.DespachoEntity
import com.ttexplo.mobile.data.local.entity.DespachoItemEntity
import com.ttexplo.mobile.data.local.entity.InventoryCodeEventEntity
import com.ttexplo.mobile.data.local.entity.InventoryEventEntity
import com.ttexplo.mobile.data.repository.InventoryRepository
import com.ttexplo.mobile.domain.inventory.InventoryEventType
import com.ttexplo.mobile.domain.inventory.InventoryReferenceType
import com.ttexplo.mobile.domain.inventory.QrSyncPackage
import com.ttexplo.mobile.domain.inventory.InventorySyncStatus
import com.ttexplo.mobile.domain.inventory.SyncExportPackage
import com.ttexplo.mobile.domain.inventory.SyncExportScope
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// SDK 36 exige Java 21; el toolchain del repo es Java 17.
@Config(sdk = [35])
class HardeningDataTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: InventoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = InventoryRepository(
            eventDao = db.inventoryEventDao(),
            codeEventDao = db.inventoryCodeEventDao(),
            productoDao = db.productoDao(),
            valeDeletionDao = db.valeDeletionDao(),
            deviceIdProvider = { "DEVICE-TEST" },
            polvorinProvider = { "POLVORIN-TEST" }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun qtyEvent(uuid: String) = InventoryEventEntity(
        eventUuid = uuid,
        deviceId = "DEVICE-TEST",
        polvorin = "POLVORIN-TEST",
        eventType = InventoryEventType.ENTRADA.name,
        productId = "315",
        productName = "EMULEX",
        presentation = "CAJA",
        productType = "ALTO_EXPLOSIVO",
        unit = "KG",
        deltaQuantity = 25.0,
        referenceType = InventoryReferenceType.ENTRADA,
        referenceId = 77L,
        timestampIso = "2026-11-22 10:00:00",
        timestampEpochMs = 1_000L,
        syncStatus = InventorySyncStatus.PENDING
    )

    @Test
    fun exportSessionDoesNotMarkExportedEventsAsSynced() = runBlocking {
        db.inventoryEventDao().insertEvents(listOf(qtyEvent("export-uuid-1"), qtyEvent("export-uuid-2")))

        val pkg = SyncExportPackage(
            scope = SyncExportScope.PENDING_ONLY,
            jsonCompact = "{}",
            jsonPretty = "{}",
            qrPackage = QrSyncPackage(
                sessionId = "session-1",
                crc32Hex = "00000000",
                chunks = emptyList(),
                rawJsonBytes = 2,
                compressedEncodedChars = 2,
                eventCount = 2,
                codeEventCount = 0
            ),
            eventUuids = listOf("export-uuid-1", "export-uuid-2"),
            codeEventUuids = emptyList(),
            deviceId = "DEVICE-TEST",
            polvorin = "POLVORIN-TEST"
        )

        repo.markExportSessionCompleted(pkg, "QR", true)

        val pendingUuids = db.inventoryEventDao().getPendingSyncEvents().map { it.eventUuid }
        assertThat(pendingUuids).contains("export-uuid-1")
        assertThat(pendingUuids).contains("export-uuid-2")
    }

    @Test
    fun doubleReversalDoesNotDuplicateLedgerEvents() = runBlocking {
        val reason = "Vale eliminado"
        val qtySeed = "orig-qty-88"
        val codeSeed = "orig-code-88"
        db.inventoryEventDao().insertEvents(
            listOf(qtyEvent(qtySeed).copy(referenceType = InventoryReferenceType.DESPACHO, referenceId = 88L))
        )
        db.inventoryCodeEventDao().insertEvents(
            listOf(
                InventoryCodeEventEntity(
                    eventUuid = codeSeed,
                    deviceId = "DEVICE-TEST",
                    polvorin = "POLVORIN-TEST",
                    actionType = InventoryEventType.DESPACHO.name,
                    status = "DESPACHADO",
                    barcode = "315150826000001000100005323457",
                    productId = "315",
                    productName = "EMULEX",
                    presentation = "CAJA",
                    inventoryCategory = "ALTO_EXPLOSIVO",
                    empaque = "CAJA",
                    referenceType = InventoryReferenceType.DESPACHO,
                    referenceId = 88L,
                    timestampIso = "2026-11-22 10:00:00",
                    timestampEpochMs = 1_000L
                )
            )
        )

        repo.reverseReferenceEvents(InventoryReferenceType.DESPACHO, 88L, reason)
        repo.reverseReferenceEvents(InventoryReferenceType.DESPACHO, 88L, reason)

        val qtyReversals = db.inventoryEventDao()
            .getEventsByReference(InventoryReferenceType.DESPACHO, 88L)
            .filter { it.eventType == InventoryEventType.REVERSAL.name }
        val codeReversals = db.inventoryCodeEventDao()
            .getCodeEventsByReference(InventoryReferenceType.DESPACHO, 88L)
            .filter { it.actionType == InventoryEventType.REVERSAL.name }

        assertThat(qtyReversals).hasSize(1)
        assertThat(codeReversals).hasSize(1)
        assertThat(qtyReversals.single().eventUuid).isEqualTo(
            UUID.nameUUIDFromBytes(
                "REVERSAL:${InventoryReferenceType.DESPACHO}:88:$reason:$qtySeed".toByteArray()
            ).toString()
        )
        assertThat(codeReversals.single().eventUuid).isEqualTo(
            UUID.nameUUIDFromBytes(
                "REVERSAL:${InventoryReferenceType.DESPACHO}:88:$reason:$codeSeed".toByteArray()
            ).toString()
        )
    }

    @Test
    fun successiveEditsWithSameReasonKeepDistinctReversals() = runBlocking {
        val code = "XCOR-PENT-5G"
        // UUID lexicográficamente mínimo y estable: garantiza que el seed histórico NO cambie
        // entre ediciones, que es justo el escenario que colisionaba.
        db.inventoryEventDao().insertEvent(
            qtyEvent("00000000-0000-0000-0000-000000000001").copy(
                referenceType = InventoryReferenceType.ACCESORIO_VALE,
                referenceId = 77L,
                productId = code,
                deltaQuantity = 25.0
            )
        )
        suspend fun record(qty: Int) {
            repo.recordAccesoriosVale(
                valeId = 77L,
                vale = "ACC9001",
                empresa = "MPRG",
                lugar = "NIVEL 500",
                isDevolucion = false,
                items = listOf(AccessoryItemRecord(code, qty, "2026-11-22 10:00:00"))
            )
        }

        assertThat(repo.getCurrentStockForProduct(code)).isWithin(1e-4).of(25.0)

        record(-30)
        assertThat(repo.getCurrentStockForProduct(code)).isWithin(1e-4).of(30.0)

        record(-40)
        assertThat(repo.getCurrentStockForProduct(code)).isWithin(1e-4).of(40.0)
    }

    @Test
    fun valeDeletionRollsBackReversalsWhenSecondDeleteFails() = runBlocking {
        val valeId = 99L
        val reason = "Anulación por eliminación de REF99"
        db.despachoDao().insertDespacho(
            DespachoEntity(
                id = valeId,
                vale = "REF99",
                empresa = "MPRG",
                lugar = "NIVEL 500",
                transportista = "TTE",
                turno = "1",
                voladura = "V1",
                fechaCreacion = "2026-11-22 10:00:00"
            )
        )
        db.despachoDao().insertItems(
            listOf(
                DespachoItemEntity(
                    despachoId = valeId,
                    codigoBarras = "315150826000001000100005323457",
                    fechaEscaneo = "2026-11-22 10:01:00",
                    productoId = "315",
                    productoNombre = "EMULEX",
                    presentacion = "CAJA",
                    tipo = "CAJA"
                )
            )
        )
        db.inventoryEventDao().insertEvents(
            listOf(qtyEvent("atomic-qty-99").copy(referenceType = InventoryReferenceType.DESPACHO, referenceId = valeId))
        )
        db.inventoryCodeEventDao().insertEvents(
            listOf(
                InventoryCodeEventEntity(
                    eventUuid = "atomic-code-99",
                    deviceId = "DEVICE-TEST",
                    polvorin = "POLVORIN-TEST",
                    actionType = InventoryEventType.DESPACHO.name,
                    status = "DESPACHADO",
                    barcode = "315150826000001000100005323457",
                    productId = "315",
                    productName = "EMULEX",
                    presentation = "CAJA",
                    inventoryCategory = "ALTO_EXPLOSIVO",
                    empaque = "CAJA",
                    referenceType = InventoryReferenceType.DESPACHO,
                    referenceId = valeId,
                    timestampIso = "2026-11-22 10:00:00",
                    timestampEpochMs = 1_000L
                )
            )
        )
        // Segundo borrado (cabecera) falla a propósito mediante un trigger SQLite.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_delete_despacho BEFORE DELETE ON despachos " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END"
        )

        var threw = false
        try {
            repo.deleteValeAndReverse(InventoryReferenceType.DESPACHO, valeId, reason)
        } catch (e: Exception) {
            threw = true
        }

        assertThat(threw).isTrue()
        // El primer borrado (hijos) también se deshace: no quedó medio vale.
        assertThat(db.despachoDao().getItemsForDespacho(valeId)).hasSize(1)
        val qtyReversals = db.inventoryEventDao()
            .getEventsByReference(InventoryReferenceType.DESPACHO, valeId)
            .filter { it.eventType == InventoryEventType.REVERSAL.name }
        val codeReversals = db.inventoryCodeEventDao()
            .getCodeEventsByReference(InventoryReferenceType.DESPACHO, valeId)
            .filter { it.actionType == InventoryEventType.REVERSAL.name }
        assertThat(qtyReversals).isEmpty()
        assertThat(codeReversals).isEmpty()
    }
}
