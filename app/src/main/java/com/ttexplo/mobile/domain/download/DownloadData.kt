package com.ttexplo.mobile.domain.download

import android.content.Context
import com.ttexplo.mobile.data.local.AppDatabase
import com.ttexplo.mobile.domain.export.FlatFileFormatter
import com.ttexplo.mobile.domain.inventory.BoxCountVale
import com.ttexplo.mobile.domain.model.AccesoriosRecord
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.model.DespachoRecord
import com.ttexplo.mobile.domain.model.DevolucionRecord
import com.ttexplo.mobile.domain.model.EntradaRecord
import com.ttexplo.mobile.domain.model.ScannedCodeRecord
import com.ttexplo.mobile.printer.ValePrintHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Construye los datos del Centro de descarga (solo lectura) a partir de Room.
 * No escribe ni borra nada: únicamente consulta.
 */
class DownloadData(
    private val db: AppDatabase,
    private val context: Context
) {

    private fun esc(value: String?): String =
        "\"" + (value ?: "").replace("\"", "\"\"") + "\""

    private fun csvRow(vararg cells: String?): String =
        cells.joinToString(",") { esc(it) } + "\r\n"

    private fun Int.countLabel(total: Int) = "$this/$total"

    suspend fun summary(): JSONObject {
        val entradas = db.entradaDao().getAllEntradas().count { it.estado != "BORRADOR" }
        val despachos = db.despachoDao().getAllDespachos().count { it.estado != "BORRADOR" }
        val devoluciones = db.devolucionDao().getAllDevoluciones().count { it.estado != "BORRADOR" }
        val accesorios = db.accesorioDao().getAllVales().count { it.estado != "BORRADOR" }
        val conteos = db.boxCountDao().getAll().map { it.folio }.distinct().size
        val productos = db.productoDao().count()
        val eventos = db.inventoryEventDao().getAllActiveEvents().size
        return JSONObject()
            .put("entradas", entradas)
            .put("despachos", despachos)
            .put("devoluciones", devoluciones)
            .put("accesorios", accesorios)
            .put("conteos", conteos)
            .put("productos", productos)
            .put("eventosInventario", eventos)
            .put("totalVales", entradas + despachos + devoluciones + accesorios + conteos)
    }

    suspend fun summaryJson(): String = summary().toString()

    suspend fun valesCsv(): String {
        val sb = StringBuilder()
        sb.append(csvRow("TIPO", "FOLIO", "FECHA", "CAMPO1", "CAMPO2", "ITEMS"))
        for (e in db.entradaDao().getAllEntradas()) {
            if (e.estado == "BORRADOR") continue
            sb.append(csvRow("ENTRADA", e.factura, e.fechaCreacion, e.proveedor, e.polvorin,
                db.entradaDao().getItemsForEntrada(e.id).size.toString()))
        }
        for (d in db.despachoDao().getAllDespachos()) {
            if (d.estado == "BORRADOR") continue
            sb.append(csvRow("DESPACHO", d.vale, d.fechaCreacion, d.empresa, d.lugar,
                db.despachoDao().getItemsForDespacho(d.id).size.toString()))
        }
        for (d in db.devolucionDao().getAllDevoluciones()) {
            if (d.estado == "BORRADOR") continue
            sb.append(csvRow("DEVOLUCION", "DEV${d.id}", d.fechaCreacion, d.empleadoEntrega, d.empleadoRecibe,
                db.devolucionDao().getItemsForDevolucion(d.id).size.toString()))
        }
        for (a in db.accesorioDao().getAllVales()) {
            if (a.estado == "BORRADOR") continue
            sb.append(csvRow("ACCESORIOS", a.vale, a.fechaCreacion, a.empresa, a.recibio,
                db.accesorioDao().getItemsForVale(a.id).size.toString()))
        }
        db.boxCountDao().getAll().groupBy { it.folio }.forEach { (folio, rows) ->
            sb.append(csvRow("CONTEO", folio, rows.firstOrNull()?.fechaHora, "${rows.size} cajas",
                "${rows.sumOf { it.piezas }} piezas", rows.size.toString()))
        }
        return sb.toString()
    }

    suspend fun inventoryCsv(): String {
        val sb = StringBuilder()
        sb.append(csvRow("EVENTO", "DISPOSITIVO", "POLVORIN", "TIPO", "PRODUCTO", "CATEGORIA",
            "UNIDAD", "DELTA", "CAJAS", "SACOS", "CARTUCHOS", "FECHA", "REFERENCIA", "ESTADO"))
        for (e in db.inventoryEventDao().getAllActiveEvents()) {
            sb.append(csvRow(e.eventUuid, e.deviceId, e.polvorin, e.eventType, e.productName,
                e.inventoryCategory, e.unit, String.format(Locale.US, "%.4f", e.deltaQuantity),
                e.cajasDelta.toString(), e.sacosDelta.toString(), e.cartuchosDelta.toString(),
                e.timestampIso, e.referenceFolio, e.syncStatus))
        }
        return sb.toString()
    }

    suspend fun catalogCsv(): String {
        val sb = StringBuilder()
        sb.append(csvRow("ID", "PRODUCTO", "PRESENTACION", "UNIDAD", "PESO", "PESO_UNITARIO",
            "CARTUCHOS_CAJA", "TIPO", "CATEGORIA", "COD_BARRAS"))
        for (p in db.productoDao().getAll()) {
            sb.append(csvRow(p.idProducto, p.producto, p.presentacion, p.unidad,
                String.format(Locale.US, "%.3f", p.peso),
                String.format(Locale.US, "%.6f", p.pesoUnitarioKg),
                p.cartuchosPorCaja.toString(), p.tipo, p.categoriaInventario, p.codBarras))
        }
        return sb.toString()
    }

    /** Archivo .txt consolidado, el mismo que usa T&T Explo para sincronizar. */
    suspend fun valesTxt(): String {
        val sb = StringBuilder()
        for (d in db.despachoDao().getAllDespachos()) {
            if (d.estado == "BORRADOR") continue
            val items = db.despachoDao().getItemsForDespacho(d.id).map {
                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
            }
            sb.append(FlatFileFormatter.formatDespacho(
                DespachoRecord(d.vale, d.empresa, d.lugar, d.transportista, d.turno, d.voladura, items)
            )).append("\r\n")
        }
        for (e in db.entradaDao().getAllEntradas()) {
            if (e.estado == "BORRADOR") continue
            val items = db.entradaDao().getItemsForEntrada(e.id).map {
                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
            }
            sb.append(FlatFileFormatter.formatEntrada(
                EntradaRecord(e.proveedor, e.polvorin, e.factura, items)
            )).append("\r\n")
        }
        for (a in db.accesorioDao().getAllVales()) {
            if (a.estado == "BORRADOR") continue
            val items = db.accesorioDao().getItemsForVale(a.id).map {
                AccessoryItemRecord(it.codigoAccesorio, it.cantidad, it.fechaEscaneo)
            }
            sb.append(FlatFileFormatter.formatAccesorios(
                AccesoriosRecord(a.vale, a.empresa, a.lugar, a.transportista, a.turno, a.recibio, a.entrego, a.voladura, items)
            )).append("\r\n")
        }
        for (dev in db.devolucionDao().getAllDevoluciones()) {
            if (dev.estado == "BORRADOR") continue
            val items = db.devolucionDao().getItemsForDevolucion(dev.id).map {
                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
            }
            sb.append(FlatFileFormatter.formatDevolucion(
                DevolucionRecord(dev.empleadoEntrega, dev.empleadoRecibe, items)
            )).append("\r\n")
        }
        return sb.toString().trim()
    }

    /** Lista de vales para el panel web (clic → previsualización). */
    suspend fun valesJson(): String {
        val arr = JSONArray()
        for (e in db.entradaDao().getAllEntradas()) {
            if (e.estado == "BORRADOR") continue
            arr.put(JSONObject().put("tipo", "ENTRADA").put("id", e.id)
                .put("folio", e.factura).put("fecha", e.fechaCreacion)
                .put("resumen", "Prov ${e.proveedor} · Polv ${e.polvorin}")
                .put("url", "/vale?tipo=ENTRADA&id=${e.id}"))
        }
        for (d in db.despachoDao().getAllDespachos()) {
            if (d.estado == "BORRADOR") continue
            arr.put(JSONObject().put("tipo", "DESPACHO").put("id", d.id)
                .put("folio", d.vale).put("fecha", d.fechaCreacion)
                .put("resumen", "${d.empresa} · ${d.lugar}")
                .put("url", "/vale?tipo=DESPACHO&id=${d.id}"))
        }
        for (dev in db.devolucionDao().getAllDevoluciones()) {
            if (dev.estado == "BORRADOR") continue
            arr.put(JSONObject().put("tipo", "DEVOLUCION").put("id", dev.id)
                .put("folio", "DEV${dev.id}").put("fecha", dev.fechaCreacion)
                .put("resumen", "${dev.empleadoEntrega} → ${dev.empleadoRecibe}")
                .put("url", "/vale?tipo=DEVOLUCION&id=${dev.id}"))
        }
        for (a in db.accesorioDao().getAllVales()) {
            if (a.estado == "BORRADOR") continue
            arr.put(JSONObject().put("tipo", "ACCESORIOS").put("id", a.id)
                .put("folio", a.vale).put("fecha", a.fechaCreacion)
                .put("resumen", "${a.empresa} · ${a.recibio}")
                .put("url", "/vale?tipo=ACCESORIOS&id=${a.id}"))
        }
        db.boxCountDao().getAll().groupBy { it.folio }.forEach { (folio, rows) ->
            arr.put(JSONObject().put("tipo", "CONTEO").put("id", 0)
                .put("folio", folio).put("fecha", rows.firstOrNull()?.fechaHora ?: "")
                .put("resumen", "${rows.size} cajas · ${rows.sumOf { it.piezas }} piezas")
                .put("url", "/vale?tipo=CONTEO&folio=${java.net.URLEncoder.encode(folio, "UTF-8")}"))
        }
        return arr.toString()
    }

    /** Ticket PLAIN de un vale para la previsualización web. */
    suspend fun ticketFor(tipo: String, id: Long, folio: String?): String? = when (tipo.uppercase()) {
        "ENTRADA" -> {
            val e = db.entradaDao().getEntradaById(id) ?: return null
            val items = db.entradaDao().getItemsForEntrada(e.id).map {
                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
            }
            ValePrintHelper.previewEntrada(context, EntradaRecord(e.proveedor, e.polvorin, e.factura, items), null)
        }
        "DESPACHO" -> {
            val d = db.despachoDao().getDespachoById(id) ?: return null
            val items = db.despachoDao().getItemsForDespacho(d.id).map {
                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
            }
            ValePrintHelper.previewDespacho(context,
                DespachoRecord(d.vale, d.empresa, d.lugar, d.transportista, d.turno, d.voladura, items), null)
        }
        "DEVOLUCION" -> {
            val dev = db.devolucionDao().getDevolucionById(id) ?: return null
            val items = db.devolucionDao().getItemsForDevolucion(dev.id).map {
                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
            }
            ValePrintHelper.previewDevolucion(context,
                DevolucionRecord(dev.empleadoEntrega, dev.empleadoRecibe, items), null)
        }
        "ACCESORIOS" -> {
            val a = db.accesorioDao().getValeById(id) ?: return null
            val items = db.accesorioDao().getItemsForVale(a.id).map {
                AccessoryItemRecord(it.codigoAccesorio, it.cantidad, it.fechaEscaneo)
            }
            ValePrintHelper.previewAccesorios(context,
                AccesoriosRecord(a.vale, a.empresa, a.lugar, a.transportista, a.turno, a.recibio, a.entrego, a.voladura, items), null)
        }
        "CONTEO" -> {
            val f = folio ?: return null
            val rows = db.boxCountDao().getByFolio(f)
            if (rows.isEmpty()) null
            else ValePrintHelper.previewBoxCount(context, BoxCountVale(f, rows.map { it.toDomain() }), null)
        }
        else -> null
    }

    suspend fun allJson(): String {
        val root = JSONObject()
        root.put("generado", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date()))
        root.put("resumen", summary())

        val vales = JSONArray()
        for (e in db.entradaDao().getAllEntradas()) {
            if (e.estado == "BORRADOR") continue
            vales.put(JSONObject().put("tipo", "ENTRADA").put("folio", e.factura)
                .put("fecha", e.fechaCreacion).put("proveedor", e.proveedor).put("polvorin", e.polvorin))
        }
        for (d in db.despachoDao().getAllDespachos()) {
            if (d.estado == "BORRADOR") continue
            vales.put(JSONObject().put("tipo", "DESPACHO").put("folio", d.vale)
                .put("fecha", d.fechaCreacion).put("empresa", d.empresa).put("lugar", d.lugar))
        }
        for (d in db.devolucionDao().getAllDevoluciones()) {
            if (d.estado == "BORRADOR") continue
            vales.put(JSONObject().put("tipo", "DEVOLUCION").put("folio", "DEV${d.id}")
                .put("fecha", d.fechaCreacion).put("entrega", d.empleadoEntrega).put("recibe", d.empleadoRecibe))
        }
        for (a in db.accesorioDao().getAllVales()) {
            if (a.estado == "BORRADOR") continue
            vales.put(JSONObject().put("tipo", "ACCESORIOS").put("folio", a.vale)
                .put("fecha", a.fechaCreacion).put("empresa", a.empresa).put("recibio", a.recibio))
        }
        root.put("vales", vales)

        val catalogo = JSONArray()
        for (p in db.productoDao().getAll()) {
            catalogo.put(JSONObject().put("id", p.idProducto).put("producto", p.producto)
                .put("presentacion", p.presentacion).put("unidad", p.unidad)
                .put("peso", p.peso).put("pesoUnitarioKg", p.pesoUnitarioKg)
                .put("cartuchosPorCaja", p.cartuchosPorCaja).put("tipo", p.tipo)
                .put("categoria", p.categoriaInventario).put("codBarras", p.codBarras))
        }
        root.put("catalogo", catalogo)

        val inventario = JSONArray()
        for (e in db.inventoryEventDao().getAllActiveEvents()) {
            inventario.put(JSONObject().put("evento", e.eventUuid).put("dispositivo", e.deviceId)
                .put("polvorin", e.polvorin).put("tipo", e.eventType).put("producto", e.productName)
                .put("categoria", e.inventoryCategory).put("unidad", e.unit)
                .put("delta", e.deltaQuantity).put("fecha", e.timestampIso)
                .put("referencia", e.referenceFolio).put("estado", e.syncStatus))
        }
        root.put("inventario", inventario)
        return root.toString()
    }
}
