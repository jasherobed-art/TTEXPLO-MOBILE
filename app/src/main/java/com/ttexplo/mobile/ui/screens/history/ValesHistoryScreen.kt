package com.ttexplo.mobile.ui.screens.history

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.export.FlatFileFormatter
import com.ttexplo.mobile.domain.inventory.InventoryReferenceType
import com.ttexplo.mobile.domain.model.*
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.printer.BluetoothPrinterManager
import com.ttexplo.mobile.printer.ValePrintHelper
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.QuickPrinterSelectorDialog
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

data class ValeSummaryItem(
    val id: Long,
    val tipo: String, // "DESPACHO", "ENTRADA", "ACCESORIOS", "DEV. ACCESORIOS", "DEVOLUCION"
    val folio: String,
    val descripcion: String,
    val fecha: String,
    val totalItems: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValesHistoryScreen(
    onNavigateBack: () -> Unit,
    onEditDespacho: (Long) -> Unit = {},
    onEditEntrada: (Long) -> Unit = {},
    onEditAccesorios: (Long) -> Unit = {},
    onEditDevolucionAccesorios: (Long) -> Unit = {},
    onEditDevolucion: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as TTExploApp
    val db = app.database
    val inventoryRepo = app.inventoryRepository
    val catalogRepo = remember { CatalogRepositoryImpl(db.productoDao()) }
    val settingsRepo = remember { SettingsRepository(context) }
    val printerManager = remember { BluetoothPrinterManager(context) }

    var valesList by remember { mutableStateOf<List<ValeSummaryItem>>(emptyList()) }
    val selectedIds = remember { mutableStateListOf<Pair<String, Long>>() }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    var savedProfiles by remember { mutableStateOf(settingsRepo.getPrinterProfiles()) }
    var activeProfile by remember { mutableStateOf(settingsRepo.getActivePrinterProfile()) }
    var showPrinterSelector by remember { mutableStateOf(false) }
    var printingValeKey by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var selectedDetailData by remember { mutableStateOf<ValeDetailData?>(null) }
    var detailCopiesPrinted by remember { mutableStateOf(0) }
    var detailPrintStatusMessage by remember { mutableStateOf<String?>(null) }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var valesToDelete by remember { mutableStateOf<List<ValeSummaryItem>>(emptyList()) }
    var securityCode by remember { mutableStateOf("") }
    var inputCode by remember { mutableStateOf("") }

    fun refreshPrinters() {
        savedProfiles = settingsRepo.getPrinterProfiles()
        activeProfile = settingsRepo.getActivePrinterProfile()
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else true
        if (granted) {
            pendingBtAction?.invoke()
            pendingBtAction = null
        } else {
            pendingBtAction = null
            Toast.makeText(context, "Se requiere permiso de Bluetooth para reimprimir", Toast.LENGTH_LONG).show()
        }
    }

    fun withBluetoothPermission(action: () -> Unit) {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingBtAction = action
            btPermissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            )
        } else {
            action()
        }
    }

    fun reprintVale(
        vale: ValeSummaryItem,
        onFinished: ((ValePrintHelper.PrintOutcome) -> Unit)? = null
    ) {
        withBluetoothPermission {
            refreshPrinters()
            if (activeProfile == null && printerManager.getPairedPrinters().size > 1) {
                showPrinterSelector = true
                return@withBluetoothPermission
            }
            scope.launch {
                printingValeKey = vale.tipo to vale.id
                val outcome = when (vale.tipo) {
                    "DESPACHO" -> {
                        val d = db.despachoDao().getAllDespachos().find { it.id == vale.id }
                        if (d == null) ValePrintHelper.PrintOutcome(false, "Vale de despacho no encontrado")
                        else {
                            val items = db.despachoDao().getItemsForDespacho(d.id).map {
                                val fallback = if (it.productoNombre.isBlank() || it.presentacion.isBlank()) {
                                    catalogRepo.lookupBarcode(it.codigoBarras)
                                } else null
                                ScannedCodeRecord(
                                    code = it.codigoBarras,
                                    timestamp = it.fechaEscaneo,
                                    productName = it.productoNombre.ifBlank { fallback?.name ?: "" },
                                    presentation = it.presentacion.ifBlank { fallback?.presentation ?: "" }
                                )
                            }
                            ValePrintHelper.printDespacho(
                                context,
                                DespachoRecord(d.vale, d.empresa, d.lugar, d.transportista, d.turno, d.voladura, items),
                                activeProfile
                            )
                        }
                    }
                    "ENTRADA" -> {
                        val e = db.entradaDao().getAllEntradas().find { it.id == vale.id }
                        if (e == null) ValePrintHelper.PrintOutcome(false, "Vale de entrada no encontrado")
                        else {
                            val items = db.entradaDao().getItemsForEntrada(e.id).map {
                                val p = catalogRepo.lookupBarcode(it.codigoBarras)
                                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, p.name, p.presentation)
                            }
                            ValePrintHelper.printEntrada(
                                context,
                                EntradaRecord(e.proveedor, e.polvorin, e.factura, items),
                                activeProfile
                            )
                        }
                    }
                    "ACCESORIOS", "DEV. ACCESORIOS" -> {
                        val a = db.accesorioDao().getAllVales().find { it.id == vale.id }
                        if (a == null) ValePrintHelper.PrintOutcome(false, "Vale de accesorios no encontrado")
                        else {
                            val items = db.accesorioDao().getItemsForVale(a.id).map {
                                AccessoryItemRecord(it.codigoAccesorio, it.cantidad, it.fechaEscaneo)
                            }
                            val cleanEmpresa = if (a.empresa.startsWith("DEV:")) a.empresa.removePrefix("DEV: ") else a.empresa
                            ValePrintHelper.printAccesorios(
                                context,
                                AccesoriosRecord(a.vale, cleanEmpresa, a.lugar, a.transportista, a.turno, a.recibio, a.entrego, a.voladura, items),
                                activeProfile
                            )
                        }
                    }
                    "DEVOLUCION" -> {
                        val dev = db.devolucionDao().getAllDevoluciones().find { it.id == vale.id }
                        if (dev == null) ValePrintHelper.PrintOutcome(false, "Vale de devolución no encontrado")
                        else {
                            val items = db.devolucionDao().getItemsForDevolucion(dev.id).map {
                                val p = catalogRepo.lookupBarcode(it.codigoBarras)
                                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, p.name, p.presentation)
                            }
                            ValePrintHelper.printDevolucion(
                                context,
                                DevolucionRecord(dev.empleadoEntrega, dev.empleadoRecibe, items),
                                activeProfile
                            )
                        }
                    }
                    else -> ValePrintHelper.PrintOutcome(false, "Tipo de vale no soportado para impresión")
                }
                printingValeKey = null
                onFinished?.invoke(outcome)
                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openValeDetails(vale: ValeSummaryItem) {
        scope.launch {
            val detail: ValeDetailData? = when (vale.tipo) {
                "DESPACHO" -> {
                    val d = db.despachoDao().getAllDespachos().find { it.id == vale.id }
                    d?.let {
                        val dbItems = db.despachoDao().getItemsForDespacho(it.id)
                        val codes = dbItems.mapIndexed { idx, row ->
                            val fallback = if (row.productoNombre.isBlank() || row.presentacion.isBlank()) {
                                catalogRepo.lookupBarcode(row.codigoBarras)
                            } else null
                            ValeDetailCodeItem(
                                index = idx + 1,
                                code = row.codigoBarras,
                                productTitle = row.productoNombre.ifBlank { fallback?.name ?: "Material ID ${row.codigoBarras.take(3)}" },
                                presentation = row.presentacion.ifBlank { fallback?.presentation ?: "Pieza" },
                                timestamp = row.fechaEscaneo
                            )
                        }
                        val grouped = codes.groupBy { "${it.productTitle} (${it.presentation})" }
                            .map { (k, v) -> k to v.size }
                            .sortedByDescending { it.second }
                        ValeDetailData(
                            summary = vale,
                            headerFields = listOf(
                                "Folio" to vale.folio,
                                "Empresa" to it.empresa,
                                "Lugar / Frente" to it.lugar,
                                "Transportista" to it.transportista,
                                "Turno / Voladura" to "Turno ${it.turno} • Vol. ${it.voladura}",
                                "Fecha" to it.fechaCreacion
                            ),
                            groupedSummary = grouped,
                            codes = codes
                        )
                    }
                }
                "ENTRADA" -> {
                    val e = db.entradaDao().getAllEntradas().find { it.id == vale.id }
                    e?.let {
                        val dbItems = db.entradaDao().getItemsForEntrada(it.id)
                        val codes = dbItems.mapIndexed { idx, row ->
                            val p = catalogRepo.lookupBarcode(row.codigoBarras)
                            ValeDetailCodeItem(
                                index = idx + 1,
                                code = row.codigoBarras,
                                productTitle = p.name.ifBlank { "Material ID ${row.codigoBarras.take(3)}" },
                                presentation = p.presentation.ifBlank { "Pieza" },
                                timestamp = row.fechaEscaneo
                            )
                        }
                        val grouped = codes.groupBy { "${it.productTitle} (${it.presentation})" }
                            .map { (k, v) -> k to v.size }
                            .sortedByDescending { it.second }
                        ValeDetailData(
                            summary = vale,
                            headerFields = listOf(
                                "Proveedor" to it.proveedor,
                                "Polvorín" to it.polvorin,
                                "Factura / Remisión" to it.factura,
                                "Fecha" to it.fechaCreacion
                            ),
                            groupedSummary = grouped,
                            codes = codes
                        )
                    }
                }
                "ACCESORIOS", "DEV. ACCESORIOS" -> {
                    val a = db.accesorioDao().getAllVales().find { it.id == vale.id }
                    a?.let {
                        val dbItems = db.accesorioDao().getItemsForVale(it.id)
                        val cleanEmpresa = if (it.empresa.startsWith("DEV:")) it.empresa.removePrefix("DEV: ") else it.empresa
                        val codes = dbItems.mapIndexed { idx, row ->
                            ValeDetailCodeItem(
                                index = idx + 1,
                                code = row.codigoAccesorio,
                                productTitle = if (vale.tipo == "DEV. ACCESORIOS") "Accesorio Devuelto" else "Accesorio Despachado",
                                presentation = "",
                                timestamp = row.fechaEscaneo,
                                quantity = row.cantidad
                            )
                        }
                        val grouped = dbItems.groupBy { it.codigoAccesorio }
                            .map { (k, v) -> k to v.sumOf { item -> item.cantidad } }
                            .sortedByDescending { kotlin.math.abs(it.second) }
                        ValeDetailData(
                            summary = vale,
                            headerFields = listOf(
                                "Folio" to vale.folio,
                                "Empresa" to cleanEmpresa,
                                "Lugar" to it.lugar,
                                "Entregó" to it.entrego,
                                "Recibió" to it.recibio,
                                "Turno / Voladura" to "Turno ${it.turno} • Vol. ${it.voladura}",
                                "Fecha" to it.fechaCreacion
                            ),
                            groupedSummary = grouped,
                            codes = codes
                        )
                    }
                }
                "DEVOLUCION" -> {
                    val dev = db.devolucionDao().getAllDevoluciones().find { it.id == vale.id }
                    dev?.let {
                        val dbItems = db.devolucionDao().getItemsForDevolucion(it.id)
                        val codes = dbItems.mapIndexed { idx, row ->
                            val p = catalogRepo.lookupBarcode(row.codigoBarras)
                            ValeDetailCodeItem(
                                index = idx + 1,
                                code = row.codigoBarras,
                                productTitle = p.name.ifBlank { "Material ID ${row.codigoBarras.take(3)}" },
                                presentation = p.presentation.ifBlank { "Pieza" },
                                timestamp = row.fechaEscaneo
                            )
                        }
                        val grouped = codes.groupBy { "${it.productTitle} (${it.presentation})" }
                            .map { (k, v) -> k to v.size }
                            .sortedByDescending { it.second }
                        ValeDetailData(
                            summary = vale,
                            headerFields = listOf(
                                "Entrega (Mina)" to it.empleadoEntrega,
                                "Recibe (Polvorín)" to it.empleadoRecibe,
                                "Fecha" to it.fechaCreacion
                            ),
                            groupedSummary = grouped,
                            codes = codes
                        )
                    }
                }
                else -> null
            }
            if (detail != null) {
                refreshPrinters()
                detailCopiesPrinted = 0
                detailPrintStatusMessage = null
                selectedDetailData = detail
            }
        }
    }

    fun loadVales() {
        scope.launch {
            val list = mutableListOf<ValeSummaryItem>()

            // Despachos
            val despachos = db.despachoDao().getAllDespachos()
            for (d in despachos) {
                val items = db.despachoDao().getItemsForDespacho(d.id)
                list.add(
                    ValeSummaryItem(
                        id = d.id,
                        tipo = "DESPACHO",
                        folio = if (d.vale.startsWith("REF")) d.vale else "REF${d.vale}",
                        descripcion = "${d.empresa} • ${d.lugar}",
                        fecha = d.fechaCreacion,
                        totalItems = items.size
                    )
                )
            }

            // Entradas
            val entradas = db.entradaDao().getAllEntradas()
            for (e in entradas) {
                val items = db.entradaDao().getItemsForEntrada(e.id)
                list.add(
                    ValeSummaryItem(
                        id = e.id,
                        tipo = "ENTRADA",
                        folio = "PRV${e.proveedor}",
                        descripcion = "Factura: ${e.factura} • Polvorín: ${e.polvorin}",
                        fecha = e.fechaCreacion,
                        totalItems = items.size
                    )
                )
            }

            // Accesorios y Devolución de Accesorios
            val accesorios = db.accesorioDao().getAllVales()
            for (a in accesorios) {
                val items = db.accesorioDao().getItemsForVale(a.id)
                val isDev = a.empresa.startsWith("DEV:") || items.any { it.cantidad < 0 }
                val tipoLabel = if (isDev) "DEV. ACCESORIOS" else "ACCESORIOS"
                val folioFormatted = if (a.vale.startsWith("ACC")) a.vale else "ACC${a.vale}"
                val cleanDesc = if (a.empresa.startsWith("DEV:")) a.empresa.removePrefix("DEV: ") else a.empresa
                list.add(
                    ValeSummaryItem(
                        id = a.id,
                        tipo = tipoLabel,
                        folio = folioFormatted,
                        descripcion = "$cleanDesc • ${a.lugar}",
                        fecha = a.fechaCreacion,
                        totalItems = items.sumOf { it.cantidad }
                    )
                )
            }

            // Devoluciones
            val devoluciones = db.devolucionDao().getAllDevoluciones()
            for (dev in devoluciones) {
                val items = db.devolucionDao().getItemsForDevolucion(dev.id)
                list.add(
                    ValeSummaryItem(
                        id = dev.id,
                        tipo = "DEVOLUCION",
                        folio = if (dev.empleadoEntrega.startsWith("DEV")) dev.empleadoEntrega else "DEV${dev.empleadoEntrega}",
                        descripcion = "Recibe: ${dev.empleadoRecibe}",
                        fecha = dev.fechaCreacion,
                        totalItems = items.size
                    )
                )
            }

            valesList = list.sortedByDescending { it.fecha }
        }
    }

    LaunchedEffect(Unit) {
        loadVales()
    }

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null && pendingExportText != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportText!!.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                Toast.makeText(context, "Archivo consolidado guardado exitosamente", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar archivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun exportConsolidated() {
        scope.launch {
            val sb = StringBuilder()
            val toExport = if (selectedIds.isEmpty()) valesList else valesList.filter { selectedIds.contains(it.tipo to it.id) }

            for (vale in toExport) {
                when (vale.tipo) {
                    "DESPACHO" -> {
                        val d = db.despachoDao().getAllDespachos().find { it.id == vale.id }
                        if (d != null) {
                            val items = db.despachoDao().getItemsForDespacho(d.id).map {
                                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
                            }
                            val rec = DespachoRecord(d.vale, d.empresa, d.lugar, d.transportista, d.turno, d.voladura, items)
                            sb.append(FlatFileFormatter.formatDespacho(rec))
                            sb.append("\r\n")
                        }
                    }
                    "ENTRADA" -> {
                        val e = db.entradaDao().getAllEntradas().find { it.id == vale.id }
                        if (e != null) {
                            val items = db.entradaDao().getItemsForEntrada(e.id).map {
                                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                            }
                            val rec = EntradaRecord(e.proveedor, e.polvorin, e.factura, items)
                            sb.append(FlatFileFormatter.formatEntrada(rec))
                            sb.append("\r\n")
                        }
                    }
                    "ACCESORIOS", "DEV. ACCESORIOS" -> {
                        val a = db.accesorioDao().getAllVales().find { it.id == vale.id }
                        if (a != null) {
                            val items = db.accesorioDao().getItemsForVale(a.id).map {
                                AccessoryItemRecord(it.codigoAccesorio, it.cantidad, it.fechaEscaneo)
                            }
                            val isDev = a.empresa.startsWith("DEV:") || items.any { it.quantity < 0 }
                            val cleanEmpresa = if (a.empresa.startsWith("DEV:")) a.empresa.removePrefix("DEV: ") else a.empresa
                            val rec = AccesoriosRecord(a.vale, cleanEmpresa, a.lugar, a.transportista, a.turno, a.recibio, a.entrego, a.voladura, items)
                            sb.append(FlatFileFormatter.formatAccesorios(rec))
                            sb.append("\r\n")
                        }
                    }
                    "DEVOLUCION" -> {
                        val dev = db.devolucionDao().getAllDevoluciones().find { it.id == vale.id }
                        if (dev != null) {
                            val items = db.devolucionDao().getItemsForDevolucion(dev.id).map {
                                ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                            }
                            val rec = DevolucionRecord(dev.empleadoEntrega, dev.empleadoRecibe, items)
                            sb.append(FlatFileFormatter.formatDevolucion(rec))
                            sb.append("\r\n")
                        }
                    }
                }
            }

            val text = sb.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(context, "No hay vales para exportar", Toast.LENGTH_SHORT).show()
                return@launch
            }

            pendingExportText = text
            val timestamp = DateTimeUtils.currentTimestamp().replace(":", "-").replace(" ", "_")
            createFileLauncher.launch("TTEXPLO_CONSOLIDADO_$timestamp.txt")
        }
    }

    fun shareVale(vale: ValeSummaryItem) {
        scope.launch {
            val text = when (vale.tipo) {
                "DESPACHO" -> {
                    val d = db.despachoDao().getAllDespachos().find { it.id == vale.id } ?: return@launch
                    val items = db.despachoDao().getItemsForDespacho(d.id).map {
                        ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
                    }
                    FlatFileFormatter.formatDespacho(DespachoRecord(d.vale, d.empresa, d.lugar, d.transportista, d.turno, d.voladura, items))
                }
                "ENTRADA" -> {
                    val e = db.entradaDao().getAllEntradas().find { it.id == vale.id } ?: return@launch
                    val items = db.entradaDao().getItemsForEntrada(e.id).map {
                        ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                    }
                    FlatFileFormatter.formatEntrada(EntradaRecord(e.proveedor, e.polvorin, e.factura, items))
                }
                "ACCESORIOS", "DEV. ACCESORIOS" -> {
                    val a = db.accesorioDao().getAllVales().find { it.id == vale.id } ?: return@launch
                    val items = db.accesorioDao().getItemsForVale(a.id).map {
                        AccessoryItemRecord(it.codigoAccesorio, it.cantidad, it.fechaEscaneo)
                    }
                    val isDev = a.empresa.startsWith("DEV:") || items.any { it.quantity < 0 }
                    val cleanEmpresa = if (a.empresa.startsWith("DEV:")) a.empresa.removePrefix("DEV: ") else a.empresa
                    FlatFileFormatter.formatAccesorios(AccesoriosRecord(a.vale, cleanEmpresa, a.lugar, a.transportista, a.turno, a.recibio, a.entrego, a.voladura, items))
                }
                "DEVOLUCION" -> {
                    val dev = db.devolucionDao().getAllDevoluciones().find { it.id == vale.id } ?: return@launch
                    val items = db.devolucionDao().getItemsForDevolucion(dev.id).map {
                        ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                    }
                    FlatFileFormatter.formatDevolucion(DevolucionRecord(dev.empleadoEntrega, dev.empleadoRecibe, items))
                }
                else -> ""
            }

            try {
                val file = File(context.cacheDir, "${vale.folio}_${DateTimeUtils.currentTimestamp().replace(":", "-").replace(" ", "_")}.txt")
                file.writeText(text, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir Vale ${vale.folio}"))
            } catch (e: Exception) {
                Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun promptDelete(vales: List<ValeSummaryItem>) {
        if (vales.isEmpty()) return
        valesToDelete = vales
        securityCode = (1000..9999).random().toString()
        inputCode = ""
        showDeleteDialog = true
    }

    fun executeDelete() {
        scope.launch {
            for (vale in valesToDelete) {
                val referenceType = when (vale.tipo) {
                    "DESPACHO" -> InventoryReferenceType.DESPACHO
                    "ENTRADA" -> InventoryReferenceType.ENTRADA
                    "ACCESORIOS", "DEV. ACCESORIOS" -> InventoryReferenceType.ACCESORIO_VALE
                    "DEVOLUCION" -> InventoryReferenceType.DEVOLUCION
                    else -> null
                }
                if (referenceType != null) {
                    inventoryRepo.deleteValeAndReverse(
                        referenceType,
                        vale.id,
                        "Anulación por eliminación de ${vale.folio}"
                    )
                }
                selectedIds.remove(vale.tipo to vale.id)
            }
            val count = valesToDelete.size
            showDeleteDialog = false
            valesToDelete = emptyList()
            loadVales()
            Toast.makeText(context, "$count vale(s) eliminado(s) permanentemente", Toast.LENGTH_SHORT).show()
        }
    }

    val currentDetail = selectedDetailData
    if (currentDetail != null) {
        ValeHistoryDetailView(
            detail = currentDetail,
            activeProfile = activeProfile,
            totalProfiles = savedProfiles.size,
            isPrinting = printingValeKey == (currentDetail.summary.tipo to currentDetail.summary.id),
            copiesPrinted = detailCopiesPrinted,
            printStatusMessage = detailPrintStatusMessage,
            onBack = { selectedDetailData = null },
            onReprintClick = {
                reprintVale(currentDetail.summary) { outcome ->
                    if (outcome.success) {
                        detailCopiesPrinted += 1
                    }
                    detailPrintStatusMessage = outcome.message
                }
            },
            onChangePrinterClick = {
                withBluetoothPermission { showPrinterSelector = true }
            },
            onEditClick = {
                val target = currentDetail.summary
                selectedDetailData = null
                when (target.tipo) {
                    "DESPACHO" -> onEditDespacho(target.id)
                    "ENTRADA" -> onEditEntrada(target.id)
                    "ACCESORIOS" -> onEditAccesorios(target.id)
                    "DEV. ACCESORIOS" -> onEditDevolucionAccesorios(target.id)
                    "DEVOLUCION" -> onEditDevolucion(target.id)
                }
            },
            onShareClick = { shareVale(currentDetail.summary) }
        )
        if (showPrinterSelector) {
            QuickPrinterSelectorDialog(
                onSelectProfile = { selected ->
                    refreshPrinters()
                    activeProfile = selected
                    showPrinterSelector = false
                    Toast.makeText(context, "Impresora activa: ${selected.displayTitle()}", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showPrinterSelector = false }
            )
        }
        return
    }

    val typeFilterOptions = remember(valesList) {
        listOf(
            HistoryTypeFilterOption(
                key = null,
                label = "Todos",
                count = valesList.size,
                accentColor = MineSafetyYellow
            ),
            HistoryTypeFilterOption(
                key = "DESPACHO",
                label = "Salidas",
                count = valesList.count { it.tipo == "DESPACHO" },
                accentColor = MineHighExplosiveOrange
            ),
            HistoryTypeFilterOption(
                key = "ENTRADA",
                label = "Entradas",
                count = valesList.count { it.tipo == "ENTRADA" },
                accentColor = MineSuccessGreenLight
            ),
            HistoryTypeFilterOption(
                key = "ACCESORIOS_ALL",
                label = "Accesorios",
                count = valesList.count { it.tipo == "ACCESORIOS" || it.tipo == "DEV. ACCESORIOS" },
                accentColor = MineAccessoryPurpleLight
            ),
            HistoryTypeFilterOption(
                key = "DEVOLUCION",
                label = "Devoluciones",
                count = valesList.count { it.tipo == "DEVOLUCION" },
                accentColor = MineAgentCyan
            )
        )
    }

    val filteredVales = remember(valesList, selectedTypeFilter) {
        when (selectedTypeFilter) {
            null -> valesList
            "ACCESORIOS_ALL" -> valesList.filter { it.tipo == "ACCESORIOS" || it.tipo == "DEV. ACCESORIOS" }
            else -> valesList.filter { it.tipo == selectedTypeFilter }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Vales Registrados",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (selectedIds.isNotEmpty()) {
                                "${selectedIds.size} seleccionados de ${valesList.size}"
                            } else {
                                "${filteredVales.size} de ${valesList.size} registros"
                            },
                            color = if (selectedIds.isNotEmpty()) MineSafetyYellow else MineTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    val anySelected = selectedIds.isNotEmpty()
                    if (anySelected) {
                        IconButton(
                            onClick = {
                                val selectedVales = valesList.filter { selectedIds.contains(it.tipo to it.id) }
                                promptDelete(selectedVales)
                            },
                            modifier = Modifier.testTag("history_bulk_delete_button")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar selección",
                                tint = MineErrorRedLight
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (anySelected) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                filteredVales.forEach { selectedIds.add(it.tipo to it.id) }
                            }
                        },
                        enabled = filteredVales.isNotEmpty(),
                        modifier = Modifier.testTag("history_select_all_button")
                    ) {
                        Icon(
                            Icons.Default.SelectAll,
                            contentDescription = if (anySelected) "Deseleccionar todos" else "Seleccionar todos",
                            tint = if (anySelected) MineSafetyYellow else MineTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MineDarkSurface,
                    titleContentColor = MineTextPrimary,
                    navigationIconContentColor = MineTextPrimary
                )
            )
        },
        bottomBar = {
            Surface(
                color = MineDarkSurface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = { exportConsolidated() },
                    enabled = valesList.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .height(48.dp)
                        .testTag("history_export_consolidated_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineSafetyYellow,
                        contentColor = MineDarkBackground
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    AutoShrinkText(
                        text = if (selectedIds.isEmpty()) {
                            "EXPORTAR CONSOLIDADO (${valesList.size} VALES)"
                        } else {
                            "EXPORTAR SELECCIONADOS (${selectedIds.size})"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        color = MineDarkBackground,
                        targetTextSize = 13.sp,
                        minTextSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MineDarkBackground)
                .padding(padding)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            HistoryTypeFilterRow(
                options = typeFilterOptions,
                selectedKey = selectedTypeFilter,
                onSelect = { selectedTypeFilter = it }
            )

            if (filteredVales.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MineTextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (selectedTypeFilter != null) {
                                "Sin vales en esta categoría"
                            } else {
                                "No hay vales registrados todavía"
                            },
                            color = MineTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        if (selectedTypeFilter != null) {
                            TextButton(onClick = { selectedTypeFilter = null }) {
                                Text("Ver todos los vales", color = MineSafetyYellow, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("history_vales_lazy_column"),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredVales, key = { "${it.tipo}_${it.id}" }) { vale ->
                        val isSelected = selectedIds.contains(vale.tipo to vale.id)
                        ValeHistoryCard(
                            vale = vale,
                            isSelected = isSelected,
                            isPrintingThis = printingValeKey == (vale.tipo to vale.id),
                            isAnyPrinting = printingValeKey != null,
                            onToggleSelect = { checked ->
                                if (checked) {
                                    if (!selectedIds.contains(vale.tipo to vale.id)) {
                                        selectedIds.add(vale.tipo to vale.id)
                                    }
                                } else {
                                    selectedIds.remove(vale.tipo to vale.id)
                                }
                            },
                            onOpenDetails = { openValeDetails(vale) },
                            onPrint = { reprintVale(vale) },
                            onEdit = {
                                when (vale.tipo) {
                                    "DESPACHO" -> onEditDespacho(vale.id)
                                    "ENTRADA" -> onEditEntrada(vale.id)
                                    "ACCESORIOS" -> onEditAccesorios(vale.id)
                                    "DEV. ACCESORIOS" -> onEditDevolucionAccesorios(vale.id)
                                    "DEVOLUCION" -> onEditDevolucion(vale.id)
                                }
                            },
                            onShare = { shareVale(vale) },
                            onDelete = { promptDelete(listOf(vale)) }
                        )
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MineErrorRed,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = if (valesToDelete.size == 1) "Eliminar Vale ${valesToDelete.first().folio}" else "Eliminar ${valesToDelete.size} Vales Seleccionados",
                        fontWeight = FontWeight.Bold,
                        color = MineErrorRed
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (valesToDelete.size == 1) {
                                "¿Está seguro de eliminar el vale ${valesToDelete.first().folio} (${valesToDelete.first().tipo}, ${valesToDelete.first().totalItems} ítems)?"
                            } else {
                                "¿Confirma la eliminación permanente de los ${valesToDelete.size} vales seleccionados y sus productos asociados?"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MineTextSecondary
                        )

                        Text(
                            text = "Esta acción es irreversible y borrará los registros de la terminal de mina.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MineErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MineDarkBackground)
                                .border(2.dp, MineErrorRed, RoundedCornerShape(8.dp))
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "CÓDIGO DE SEGURIDAD (4 DÍGITOS)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MineTextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = securityCode.toCharArray().joinToString("  "),
                                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = MineSafetyYellow
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Escriba este número abajo para confirmar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MineTextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { str ->
                                if (str.length <= 4 && str.all { it.isDigit() }) {
                                    inputCode = str
                                }
                            },
                            label = { Text("Escriba el código de 4 dígitos") },
                            placeholder = { Text("Ej. $securityCode") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (inputCode == securityCode) MineSuccessGreen else MineSafetyYellow,
                                unfocusedBorderColor = MineBorderGray,
                                focusedTextColor = MineTextPrimary,
                                unfocusedTextColor = MineTextPrimary
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { executeDelete() },
                        enabled = (inputCode == securityCode),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MineErrorRed,
                            disabledContainerColor = MineDarkBackground,
                            disabledContentColor = MineTextMuted
                        )
                    ) {
                        Text(
                            text = "ELIMINAR DEFINITIVAMENTE",
                            fontWeight = FontWeight.Bold,
                            color = if (inputCode == securityCode) MineTextPrimary else MineTextMuted
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar", color = MineTextSecondary)
                    }
                }
            )
        }
        if (showPrinterSelector) {
            QuickPrinterSelectorDialog(
                onSelectProfile = { selected ->
                    refreshPrinters()
                    activeProfile = selected
                    showPrinterSelector = false
                    Toast.makeText(context, "Impresora activa: ${selected.displayTitle()}", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showPrinterSelector = false }
            )
        }
    }
}
