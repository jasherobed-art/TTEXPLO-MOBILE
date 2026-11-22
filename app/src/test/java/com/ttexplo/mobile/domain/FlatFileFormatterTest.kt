package com.ttexplo.mobile.domain

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.domain.export.FlatFileFormatter
import com.ttexplo.mobile.domain.model.*
import org.junit.Test

class FlatFileFormatterTest {

    @Test
    fun testFormatEntrada_matchesUserSpecification() {
        val entrada = EntradaRecord(
            proveedor = "1",
            polvorin = "3",
            factura = "FORMATONUEVO",
            items = listOf(
                ScannedCodeRecord("298020926004801028", "2026-10-06 12:00:00"),
                ScannedCodeRecord("337150826014346014401005323061", "2026-10-06 12:00:00")
            )
        )

        val output = FlatFileFormatter.formatEntrada(entrada)
        val lines = output.split("\r\n").filter { it.isNotEmpty() }

        assertThat(lines[0]).isEqualTo("PRV1,3,FORMATONUEVO")
        assertThat(lines[1]).isEqualTo("298020926004801028,2026-10-06 12:00:00")
        assertThat(lines[2]).isEqualTo("337150826014346014401005323061,2026-10-06 12:00:00")
    }

    @Test
    fun testFormatDespacho_matchesUserSpecification() {
        val despacho = DespachoRecord(
            vale = "7712",
            empresa = "MPRG",
            lugar = "576-S/RMP/576/PIBOTEO",
            transportista = "GILBERTO MENDOZA AGUIRRE",
            turno = "3",
            voladura = "1",
            items = listOf(
                ScannedCodeRecord("272120826024843282", "2026-10-07 04:41:01")
            )
        )

        val output = FlatFileFormatter.formatDespacho(despacho)
        val lines = output.split("\r\n").filter { it.isNotEmpty() }

        assertThat(lines[0]).isEqualTo("REF7712,MPRG,576-S/RMP/576/PIBOTEO,GILBERTO MENDOZA AGUIRRE,3,1")
        assertThat(lines[1]).isEqualTo("272120826024843282,2026-10-07 04:41:01")
    }

    @Test
    fun testFormatDevolucion_matchesUserSpecification() {
        val devolucion = DevolucionRecord(
            empleadoEntrega = "1980",
            empleadoRecibe = "2527",
            items = listOf(
                ScannedCodeRecord("207120814045474045686000762563", "2026-10-06 17:24:52")
            )
        )

        val output = FlatFileFormatter.formatDevolucion(devolucion)
        val lines = output.split("\r\n").filter { it.isNotEmpty() }

        assertThat(lines[0]).isEqualTo("DEV1980,2527")
        assertThat(lines[1]).isEqualTo("207120814045474045686000762563,2026-10-06 17:24:52")
    }

    @Test
    fun testFormatAccesorios_matchesUserSpecification() {
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

        val output = FlatFileFormatter.formatAccesorios(accesorios)
        val lines = output.split("\r\n").filter { it.isNotEmpty() }

        assertThat(lines[0]).isEqualTo("ACC7712,MPRG,576-S/RMP/576/PIBOTEO,GILBERTO MENDOZA AGUIRRE,3,MPRG,GILBERTO MENDOZA AGUIRRE,1")
        assertThat(lines[1]).isEqualTo("X100182000000009,2,2026-10-07 06:21:57")
    }
}
