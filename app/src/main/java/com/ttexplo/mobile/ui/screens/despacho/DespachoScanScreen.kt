package com.ttexplo.mobile.ui.screens.despacho

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.DummyExplosivesScannerBar
import com.ttexplo.mobile.ui.common.ExitScanConfirmDialog
import com.ttexplo.mobile.ui.common.ProductEditDialog
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DespachoScanScreen(
    vale: String,
    empresa: String,
    lugar: String,
    transportista: String,
    turno: String,
    voladura: String,
    despachoId: Long = 0L,
    viewModel: DespachoViewModel,
    onNavigateBack: () -> Unit = {},
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var manualInput by remember { mutableStateOf("") }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var catalogAddInitialId by remember { mutableStateOf<String?>(null) }
    var showPrinterPickerDialog by remember { mutableStateOf(false) }
    var preferredPrinterName by remember { mutableStateOf(viewModel.settingsRepository.getPreferredPrinterName()) }
    var itemToDelete by remember { mutableStateOf<BarcodeItem?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.scannedItems.isNotEmpty()) {
        showExitConfirm = true
    }

    LaunchedEffect(Unit) {
        preferredPrinterName = viewModel.settingsRepository.getPreferredPrinterName()
    }

    val btPrintLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else true
        if (granted) {
            viewModel.printTicket { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        } else {
            Toast.makeText(context, "Se requiere permiso de Bluetooth para imprimir", Toast.LENGTH_LONG).show()
        }
    }

    fun requestPrint() {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPrintLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            val paired = viewModel.printerManager.getPairedPrinters()
            if (paired.isEmpty()) {
                Toast.makeText(context, "No hay dispositivos Bluetooth vinculados en Android. Conéctelo en Ajustes Bluetooth.", Toast.LENGTH_LONG).show()
                return
            }

            val currentMac = viewModel.settingsRepository.getPreferredPrinterMac()
            if (currentMac.isBlank() && paired.size > 1) {
                showPrinterPickerDialog = true
            } else {
                if (currentMac.isBlank() && paired.size == 1) {
                    val single = paired.first()
                    val sName = try { single.name ?: single.address } catch (_: Exception) { single.address }
                    viewModel.savePreferredPrinter(single.address, sName)
                    preferredPrinterName = sName
                }
                viewModel.printTicket { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(despachoId) {
        if (despachoId > 0L) {
            viewModel.loadExistingDespacho(despachoId)
        }
    }

    LaunchedEffect(vale, empresa, lugar, transportista, turno, voladura) {
        if (despachoId <= 0L) {
            viewModel.updateHeader(vale, empresa, lugar, transportista, turno, voladura)
        }
    }

    // Register hardware Zebra SE4710 broadcast receiver
    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scannedBarcode ->
            viewModel.onBarcodeScanned(scannedBarcode)
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose {
            ZebraScannerReceiver.unregister(context, receiver)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Vale: ${if (vale.startsWith("REF")) vale else "REF$vale"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MineTextPrimary
                        )
                        Text(
                            text = "$empresa • $lugar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MineSafetyYellow
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { requestPrint() },
                        enabled = !uiState.isPrinting && uiState.scannedItems.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Imprimir Ticket",
                            tint = if (uiState.scannedItems.isNotEmpty()) MineSafetyYellow else MineTextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MineDarkBackground)
            )
        },
        containerColor = MineDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Draft Restored Banner (Crash / Power-off recovery)
            if (uiState.wasRestoredFromDraft) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MineHighExplosiveOrange.copy(alpha = 0.15f))
                        .border(1.dp, MineHighExplosiveOrange, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MineHighExplosiveOrange, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Vale recuperado (${uiState.scannedItems.size} ítems)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MineHighExplosiveOrangeLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // CONTADOR COMPACTO OPTIMIZADO (TOTAL, CAJAS, SACOS, CARTUCHOS)
            val totalCajas = uiState.scannedItems.count { it.empaque == TipoEmpaque.CAJA }
            val totalSacos = uiState.scannedItems.count { it.empaque == TipoEmpaque.SACO }
            val totalCartuchos = uiState.scannedItems.count { it.empaque == TipoEmpaque.PIEZA }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Turno: $turno • Vol: $voladura",
                            style = MaterialTheme.typography.labelSmall,
                            color = MineTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.scannedItems.isEmpty()) "Listo para pistolear" else "${uiState.scannedItems.size} despachados",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.scannedItems.isEmpty()) MineSuccessGreenLight else MineSafetyYellow,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 4 Contadores en 1 sola fila compacta con colores normalizados
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactDespachoMetric(
                            modifier = Modifier.weight(1f),
                            label = "TOTAL",
                            count = uiState.scannedItems.size,
                            borderColor = MineBorderGray,
                            countColor = EmpaqueTotalColor
                        )
                        CompactDespachoMetric(
                            modifier = Modifier.weight(1f),
                            label = "CAJAS",
                            count = totalCajas,
                            borderColor = EmpaqueCajaColor,
                            countColor = EmpaqueCajaColor
                        )
                        CompactDespachoMetric(
                            modifier = Modifier.weight(1f),
                            label = "SACOS",
                            count = totalSacos,
                            borderColor = EmpaqueSacoColor,
                            countColor = EmpaqueSacoColor
                        )
                        CompactDespachoMetric(
                            modifier = Modifier.weight(1f),
                            label = "CARTUCHOS",
                            count = totalCartuchos,
                            borderColor = EmpaqueCartuchoColor,
                            countColor = EmpaqueCartuchoColor
                        )
                    }
                }
            }

            // 1. CASILLA DE ESCANEO COMPACTA
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                placeholder = { Text("Código de barras o pistolear...", fontSize = 13.sp, color = MineTextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (manualInput.isNotBlank()) {
                            viewModel.onBarcodeScanned(manualInput.trim())
                            manualInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineHighExplosiveOrange,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedPlaceholderColor = MineTextMuted
                )
            )

            // Simulador de escaneo (MODO PRUEBA plegable)
            DummyExplosivesScannerBar(
                onSimulateScan = { code -> viewModel.onBarcodeScanned(code) },
                lastValidCode = uiState.scannedItems.firstOrNull()?.code,
                allowAccessories = false
            )

            // 2. BANNER COMPACTO DE RESULTADO
            uiState.latestScanResult?.let { result ->
                val isError = !result.isValid
                val isDup = result.isDuplicate
                val bannerBg = when {
                    isError -> MineErrorRed.copy(alpha = 0.2f)
                    isDup -> MineWarningAmber.copy(alpha = 0.2f)
                    else -> MineSuccessGreen.copy(alpha = 0.2f)
                }
                val bannerBorder = when {
                    isError -> MineErrorRed
                    isDup -> MineWarningAmber
                    else -> MineSuccessGreen
                }
                val bannerTint = when {
                    isError -> MineErrorRed
                    isDup -> MineWarningAmber
                    else -> MineSuccessGreenLight
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isError || isDup -> Icons.Default.Warning
                            else -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = bannerTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MineTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (result.unknownId != null) {
                        Button(
                            onClick = {
                                catalogAddInitialId = result.unknownId
                                showCatalogDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow, contentColor = MineDarkBackground),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+ Agregar ID", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }

            var selectedMarkerKey by remember { mutableStateOf<Triple<String, String, TipoEmpaque>?>(null) }

            val filteredItems = remember(uiState.scannedItems, selectedMarkerKey) {
                if (selectedMarkerKey == null) {
                    uiState.scannedItems
                } else {
                    val (name, pres, emp) = selectedMarkerKey!!
                    uiState.scannedItems.filter {
                        it.productInfo.name == name && it.productInfo.presentation == pres && it.empaque == emp
                    }
                }
            }

            // 3. DESGLOSE POR PRODUCTO (CUADRÍCULA DE 2 COLUMNAS DINÁMICA E INTERACTIVA)
            if (uiState.markers.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 145.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.markers.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { marker ->
                                val key = Triple(marker.productName, marker.presentation, marker.empaque)
                                val isSelected = selectedMarkerKey == key
                                Box(modifier = Modifier.weight(1f)) {
                                    DespachoProductCounterCard(
                                        marker = marker,
                                        isSelected = isSelected,
                                        onClick = {
                                            selectedMarkerKey = if (isSelected) null else key
                                        }
                                    )
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Encabezado de lecturas con filtro interactivo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedMarkerKey != null) {
                    val selectedMarker = uiState.markers.firstOrNull {
                        it.productName == selectedMarkerKey!!.first &&
                        it.presentation == selectedMarkerKey!!.second &&
                        it.empaque == selectedMarkerKey!!.third
                    }
                    Text(
                        text = "FILTRADO: ${selectedMarker?.productName ?: ""} (${filteredItems.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineSafetyYellow,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "[VER TODOS (${uiState.scannedItems.size})]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineAgentCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { selectedMarkerKey = null }
                            .padding(4.dp)
                    )
                } else {
                    Text(
                        text = "HISTORIAL DE LECTURAS (${uiState.scannedItems.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toca un producto para filtrar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineTextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            // Scanned Items List (muestra filteredItems, se ajusta dinámicamente)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredItems, key = { it.code + it.timestamp }) { item ->
                    DespachoScannedItemRow(
                        item = item,
                        onDelete = { itemToDelete = item }
                    )
                }
            }

            // Barra de estado de impresora rápida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showPrinterPickerDialog = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = if (preferredPrinterName.isNotBlank()) MineSafetyYellow else MineWarningAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (preferredPrinterName.isNotBlank()) "Impresora: $preferredPrinterName (Cambiar)" else "Seleccionar Impresora",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (preferredPrinterName.isNotBlank()) MineTextSecondary else MineSafetyYellow,
                        textDecoration = TextDecoration.Underline
                    )
                }

                if (uiState.isPrinting) {
                    Text(
                        text = "Enviando ticket...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MineSafetyYellow
                    )
                }
            }

            // Action Buttons: Print Ticket & Finish
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { requestPrint() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !uiState.isPrinting && uiState.scannedItems.isNotEmpty(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MineSafetyYellow),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    AutoShrinkText(
                        text = if (uiState.isPrinting) "Imprimiendo..." else "Imprimir Ticket",
                        fontWeight = FontWeight.Bold,
                        targetTextSize = 12.sp,
                        minTextSize = 9.sp,
                        color = MineSafetyYellow,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = {
                        viewModel.finishAndSave(onFinished)
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MineSuccessGreen)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = MineTextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Guardar Vale (${uiState.scannedItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MineTextPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
    ScanFlashOverlay(
        event = uiState.latestFlashEvent,
        enabled = viewModel.isVisualFlashEnabled()
    )
    }

    if (showPrinterPickerDialog) {
        val pairedDevices = remember { viewModel.printerManager.getPairedPrinters() }
        AlertDialog(
            onDismissRequest = { showPrinterPickerDialog = false },
            title = {
                Text("Seleccionar Impresora Bluetooth", color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                if (pairedDevices.isEmpty()) {
                    Text(
                        "No se detectaron dispositivos Bluetooth vinculados en Android.\n\nPor favor empareje la impresora térmica en la configuración de Bluetooth de su teléfono primero.",
                        color = MineTextSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(pairedDevices) { device ->
                            val devName = try { device.name ?: device.address } catch (_: Exception) { device.address }
                            val isSelected = device.address.equals(viewModel.settingsRepository.getPreferredPrinterMac(), ignoreCase = true)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.savePreferredPrinter(device.address, devName)
                                        preferredPrinterName = devName
                                        showPrinterPickerDialog = false
                                        // Mandar a imprimir de inmediato con la impresora seleccionada
                                        viewModel.printTicket { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) MineSafetyYellow.copy(alpha = 0.15f) else MineDarkSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MineSafetyYellow else MineBorderGray),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(devName, fontWeight = FontWeight.Bold, color = MineTextPrimary, fontSize = 14.sp)
                                        Text(device.address, color = MineTextMuted, fontSize = 11.sp)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MineSafetyYellow)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterPickerDialog = false }) {
                    Text("Cerrar", color = MineSafetyYellow)
                }
            },
            containerColor = MineDarkBackground
        )
    }

    if (showCatalogDialog) {
        ProductEditDialog(
            initialId = catalogAddInitialId,
            initial = null,
            onDismiss = { showCatalogDialog = false },
            onSave = { entity ->
                viewModel.registerProductInCatalog(entity)
                showCatalogDialog = false
            }
        )
    }

    itemToDelete?.let { target ->
        TwoStepDeleteDialog(
            itemDescription = "${target.productInfo.name} (${target.productInfo.presentation})",
            itemCode = "Cód: ${target.code}",
            onConfirmDelete = {
                viewModel.deleteItem(target)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    if (showExitConfirm) {
        ExitScanConfirmDialog(
            itemCount = uiState.scannedItems.size,
            savedAsDraft = true,
            onConfirmExit = {
                showExitConfirm = false
                onNavigateBack()
            },
            onDismiss = { showExitConfirm = false }
        )
    }
}

@Composable
fun CompactDespachoMetric(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    borderColor: Color,
    countColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MineDarkBackground)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = countColor,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MineTextMuted,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DespachoProductCounterCard(
    marker: ProductMarker,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    // Colores normalizados según el tipo de empaque:
    // CAJA: Amarillo Oro (#FFD600)
    // SACO: Cian Eléctrico (#00E5FF)
    // CARTUCHO / PIEZA: Coral / Naranja Rojizo (#FF916F)
    // ACCESORIO: Morado (#BA68C8)
    val empaqueColor = when (marker.empaque) {
        TipoEmpaque.CAJA -> EmpaqueCajaColor
        TipoEmpaque.SACO -> EmpaqueSacoColor
        TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
        TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
        else -> MineBorderGray
    }
    val empaqueLabel = when (marker.empaque) {
        TipoEmpaque.SACO -> "Sacos"
        TipoEmpaque.PIEZA -> "Cartuchos"
        else -> "Cajas"
    }

    val borderWidth = if (isSelected) 2.dp else 1.dp
    val borderColor = if (isSelected) empaqueColor else empaqueColor.copy(alpha = 0.7f)
    val cardBg = if (isSelected) empaqueColor.copy(alpha = 0.18f) else MineDarkSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(cardBg)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge de conteo siempre a la izquierda con el color de su empaque
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(empaqueColor)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${marker.count}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MineDarkBackground
                )
                Text(
                    text = empaqueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MineDarkBackground,
                    fontSize = 9.sp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = marker.productName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) empaqueColor else MineTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = if (marker.presentation.isNotBlank()) marker.presentation else marker.type.label,
                style = MaterialTheme.typography.labelSmall,
                color = MineTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DespachoScannedItemRow(
    item: BarcodeItem,
    onDelete: () -> Unit
) {
    val empaqueColor = when (item.empaque) {
        TipoEmpaque.CAJA -> EmpaqueCajaColor
        TipoEmpaque.SACO -> EmpaqueSacoColor
        TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
        TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
        else -> MineBorderGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkSurface)
            .border(1.dp, empaqueColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(empaqueColor.copy(alpha = 0.2f))
                        .border(0.5.dp, empaqueColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.empaque.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = empaqueColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                AutoShrinkText(
                    text = item.productInfo.name,
                    initialTextSize = 13.sp,
                    minTextSize = 9.sp,
                    color = MineTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            AutoShrinkText(
                text = "Medida: ${item.productInfo.presentation} • ${item.productInfo.type.label}",
                initialTextSize = 11.sp,
                minTextSize = 8.sp,
                color = MineTextSecondary,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
            AutoShrinkText(
                text = "Cód: ${item.code}",
                initialTextSize = 11.sp,
                minTextSize = 8.sp,
                color = MineTextSecondary,
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.timestamp.substringAfter(" "),
                style = MaterialTheme.typography.bodyMedium,
                color = MineTextMuted,
                fontSize = 12.sp
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MineErrorRed
                )
            }
        }
    }
}
