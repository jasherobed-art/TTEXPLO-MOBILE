package com.ttexplo.mobile.domain

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.data.repository.CatalogRepository
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.ScanValidator
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.domain.scanner.correctBarcodeDate
import com.ttexplo.mobile.ui.common.DummyBarcodeGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class ScanValidatorTest {

    private lateinit var fakeCatalogRepository: CatalogRepository
    private lateinit var scanValidator: ScanValidator

    @Before
    fun setUp() {
        fakeCatalogRepository = object : CatalogRepository {
            override suspend fun importCsv(csvContent: String): Int = 0

            override suspend fun lookupBarcode(barcode: String): ProductInfo {
                return when {
                    barcode.startsWith("X") -> ProductInfo("ACC", "Accesorio", "PZA", "", "PZA", 0.0, ProductType.ACCESORIO, "")
                    barcode.startsWith("298") -> ProductInfo("298", "AUSTINITE 15 CA-1", "SACO", "100091", "KG", 25.0, ProductType.AGENTE, "0035")
                    barcode.startsWith("272") -> ProductInfo("272", "MEXAMON", "SACO 25 KG", "MEX25KG001", "KG", 25.0, ProductType.AGENTE, "0119")
                    barcode.startsWith("337") -> ProductInfo("337", "EMULEX C", "1\" X 39.3\"", "111923", "KG", 25.0, ProductType.ALTO_EXPLOSIVO, "0035")
                    barcode.startsWith("056") -> ProductInfo("056", "EMULEX 1", "1 1/4\" X 16\"", "111920", "KG", 25.0, ProductType.ALTO_EXPLOSIVO, "0035")
                    barcode.startsWith("315") -> ProductInfo("315", "EMULEX 1", "1 1/4\" X 16\"", "111920", "KG", 25.0, ProductType.ALTO_EXPLOSIVO, "0035")
                    barcode.startsWith("207") -> ProductInfo("207", "EMULEX 1", "1 1/4\" X 16\"", "EMX32X400MX", "KG", 25.0, ProductType.ALTO_EXPLOSIVO, "0035")
                    barcode.startsWith("319") -> ProductInfo("319", "EMULEX 1", "1\" X 8\"", "EMX25X200MX", "KG", 25.0, ProductType.ALTO_EXPLOSIVO, "0119")
                    barcode.startsWith("001") -> ProductInfo("001", "MAGNAFRAC", "1\" X 5\"", "EMFN25X125BKGLA", "KG", 25.0, ProductType.ALTO_EXPLOSIVO, "0119")
                    else -> ProductInfo("???", "Material no catalogado", "", "", "KG", 0.0, ProductType.DESCONOCIDO, "")
                }
            }

            override suspend fun countProducts(): Int = 8
            override suspend fun getAllProducts(): List<ProductInfo> = emptyList()
        }

        scanValidator = ScanValidator(fakeCatalogRepository)
    }

    @Test
    fun testProcessScan_valid30DigitCaja() = runBlocking {
        // User sample: 315150826026293026360005323457 (30 digits)
        val code = "315150826026293026360005323457"
        assertThat(code.length).isEqualTo(30)

        val result = scanValidator.processScan(code, emptySet())

        assertThat(result.isValid).isTrue()
        assertThat(result.empaque).isEqualTo(TipoEmpaque.CAJA)
        assertThat(result.productInfo.name).isEqualTo("EMULEX 1")
        assertThat(result.productInfo.type).isEqualTo(ProductType.ALTO_EXPLOSIVO)
        assertThat(result.isDuplicate).isFalse()
    }

    @Test
    fun testProcessScan_valid18DigitSaco() = runBlocking {
        // User sample: 298020926004801474 (18 digits)
        val code = "298020926004801474"
        assertThat(code.length).isEqualTo(18)

        val result = scanValidator.processScan(code, emptySet())

        assertThat(result.isValid).isTrue()
        assertThat(result.empaque).isEqualTo(TipoEmpaque.SACO)
        assertThat(result.productInfo.name).isEqualTo("AUSTINITE 15 CA-1")
        assertThat(result.productInfo.type).isEqualTo(ProductType.AGENTE)
    }

    @Test
    fun testProcessScan_valid16DigitCartucho() = runBlocking {
        // User sample: 0562101024112934 (16 digits)
        val code = "0562101024112934"
        assertThat(code.length).isEqualTo(16)

        val result = scanValidator.processScan(code, emptySet())

        assertThat(result.isValid).isTrue()
        assertThat(result.empaque).isEqualTo(TipoEmpaque.PIEZA)
        assertThat(result.productInfo.name).isEqualTo("EMULEX 1")
    }

    @Test
    fun testProcessScan_valid17DigitCartucho() = runBlocking {
        val code = "05611110240605501" // 17 digits
        assertThat(code.length).isEqualTo(17)

        val result = scanValidator.processScan(code, emptySet())

        assertThat(result.isValid).isTrue()
        assertThat(result.empaque).isEqualTo(TipoEmpaque.PIEZA)
    }

    @Test
    fun testProcessScan_rejectsInvalidLength() = runBlocking {
        // 14 digits (invalid)
        val shortCode = "29802092600480"
        val result = scanValidator.processScan(shortCode, emptySet())

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("Solo se permiten Cajas (30 dígitos), Sacos (18 dígitos) o Cartuchos (16 dígitos)")
    }

    @Test
    fun testProcessScan_rejectsUnknownPrefixAndExposesUnknownId() = runBlocking {
        // 18 digits but prefix 999 does not exist in catalog
        val code = "999020926004801474"
        val result = scanValidator.processScan(code, emptySet())

        assertThat(result.isValid).isFalse()
        assertThat(result.unknownId).isEqualTo("999")
        assertThat(result.message).contains("ID '999' no existe en el catálogo")
    }

    @Test
    fun testProcessScan_rejectsAccessoryWhenNotAllowed() = runBlocking {
        val code = "X123456789"
        val result = scanValidator.processScan(code, emptySet(), allowAccessories = false)

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("módulo de Salida de Accesorios")
    }

    @Test
    fun testProcessScan_acceptsAccessoryWhenAllowed() = runBlocking {
        val code = "X123456789"
        val result = scanValidator.processScan(code, emptySet(), allowAccessories = true)

        assertThat(result.isValid).isTrue()
        assertThat(result.empaque).isEqualTo(TipoEmpaque.ACCESORIO)
    }

    @Test
    fun testProcessScan_scannedTwice_markedAsDuplicate() = runBlocking {
        val code = "298020926004801474"
        val existingSession = setOf(code)

        val result = scanValidator.processScan(code, existingSession)

        assertThat(result.isDuplicate).isTrue()
        assertThat(result.isValid).isTrue()
        assertThat(result.message).contains("REPETIDO")
    }

    @Test
    fun testProcessScan_rejectsAltoExplosivoWith18DigitsSaco() = runBlocking {
        // User sample: 310210102411293122 (18 digits for EMULEX 1 which is Alto Explosivo)
        val code = "310210102411293122"
        assertThat(code.length).isEqualTo(18)

        // Mock returns 315 as Alto Explosivo in fake repo, let's use 315 with 18 digits
        val alto18Code = "315020926004801474"
        val result = scanValidator.processScan(alto18Code, emptySet())

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("Alto Explosivo y solo se presenta en Cajas (30 dígitos) o Cartuchos (16 dígitos), nunca en Sacos")
    }

    @Test
    fun testProcessScan_rejectsAgenteWith16DigitsCartucho() = runBlocking {
        // 298 is AUSTINITE (Agente de Voladura)
        val agente16Code = "2982101024112934" // 16 digits
        val result = scanValidator.processScan(agente16Code, emptySet())

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("Agente de Voladura y solo se presenta en Sacos (18 dígitos) o Cajas (30 dígitos), no en Cartuchos")
    }

    @Test
    fun testProcessScan_rejectsCajaWhenFinalLessThanInitial() = runBlocking {
        // 315 is EMULEX 1. Initial 016189, Final 016143 (inverted!)
        val invertedCode = "315101225016189016143005013023"
        val result = scanValidator.processScan(invertedCode, emptySet())

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("código inicial")
    }

    @Test
    fun testProcessScan_rejectsInvalidDateInBarcode() = runBlocking {
        // Date 999999 is invalid
        val badDateCode = "315999999016143016189005013023"
        val result = scanValidator.processScan(badDateCode, emptySet())

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("Fecha de fabricación inválida")
    }

    @Test
    fun testProcessScan_acceptsExactUserSampleCaja() = runBlocking {
        // User structure: 315 (ID), 101225 (Date 10/12/25), 016143 (Init), 016189 (Fin), 005013023 (Serie)
        val sampleCaja = "315101225016143016189005013023"
        val result = scanValidator.processScan(sampleCaja, emptySet())

        assertThat(result.isValid).isTrue()
        assertThat(result.empaque).isEqualTo(TipoEmpaque.CAJA)
    }

    @Test
    fun testDummyGenerator_allValidTypesPassScanValidatorAndAreUnique() = runBlocking {
        val seen = mutableSetOf<String>()

        repeat(5) {
            val caja = DummyBarcodeGenerator.nextCaja()
            assertThat(caja.length).isEqualTo(30)
            val res = scanValidator.processScan(caja, seen, allowAccessories = false)
            assertThat(res.isValid).isTrue()
            assertThat(res.isDuplicate).isFalse()
            assertThat(res.empaque).isEqualTo(TipoEmpaque.CAJA)
            seen.add(caja)
        }

        repeat(4) {
            val saco = DummyBarcodeGenerator.nextSaco()
            assertThat(saco.length).isEqualTo(18)
            val res = scanValidator.processScan(saco, seen, allowAccessories = false)
            assertThat(res.isValid).isTrue()
            assertThat(res.isDuplicate).isFalse()
            assertThat(res.empaque).isEqualTo(TipoEmpaque.SACO)
            seen.add(saco)
        }

        repeat(4) {
            val cartucho = DummyBarcodeGenerator.nextCartucho()
            assertThat(cartucho.length).isEqualTo(16)
            val res = scanValidator.processScan(cartucho, seen, allowAccessories = false)
            assertThat(res.isValid).isTrue()
            assertThat(res.isDuplicate).isFalse()
            assertThat(res.empaque).isEqualTo(TipoEmpaque.PIEZA)
            seen.add(cartucho)
        }

        repeat(3) {
            val acc = DummyBarcodeGenerator.nextAccesorioUnique()
            val res = scanValidator.processScan(acc, seen, allowAccessories = true)
            assertThat(res.isValid).isTrue()
            assertThat(res.isDuplicate).isFalse()
            assertThat(res.empaque).isEqualTo(TipoEmpaque.ACCESORIO)
            seen.add(acc)
        }
    }

    @Test
    fun testDummyGenerator_batchAndNegativeCasesWorkAsExpected() = runBlocking {
        val session = mutableSetOf<String>()
        val batch = DummyBarcodeGenerator.sampleBatchForExplosives(includeAccessory = true)
        assertThat(batch).hasSize(6)

        batch.forEach { code ->
            val res = scanValidator.processScan(code, session, allowAccessories = true)
            assertThat(res.isValid).isTrue()
            assertThat(res.isDuplicate).isFalse()
            session.add(code)
        }

        val coherenceErr = scanValidator.processScan(DummyBarcodeGenerator.sampleInvalidCoherence(), emptySet())
        assertThat(coherenceErr.isValid).isFalse()
        assertThat(coherenceErr.message).contains("nunca en Sacos")

        val unknownErr = scanValidator.processScan(DummyBarcodeGenerator.sampleUnknownId(), emptySet())
        assertThat(unknownErr.isValid).isFalse()
        assertThat(unknownErr.unknownId).isEqualTo("999")

        val forbiddenAcc = scanValidator.processScan(DummyBarcodeGenerator.sampleForbiddenAccessory(), emptySet(), allowAccessories = false)
        assertThat(forbiddenAcc.isValid).isFalse()
        assertThat(forbiddenAcc.message).contains("Salida de Accesorios")
    }

    @Test
    fun reportedCodeWithYear96IsRejectedAndExposesDetectedDate() = runBlocking {
        val result = scanValidator.processScan("298190896004876376", emptySet())
        assertThat(result.isValid).isFalse()
        assertThat(result.invalidDate).isEqualTo("190896")
        assertThat(result.message).contains("19/08/96")
    }

    @Test
    fun impossibleDatesAreRejectedAndLeapDayIsReal() = runBlocking {
        assertThat(scanValidator.processScan("298310226004876376", emptySet()).isValid).isFalse()
        assertThat(scanValidator.processScan("298290224004876376", emptySet()).isValid).isTrue()
        assertThat(scanValidator.processScan("298290225004876376", emptySet()).isValid).isFalse()
    }

    @Test
    fun correctBarcodeDateReplacesOnlyDateDigits() {
        assertThat(correctBarcodeDate("298190896004876376", "190826"))
            .isEqualTo("298190826004876376")
        assertThat(correctBarcodeDate("315150826026293026360005323457", "270826"))
            .isEqualTo("315270826026293026360005323457")
        assertThat(correctBarcodeDate("0562101024112934", "220826"))
            .isEqualTo("0562220826112934")
        assertThat(correctBarcodeDate("298190896004876376", "190996")).isNull()
        assertThat(correctBarcodeDate("05611110240605501", "220826")).isNull()
    }

    @Test
    fun correctedCodeIsAccepted() = runBlocking {
        val corrected = correctBarcodeDate("298190896004876376", "190826")!!
        val result = scanValidator.processScan(corrected, emptySet())
        assertThat(result.isValid).isTrue()
        assertThat(result.invalidDate).isNull()
    }

    @Test
    fun seventeenDigitCartridgeKeepsSkippingDateValidation() = runBlocking {
        assertThat(scanValidator.processScan("05611110240605501", emptySet()).isValid).isTrue()
    }
}
