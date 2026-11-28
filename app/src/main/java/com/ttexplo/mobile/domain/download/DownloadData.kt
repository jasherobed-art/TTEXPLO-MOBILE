package com.ttexplo.mobile.domain.download

import android.content.Context
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.AppDatabase
import com.ttexplo.mobile.domain.inventory.InventoryReferenceType
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.domain.export.FlatFileFormatter
import com.ttexplo.mobile.domain.inventory.BoxCountTxtExporter
import com.ttexplo.mobile.domain.inventory.BoxCountVale
import com.ttexplo.mobile.domain.model.AccesoriosRecord
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.model.DespachoRecord
import com.ttexplo.mobile.domain.model.DevolucionRecord
import com.ttexplo.mobile.domain.model.EntradaRecord
import com.ttexplo.mobile.domain.model.ScannedCodeRecord
import com.ttexplo.mobile.domain.model.StandardUnits
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.printer.ValePrintHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val MOTIVO_BORRADO = "Anulación desde Centro de descarga"

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

    /**
     * Todo renglón del .txt lleva "yyyy-MM-dd HH:mm:ss". Si la corrección trae solo la hora,
     * se le pone la fecha del vale; si viene vacía, la hora actual.
     */
    private fun stamp(hora: String, fechaVale: String): String {
        val t = hora.trim()
        if (t.length >= 16 && t[4] == '-' && t[7] == '-') return t
        val ahora = DateTimeUtils.currentTimestamp()
        val fecha = if (fechaVale.length >= 10 && fechaVale[4] == '-' && fechaVale[7] == '-') {
            fechaVale.take(10)
        } else {
            ahora.take(10)
        }
        return "$fecha ${t.ifBlank { ahora.substring(11) }}"
    }

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

    suspend fun catalogCsv(tipos: Set<String>? = null): String {
        val sb = StringBuilder()
        sb.append(csvRow("ID", "PRODUCTO", "PRESENTACION", "UNIDAD", "PESO", "PESO_UNITARIO",
            "CARTUCHOS_CAJA", "TIPO", "CATEGORIA", "COD_BARRAS"))
        for (p in db.productoDao().getAll()) {
            if (tipos != null && p.tipo !in tipos) continue
            sb.append(csvRow(p.idProducto, p.producto, p.presentacion, p.unidad,
                String.format(Locale.US, "%.3f", p.peso),
                String.format(Locale.US, "%.6f", p.pesoUnitarioKg),
                p.cartuchosPorCaja.toString(), p.tipo, p.categoriaInventario, p.codBarras))
        }
        return sb.toString()
    }

    /** Borra vales (uno o todos) revirtiendo inventario, igual que el Historial de la app. */
    suspend fun deleteVales(keys: List<String>?): Int {
        val repo = (context.applicationContext as TTExploApp).inventoryRepository
        val todas = buildList {
            db.entradaDao().getAllEntradas().filter { it.estado != "BORRADOR" }
                .forEach { add("ENTRADA:${it.id}") }
            db.despachoDao().getAllDespachos().filter { it.estado != "BORRADOR" }
                .forEach { add("DESPACHO:${it.id}") }
            db.devolucionDao().getAllDevoluciones().filter { it.estado != "BORRADOR" }
                .forEach { add("DEVOLUCION:${it.id}") }
            db.accesorioDao().getAllVales().filter { it.estado != "BORRADOR" }
                .forEach { add("ACCESORIOS:${it.id}") }
            db.boxCountDao().getAll().map { it.folio }.distinct()
                .forEach { add("CONTEO:$it") }
        }
        val objetivo = keys?.takeIf { it.isNotEmpty() } ?: todas
        var borrados = 0
        for (key in objetivo) {
            val tipo = key.substringBefore(':')
            val raw = key.substringAfter(':', "")
            if (raw.isBlank()) continue
            when (tipo) {
                "ENTRADA" -> raw.toLongOrNull()?.let {
                    repo.deleteValeAndReverse(InventoryReferenceType.ENTRADA, it, MOTIVO_BORRADO)
                    borrados++
                }
                "DESPACHO" -> raw.toLongOrNull()?.let {
                    repo.deleteValeAndReverse(InventoryReferenceType.DESPACHO, it, MOTIVO_BORRADO)
                    borrados++
                }
                "DEVOLUCION" -> raw.toLongOrNull()?.let {
                    repo.deleteValeAndReverse(InventoryReferenceType.DEVOLUCION, it, MOTIVO_BORRADO)
                    borrados++
                }
                "ACCESORIOS" -> raw.toLongOrNull()?.let {
                    repo.deleteValeAndReverse(InventoryReferenceType.ACCESORIO_VALE, it, MOTIVO_BORRADO)
                    borrados++
                }
                "CONTEO" -> {
                    db.boxCountDao().deleteByFolio(raw)
                    borrados++
                }
            }
        }
        return borrados
    }

    /** Catálogo de la terminal para consultarlo desde la web. */
    suspend fun catalogJson(tipos: Set<String>?, q: String?): String {
        val term = q?.trim()?.lowercase().orEmpty()
        val arr = JSONArray()
        db.productoDao().getAll()
            .asSequence()
            .filter { tipos == null || it.tipo in tipos }
            .filter {
                term.isEmpty() ||
                    it.producto.lowercase().contains(term) ||
                    it.idProducto.lowercase().contains(term) ||
                    it.codBarras.lowercase().contains(term)
            }
            .sortedBy { it.producto.lowercase() }
            .forEach { p ->
                arr.put(
                    JSONObject()
                        .put("id", p.idProducto)
                        .put("producto", p.producto.trim())
                        .put("presentacion", p.presentacion.trim())
                        .put("unidad", StandardUnits.normalize(p.unidad, p.tipo, p.idProducto, p.producto))
                        .put("peso", p.peso)
                        .put("tipo", p.tipo)
                        .put("categoria", p.categoriaInventario)
                        .put("codBarras", p.codBarras)
                        .put("noSap", p.noSap)
                        .put("cartuchosPorCaja", p.cartuchosPorCaja)
                        .put("pesoUnitarioKg", p.pesoUnitarioKg)
                )
            }
        return JSONObject().put("total", arr.length()).put("productos", arr).toString()
    }

    /**
     * Agrega o actualiza un producto del catálogo desde la web.
     * Devuelve 0 = guardado, 1 = no existe (edición), 2 = ya existe (alta).
     */
    suspend fun saveProductJson(json: String): Int {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return 1
        val id = obj.optString("id").trim()
        if (id.isEmpty()) return 1
        val crear = obj.optBoolean("crear", false)
        val current = db.productoDao().getById(id)
        if (current == null && !crear) return 1
        if (current != null && crear) return 2

        val tipo = obj.optString("tipo").uppercase().takeIf {
            it == "ALTO_EXPLOSIVO" || it == "AGENTE" || it == "ACCESORIO"
        } ?: current?.tipo ?: "ACCESORIO"
        val nombre = obj.optString("producto", current?.producto ?: "").trim()
            .ifBlank { current?.producto ?: id }
        val presentacion = obj.optString("presentacion", current?.presentacion ?: "").trim()
        val unidad = StandardUnits.normalize(
            obj.optString("unidad", current?.unidad ?: ""),
            tipo,
            id,
            nombre
        )
        val peso = if (obj.has("peso")) obj.optDouble("peso", current?.peso ?: 0.0)
        else current?.peso ?: 0.0
        val noSap = obj.optString("noSap", current?.noSap ?: "").trim()
        val cartuchos = if (tipo == "ALTO_EXPLOSIVO") {
            obj.optInt("cartuchosPorCaja", current?.cartuchosPorCaja ?: 0).coerceAtLeast(0)
        } else {
            0
        }
        val pesoUnitario = if (tipo == "ALTO_EXPLOSIVO") {
            obj.optDouble("pesoUnitarioKg", current?.pesoUnitarioKg ?: 0.0)
        } else {
            0.0
        }
        val parsedType = runCatching { com.ttexplo.mobile.domain.model.ProductType.valueOf(tipo) }
            .getOrDefault(com.ttexplo.mobile.domain.model.ProductType.ACCESORIO)
        val categoria = com.ttexplo.mobile.domain.model.InventoryCategory
            .fromStringOrNull(obj.optString("categoria"))
            ?.name
            ?: current?.categoriaInventario
            ?: com.ttexplo.mobile.domain.model.InventoryCategory
                .inferCategory(parsedType, id, nombre, unidad).name

        val base = current ?: com.ttexplo.mobile.data.local.entity.ProductoEntity(
            idProducto = id,
            producto = nombre,
            presentacion = presentacion,
            noSap = noSap,
            unidad = unidad,
            cantidad = 0.0,
            codBarras = id,
            peso = peso,
            pg = "",
            pu = 0.0,
            tipo = tipo,
            pesoUnitarioKg = pesoUnitario,
            cartuchosPorCaja = cartuchos,
            categoriaInventario = categoria
        )
        db.productoDao().insert(
            base.copy(
                producto = nombre,
                presentacion = presentacion,
                noSap = noSap,
                unidad = unidad,
                peso = peso,
                tipo = tipo,
                pesoUnitarioKg = pesoUnitario,
                cartuchosPorCaja = cartuchos,
                categoriaInventario = categoria
            )
        )
        return 0
    }

    /** Sube un catálogo en CSV desde la web y lo aplica en la base de la terminal. */
    suspend fun importCatalogCsv(csv: String): Int =
        CatalogRepositoryImpl(db.productoDao()).importCsv(csv)

    /** Archivo .txt consolidado con PRV, REF, DEV, ACC y CAJ; aplica correcciones de la sesión. */
    suspend fun valesTxt(keys: Set<String>? = null, edits: Map<String, ValeEdit>? = null): String {
        val sb = StringBuilder()
        for (e in db.entradaDao().getAllEntradas()) {
            if (e.estado == "BORRADOR") continue
            val key = "ENTRADA:${e.id}"
            if (keys != null && key !in keys) continue
            val edit = edits?.get(key)
            val items = edit?.codigos?.map { ScannedCodeRecord(it.codigo, stamp(it.hora, e.fechaCreacion)) }
                ?: db.entradaDao().getItemsForEntrada(e.id).map {
                    ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                }
            sb.append(FlatFileFormatter.formatEntrada(
                EntradaRecord(
                    edit?.datos?.get("proveedor") ?: e.proveedor,
                    edit?.datos?.get("polvorin") ?: e.polvorin,
                    edit?.datos?.get("factura") ?: e.factura,
                    items
                )
            )).append("\r\n")
        }
        for (d in db.despachoDao().getAllDespachos()) {
            if (d.estado == "BORRADOR") continue
            val key = "DESPACHO:${d.id}"
            if (keys != null && key !in keys) continue
            val edit = edits?.get(key)
            val items = edit?.codigos?.map { ScannedCodeRecord(it.codigo, stamp(it.hora, d.fechaCreacion)) }
                ?: db.despachoDao().getItemsForDespacho(d.id).map {
                    ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
                }
            sb.append(FlatFileFormatter.formatDespacho(
                DespachoRecord(
                    d.vale,
                    edit?.datos?.get("empresa") ?: d.empresa,
                    edit?.datos?.get("lugar") ?: d.lugar,
                    edit?.datos?.get("transportista") ?: d.transportista,
                    edit?.datos?.get("turno") ?: d.turno,
                    edit?.datos?.get("voladura") ?: d.voladura,
                    items
                )
            )).append("\r\n")
        }
        for (dev in db.devolucionDao().getAllDevoluciones()) {
            if (dev.estado == "BORRADOR") continue
            val key = "DEVOLUCION:${dev.id}"
            if (keys != null && key !in keys) continue
            val edit = edits?.get(key)
            val items = edit?.codigos?.map { ScannedCodeRecord(it.codigo, stamp(it.hora, dev.fechaCreacion)) }
                ?: db.devolucionDao().getItemsForDevolucion(dev.id).map {
                    ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                }
            sb.append(FlatFileFormatter.formatDevolucion(
                DevolucionRecord(
                    edit?.datos?.get("empleadoEntrega") ?: dev.empleadoEntrega,
                    edit?.datos?.get("empleadoRecibe") ?: dev.empleadoRecibe,
                    items
                )
            )).append("\r\n")
        }
        for (a in db.accesorioDao().getAllVales()) {
            if (a.estado == "BORRADOR") continue
            val key = "ACCESORIOS:${a.id}"
            if (keys != null && key !in keys) continue
            val edit = edits?.get(key)
            val items = edit?.codigos?.map { AccessoryItemRecord(it.codigo, it.cantidad, stamp(it.hora, a.fechaCreacion)) }
                ?: db.accesorioDao().getItemsForVale(a.id).map {
                    AccessoryItemRecord(it.codigoAccesorio, it.cantidad, it.fechaEscaneo)
                }
            sb.append(FlatFileFormatter.formatAccesorios(
                AccesoriosRecord(
                    a.vale,
                    edit?.datos?.get("empresa") ?: a.empresa,
                    edit?.datos?.get("lugar") ?: a.lugar,
                    edit?.datos?.get("transportista") ?: a.transportista,
                    edit?.datos?.get("turno") ?: a.turno,
                    edit?.datos?.get("recibio") ?: a.recibio,
                    edit?.datos?.get("entrego") ?: a.entrego,
                    edit?.datos?.get("voladura") ?: a.voladura,
                    items
                )
            )).append("\r\n")
        }
        db.boxCountDao().getAll().groupBy { it.folio }.forEach { (folio, rows) ->
            if (keys != null && "CONTEO:$folio" !in keys) return@forEach
            sb.append(BoxCountTxtExporter.renderItems(rows.map { it.toDomain() })).append("\r\n")
        }
        return sb.toString().replace("\r\n", "\n").replace("\n", "\r\n").trim()
    }

    private fun empaqueFor(code: String): String = when (code.length) {
        30 -> "CAJA"
        18 -> "SACO"
        16 -> "CARTUCHO"
        else -> "PIEZA"
    }

    private fun detailJson(
        tipo: String,
        tipoLabel: String,
        folio: String,
        campos: List<Triple<String, String, String>>,
        items: List<Triple<String, String, Int>>,
        codigos: List<OverlayCode>,
        totales: Map<String, Int>,
        firmas: List<String>,
        alerta: Boolean = false
    ): String {
        val root = JSONObject().put("tipo", tipo).put("tipoLabel", tipoLabel).put("folio", folio)
        val d = JSONArray()
        val cpos = JSONArray()
        campos.forEach {
            d.put(JSONObject().put("label", it.second).put("value", it.third))
            cpos.put(JSONObject().put("field", it.first).put("label", it.second).put("value", it.third))
        }
        root.put("datos", d)
        root.put("campos", cpos)
        val itemsArr = JSONArray()
        items.forEach {
            itemsArr.put(JSONObject().put("nombre", it.first).put("empaque", it.second).put("cantidad", it.third))
        }
        root.put("items", itemsArr)
        val codes = JSONArray()
        codigos.forEach {
            codes.put(JSONObject().put("codigo", it.codigo).put("hora", it.hora).put("cantidad", it.cantidad))
        }
        root.put("codigos", codes)
        val totals = JSONObject()
        totales.forEach { (k, v) -> totals.put(k, v) }
        root.put("totales", totals)
        val f = JSONArray()
        firmas.forEach { f.put(it) }
        root.put("firmas", f)
        root.put("alerta", alerta)
        root.put("sub", if (tipo == "ACCESORIOS") {
            "Accesorios ${totales["accesorios"] ?: 0}"
        } else {
            "Cajas ${totales["cajas"] ?: 0} · Sacos ${totales["sacos"] ?: 0}"
        })
        return root.toString()
    }

    private fun explosiveDetail(
        tipo: String,
        tipoLabel: String,
        folio: String,
        campos: List<Triple<String, String, String>>,
        items: List<ScannedCodeRecord>,
        firmas: List<String>
    ): String {
        val grouped = items.groupBy {
            (it.productName.ifBlank { "Material ${it.code.take(3)}" }) to empaqueFor(it.code)
        }.map { (k, v) -> Triple(k.first, k.second, v.size) }
        val totals = mutableMapOf("piezas" to items.size, "cajas" to 0, "sacos" to 0, "cartuchos" to 0, "accesorios" to 0)
        items.forEach {
            val key = when (empaqueFor(it.code)) {
                "CAJA" -> "cajas"
                "SACO" -> "sacos"
                "CARTUCHO" -> "cartuchos"
                else -> null
            }
            if (key != null) totals[key] = (totals[key] ?: 0) + 1
        }
        return detailJson(tipo, tipoLabel, folio, campos, grouped,
            items.map { OverlayCode(it.code, it.timestamp) }, totals, firmas)
    }

    private suspend fun accessoryName(code: String): String {
        val key = if (code.length > 7 && code.take(7).matches(Regex("^X\\d{6}$"))) code.take(7) else code
        val product = db.productoDao().getById(key)
            ?: db.productoDao().getByCodBarras(key)
            ?: db.productoDao().getByCodBarras(code)
            ?: db.productoDao().getById(code)
        return product?.producto ?: code
    }

    /** Detalle estructurado de un vale para el panel web. */
    suspend fun valeDetail(key: String): String? {
        val parts = key.split(':', limit = 2)
        if (parts.size != 2) return null
        val tipo = parts[0].uppercase()
        val raw = parts[1]
        return when (tipo) {
            "ENTRADA" -> {
                val id = raw.toLongOrNull() ?: return null
                val e = db.entradaDao().getEntradaById(id) ?: return null
                val items = db.entradaDao().getItemsForEntrada(e.id).map {
                    ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
                }
                explosiveDetail("ENTRADA", "Entrada", e.factura, listOf(
                    Triple("proveedor", "Proveedor", e.proveedor),
                    Triple("polvorin", "Polvorín", e.polvorin),
                    Triple("factura", "Factura / Remisión", e.factura),
                    Triple("fecha", "Fecha", e.fechaCreacion)
                ), items, listOf("Recibió en polvorín: ${e.polvorin}", "Entregó proveedor: ${e.proveedor}"))
            }
            "DESPACHO" -> {
                val id = raw.toLongOrNull() ?: return null
                val d = db.despachoDao().getDespachoById(id) ?: return null
                val items = db.despachoDao().getItemsForDespacho(d.id).map {
                    ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
                }
                explosiveDetail("DESPACHO", "Salida", d.vale, listOf(
                    Triple("empresa", "Empresa", d.empresa),
                    Triple("lugar", "Lugar", d.lugar),
                    Triple("transportista", "Transporte", d.transportista),
                    Triple("turno", "Turno", d.turno),
                    Triple("voladura", "Voladura", d.voladura),
                    Triple("fecha", "Fecha", d.fechaCreacion)
                ), items, listOf("Entregó: ${d.transportista}", "Recibió: ${d.empresa}"))
            }
            "DEVOLUCION" -> {
                val id = raw.toLongOrNull() ?: return null
                val dev = db.devolucionDao().getDevolucionById(id) ?: return null
                val items = db.devolucionDao().getItemsForDevolucion(dev.id).map {
                    ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                }
                explosiveDetail("DEVOLUCION", "Devolución", "DEV${dev.id}", listOf(
                    Triple("empleadoEntrega", "Entrega", dev.empleadoEntrega),
                    Triple("empleadoRecibe", "Recibe", dev.empleadoRecibe),
                    Triple("fecha", "Fecha", dev.fechaCreacion)
                ), items, listOf("Entregó: ${dev.empleadoEntrega}", "Recibió: ${dev.empleadoRecibe}"))
            }
            "ACCESORIOS" -> {
                val id = raw.toLongOrNull() ?: return null
                val a = db.accesorioDao().getValeById(id) ?: return null
                val rows = db.accesorioDao().getItemsForVale(a.id)
                val items = rows.map {
                    Triple(accessoryName(it.codigoAccesorio), "ACCESORIO", kotlin.math.abs(it.cantidad))
                }
                val codes = rows.map {
                    OverlayCode(it.codigoAccesorio, it.fechaEscaneo, kotlin.math.abs(it.cantidad))
                }
                val total = codes.sumOf { it.cantidad }
                val header = a.voladura.toIntOrNull()
                detailJson("ACCESORIOS", "Accesorios", a.vale, listOf(
                    Triple("empresa", "Empresa", a.empresa),
                    Triple("lugar", "Lugar", a.lugar),
                    Triple("entrego", "Entrega", a.entrego),
                    Triple("recibio", "Recibió", a.recibio),
                    Triple("turno", "Turno", a.turno),
                    Triple("fecha", "Fecha", a.fechaCreacion)
                ), items, codes,
                    mapOf("piezas" to total, "cajas" to 0, "sacos" to 0, "cartuchos" to 0, "accesorios" to total),
                    listOf("Entregó: ${a.entrego}", "Recibió: ${a.recibio}"),
                    alerta = header != null && header != total)
            }
            "CONTEO" -> {
                val rows = db.boxCountDao().getByFolio(raw)
                if (rows.isEmpty()) return null
                val items = rows.map { Triple("Caja ${it.codigo}", "CAJA", it.piezas) }
                detailJson("CONTEO", "Conteo", raw, listOf(
                    Triple("folio", "Folio", raw),
                    Triple("cajas", "Cajas", rows.size.toString()),
                    Triple("fecha", "Fecha", rows.firstOrNull()?.fechaHora ?: "")
                ), items, emptyList(),
                    mapOf("piezas" to rows.sumOf { it.piezas }, "cajas" to rows.size,
                        "sacos" to 0, "cartuchos" to 0, "accesorios" to 0), emptyList())
            }
            else -> null
        }
    }

    /** Lista de vales para el panel web. */
    suspend fun valesJson(): String {
        val arr = JSONArray()
        for (e in db.entradaDao().getAllEntradas()) {
            if (e.estado == "BORRADOR") continue
            val items = db.entradaDao().getItemsForEntrada(e.id)
            arr.put(JSONObject().put("tipo", "ENTRADA").put("tipoLabel", "Entrada").put("id", e.id)
                .put("key", "ENTRADA:${e.id}")
                .put("folio", e.factura).put("fecha", e.fechaCreacion)
                .put("resumen", "Prov ${e.proveedor} · Polv ${e.polvorin}")
                .put("piezas", items.size)
                .put("codigos", items.joinToString(" ") { it.codigoBarras })
                .put("url", "/vale?tipo=ENTRADA&id=${e.id}"))
        }
        for (d in db.despachoDao().getAllDespachos()) {
            if (d.estado == "BORRADOR") continue
            val items = db.despachoDao().getItemsForDespacho(d.id)
            arr.put(JSONObject().put("tipo", "DESPACHO").put("tipoLabel", "Salida").put("id", d.id)
                .put("key", "DESPACHO:${d.id}")
                .put("folio", d.vale).put("fecha", d.fechaCreacion)
                .put("resumen", "${d.empresa} · ${d.lugar}")
                .put("piezas", items.size)
                .put("codigos", items.joinToString(" ") { it.codigoBarras })
                .put("url", "/vale?tipo=DESPACHO&id=${d.id}"))
        }
        for (dev in db.devolucionDao().getAllDevoluciones()) {
            if (dev.estado == "BORRADOR") continue
            val items = db.devolucionDao().getItemsForDevolucion(dev.id)
            arr.put(JSONObject().put("tipo", "DEVOLUCION").put("tipoLabel", "Devolución").put("id", dev.id)
                .put("key", "DEVOLUCION:${dev.id}")
                .put("folio", "DEV${dev.id}").put("fecha", dev.fechaCreacion)
                .put("resumen", "${dev.empleadoEntrega} → ${dev.empleadoRecibe}")
                .put("piezas", items.size)
                .put("codigos", items.joinToString(" ") { it.codigoBarras })
                .put("url", "/vale?tipo=DEVOLUCION&id=${dev.id}"))
        }
        for (a in db.accesorioDao().getAllVales()) {
            if (a.estado == "BORRADOR") continue
            val rows = db.accesorioDao().getItemsForVale(a.id)
            arr.put(JSONObject().put("tipo", "ACCESORIOS").put("tipoLabel", "Accesorio").put("id", a.id)
                .put("key", "ACCESORIOS:${a.id}")
                .put("folio", a.vale).put("fecha", a.fechaCreacion)
                .put("resumen", "${a.empresa} · ${a.recibio}")
                .put("piezas", rows.sumOf { kotlin.math.abs(it.cantidad) })
                .put("codigos", rows.joinToString(" ") { it.codigoAccesorio })
                .put("url", "/vale?tipo=ACCESORIOS&id=${a.id}"))
        }
        db.boxCountDao().getAll().groupBy { it.folio }.forEach { (folio, rows) ->
            arr.put(JSONObject().put("tipo", "CONTEO").put("tipoLabel", "Conteo").put("id", 0)
                .put("key", "CONTEO:$folio")
                .put("folio", folio).put("fecha", rows.firstOrNull()?.fechaHora ?: "")
                .put("resumen", "${rows.size} cajas · ${rows.sumOf { it.piezas }} piezas")
                .put("piezas", rows.sumOf { it.piezas })
                .put("codigos", rows.joinToString(" ") { it.codigo })
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
