package com.ttexplo.mobile.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.data.local.AppDatabase
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.local.entity.SyncSessionDirection
import com.ttexplo.mobile.data.repository.InventoryRepository
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ttexplo.mobile.domain.inventory.Base45Codec
import com.ttexplo.mobile.domain.inventory.InventoryCodeStatus
import com.ttexplo.mobile.domain.inventory.QrChunkIngestResult
import com.ttexplo.mobile.domain.inventory.QrDensityMode
import com.ttexplo.mobile.domain.inventory.QrMatrixGenerator
import com.ttexplo.mobile.domain.inventory.QrSyncCodec
import com.ttexplo.mobile.domain.inventory.QrSyncReassembler
import com.ttexplo.mobile.domain.inventory.QuickStockMode
import com.ttexplo.mobile.domain.inventory.SyncConflictSeverity
import com.ttexplo.mobile.domain.inventory.SyncConflictType
import com.ttexplo.mobile.domain.inventory.SyncExportScope
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InventoryMultiScannerSyncTest {

    private lateinit var dbA: AppDatabase
    private lateinit var dbB: AppDatabase
    private lateinit var repoA: InventoryRepository
    private lateinit var repoB: InventoryRepository

    private val altoInfo = ProductInfo(
        id = "315",
        name = "EMULEX 1",
        presentation = "1 1/4\" X 16\"",
        weightKg = 25.0,
        noSap = "111920",
        pg = "0035",
        type = ProductType.ALTO_EXPLOSIVO,
        unit = "KG",
        unitWeightKg = 0.25,
        cartridgesPerBox = 100,
        inventoryCategory = InventoryCategory.ALTO_EXPLOSIVO
    )

    private val agenteInfo = ProductInfo(
        id = "298",
        name = "AUSTINITE 15 CA-1",
        presentation = "SACO 25 KG",
        weightKg = 25.0,
        noSap = "100091",
        pg = "0035",
        type = ProductType.AGENTE,
        unit = "KG",
        inventoryCategory = InventoryCategory.AGENTE_EXPLOSIVO
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repoA = InventoryRepository(
            eventDao = dbA.inventoryEventDao(),
            codeEventDao = dbA.inventoryCodeEventDao(),
            productoDao = dbA.productoDao(),
            deviceIdProvider = { "ZBR-POLV-01" },
            polvorinProvider = { "POLVORÍN NORTE" },
            syncSessionDao = dbA.syncSessionDao()
        )
        repoB = InventoryRepository(
            eventDao = dbB.inventoryEventDao(),
            codeEventDao = dbB.inventoryCodeEventDao(),
            productoDao = dbB.productoDao(),
            deviceIdProvider = { "ZBR-POLV-02" },
            polvorinProvider = { "POLVORÍN NORTE" },
            syncSessionDao = dbB.syncSessionDao()
        )

        val seedProducts = listOf(
            ProductoEntity(
                idProducto = "315",
                producto = "EMULEX 1",
                presentacion = "1 1/4\" X 16\"",
                noSap = "111920",
                unidad = "KG",
                cantidad = 100.0,
                codBarras = "315",
                peso = 25.0,
                pg = "0035",
                pu = 0.0,
                tipo = ProductType.ALTO_EXPLOSIVO.name,
                pesoUnitarioKg = 0.25,
                cartuchosPorCaja = 100,
                categoriaInventario = InventoryCategory.ALTO_EXPLOSIVO.name
            ),
            ProductoEntity(
                idProducto = "298",
                producto = "AUSTINITE 15 CA-1",
                presentacion = "SACO 25 KG",
                noSap = "100091",
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

        runBlocking {
            dbA.productoDao().insertAll(seedProducts)
            // En Terminal B simulamos que el peso unitario de 315 aún NO estaba calibrado (0.0)
            dbB.productoDao().insertAll(
                seedProducts.map { if (it.idProducto == "315") it.copy(pesoUnitarioKg = 0.0, cartuchosPorCaja = 0) else it }
            )
        }
    }

    @After
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    @Test
    fun qualityGate1_qrMultiPartEncodeReassembleOutOfOrderAndRejectCorruptOrMixed() {
        val sampleJson = """{"schema_version":"ttexplo-inventory-events-v2","device_id":"ZBR-POLV-01","events":[${
            (1..12).joinToString(",") { i ->
                """{"event_uuid":"uuid-$i","product_id":"315","delta_quantity":25.0,"notes":"Entrada lote numero $i con descripcion amplia"}"""
            }
        }]}"""

        val pkg = QrSyncCodec.encode(
            json = sampleJson,
            eventCount = 12,
            codeEventCount = 0,
            maxChunkChars = 120
        )

        assertThat(pkg.chunks.size).isAtLeast(3)

        // Validar generación real de matriz QR ISO con ZXing
        val matrix = QrMatrixGenerator.encode(pkg.chunks.first().rawText)
        assertThat(matrix.size).isGreaterThan(20)

        // 1) Reensamblar en orden aleatorio (último, primero, intermedios) + duplicado intermedio
        val reassembler = QrSyncReassembler()
        val reordered = listOf(pkg.chunks.last(), pkg.chunks.first()) + pkg.chunks.drop(1).dropLast(1)

        var finalJson: String? = null
        reordered.forEachIndexed { idx, chunk ->
            val res = reassembler.acceptScan(chunk.rawText)
            if (idx < reordered.lastIndex) {
                assertThat(res).isInstanceOf(QrChunkIngestResult.Progress::class.java)
                if (idx == 0) {
                    // Repetir el mismo QR no rompe ni corrompe la sesión
                    val dup = reassembler.acceptScan(chunk.rawText)
                    assertThat(dup).isInstanceOf(QrChunkIngestResult.Progress::class.java)
                    assertThat((dup as QrChunkIngestResult.Progress).wasDuplicatePart).isTrue()
                }
            } else {
                assertThat(res).isInstanceOf(QrChunkIngestResult.Complete::class.java)
                finalJson = (res as QrChunkIngestResult.Complete).json
            }
        }
        assertThat(finalJson).isEqualTo(sampleJson)

        // 2) Rechazo de CRC32 corrupto
        val corruptReassembler = QrSyncReassembler()
        val lastIdx = pkg.chunks.lastIndex
        pkg.chunks.forEachIndexed { idx, chunk ->
            val text = if (idx == lastIdx) {
                // Alterar el último carácter de la carga útil manteniendo el CRC declarado
                val alteredPayload = chunk.payloadChunk.dropLast(1) + if (chunk.payloadChunk.last() == 'A') 'B' else 'A'
                "TTEXSYNC|V2|${chunk.sessionId}|${chunk.partIndex}/${chunk.totalParts}|${chunk.expectedCrc32Hex}|$alteredPayload"
            } else {
                chunk.rawText
            }
            val res = corruptReassembler.acceptScan(text)
            if (idx == lastIdx) {
                assertThat(res).isInstanceOf(QrChunkIngestResult.Error::class.java)
                assertThat((res as QrChunkIngestResult.Error).reason).contains("CRC32")
            }
        }

        // 3) Rechazo de paquete mezclado de otra sesión
        val otherPkg = QrSyncCodec.encode(sampleJson, maxChunkChars = 120, sessionIdOverride = "ZZ9999")
        val mixedReassembler = QrSyncReassembler()
        val firstOk = mixedReassembler.acceptScan(pkg.chunks.first().rawText)
        assertThat(firstOk).isInstanceOf(QrChunkIngestResult.Progress::class.java)
        val mismatch = mixedReassembler.acceptScan(otherPkg.chunks.first().rawText)
        assertThat(mismatch).isInstanceOf(QrChunkIngestResult.Error::class.java)
        assertThat((mismatch as QrChunkIngestResult.Error).isSessionMismatch).isTrue()
    }

    @Test
    fun qualityGate2_twoScannersBidirectionalSyncAcross5Families_convergeIdempotentlyWithZeroDuplicates() = runBlocking {
        // Escáner A: Entrada Alto Explosivo (2 cajas = 50 kg, 2 códigos 30d) + Carga Inicial Cordón (500 MTS) + Cañuelas (200 PZA)
        val box1 = "315150826000001000100005323401"
        val box2 = "315150826000001000100005323402"
        repoA.recordEntrada(
            entradaId = 101L,
            proveedor = "AUSTIN",
            polvorin = "POLVORÍN NORTE",
            factura = "FAC-01",
            items = listOf(
                BarcodeItem(box1, "2026-11-22 08:00:00", altoInfo, TipoEmpaque.CAJA),
                BarcodeItem(box2, "2026-11-22 08:00:01", altoInfo, TipoEmpaque.CAJA)
            )
        )
        repoA.recordQuickPhysicalCount(
            productId = "XCOR-01",
            productName = "CORDÓN DETONANTE 5G",
            presentation = "MTS",
            productType = ProductType.ACCESORIO,
            unit = "MTS",
            mode = QuickStockMode.ADD_INITIAL_STOCK,
            targetQuantity = 500.0,
            inventoryCategory = InventoryCategory.CORDON_DETONANTE
        )
        repoA.recordQuickPhysicalCount(
            productId = "XCAN-01",
            productName = "CAÑUELA 2.4M",
            presentation = "PZA",
            productType = ProductType.ACCESORIO,
            unit = "PZA",
            mode = QuickStockMode.ADD_INITIAL_STOCK,
            targetQuantity = 200.0,
            inventoryCategory = InventoryCategory.CANUELAS
        )

        // Escáner B: Entrada Agente (4 sacos = 100 kg, 1 código 18d) + Despacho 1 caja Alto (-25 kg, box1) + Detonadores (150 PZA)
        val saco1 = "298150826000000001"
        repoB.recordEntrada(
            entradaId = 201L,
            proveedor = "AUSTIN",
            polvorin = "POLVORÍN NORTE",
            factura = "FAC-02",
            items = listOf(BarcodeItem(saco1, "2026-11-22 08:15:00", agenteInfo, TipoEmpaque.SACO))
        )
        repoB.recordDespacho(
            despachoId = 202L,
            vale = "REF-B1",
            empresa = "MINA SUR",
            lugar = "NIVEL 4",
            items = listOf(BarcodeItem(box1, "2026-11-22 09:00:00", altoInfo, TipoEmpaque.CAJA))
        )
        repoB.recordQuickPhysicalCount(
            productId = "XDET-01",
            productName = "DETONADOR NO ELÉCTRICO",
            presentation = "PZA",
            productType = ProductType.ACCESORIO,
            unit = "PZA",
            mode = QuickStockMode.ADD_INITIAL_STOCK,
            targetQuantity = 150.0,
            inventoryCategory = InventoryCategory.DETONADORES
        )
        repoB.recordAccesoriosVale(
            valeId = 203L,
            vale = "ACC-B1",
            empresa = "MINA SUR",
            lugar = "NIVEL 4",
            isDevolucion = false,
            items = listOf(AccessoryItemRecord("XCOR-01", 80, "2026-11-22 09:10:00")) // -80 MTS de cordón
        )

        // 1) A -> B vía QR multi-parte (en orden inverso)
        val pkgA = repoA.buildSyncExportPackage(SyncExportScope.PENDING_ONLY, maxChunkChars = 250)
        val reassemblerB = QrSyncReassembler()
        var jsonFromA = ""
        for (chunk in pkgA.qrPackage.chunks.reversed()) {
            val res = reassemblerB.acceptScan(chunk.rawText)
            if (res is QrChunkIngestResult.Complete) jsonFromA = res.json
        }
        val reportB1 = repoB.importSyncJson(jsonFromA, SyncSessionDirection.IMPORT_QR).getOrThrow()
        assertThat(reportB1.eventsMerged).isEqualTo(3)
        assertThat(reportB1.codeEventsMerged).isEqualTo(2)
        assertThat(reportB1.eventsSkippedDuplicate).isEqualTo(0)
        // Verificar propagación automática de calibración de peso unitario en Terminal B
        val prod315OnB = dbB.productoDao().getById("315")!!
        assertThat(prod315OnB.pesoUnitarioKg).isWithin(1e-6).of(0.25)

        // Re-importar exactamente el mismo paquete en B -> 0 nuevos, 100% duplicados omitidos
        val reportB2 = repoB.importSyncJson(jsonFromA, SyncSessionDirection.IMPORT_QR).getOrThrow()
        assertThat(reportB2.eventsMerged).isEqualTo(0)
        assertThat(reportB2.codeEventsMerged).isEqualTo(0)
        assertThat(reportB2.eventsSkippedDuplicate).isEqualTo(3)
        assertThat(reportB2.codeEventsSkippedDuplicate).isEqualTo(2)

        // 2) B -> A vía JSON completo
        val pkgB = repoB.buildSyncExportPackage(SyncExportScope.FULL_HISTORY)
        val reportA = repoA.importSyncJson(pkgB.jsonPretty, SyncSessionDirection.IMPORT_JSON).getOrThrow()
        assertThat(reportA.eventsMerged).isEqualTo(4) // 4 eventos propios de B (los 3 de A fueron omitidos por UUID)
        assertThat(reportA.eventsSkippedDuplicate).isEqualTo(3)

        // 3) Ambos escáneres convergen a totales 100% IDÉNTICOS en las 5 familias y códigos SEDENA
        val (totalsA, _) = repoA.observeInventoryOverview().first()
        val (totalsB, _) = repoB.observeInventoryOverview().first()

        assertThat(totalsA.altoExplosivoKg).isWithin(1e-4).of(25.0) // +50 - 25
        assertThat(totalsB.altoExplosivoKg).isWithin(1e-4).of(25.0)
        assertThat(totalsA.agenteExplosivoKg).isWithin(1e-4).of(25.0)
        assertThat(totalsB.agenteExplosivoKg).isWithin(1e-4).of(25.0)
        assertThat(totalsA.cordonDetonanteMts).isWithin(1e-4).of(420.0) // 500 - 80
        assertThat(totalsB.cordonDetonanteMts).isWithin(1e-4).of(420.0)
        assertThat(totalsA.canuelasPza).isWithin(1e-4).of(200.0)
        assertThat(totalsB.canuelasPza).isWithin(1e-4).of(200.0)
        assertThat(totalsA.detonadoresPza).isWithin(1e-4).of(150.0)
        assertThat(totalsB.detonadoresPza).isWithin(1e-4).of(150.0)

        // Códigos SEDENA de 315: box1 DESPACHADO, box2 EN_POLVORIN en ambos escáneres
        val codesA = repoA.getProductCodes("315").associateBy { it.barcode }
        val codesB = repoB.getProductCodes("315").associateBy { it.barcode }
        assertThat(codesA[box1]?.status).isEqualTo(InventoryCodeStatus.DESPACHADO)
        assertThat(codesA[box2]?.status).isEqualTo(InventoryCodeStatus.EN_POLVORIN)
        assertThat(codesB[box1]?.status).isEqualTo(InventoryCodeStatus.DESPACHADO)
        assertThat(codesB[box2]?.status).isEqualTo(InventoryCodeStatus.EN_POLVORIN)

        // Bitácora de sesiones registrada en Room v5
        val sessionsB = repoB.observeSyncSessions().first()
        assertThat(sessionsB.size).isEqualTo(2)
    }

    @Test
    fun qualityGate3_conflictAnalyzerDetectsDoubleDispatchConcurrentCountAndNegativeBalance() = runBlocking {
        val duplicateBox = "315150826000001000100009999901"

        // Escáner A despacha la caja `duplicateBox` (sin entrada previa -> además genera saldo negativo)
        // y registra conteo físico en XDET-99
        repoA.recordDespacho(
            despachoId = 301L,
            vale = "REF-A1",
            empresa = "MINA A",
            lugar = "NIVEL 1",
            items = listOf(BarcodeItem(duplicateBox, "2026-11-22 10:00:00", altoInfo, TipoEmpaque.CAJA))
        )
        repoA.recordQuickPhysicalCount(
            productId = "XDET-99",
            productName = "DETONADOR TEST",
            presentation = "PZA",
            productType = ProductType.ACCESORIO,
            unit = "PZA",
            mode = QuickStockMode.SET_PHYSICAL_COUNT,
            targetQuantity = 100.0,
            inventoryCategory = InventoryCategory.DETONADORES
        )

        // Escáner B despacha EXACTAMENTE LA MISMA serie `duplicateBox` en otro vale
        // y también registra conteo físico paralelo en XDET-99 dentro de la misma hora
        repoB.recordDespacho(
            despachoId = 401L,
            vale = "REF-B9",
            empresa = "MINA B",
            lugar = "NIVEL 2",
            items = listOf(BarcodeItem(duplicateBox, "2026-11-22 10:05:00", altoInfo, TipoEmpaque.CAJA))
        )
        repoB.recordQuickPhysicalCount(
            productId = "XDET-99",
            productName = "DETONADOR TEST",
            presentation = "PZA",
            productType = ProductType.ACCESORIO,
            unit = "PZA",
            mode = QuickStockMode.SET_PHYSICAL_COUNT,
            targetQuantity = 95.0,
            inventoryCategory = InventoryCategory.DETONADORES
        )

        // Fusionar B -> A
        val pkgB = repoB.buildSyncExportPackage(SyncExportScope.FULL_HISTORY)
        val report = repoA.importSyncJson(pkgB.jsonCompact, SyncSessionDirection.IMPORT_QR).getOrThrow()

        val byType = report.conflicts.groupBy { it.type }

        // (a) Doble despacho SEDENA detectado como CRÍTICO involucrando ambas terminales
        val doubleDispatches = byType[SyncConflictType.DOUBLE_DISPATCH_SEDENA_CODE].orEmpty()
        assertThat(doubleDispatches).hasSize(1)
        assertThat(doubleDispatches.first().severity).isEqualTo(SyncConflictSeverity.CRITICAL)
        assertThat(doubleDispatches.first().barcode).isEqualTo(duplicateBox)
        assertThat(doubleDispatches.first().involvedDevices).containsExactly("ZBR-POLV-01", "ZBR-POLV-02")

        // (b) Conteo físico concurrente en XDET-99 detectado como ADVERTENCIA
        val concurrentCounts = byType[SyncConflictType.CONCURRENT_PHYSICAL_COUNT].orEmpty()
        assertThat(concurrentCounts).hasSize(1)
        assertThat(concurrentCounts.first().severity).isEqualTo(SyncConflictSeverity.WARNING)
        assertThat(concurrentCounts.first().productId).isEqualTo("XDET-99")

        // (c) Saldo negativo en 315 (-50 kg) detectado como ALERTA
        val negatives = byType[SyncConflictType.NEGATIVE_STOCK_BALANCE].orEmpty()
        assertThat(negatives).hasSize(1)
        assertThat(negatives.first().severity).isEqualTo(SyncConflictSeverity.ALERT)
        assertThat(negatives.first().productId).isEqualTo("315")
    }

    @Test
    fun v241_base45Rfc9285VectorsAndQrAlphanumericAlphabet() {
        // Vectores oficiales RFC 9285 sección 4
        assertThat(Base45Codec.encode("AB".toByteArray(Charsets.US_ASCII))).isEqualTo("BB8")
        assertThat(Base45Codec.encode("Hello!!".toByteArray(Charsets.US_ASCII))).isEqualTo("%69 VD92EX0")
        assertThat(Base45Codec.encode("base-45".toByteArray(Charsets.US_ASCII))).isEqualTo("UJCLQE7W581")
        assertThat(Base45Codec.encode("ietf!".toByteArray(Charsets.US_ASCII))).isEqualTo("QED8WEX0")

        // Round-trip de bytes arbitrarios (longitud par e impar, incluyendo 0x00 y 0xFF)
        val evenBytes = ByteArray(256) { it.toByte() }
        val oddBytes = ByteArray(257) { ((it * 37) and 0xFF).toByte() }
        val encEven = Base45Codec.encode(evenBytes)
        val encOdd = Base45Codec.encode(oddBytes)
        assertThat(Base45Codec.isQrAlphanumeric(encEven)).isTrue()
        assertThat(Base45Codec.isQrAlphanumeric(encOdd)).isTrue()
        assertThat(Base45Codec.decode(encEven)).isEqualTo(evenBytes)
        assertThat(Base45Codec.decode(encOdd)).isEqualTo(oddBytes)
    }

    @Test
    fun v241_highCapacityV3_fits25PlusEventsInSingleQrWithAlphanumericModeAndLosslessMerge() = runBlocking {
        // Generamos un turno realista grande en Escáner A: 20 cajas SEDENA (20 code_events + 1 entrada)
        // + 6 movimientos de accesorios/ajustes en distintas familias = 27 eventos combinados
        val boxes = (1..20).map { i ->
            val seq = i.toString().padStart(9, '0')
            BarcodeItem("315150826000001000100$seq", "2026-11-22 07:30:00", altoInfo, TipoEmpaque.CAJA)
        }
        repoA.recordEntrada(
            entradaId = 501L,
            proveedor = "AUSTIN",
            polvorin = "POLVORÍN NORTE",
            factura = "FAC-TURNO-1",
            items = boxes
        )
        for (k in 1..6) {
            repoA.recordAccesoriosVale(
                valeId = 600L + k,
                vale = "ACC-$k",
                empresa = "MINA NORTE",
                lugar = "NIVEL $k",
                isDevolucion = false,
                items = listOf(AccessoryItemRecord("XCOR-0$k", 25 * k, "2026-11-22 08:0$k:00"))
            )
        }

        val pkgV3 = repoA.buildSyncExportPackage(
            scope = SyncExportScope.PENDING_ONLY,
            densityMode = QrDensityMode.HIGH_CAPACITY
        )
        val totalCombined = pkgV3.eventUuids.size + pkgV3.codeEventUuids.size
        assertThat(totalCombined).isEqualTo(27)

        // Compuerta de capacidad: los 27 eventos combinados caben en 1 SOLO QR V3 (< 1,400 chars)
        assertThat(pkgV3.qrPackage.protocolVersion).isEqualTo("V3")
        assertThat(pkgV3.qrPackage.chunks).hasSize(1)
        val singleQrText = pkgV3.qrPackage.chunks.first().rawText
        assertThat(singleQrText).startsWith("TTEX3:")
        assertThat(Base45Codec.isQrAlphanumeric(singleQrText)).isTrue()
        assertThat(pkgV3.qrPackage.compressedEncodedChars).isLessThan(1400)

        // Ahorro fuerte frente a V2 JSON + Base64
        val legacyV2 = QrSyncCodec.encode(pkgV3.jsonCompact, maxChunkChars = 450)
        assertThat(legacyV2.chunks.size).isGreaterThan(1)
        assertThat(pkgV3.qrPackage.compressedEncodedChars).isLessThan(legacyV2.compressedEncodedChars)

        // Matriz QR ISO con corrección L se genera correctamente
        val matrix = QrMatrixGenerator.encode(singleQrText, ErrorCorrectionLevel.L)
        assertThat(matrix.size).isGreaterThan(25)

        // Decodificación V3 en Escáner B en 1 solo disparo + fusión sin pérdida
        val reassembler = QrSyncReassembler()
        val ingest = reassembler.acceptScan(singleQrText)
        assertThat(ingest).isInstanceOf(QrChunkIngestResult.Complete::class.java)
        val complete = ingest as QrChunkIngestResult.Complete
        assertThat(complete.protocolVersion).isEqualTo("V3")

        val reportB = repoB.importSyncJson(complete.json, SyncSessionDirection.IMPORT_QR).getOrThrow()
        assertThat(reportB.eventsMerged).isEqualTo(7)
        assertThat(reportB.codeEventsMerged).isEqualTo(20)
        assertThat(repoB.getCurrentStockForProduct("315")).isWithin(1e-4).of(500.0)
        assertThat(repoB.getProductCodes("315")).hasSize(20)

        // Retrocompatibilidad: el mismo receptor sigue decodificando paquetes V2 (TTEXSYNC|V2|...)
        val v2Reassembler = QrSyncReassembler()
        var v2Json = ""
        for (chunk in legacyV2.chunks.reversed()) {
            val res = v2Reassembler.acceptScan(chunk.rawText)
            if (res is QrChunkIngestResult.Complete) {
                assertThat(res.protocolVersion).isEqualTo("V2")
                v2Json = res.json
            }
        }
        val dupReport = repoB.importSyncJson(v2Json, SyncSessionDirection.IMPORT_QR).getOrThrow()
        assertThat(dupReport.eventsMerged).isEqualTo(0)
        assertThat(dupReport.codeEventsMerged).isEqualTo(0)
        assertThat(dupReport.eventsSkippedDuplicate).isEqualTo(7)
        assertThat(dupReport.codeEventsSkippedDuplicate).isEqualTo(20)
    }
}
