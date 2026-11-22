package com.ttexplo.mobile.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.data.local.AppDatabase
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.data.repository.InventoryRepository
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.inventory.InventoryCodeStatus
import com.ttexplo.mobile.domain.inventory.InventoryEventType
import com.ttexplo.mobile.domain.inventory.InventoryReferenceType
import com.ttexplo.mobile.domain.inventory.InventorySyncJsonExporter
import com.ttexplo.mobile.domain.inventory.QuickStockMode
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.ScanValidator
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InventoryAndUnitWeightTest {

    private lateinit var db: AppDatabase
    private lateinit var catalogRepo: CatalogRepositoryImpl
    private lateinit var scanValidator: ScanValidator
    private lateinit var inventoryRepo: InventoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        catalogRepo = CatalogRepositoryImpl(db.productoDao())
        scanValidator = ScanValidator(catalogRepo)
        inventoryRepo = InventoryRepository(
            eventDao = db.inventoryEventDao(),
            codeEventDao = db.inventoryCodeEventDao(),
            productoDao = db.productoDao(),
            deviceIdProvider = { "F020-F600-E29C-76D6" },
            polvorinProvider = { "POLVORÍN PRINCIPAL" }
        )

        runBlocking {
            db.productoDao().insertAll(
                listOf(
                    ProductoEntity(
                        idProducto = "315",
                        producto = "EMULEX 1",
                        presentacion = "1 1/4\" X 16\"",
                        noSap = "111920",
                        unidad = "KG",
                        cantidad = 25.0,
                        codBarras = "315",
                        peso = 25.0,
                        pg = "0035",
                        pu = 0.0,
                        tipo = ProductType.ALTO_EXPLOSIVO.name,
                        categoriaInventario = InventoryCategory.ALTO_EXPLOSIVO.name
                    ),
                    ProductoEntity(
                        idProducto = "298",
                        producto = "AUSTINITE 15 CA-1",
                        presentacion = "SACO 25 KG",
                        noSap = "100091JAL",
                        unidad = "KG",
                        cantidad = 1.0,
                        codBarras = "298",
                        peso = 25.0,
                        pg = "0035",
                        pu = 0.0,
                        tipo = ProductType.AGENTE.name,
                        categoriaInventario = InventoryCategory.AGENTE_EXPLOSIVO.name
                    )
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun box30Digits_autoCalculatesAndPersistsUnitWeightInCatalog() = runBlocking {
        // [315][150826][000001][000100][005323457] -> (100 - 1) + 1 = 100 cartuchos; 25 kg / 100 = 0.250 kg
        val box100 = "315150826000001000100005323457"
        assertThat(box100.length).isEqualTo(30)

        val scanResult = scanValidator.processScan(box100, emptySet(), allowAccessories = false)

        assertThat(scanResult.isValid).isTrue()
        assertThat(scanResult.empaque).isEqualTo(TipoEmpaque.CAJA)
        assertThat(scanResult.detectedCartridgesPerBox).isEqualTo(100)
        assertThat(scanResult.calculatedUnitWeightKg).isWithin(1e-6).of(0.25)
        assertThat(scanResult.productInfo.unitWeightKg).isWithin(1e-6).of(0.25)
        assertThat(scanResult.productInfo.cartridgesPerBox).isEqualTo(100)
        assertThat(scanResult.productInfo.inventoryCategory).isEqualTo(InventoryCategory.ALTO_EXPLOSIVO)

        // Verify persisted in Room catalog
        val persisted = db.productoDao().getById("315")
        assertThat(persisted).isNotNull()
        assertThat(persisted!!.cartuchosPorCaja).isEqualTo(100)
        assertThat(persisted.pesoUnitarioKg).isWithin(1e-6).of(0.25)
    }

    @Test
    fun visualEquivalenceFormatter_matchesExactExamples() {
        val explosive = BoxUnitWeightCalculator.formatVisualEquivalence(
            stock = 1003.0,
            unit = "KG",
            productType = ProductType.ALTO_EXPLOSIVO,
            boxWeightKg = 25.0,
            unitWeightKg = 0.25,
            cartridgesPerBox = 100
        )
        assertThat(explosive).isEqualTo("1,003.00 kg · 40 cajas + 12 cart.")

        val agent = BoxUnitWeightCalculator.formatVisualEquivalence(
            stock = 625.0,
            unit = "KG",
            productType = ProductType.AGENTE,
            boxWeightKg = 25.0,
            unitWeightKg = 0.0,
            cartridgesPerBox = 0
        )
        assertThat(agent).isEqualTo("625.00 kg · 25 sacos")

        val pza = BoxUnitWeightCalculator.formatVisualEquivalence(
            stock = 350.0,
            unit = "PZA",
            productType = ProductType.ACCESORIO,
            boxWeightKg = 0.0,
            unitWeightKg = 0.0,
            cartridgesPerBox = 0
        )
        assertThat(pza).isEqualTo("350 PZA")

        val mts = BoxUnitWeightCalculator.formatVisualEquivalence(
            stock = 1250.5,
            unit = "MTS",
            productType = ProductType.ACCESORIO,
            boxWeightKg = 0.0,
            unitWeightKg = 0.0,
            cartridgesPerBox = 0
        )
        assertThat(mts).isEqualTo("1,250.50 MTS")
    }

    @Test
    fun newAccessoryCodes_autoSuggestCategoryAndUnit_andRespectManualOverride() = runBlocking {
        // Auto-suggestions on unregistered X... codes
        val cordon = catalogRepo.lookupBarcode("XCOR-PENT-5G")
        assertThat(cordon.inventoryCategory).isEqualTo(InventoryCategory.CORDON_DETONANTE)
        assertThat(cordon.unit).isEqualTo("MTS")

        val canuela = catalogRepo.lookupBarcode("XCAN-SEG-3M")
        assertThat(canuela.inventoryCategory).isEqualTo(InventoryCategory.CANUELAS)
        assertThat(canuela.unit).isEqualTo("PZA")

        val detonador = catalogRepo.lookupBarcode("XDET-NOEL-3M")
        assertThat(detonador.inventoryCategory).isEqualTo(InventoryCategory.DETONADORES)
        assertThat(detonador.unit).isEqualTo("PZA")

        // Operator overrides an ambiguous X-code to CANUELAS via Quick Physical Count
        inventoryRepo.recordQuickPhysicalCount(
            productId = "X999-CUSTOM",
            productName = "Cañuela Especial 2.4m",
            presentation = "Pieza",
            productType = ProductType.ACCESORIO,
            inventoryCategory = InventoryCategory.CANUELAS,
            unit = "PZA",
            mode = QuickStockMode.SET_PHYSICAL_COUNT,
            targetQuantity = 75.0
        )

        val saved = catalogRepo.lookupBarcode("X999-CUSTOM")
        assertThat(saved.inventoryCategory).isEqualTo(InventoryCategory.CANUELAS)
        assertThat(saved.unit).isEqualTo("PZA")
    }

    @Test
    fun fiveFamilies_dailyOperations_sedenaBarcodeLedger_reversals_andSyncJsonV2() = runBlocking {
        // 1. Scan 1 sample 30-digit box of 315 (100 cartridges -> 0.250 kg/cart.)
        val box100 = "315150826000001000100005323457"
        val sampleScan = scanValidator.processScan(box100, emptySet(), allowAccessories = true)
        assertThat(sampleScan.isValid).isTrue()

        // 2. Quick physical count across Alto Explosivo + 3 separated accessory families
        val totalInitialKg = BoxUnitWeightCalculator.calculateExplosiveWeightKg(
            fullBoxes = 40,
            looseCartridges = 12,
            boxWeightKg = 25.0,
            unitWeightKg = 0.25
        )
        assertThat(totalInitialKg).isWithin(1e-6).of(1003.0)

        val initEvent = inventoryRepo.recordQuickPhysicalCount(
            productId = "315",
            productName = "EMULEX 1",
            presentation = "1 1/4\" X 16\"",
            productType = ProductType.ALTO_EXPLOSIVO,
            inventoryCategory = InventoryCategory.ALTO_EXPLOSIVO,
            unit = "KG",
            mode = QuickStockMode.SET_PHYSICAL_COUNT,
            targetQuantity = totalInitialKg,
            cajasCount = 40,
            cartuchosCount = 12,
            boxWeightKg = 25.0,
            unitWeightKg = 0.25,
            cartridgesPerBox = 100
        )
        assertThat(initEvent).isNotNull()
        assertThat(initEvent!!.eventType).isEqualTo(InventoryEventType.INITIAL_STOCK.name)

        // Cordón Detonante (MTS), Cañuelas (PZA), Detonadores (PZA) — strictly separated
        inventoryRepo.recordQuickPhysicalCount(
            productId = "XCOR-PENT-5G",
            productName = "Cordón Detonante 5g",
            presentation = "Rollo",
            productType = ProductType.ACCESORIO,
            inventoryCategory = InventoryCategory.CORDON_DETONANTE,
            unit = "MTS",
            mode = QuickStockMode.SET_PHYSICAL_COUNT,
            targetQuantity = 500.0
        )
        inventoryRepo.recordQuickPhysicalCount(
            productId = "XCAN-SEG-3M",
            productName = "Cañuela de Seguridad 3m",
            presentation = "Pieza",
            productType = ProductType.ACCESORIO,
            inventoryCategory = InventoryCategory.CANUELAS,
            unit = "PZA",
            mode = QuickStockMode.SET_PHYSICAL_COUNT,
            targetQuantity = 120.0
        )
        inventoryRepo.recordQuickPhysicalCount(
            productId = "XDET-NOEL-3M",
            productName = "Detonador No Eléctrico 3m",
            presentation = "Pieza",
            productType = ProductType.ACCESORIO,
            inventoryCategory = InventoryCategory.DETONADORES,
            unit = "PZA",
            mode = QuickStockMode.SET_PHYSICAL_COUNT,
            targetQuantity = 200.0
        )

        // 3. Daily Entrada: +2 boxes of Alto (315, +50 kg) and +4 sacks of Agente (298, +100 kg)
        //    Each scanned barcode MUST enter the SEDENA code ledger as EN_POLVORIN.
        val prod315 = catalogRepo.lookupBarcode(box100)
        val prod298 = catalogRepo.lookupBarcode("298020926004801474")
        val boxCodeA = "315150826000001000100005323458"
        val boxCodeB = "315150826000001000100005323459"
        val sackCode1 = "298020926004801471"
        val sackCode2 = "298020926004801472"
        val sackCode3 = "298020926004801473"
        val sackCode4 = "298020926004801474"

        inventoryRepo.recordEntrada(
            entradaId = 10L,
            proveedor = "1",
            polvorin = "POLVORÍN PRINCIPAL",
            factura = "FAC-900",
            items = listOf(
                BarcodeItem(boxCodeA, "2026-11-22 08:00:00", prod315, TipoEmpaque.CAJA),
                BarcodeItem(boxCodeB, "2026-11-22 08:00:01", prod315, TipoEmpaque.CAJA),
                BarcodeItem(sackCode1, "2026-11-22 08:00:02", prod298, TipoEmpaque.SACO),
                BarcodeItem(sackCode2, "2026-11-22 08:00:03", prod298, TipoEmpaque.SACO),
                BarcodeItem(sackCode3, "2026-11-22 08:00:04", prod298, TipoEmpaque.SACO),
                BarcodeItem(sackCode4, "2026-11-22 08:00:05", prod298, TipoEmpaque.SACO)
            )
        )

        // Verify codes after Entrada: 2 Alto EN_POLVORIN, 4 Agente EN_POLVORIN
        val altoCodesAfterEntrada = inventoryRepo.getProductCodes("315")
        val agenteCodesAfterEntrada = inventoryRepo.getProductCodes("298")
        assertThat(altoCodesAfterEntrada.count { it.status == InventoryCodeStatus.EN_POLVORIN }).isEqualTo(2)
        assertThat(agenteCodesAfterEntrada.count { it.status == InventoryCodeStatus.EN_POLVORIN }).isEqualTo(4)

        // 4. Daily Despacho: -1 box (boxCodeA, 25 kg) & -4 loose cartridges (1.00 kg) of Alto 315
        //    AND -1 sack (sackCode1, 25 kg) of Agente 298
        val cart1 = "3152101024112931"
        val cart2 = "3152101024112932"
        val cart3 = "3152101024112933"
        val cart4 = "3152101024112934"

        inventoryRepo.recordDespacho(
            despachoId = 20L,
            vale = "7712",
            empresa = "MPRG",
            lugar = "NIVEL 500",
            items = listOf(
                BarcodeItem(boxCodeA, "2026-11-22 09:00:00", prod315, TipoEmpaque.CAJA),
                BarcodeItem(cart1, "2026-11-22 09:00:01", prod315, TipoEmpaque.PIEZA),
                BarcodeItem(cart2, "2026-11-22 09:00:02", prod315, TipoEmpaque.PIEZA),
                BarcodeItem(cart3, "2026-11-22 09:00:03", prod315, TipoEmpaque.PIEZA),
                BarcodeItem(cart4, "2026-11-22 09:00:04", prod315, TipoEmpaque.PIEZA),
                BarcodeItem(sackCode1, "2026-11-22 09:00:05", prod298, TipoEmpaque.SACO)
            )
        )
        // Alto 315: 1003 + 50 - 26 = 1027.00 kg | boxCodeA -> DESPACHADO, boxCodeB -> EN_POLVORIN
        // Agente 298: 100 - 25 = 75.00 kg | sackCode1 -> DESPACHADO, 3 sacks -> EN_POLVORIN

        val altoAfterDespacho = inventoryRepo.getProductCodes("315")
        assertThat(altoAfterDespacho.first { it.barcode == boxCodeA }.status).isEqualTo(InventoryCodeStatus.DESPACHADO)
        assertThat(altoAfterDespacho.first { it.barcode == boxCodeB }.status).isEqualTo(InventoryCodeStatus.EN_POLVORIN)
        assertThat(altoAfterDespacho.first { it.barcode == cart3 }.status).isEqualTo(InventoryCodeStatus.DESPACHADO)

        // 5. Daily Devolucion: +2 loose cartridges (cart3, cart4 -> +0.50 kg) re-enter as EN_POLVORIN!
        inventoryRepo.recordDevolucion(
            devolucionId = 30L,
            empleadoEntrega = "1980",
            empleadoRecibe = "2527",
            items = listOf(
                BarcodeItem(cart3, "2026-11-22 10:00:00", prod315, TipoEmpaque.PIEZA),
                BarcodeItem(cart4, "2026-11-22 10:00:01", prod315, TipoEmpaque.PIEZA)
            )
        )
        // Alto 315 = 1027.50 kg (41 cajas + 10 cart.) | EN_POLVORIN codes = boxCodeB, cart3, cart4 (3 codes!)
        val altoAfterDev = inventoryRepo.getProductCodes("315")
        assertThat(altoAfterDev.first { it.barcode == cart3 }.status).isEqualTo(InventoryCodeStatus.EN_POLVORIN)
        assertThat(altoAfterDev.first { it.barcode == cart4 }.status).isEqualTo(InventoryCodeStatus.EN_POLVORIN)
        assertThat(altoAfterDev.count { it.status == InventoryCodeStatus.EN_POLVORIN }).isEqualTo(3)

        // 6. Accesorios Salida & Devolución across all 3 separated accessory families
        inventoryRepo.recordAccesoriosVale(
            valeId = 40L,
            vale = "9001",
            empresa = "MPRG",
            lugar = "NIVEL 500",
            isDevolucion = false,
            items = listOf(
                AccessoryItemRecord("XCOR-PENT-5G", 120, "2026-11-22 09:31:00"),
                AccessoryItemRecord("XCAN-SEG-3M", 30, "2026-11-22 09:32:00"),
                AccessoryItemRecord("XDET-NOEL-3M", 45, "2026-11-22 09:33:00")
            )
        )
        inventoryRepo.recordAccesoriosVale(
            valeId = 41L,
            vale = "9002",
            empresa = "DEV: MPRG",
            lugar = "NIVEL 500",
            isDevolucion = true,
            items = listOf(
                AccessoryItemRecord("XDET-NOEL-3M", -5, "2026-11-22 10:30:00"),
                AccessoryItemRecord("XCAN-SEG-3M", -10, "2026-11-22 10:31:00")
            )
        )

        // Verify ALL 5 separated totals in dashboard!
        val (totals, stockItems) = inventoryRepo.observeInventoryOverview().first()
        assertThat(totals.altoExplosivoKg).isWithin(1e-4).of(1027.5)
        assertThat(totals.agenteExplosivoKg).isWithin(1e-4).of(75.0)
        assertThat(totals.cordonDetonanteMts).isWithin(1e-4).of(380.0) // 500 - 120
        assertThat(totals.canuelasPza).isWithin(1e-4).of(100.0)        // 120 - 30 + 10
        assertThat(totals.detonadoresPza).isWithin(1e-4).of(160.0)     // 200 - 45 + 5
        assertThat(totals.altoCodesInStock).isEqualTo(3)               // boxCodeB + cart3 + cart4
        assertThat(totals.agenteCodesInStock).isEqualTo(3)             // sackCode2, 3, 4
        assertThat(totals.calibratedUnitWeightCount).isEqualTo(1)

        val item315 = stockItems.first { it.productId == "315" }
        assertThat(item315.visualEquivalence).isEqualTo("1,027.50 kg · 41 cajas + 10 cart.")
        assertThat(item315.codesInPolvorinCount).isEqualTo(3)
        assertThat(item315.codesDispatchedCount).isEqualTo(3) // boxCodeA, cart1, cart2

        // 7. Kardex running balance check (entries returned newest-first)
        val kardex315 = inventoryRepo.observeProductKardex("315").first()
        assertThat(kardex315).hasSize(4)
        assertThat(kardex315.first().runningBalance).isWithin(1e-4).of(1027.5)
        assertThat(kardex315.last().runningBalance).isWithin(1e-4).of(1003.0)

        // 8. Reversal on deleting Despacho 20L restores BOTH quantities AND SEDENA barcode statuses!
        inventoryRepo.reverseReferenceEvents(InventoryReferenceType.DESPACHO, 20L, "Vale eliminado")
        assertThat(inventoryRepo.getCurrentStockForProduct("315")).isWithin(1e-4).of(1053.5)
        assertThat(inventoryRepo.getCurrentStockForProduct("298")).isWithin(1e-4).of(100.0)

        val altoAfterReversal = inventoryRepo.getProductCodes("315")
        val agenteAfterReversal = inventoryRepo.getProductCodes("298")
        // boxCodeA MUST revert from DESPACHADO back to EN_POLVORIN (from Entrada 10L)!
        assertThat(altoAfterReversal.first { it.barcode == boxCodeA }.status).isEqualTo(InventoryCodeStatus.EN_POLVORIN)
        // sackCode1 MUST revert from DESPACHADO back to EN_POLVORIN (from Entrada 10L)!
        assertThat(agenteAfterReversal.first { it.barcode == sackCode1 }.status).isEqualTo(InventoryCodeStatus.EN_POLVORIN)
        assertThat(agenteAfterReversal.count { it.status == InventoryCodeStatus.EN_POLVORIN }).isEqualTo(4)
        // cart1 & cart2 only existed in Despacho 20L -> once reversed, they disappear from active ledger!
        assertThat(altoAfterReversal.none { it.barcode == cart1 }).isTrue()
        assertThat(altoAfterReversal.none { it.barcode == cart2 }).isTrue()

        // 9. Export Sync JSON v2 contains both `events` and `code_events` with unique UUIDs
        val syncJson = inventoryRepo.exportEventsAsSyncJson()
        val root = JSONObject(syncJson)
        assertThat(root.getString("schema_version")).isEqualTo(InventorySyncJsonExporter.SCHEMA_VERSION)
        assertThat(root.getString("device_id")).isEqualTo("F020-F600-E29C-76D6")
        val eventsArr = root.getJSONArray("events")
        val codeEventsArr = root.getJSONArray("code_events")
        assertThat(eventsArr.length()).isGreaterThan(9)
        assertThat(codeEventsArr.length()).isGreaterThan(10)

        val qtyUuids = (0 until eventsArr.length()).map { eventsArr.getJSONObject(it).getString("event_uuid") }
        val codeUuids = (0 until codeEventsArr.length()).map { codeEventsArr.getJSONObject(it).getString("event_uuid") }
        assertThat(qtyUuids.toSet().size).isEqualTo(eventsArr.length())
        assertThat(codeUuids.toSet().size).isEqualTo(codeEventsArr.length())
    }
}
