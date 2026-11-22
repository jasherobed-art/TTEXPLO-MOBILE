package com.ttexplo.mobile.domain.export

import com.ttexplo.mobile.domain.model.*

object FlatFileFormatter {

    private const val CRLF = "\r\n"

    fun formatEntrada(entrada: EntradaRecord): String {
        val sb = StringBuilder()
        val provPrefix = if (entrada.proveedor.startsWith("PRV", ignoreCase = true)) {
            entrada.proveedor
        } else {
            "PRV${entrada.proveedor}"
        }
        sb.append("$provPrefix,${entrada.polvorin},${entrada.factura}").append(CRLF)
        for (item in entrada.items) {
            sb.append("${item.code},${item.timestamp}").append(CRLF)
        }
        return sb.toString()
    }

    fun formatDespacho(despacho: DespachoRecord): String {
        val sb = StringBuilder()
        val refPrefix = if (despacho.vale.startsWith("REF", ignoreCase = true)) {
            despacho.vale
        } else {
            "REF${despacho.vale}"
        }
        sb.append("$refPrefix,${despacho.empresa},${despacho.lugar},${despacho.transportista},${despacho.turno},${despacho.voladura}").append(CRLF)
        for (item in despacho.items) {
            sb.append("${item.code},${item.timestamp}").append(CRLF)
        }
        return sb.toString()
    }

    fun formatDevolucion(devolucion: DevolucionRecord): String {
        val sb = StringBuilder()
        val devPrefix = if (devolucion.empleadoEntrega.startsWith("DEV", ignoreCase = true)) {
            devolucion.empleadoEntrega
        } else {
            "DEV${devolucion.empleadoEntrega}"
        }
        sb.append("$devPrefix,${devolucion.empleadoRecibe}").append(CRLF)
        for (item in devolucion.items) {
            sb.append("${item.code},${item.timestamp}").append(CRLF)
        }
        return sb.toString()
    }

    fun formatAccesorios(accesorios: AccesoriosRecord): String {
        val sb = StringBuilder()
        val accPrefix = if (accesorios.vale.startsWith("ACC", ignoreCase = true)) {
            accesorios.vale
        } else {
            "ACC${accesorios.vale}"
        }
        sb.append("$accPrefix,${accesorios.empresa},${accesorios.lugar},${accesorios.transportista},${accesorios.turno},${accesorios.recibio},${accesorios.entrego},${accesorios.voladura}").append(CRLF)
        for (item in accesorios.items) {
            sb.append("${item.code},${item.quantity},${item.timestamp}").append(CRLF)
        }
        return sb.toString()
    }

    fun generateConsolidatedFile(
        entradas: List<EntradaRecord>,
        despachos: List<DespachoRecord>,
        devoluciones: List<DevolucionRecord>,
        accesorios: List<AccesoriosRecord>
    ): String {
        val sb = StringBuilder()
        for (entrada in entradas) {
            sb.append(formatEntrada(entrada))
        }
        for (despacho in despachos) {
            sb.append(formatDespacho(despacho))
        }
        for (devolucion in devoluciones) {
            sb.append(formatDevolucion(devolucion))
        }
        for (accesorio in accesorios) {
            sb.append(formatAccesorios(accesorio))
        }
        return sb.toString()
    }
}
