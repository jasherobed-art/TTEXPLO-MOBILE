package com.ttexplo.mobile.ui.screens.entradas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.domain.scanner.correctBarcodeDate
import com.ttexplo.mobile.printer.PrinterProfile
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AlwaysActiveBarcodeField
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.CompactScanMetricsRibbon
import com.ttexplo.mobile.ui.common.DateCorrectionDialog
import com.ttexplo.mobile.ui.common.DuplicateBadgeChip
import com.ttexplo.mobile.ui.common.DummyExplosivesScannerBar
import com.ttexplo.mobile.ui.common.ExitScanConfirmDialog
import com.ttexplo.mobile.ui.common.HorizontalProductFilterRow
import com.ttexplo.mobile.ui.common.ProductEditDialog
import com.ttexplo.mobile.ui.common.QuickPrinterSelectorDialog
import com.ttexplo.mobile.ui.common.SavedValeReprintDialog
import com.ttexplo.mobile.ui.common.ScanFilterKeys
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.common.buildExplosiveFilterChips
import com.ttexplo.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntradasScanScreen(
    proveedor: String,
    polvorin: String,
    factura: String,
    entradaId: Long = 0L,
    viewModel: EntradasViewModel,
    onNavigateBack: () -> Unit = {},
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var manualInput by remember { mutableStateOf("") }
    var isHeaderExpanded by remember { mutableStateOf(true) }
    var lastObservedCount by remember { mutableStateOf(0) }
    val barcodeFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var showCatalogDialog by remember { mutableStateOf(false) }
    var catalogAddInitialId by remember { mutableStateOf<String?>(null) }

    var savedProfiles by remember { mutableStateOf(viewModel.settingsRepository.getPrinterProfiles()) }
    var activeProfile by remember { mutableStateOf(viewModel.settingsRepository.getActivePrinterProfile()) }
    var showPrinterPickerDialog by remember { mutableStateOf(false) }
    var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showSavedReprintDialog by remember { mutableStateOf(false) }
    var copiesPrinted by remember { mutableStateOf(0) }

    var itemToDelete by remember { mutableStateOf<BarcodeItem?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var selectedFilterKey by remember { mutableStateOf<String?>(null) }
    var lastHandledDuplicateEventId by remember { mutableStateOf<Long?>(null) }
    var dateCorrectionTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val anyDialogOpen = showSavedReprintDialog || showPrinterPickerDialog || showCatalogDialog || itemToDelete != null || showExitConfirm || dateCorrectionTarget != null

    fun refreshPrinters() {
        savedProfiles = viewModel.settingsRepository.getPrinterProfiles()
        activeProfile = viewModel.settingsRepository.getActivePrinterProfile()
    }

    BackHandler(enabled = uiState.scannedItems.isNotEmpty() && !showSavedReprintDialog) {
        showExitConfirm = true
    }

    LaunchedEffect(Unit) {
        refreshPrinters()
        runCatching { barcodeFocusRequester.requestFocus() }
    }

    LaunchedEffect(anyDialogOpen) {
        if (!anyDialogOpen) {
            runCatching { barcodeFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(uiState.scannedItems.size) {
        val currentSize = uiState.scannedItems.size
        if (currentSize > 0 && currentSize != lastObservedCount) {
            isHeaderExpanded = false
            if (currentSize > lastObservedCount) {
                runCatching { listState.animateScrollToItem(0) }
            }
        }
        lastObservedCount = currentSize
        if (!anyDialogOpen) {
            runCatching { barcodeFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(listState.isScrollInProgress, listState.firstVisibleItemIndex) {
        if ((listState.isScrollInProgress || listState.firstVisibleItemIndex > 0) && isHeaderExpanded) {
            isHeaderExpanded = false
        }
    }

    val btPrintLauncher = rememberLauncherForActivityResult(
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
            Toast.makeText(context, "Se requiere permiso de Bluetooth para imprimir", Toast.LENGTH_LONG).show()
        }
    }

    fun withBluetoothPermission(action: () -> Unit) {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingBtAction = action
            btPrintLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            action()
        }
    }

    fun executePrint(profileOverride: PrinterProfile? = null, trackCopy: Boolean = false) {
        withBluetoothPermission {
            val target = profileOverride ?: activeProfile
            if (target == null && viewModel.printerManager.getPairedPrinters().size > 1) {
                showPrinterPickerDialog = true
                return@withBluetoothPermission
            }
            viewModel.printTicket(profile = target) { ok, msg ->
                if (ok && trackCopy) copiesPrinted += 1
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(entradaId) {
        if (entradaId > 0L) {
            viewModel.loadExistingEntrada(entradaId)
        }
    }

    LaunchedEffect(proveedor, polvorin, factura) {
        if (entradaId <= 0L) {
            viewModel.updateHeader(proveedor, polvorin, factura)
        }
    }

    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scannedBarcode ->
            if (dateCorrectionTarget == null) {
                isHeaderExpanded = false
                viewModel.onBarcodeScanned(scannedBarcode)
            }
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose {
            ZebraScannerReceiver.unregister(context, receiver)
        }
    }

    LaunchedEffect(uiState.latestScanResult) {
        val result = uiState.latestScanResult
        val invalidDate = result?.invalidDate
        if (result != null && invalidDate != null) {
            dateCorrectionTarget = result.rawBarcode to invalidDate
        }
    }

    val effProv = uiState.proveedor.ifEmpty { proveedor }
    val effPolv = uiState.polvorin.ifEmpty { polvorin }
    val effFact = uiState.factura.ifEmpty { factura }

    val totalCajas = uiState.scannedItems.count { it.empaque == TipoEmpaque.CAJA || it.code.length == 30 }
    val totalSacos = uiState.scannedItems.count { it.empaque == TipoEmpaque.SACO || it.code.length == 18 }
    val totalCartuchos = uiState.scannedItems.count { it.empaque == TipoEmpaque.PIEZA || it.code.length in 16..17 }

    val filterChips = remember(uiState.scannedItems) {
        buildExplosiveFilterChips(
            items = uiState.scannedItems,
            includeAccessoriesType = false,
            useIdKey = true
        )
    }

    val filteredItems = remember(uiState.scannedItems, uiState.duplicateReads, selectedFilterKey) {
        (uiState.duplicateReads + uiState.scannedItems).filter { item ->
            ScanFilterKeys.matchesBarcodeItem(item, selectedFilterKey, useIdKey = true)
        }
    }

    val codeFrequencies = remember(uiState.scannedItems) {
        uiState.scannedItems.groupingBy { it.code }.eachCount()
    }

    // Al detectar un código duplicado, garantiza visibilidad en LazyColumn y desplaza hasta la fila
    LaunchedEffect(uiState.lastDuplicateAlert?.eventId, filteredItems) {
        val alert = uiState.lastDuplicateAlert ?: return@LaunchedEffect
        if (lastHandledDuplicateEventId == alert.eventId) return@LaunchedEffect

        isHeaderExpanded = false
        val indexInFiltered = filteredItems.indexOfFirst { it.code == alert.code }
        if (indexInFiltered >= 0) {
            lastHandledDuplicateEventId = alert.eventId
            runCatching { listState.animateScrollToItem(indexInFiltered) }
            if (!anyDialogOpen) {
                runCatching { barcodeFocusRequester.requestFocus() }
            }
        } else if (selectedFilterKey != null && uiState.scannedItems.any { it.code == alert.code }) {
            selectedFilterKey = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            AutoShrinkText(
                                text = "Entradas: Polvorín $effPolv",
                                targetTextSize = 15.sp,
                                minTextSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MineTextPrimary
                            )
                            AutoShrinkText(
                                text = "Prov: $effProv • Fact: $effFact",
                                targetTextSize = 12.sp,
                                minTextSize = 9.sp,
                                color = MineSafetyYellow
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { executePrint() },
                            enabled = !uiState.isPrinting && uiState.scannedItems.isNotEmpty(),
                            modifier = Modifier.testTag("entradas_topbar_print_button")
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
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (uiState.wasRestoredFromDraft) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MineHighExplosiveOrange.copy(alpha = 0.15f))
                            .border(1.dp, MineHighExplosiveOrange, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MineHighExplosiveOrange, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Vale en progreso recuperado (${uiState.scannedItems.size} ítems)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MineHighExplosiveOrangeLight,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }

                // 1. CINTA COMPACTA SUPERIOR
                CompactScanMetricsRibbon(
                    totalCount = uiState.scannedItems.size,
                    cajasCount = totalCajas,
                    sacosCount = totalSacos,
                    cartuchosCount = totalCartuchos,
                    accentColor = MineSafetyYellow,
                    isPanelExpanded = isHeaderExpanded,
                    onTogglePanel = { isHeaderExpanded = !isHeaderExpanded }
                )

                // 2. ENCABEZADO COLAPSABLE AUTOMÁTICAMENTE AL DETECTAR ACTIVIDAD
                AnimatedVisibility(
                    visible = isHeaderExpanded,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                    exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(160))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                            border = BorderStroke(1.dp, MineBorderGray),
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
                                    AutoShrinkText(
                                        text = "Factura: $effFact • Prov: $effProv",
                                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                                        color = MineTextSecondary,
                                        targetTextSize = 12.sp,
                                        minTextSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (uiState.scannedItems.isEmpty()) "Listo para escanear" else "${uiState.scannedItems.size} ingresados",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.scannedItems.isEmpty()) MineSuccessGreenLight else MineSafetyYellow,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CompactMetric(Modifier.weight(1f), "TOTAL", uiState.scannedItems.size, MineBorderGray, EmpaqueTotalColor)
                                    CompactMetric(Modifier.weight(1f), "CAJAS", totalCajas, EmpaqueCajaColor, EmpaqueCajaColor)
                                    CompactMetric(Modifier.weight(1f), "SACOS", totalSacos, EmpaqueSacoColor, EmpaqueSacoColor)
                                    CompactMetric(Modifier.weight(1f), "CARTUCHOS", totalCartuchos, EmpaqueCartuchoColor, EmpaqueCartuchoColor)
                                }
                            }
                        }

                        if (com.ttexplo.mobile.license.LicenseManager.isDemoModeAllowed(androidx.compose.ui.platform.LocalContext.current)) {
                            DummyExplosivesScannerBar(
                                onSimulateScan = { code ->
                                    isHeaderExpanded = false
                                    viewModel.onBarcodeScanned(code)
                                },
                                lastValidCode = uiState.scannedItems.firstOrNull()?.code,
                                allowAccessories = false
                            )
                        }
                    }
                }

                // 3. CASILLA DE CAPTURA DE CÓDIGOS DE BARRAS (SIEMPRE VISIBLE Y ACTIVA)
                AlwaysActiveBarcodeField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    onSubmitBarcode = { code ->
                        if (dateCorrectionTarget == null) viewModel.onBarcodeScanned(code)
                    },
                    accentColor = MineSafetyYellow,
                    placeholderText = "Escanear o escribir código + ENTER...",
                    focusRequester = barcodeFocusRequester,
                    testTagName = "entradas_manual_barcode_input",
                    onActivityDetected = {
                        if (isHeaderExpanded) isHeaderExpanded = false
                    }
                )

                // Banner compacto de resultado
                uiState.latestScanResult?.let { result ->
                    val isError = !result.isValid
                    val isDup = result.isDuplicate
                    val bannerBg = when {
                        isError || isDup -> MineErrorRed.copy(alpha = 0.24f)
                        else -> MineSuccessGreen.copy(alpha = 0.2f)
                    }
                    val bannerBorder = when {
                        isError || isDup -> MineErrorRed
                        else -> MineSuccessGreen
                    }
                    val bannerTint = when {
                        isError || isDup -> MineErrorRedLight
                        else -> MineSuccessGreenLight
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(bannerBg)
                            .border(1.dp, bannerBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isError || isDup) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = bannerTint,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MineTextPrimary,
                            fontSize = 11.sp,
                            maxLines = 2,
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
                                Text(stringResource(R.string.scan_add_id), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // 2. FILTROS HORIZONTALES POR PRODUCTO (1 sola línea visible)
                HorizontalProductFilterRow(
                    totalCount = uiState.scannedItems.size,
                    chips = filterChips,
                    selectedKey = selectedFilterKey,
                    accentColor = MineSafetyYellow,
                    onSelectKey = { selectedFilterKey = it }
                )

                // Encabezado del listado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedFilterKey != null) {
                            "FILTRADOS: ${filteredItems.size} DE ${uiState.scannedItems.size}"
                        } else {
                            "CÓDIGOS CAPTURADOS (${uiState.scannedItems.size})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedFilterKey != null) MineSafetyYellow else MineTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedFilterKey != null) {
                        Text(
                            text = stringResource(R.string.scan_view_all),
                            style = MaterialTheme.typography.labelSmall,
                            color = MineAgentCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { selectedFilterKey = null }
                        )
                    }
                }

                // 5. LISTADO DE CÓDIGOS ESCANEADOS
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("entradas_codes_lazy_column"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedFilterKey != null) {
                                        "Sin códigos para este filtro de tipo/producto"
                                    } else {
                                        "Casilla activa: dispare el lector o escriba un código"
                                    },
                                    color = MineTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        items(filteredItems) { item ->
                            val dupAttempts = uiState.duplicateAttemptCounts[item.code] ?: 0
                            val isDuplicateHighlighted = dupAttempts > 0 || (codeFrequencies[item.code] ?: 0) > 1
                            EntradaScannedItemCard(
                                item = item,
                                isDuplicateHighlighted = isDuplicateHighlighted,
                                duplicateAttemptCount = dupAttempts.coerceAtLeast(1),
                                onDelete = { itemToDelete = item }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { executePrint() },
                        enabled = !uiState.isPrinting && uiState.scannedItems.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("entradas_print_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MineSafetyYellow),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        AutoShrinkText(
                            text = stringResource(if (uiState.isPrinting) R.string.scan_printing else R.string.scan_print),
                            fontWeight = FontWeight.Bold,
                            targetTextSize = 12.sp,
                            minTextSize = 9.sp,
                            color = MineSafetyYellow
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.finishAndSave {
                                refreshPrinters()
                                copiesPrinted = 0
                                showSavedReprintDialog = true
                            }
                        },
                        enabled = uiState.scannedItems.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("entradas_save_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MineSuccessGreen,
                            contentColor = MineTextPrimary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        AutoShrinkText(
                            text = stringResource(R.string.scan_save_entrada, uiState.scannedItems.size),
                            fontWeight = FontWeight.Bold,
                            targetTextSize = 13.sp,
                            minTextSize = 10.sp,
                            color = MineTextPrimary
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

    if (showSavedReprintDialog) {
        SavedValeReprintDialog(
            valeTypeTitle = "Vale de Entrada",
            folio = "PRV$effProv • $effFact",
            itemCountSummary = "${uiState.scannedItems.size} piezas",
            activeProfile = activeProfile,
            totalProfiles = savedProfiles.size,
            copiesPrinted = copiesPrinted,
            isPrinting = uiState.isPrinting,
            printStatusMessage = uiState.printStatusMessage,
            accentColor = MineSafetyYellow,
            onPrintClick = { executePrint(trackCopy = true) },
            onChangePrinterClick = {
                withBluetoothPermission { showPrinterPickerDialog = true }
            },
            onFinishClick = {
                showSavedReprintDialog = false
                onFinished()
            }
        )
    }

    if (showPrinterPickerDialog) {
        QuickPrinterSelectorDialog(
            onSelectProfile = { selected ->
                refreshPrinters()
                activeProfile = selected
                showPrinterPickerDialog = false
                Toast.makeText(context, "Impresora activa: ${selected.displayTitle()}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPrinterPickerDialog = false }
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

    dateCorrectionTarget?.let { (rawCode, detectedDate) ->
        DateCorrectionDialog(
            rawCode = rawCode,
            detectedDate = detectedDate,
            onConfirm = { correctedDate ->
                val correctedCode = correctBarcodeDate(rawCode, correctedDate)
                dateCorrectionTarget = null
                if (correctedCode != null) viewModel.onBarcodeScanned(correctedCode)
            },
            onDismiss = { dateCorrectionTarget = null }
        )
    }
}

@Composable
private fun CompactMetric(
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
private fun EntradaScannedItemCard(
    item: BarcodeItem,
    isDuplicateHighlighted: Boolean = false,
    duplicateAttemptCount: Int = 1,
    onDelete: () -> Unit
) {
    val empaqueColor = when (item.empaque) {
        TipoEmpaque.CAJA -> EmpaqueCajaColor
        TipoEmpaque.SACO -> EmpaqueSacoColor
        TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
        TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
        else -> MineBorderGray
    }

    val rowBackground = if (isDuplicateHighlighted) MineErrorRedBackground else MineDarkSurface
    val rowBorderColor = if (isDuplicateHighlighted) MineErrorRed else empaqueColor.copy(alpha = 0.6f)
    val rowBorderWidth = if (isDuplicateHighlighted) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBackground)
            .border(rowBorderWidth, rowBorderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(if (isDuplicateHighlighted) "duplicate_highlighted_item_${item.code}" else "scanned_item_${item.code}"),
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
                if (isDuplicateHighlighted) {
                    DuplicateBadgeChip(attemptCount = duplicateAttemptCount)
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
                color = if (isDuplicateHighlighted) MineErrorRedLight else MineTextSecondary,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
            AutoShrinkText(
                text = if (isDuplicateHighlighted) "Cód: ${item.code} (YA ESCANEADO)" else "Cód: ${item.code}",
                initialTextSize = 11.sp,
                minTextSize = 8.sp,
                color = if (isDuplicateHighlighted) MineErrorRedLight else MineTextSecondary,
                fontWeight = if (isDuplicateHighlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.timestamp.substringAfter(" "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDuplicateHighlighted) MineErrorRedLight else MineTextMuted,
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
