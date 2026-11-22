package com.ttexplo.mobile

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.domain.export.FlatFileFormatter
import com.ttexplo.mobile.domain.model.*
import com.ttexplo.mobile.domain.scanner.ScanValidator
import com.ttexplo.mobile.printer.PaperWidth
import com.ttexplo.mobile.printer.TicketGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.charset.Charset

class EndToEndFlowTest {

    @Test
    fun testCompleteOperationalFlow_fromCatalogToExportAndPrinter() = runBlocking {
        // 1. Simulate Catalog parsing
        val catalogCsv = """
            "IdProducto","Producto","Presentacion","noSAP","Unidad","Cantidad","CodBarras","peso","PG","PU"
            "298","AUSTINITE 15 CA-1","SACO","100091JAL","KG",0,"298",25,"0035",0
            "337","EMULEX C","1\" X 39.3\"","111923","KG",25,"337",25,"0035",0
            "272","AUSTINITE 15 CA-1","SACO","100091","KG",0,"272",25,"0035",0
        """.trimIndent()

        val parsedEntities = mutableMapOf<String, ProductInfo>()
        val lines = catalogCsv.lines()
        for ((index, line) in lines.withIndex()) {
            if (index == 0) continue
            val tokens = CatalogRepositoryImpl.parseCsvLine(line)
            val id = tokens[0].trim()
            val name = tokens[1].trim()
            val pres = tokens[2].trim()
            val type = if (name.contains("AUSTINITE")) ProductType.AGENTE else ProductType.ALTO_EXPLOSIVO
            parsedEntities[id] = ProductInfo(id, name, pres, tokens[3], tokens[4], tokens[7].toDouble(), type, tokens[8])
        }

        assertThat(parsedEntities).hasSize(3)
        assertThat(parsedEntities["298"]?.type).isEqualTo(ProductType.AGENTE)
        assertThat(parsedEntities["337"]?.type).isEqualTo(ProductType.ALTO_EXPLOSIVO)

        // 2. Validate Scans and Duplicate Handling
        val fakeCatalogRepo = object : com.ttexplo.mobile.data.repository.CatalogRepository {
            override suspend fun importCsv(csvContent: String): Int = parsedEntities.size
            override suspend fun lookupBarcode(barcode: String): ProductInfo {
                val prefix = barcode.take(3)
                return parsedEntities[prefix] ?: ProductInfo(prefix, "Material no catalogado", "", "", "KG", 0.0, ProductType.DESCONOCIDO, "")
            }
            override suspend fun countProducts(): Int = parsedEntities.size
            override suspend fun getAllProducts(): List<ProductInfo> = parsedEntities.values.toList()
        }

        val validator = ScanValidator(fakeCatalogRepo)
        val sessionCodes = mutableSetOf<String>()

        // Scan 1: Normal
        val scan1 = validator.processScan("298020926004801028", sessionCodes)
        assertThat(scan1.isDuplicate).isFalse()
        assertThat(scan1.productInfo.name).isEqualTo("AUSTINITE 15 CA-1")
        sessionCodes.add(scan1.rawBarcode)

        // Scan 2: Duplicate scan of same code
        val scan2 = validator.processScan("298020926004801028", sessionCodes)
        assertThat(scan2.isDuplicate).isTrue()
        assertThat(scan2.message).contains("REPETIDO")

        // Scan 3: Different code (Alto Explosivo)
        val scan3 = validator.processScan("337150826014346014401005323061", sessionCodes)
        assertThat(scan3.isDuplicate).isFalse()
        assertThat(scan3.productInfo.name).isEqualTo("EMULEX C")
        sessionCodes.add(scan3.rawBarcode)

        // 3. Flat File Export Formatting
        val entrada = EntradaRecord(
            proveedor = "1",
            polvorin = "3",
            factura = "FORMATONUEVO",
            items = listOf(
                ScannedCodeRecord("298020926004801028", "2026-10-06 12:00:00")
            )
        )

        val despacho = DespachoRecord(
            vale = "7712",
            empresa = "MPRG",
            lugar = "576-S/RMP/576/PIBOTEO",
            transportista = "GILBERTO MENDOZA AGUIRRE",
            turno = "3",
            voladura = "1",
            items = listOf(
                ScannedCodeRecord("272120826024843282", "2026-10-07 04:41:01", "AUSTINITE 15 CA-1", "SACO"),
                ScannedCodeRecord("337150826014346014401005323061", "2026-10-07 06:20:07", "EMULEX C", "1\" X 39.3\"")
            )
        )

        val devolucion = DevolucionRecord(
            empleadoEntrega = "1980",
            empleadoRecibe = "2527",
            items = listOf(
                ScannedCodeRecord("207120814045474045686000762563", "2026-10-06 17:24:52")
            )
        )

        val accesorios = AccesoriosRecord(
            vale = "7712",
            empresa = "MPRG",
            lugar = "576-S/RMP/576/PIBOTEO",
            transportista = "GILBERTO MENDOZA AGUIRRE",
            turno = "3",
            recibio = "MPRG",
            entrego = "GILBERTO MENDOZA AGUIRRE",
            voladura = "1",
            items = listOf(
                AccessoryItemRecord("X100182000000009", 2, "2026-10-07 06:21:57")
            )
        )

        val consolidatedText = FlatFileFormatter.generateConsolidatedFile(
            listOf(entrada), listOf(despacho), listOf(devolucion), listOf(accesorios)
        )

        assertThat(consolidatedText).contains("PRV1,3,FORMATONUEVO\r\n")
        assertThat(consolidatedText).contains("REF7712,MPRG,576-S/RMP/576/PIBOTEO,GILBERTO MENDOZA AGUIRRE,3,1\r\n")
        assertThat(consolidatedText).contains("DEV1980,2527\r\n")
        assertThat(consolidatedText).contains("ACC7712,MPRG,576-S/RMP/576/PIBOTEO,GILBERTO MENDOZA AGUIRRE,3,MPRG,GILBERTO MENDOZA AGUIRRE,1\r\n")
        assertThat(consolidatedText).contains("X100182000000009,2,2026-10-07 06:21:57\r\n")

        // 4. Zebra Thermal Ticket Printing
        val ticketBytes = TicketGenerator.generateDespachoTicket(despacho, PaperWidth.WIDTH_3_INCH)
        assertThat(ticketBytes.size).isGreaterThan(100)

        val ticketText = String(ticketBytes, Charset.forName("CP437"))
        assertThat(ticketText).contains("ARIA")
        assertThat(ticketText).contains("REF7712")
        assertThat(ticketText).contains("AUSTINITE 15 CA-1 (SACO)")
        assertThat(ticketText).contains("EMULEX C (1\" X 39.3\")")
        assertThat(ticketText).contains("TOTAL DE PIEZAS / BULTOS: 2")
        assertThat(ticketText).contains("ENTREGO:")
        assertThat(ticketText).contains("RECIBIO:")
    }
}
