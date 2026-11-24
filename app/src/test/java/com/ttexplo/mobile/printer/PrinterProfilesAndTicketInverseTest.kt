package com.ttexplo.mobile.printer

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.model.AccesoriosRecord
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.model.DespachoRecord
import com.ttexplo.mobile.domain.model.DevolucionRecord
import com.ttexplo.mobile.domain.model.EntradaRecord
import com.ttexplo.mobile.domain.model.ScannedCodeRecord
import org.junit.Before
import org.junit.Test

class PrinterProfilesAndTicketInverseTest {

    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var settingsRepo: SettingsRepository

    private val sampleDespacho = DespachoRecord(
        vale = "8841",
        empresa = "MINERA T&T",
        lugar = "NIVEL 450",
        transportista = "CARLOS PEREZ",
        turno = "2",
        voladura = "4",
        items = listOf(
            ScannedCodeRecord("337130626009586009641005228514", "2026-11-22 10:00:00", "EMULEX 1", "1\" X 8\""),
            ScannedCodeRecord("272120826024843282", "2026-11-22 10:01:00", "AUSTINITE 15", "SACO"),
            ScannedCodeRecord("3371306260095861", "2026-11-22 10:02:00", "EMULEX 1", "CARTUCHO")
        )
    )

    private val sampleEntrada = EntradaRecord(
        proveedor = "AUSTIN POWDER MEXICO",
        polvorin = "POLVORIN PRINCIPAL #1",
        factura = "FAC-2026-0091",
        items = listOf(
            ScannedCodeRecord("337130626009586009641005228514", "2026-11-22 08:15:00", "EMULEX 1", "CAJA"),
            ScannedCodeRecord("272120826024843282", "2026-11-22 08:16:00", "AUSTINITE 15", "SACO")
        )
    )

    private val sampleDevolucion = DevolucionRecord(
        empleadoEntrega = "JUAN OPERADOR",
        empleadoRecibe = "PEDRO POLVORINERO",
        items = listOf(
            ScannedCodeRecord("337130626009586009641005228514", "2026-11-22 14:20:00", "EMULEX 1", "CAJA")
        )
    )

    private val sampleAccesorios = AccesoriosRecord(
        vale = "9012",
        empresa = "MINERA T&T",
        lugar = "RAMPA SUR",
        transportista = "CARLOS PEREZ",
        turno = "1",
        recibio = "JUAN",
        entrego = "PEDRO",
        voladura = "2",
        items = listOf(
            AccessoryItemRecord("XDET-001", 10, "2026-11-22 09:00:00"),
            AccessoryItemRecord("XCOR-005", 25, "2026-11-22 09:01:00")
        )
    )

    @Before
    fun setup() {
        prefs = InMemorySharedPreferences()
        settingsRepo = SettingsRepository(prefs)
    }

    private fun containsSequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun extractEscPosInvertedPayload(bytes: ByteArray): String {
        val on = TicketGenerator.ESC_INVERSE_ON
        val off = TicketGenerator.ESC_INVERSE_OFF
        val start = indexOfSequence(bytes, on)
        assertThat(start).isAtLeast(0)
        val end = indexOfSequence(bytes, off, start + on.size)
        assertThat(end).isGreaterThan(start)
        return String(bytes.copyOfRange(start + on.size, end), Charsets.US_ASCII)
    }

    private fun indexOfSequence(haystack: ByteArray, needle: ByteArray, fromIndex: Int = 0): Int {
        outer@ for (i in fromIndex..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    @Test
    fun escPos_allTicketsEmitInverseOnOffAndFullWidthTotalDeMaterialesAcrossPaperWidths() {
        for (width in PaperWidth.entries) {
            val tickets = listOf(
                TicketGenerator.generateDespachoEscPos(sampleDespacho, paperWidth = width),
                TicketGenerator.generateEntradaEscPos(sampleEntrada, paperWidth = width),
                TicketGenerator.generateDevolucionEscPos(sampleDevolucion, paperWidth = width),
                TicketGenerator.generateAccesoriosEscPos(sampleAccesorios, paperWidth = width),
                TicketGenerator.generateTestTicket(paperWidth = width)
            )

            for (bytes in tickets) {
                assertThat(containsSequence(bytes, TicketGenerator.ESC_INVERSE_ON)).isTrue()
                assertThat(containsSequence(bytes, TicketGenerator.ESC_INVERSE_OFF)).isTrue()

                val invertedText = extractEscPosInvertedPayload(bytes)
                assertThat(invertedText).contains("TOTAL DE MATERIALES")

                // Every printed text line inside the inverted block must be padded to exact paper column width
                val contentLines = invertedText
                    .replace("\u001BE\u0001", "")
                    .replace("\u001BE\u0000", "")
                    .split("\r\n")
                    .filter { it.isNotEmpty() }
                assertThat(contentLines).isNotEmpty()
                contentLines.forEach { line ->
                    assertThat(line.length).isEqualTo(width.lineLength)
                }
            }
        }
    }

    @Test
    fun cpcl_allTicketsEmitInverseLineAndCorrectPageWidthAcross58_80_104mm() {
        for (width in PaperWidth.entries) {
            val tickets = listOf(
                TicketGenerator.generateDespachoCpcl(sampleDespacho, paperWidth = width),
                TicketGenerator.generateEntradaCpcl(sampleEntrada, paperWidth = width),
                TicketGenerator.generateDevolucionCpcl(sampleDevolucion, paperWidth = width),
                TicketGenerator.generateAccesoriosCpcl(sampleAccesorios, paperWidth = width),
                TicketGenerator.generateCpclTestTicket(paperWidth = width)
            )

            for (bytes in tickets) {
                val cpcl = String(bytes, Charsets.US_ASCII)
                assertThat(cpcl).startsWith("! 0 200 200 ")
                assertThat(cpcl).contains("PAGE-WIDTH ${width.cpclDots}")
                assertThat(cpcl).contains("TOTAL DE MATERIALES")
                assertThat(cpcl).contains("INVERSE-LINE 10 ")
                assertThat(cpcl).contains("PRINT")

                val inverseLines = cpcl.lines().filter { it.startsWith("INVERSE-LINE ") }
                assertThat(inverseLines).isNotEmpty()
                val parts = inverseLines.first().split(" ")
                assertThat(parts.size).isEqualTo(6)
                val x1 = parts[1].toInt()
                val y1 = parts[2].toInt()
                val x2 = parts[3].toInt()
                val y2 = parts[4].toInt()
                val height = parts[5].toInt()
                assertThat(x1).isEqualTo(10)
                assertThat(x2).isEqualTo(width.cpclDots - 20)
                assertThat(y2).isEqualTo(y1)
                assertThat(height).isAtLeast(36)

                val textY = cpcl.lines()
                    .filter { it.startsWith("TEXT ") }
                    .map { it.split(" ")[4].toInt() }
                for (i in 0 until textY.size - 1) {
                    assertThat(textY[i + 1]).isAtLeast(textY[i])
                }
            }
        }
    }

    @Test
    fun allTickets_includeFullScannedCodeDetailsLikeDespachoAcrossProtocolsAndPaperWidths() {
        for (width in PaperWidth.entries) {
            // Despacho (salidas de alto explosivo y agente)
            val despachoOutputs = listOf(
                String(TicketGenerator.generateDespachoEscPos(sampleDespacho, paperWidth = width), Charsets.US_ASCII),
                String(TicketGenerator.generateDespachoPlain(sampleDespacho, paperWidth = width), Charsets.US_ASCII),
                String(TicketGenerator.generateDespachoCpcl(sampleDespacho, paperWidth = width), Charsets.US_ASCII)
            )
            for (text in despachoOutputs) {
                assertThat(text).contains("LISTADO DE CODIGOS (3):")
                assertThat(text).contains("[01] 337130626009586009641005228514")
                assertThat(text).contains("2026-11-22 10:00:00")
                assertThat(text).contains("EMULEX 1 (1\" X 8\")")
                assertThat(text).contains("[02] 272120826024843282")
                assertThat(text).contains("2026-11-22 10:01:00")
                assertThat(text).contains("AUSTINITE 15 (SACO)")
            }

            // Entradas a polvorín — mismo nivel de detalle de códigos que Despacho
            val entradaOutputs = listOf(
                String(TicketGenerator.generateEntradaEscPos(sampleEntrada, paperWidth = width), Charsets.US_ASCII),
                String(TicketGenerator.generateEntradaPlain(sampleEntrada, paperWidth = width), Charsets.US_ASCII),
                String(TicketGenerator.generateEntradaCpcl(sampleEntrada, paperWidth = width), Charsets.US_ASCII)
            )
            for (text in entradaOutputs) {
                assertThat(text).contains("LISTADO DE CODIGOS (2):")
                assertThat(text).contains("[01] 337130626009586009641005228514")
                assertThat(text).contains("2026-11-22 08:15:00")
                assertThat(text).contains("EMULEX 1 (CAJA)")
                assertThat(text).contains("[02] 272120826024843282")
                assertThat(text).contains("2026-11-22 08:16:00")
                assertThat(text).contains("AUSTINITE 15 (SACO)")
            }

            // Devoluciones a polvorín — mismo nivel de detalle de códigos que Despacho
            val devolucionOutputs = listOf(
                String(TicketGenerator.generateDevolucionEscPos(sampleDevolucion, paperWidth = width), Charsets.US_ASCII),
                String(TicketGenerator.generateDevolucionPlain(sampleDevolucion, paperWidth = width), Charsets.US_ASCII),
                String(TicketGenerator.generateDevolucionCpcl(sampleDevolucion, paperWidth = width), Charsets.US_ASCII)
            )
            for (text in devolucionOutputs) {
                assertThat(text).contains("LISTADO DE CODIGOS (1):")
                assertThat(text).contains("[01] 337130626009586009641005228514")
                assertThat(text).contains("2026-11-22 14:20:00")
                assertThat(text).contains("EMULEX 1 (CAJA)")
            }

            // Accesorios — índice, código, cantidad y hora en todos los anchos y protocolos
            val accOutputs = listOf(
                String(TicketGenerator.generateAccesoriosEscPos(sampleAccesorios, paperWidth = width), Charsets.US_ASCII),
                String(TicketGenerator.generateAccesoriosPlain(sampleAccesorios, paperWidth = width), Charsets.US_ASCII),
                String(TicketGenerator.generateAccesoriosCpcl(sampleAccesorios, paperWidth = width), Charsets.US_ASCII)
            )
            for (text in accOutputs) {
                assertThat(text).contains("[01] XDET-001   CANT: 10")
                assertThat(text).contains("2026-11-22 09:00:00")
                assertThat(text).contains("[02] XCOR-005   CANT: 25")
                assertThat(text).contains("2026-11-22 09:01:00")
            }
        }
    }

    @Test
    fun printerProfile_jsonRoundTripPreservesAllFields() {
        val list = listOf(
            PrinterProfile(
                id = "p1",
                alias = "Zebra Polvorin 1",
                macAddress = "AA:BB:CC:DD:EE:01",
                deviceName = "ZQ521",
                protocol = "CPCL",
                paperWidth = PaperWidth.WIDTH_4_INCH,
                isDefault = true
            ),
            PrinterProfile(
                id = "p2",
                alias = "Munbyn Bolsillo",
                macAddress = "AA:BB:CC:DD:EE:02",
                deviceName = "IMP001",
                protocol = "ESCPOS",
                paperWidth = PaperWidth.WIDTH_2_INCH,
                isDefault = false
            )
        )

        val serialized = PrinterProfile.listToJsonString(list)
        val restored = PrinterProfile.listFromJsonString(serialized)

        assertThat(restored).hasSize(2)
        assertThat(restored[0]).isEqualTo(list[0])
        assertThat(restored[1]).isEqualTo(list[1])
    }

    @Test
    fun settingsRepository_migratesLegacySinglePrinterAndSupportsMultiPrinterCrud() {
        prefs.edit()
            .putString("preferred_printer_mac", "11:22:33:44:55:66")
            .putString("preferred_printer_name", "ZQ520-LEGACY")
            .putString("printer_protocol", "CPCL")
            .putString("paper_width", PaperWidth.WIDTH_4_INCH.name)
            .commit()

        val migrated = settingsRepo.getPrinterProfiles()
        assertThat(migrated).hasSize(1)
        assertThat(migrated[0].macAddress).isEqualTo("11:22:33:44:55:66")
        assertThat(migrated[0].alias).isEqualTo("ZQ520-LEGACY")
        assertThat(migrated[0].isDefault).isTrue()
        assertThat(migrated[0].paperWidth).isEqualTo(PaperWidth.WIDTH_4_INCH)

        val second = PrinterProfile(
            id = "munbyn-58",
            alias = "Munbyn 58mm",
            macAddress = "AA:11:22:33:44:55",
            deviceName = "MTP-2",
            protocol = "ESCPOS",
            paperWidth = PaperWidth.WIDTH_2_INCH,
            isDefault = true
        )
        val afterAdd = settingsRepo.upsertPrinterProfile(second)
        assertThat(afterAdd).hasSize(2)

        val active = settingsRepo.getActivePrinterProfile()
        assertThat(active?.id).isEqualTo("munbyn-58")
        assertThat(settingsRepo.getPreferredPrinterMac()).isEqualTo("AA:11:22:33:44:55")
        assertThat(settingsRepo.getPrinterProtocol()).isEqualTo("ESCPOS")
        assertThat(settingsRepo.getPaperWidth()).isEqualTo(PaperWidth.WIDTH_2_INCH)

        val firstId = afterAdd.first { it.macAddress == "11:22:33:44:55:66" }.id
        settingsRepo.setActivePrinterProfile(firstId)
        assertThat(settingsRepo.getActivePrinterProfile()?.id).isEqualTo(firstId)
        assertThat(settingsRepo.getPrinterProtocol()).isEqualTo("CPCL")
        assertThat(settingsRepo.getPaperWidth()).isEqualTo(PaperWidth.WIDTH_4_INCH)

        val afterDelete = settingsRepo.deletePrinterProfile(firstId)
        assertThat(afterDelete).hasSize(1)
        assertThat(afterDelete[0].id).isEqualTo("munbyn-58")
        assertThat(afterDelete[0].isDefault).isTrue()
        assertThat(settingsRepo.getActivePrinterProfile()?.id).isEqualTo("munbyn-58")
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            }
        }
    }
}
