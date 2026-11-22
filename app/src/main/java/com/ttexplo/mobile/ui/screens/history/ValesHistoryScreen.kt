package com.ttexplo.mobile.ui.screens.history

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.domain.export.FlatFileFormatter
import com.ttexplo.mobile.domain.inventory.InventoryReferenceType
import com.ttexplo.mobile.domain.model.*
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.ui.common.AutoShrinkText
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

    var valesList by remember { mutableStateOf<List<ValeSummaryItem>>(emptyList()) }
    val selectedIds = remember { mutableStateListOf<Pair<String, Long>>() }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var valesToDelete by remember { mutableStateOf<List<ValeSummaryItem>>(emptyList()) }
    var securityCode by remember { mutableStateOf("") }
    var inputCode by remember { mutableStateOf("") }

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
                when (vale.tipo) {
                    "DESPACHO" -> {
                        db.despachoDao().deleteItemsForDespacho(vale.id)
                        db.despachoDao().deleteDespacho(vale.id)
                        inventoryRepo.reverseReferenceEvents(
                            InventoryReferenceType.DESPACHO,
                            vale.id,
                            "Anulación por eliminación de ${vale.folio}"
                        )
                    }
                    "ENTRADA" -> {
                        db.entradaDao().deleteItemsForEntrada(vale.id)
                        db.entradaDao().deleteEntrada(vale.id)
                        inventoryRepo.reverseReferenceEvents(
                            InventoryReferenceType.ENTRADA,
                            vale.id,
                            "Anulación por eliminación de ${vale.folio}"
                        )
                    }
                    "ACCESORIOS", "DEV. ACCESORIOS" -> {
                        db.accesorioDao().deleteItemsForVale(vale.id)
                        db.accesorioDao().deleteVale(vale.id)
                        inventoryRepo.reverseReferenceEvents(
                            InventoryReferenceType.ACCESORIO_VALE,
                            vale.id,
                            "Anulación por eliminación de ${vale.folio}"
                        )
                    }
                    "DEVOLUCION" -> {
                        db.devolucionDao().deleteItemsForDevolucion(vale.id)
                        db.devolucionDao().deleteDevolucion(vale.id)
                        inventoryRepo.reverseReferenceEvents(
                            InventoryReferenceType.DEVOLUCION,
                            vale.id,
                            "Anulación por eliminación de ${vale.folio}"
                        )
                    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Vales", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { exportConsolidated() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineSafetyYellow,
                        contentColor = MineDarkBackground
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedIds.isEmpty()) {
                            "GUARDAR ARCHIVO CONSOLIDADO (${valesList.size} VALES)"
                        } else {
                            "GUARDAR ARCHIVO CONSOLIDADO (${selectedIds.size} SELECCIONADOS)"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Barra de conteo y seleccionar todo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VALES REGISTRADOS (${valesList.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MineSafetyYellow,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val anySelected = selectedIds.isNotEmpty()
                    TextButton(onClick = {
                        if (anySelected) {
                            selectedIds.clear()
                        } else {
                            selectedIds.clear()
                            valesList.forEach { selectedIds.add(it.tipo to it.id) }
                        }
                    }) {
                        Text(
                            text = if (anySelected) "Deseleccionar" else "Seleccionar todos",
                            color = MineTextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    if (anySelected) {
                        IconButton(onClick = {
                            val selectedVales = valesList.filter { selectedIds.contains(it.tipo to it.id) }
                            promptDelete(selectedVales)
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar selección",
                                tint = MineErrorRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(valesList) { vale ->
                    val isSelected = selectedIds.contains(vale.tipo to vale.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) {
                                    selectedIds.remove(vale.tipo to vale.id)
                                } else {
                                    selectedIds.add(vale.tipo to vale.id)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MineDarkSurface.copy(alpha = 0.9f) else MineDarkSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MineSafetyYellow else MineBorderGray
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedIds.add(vale.tipo to vale.id)
                                    } else {
                                        selectedIds.remove(vale.tipo to vale.id)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MineSafetyYellow,
                                    uncheckedColor = MineTextMuted,
                                    checkmarkColor = MineDarkBackground
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val (badgeColor, textColor) = when (vale.tipo) {
                                        "DESPACHO" -> MineHighExplosiveOrange to MineDarkBackground
                                        "ENTRADA" -> MineSuccessGreen to MineDarkBackground
                                        "ACCESORIOS" -> MineAccessoryPurple to MineTextPrimary
                                        "DEV. ACCESORIOS" -> MineAgentCyan to MineDarkBackground
                                        "DEVOLUCION" -> MineSafetyYellow to MineDarkBackground
                                        else -> MineTextMuted to MineDarkBackground
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeColor)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = vale.tipo,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }

                                    AutoShrinkText(
                                        text = vale.folio,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MineTextPrimary,
                                        initialTextSize = 16.sp,
                                        minTextSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                AutoShrinkText(
                                    text = vale.descripcion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MineTextSecondary,
                                    initialTextSize = 13.sp,
                                    minTextSize = 11.sp,
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                AutoShrinkText(
                                    text = "Fecha: ${vale.fecha} • Cantidad: ${vale.totalItems} ítems",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MineTextMuted,
                                    initialTextSize = 12.sp,
                                    minTextSize = 10.sp,
                                    maxLines = 1
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        when (vale.tipo) {
                                            "DESPACHO" -> onEditDespacho(vale.id)
                                            "ENTRADA" -> onEditEntrada(vale.id)
                                            "ACCESORIOS" -> onEditAccesorios(vale.id)
                                            "DEV. ACCESORIOS" -> onEditDevolucionAccesorios(vale.id)
                                            "DEVOLUCION" -> onEditDevolucion(vale.id)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Editar Vale",
                                        tint = MineSafetyYellow
                                    )
                                }

                                IconButton(
                                    onClick = { shareVale(vale) }
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Compartir",
                                        tint = MineTextSecondary
                                    )
                                }

                                IconButton(
                                    onClick = { promptDelete(listOf(vale)) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar",
                                        tint = MineErrorRed
                                    )
                                }
                            }
                        }
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}
