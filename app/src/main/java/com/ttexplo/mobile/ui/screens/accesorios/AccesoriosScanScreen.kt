package com.ttexplo.mobile.ui.screens.accesorios

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.printer.PrinterProfile
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.ActivePrinterMiniBar
import com.ttexplo.mobile.ui.common.AlwaysActiveBarcodeField
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.DummyAccesoriosScannerBar
import com.ttexplo.mobile.ui.common.ExitScanConfirmDialog
import com.ttexplo.mobile.ui.common.HorizontalProductFilterRow
import com.ttexplo.mobile.ui.common.ProductFilterChipData
import com.ttexplo.mobile.ui.common.QuickPrinterSelectorDialog
import com.ttexplo.mobile.ui.common.SavedValeReprintDialog
import com.ttexplo.mobile.ui.common.ScanFilterKeys
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccesoriosScanScreen(
    vale: String,
    empresa: String,
    lugar: String,
    transportista: String,
    turno: String,
    recibio: String,
    entrego: String,
    voladura: String,
    valeId: Long = 0L,
    isDevolucion: Boolean = false,
    viewModel: AccesoriosViewModel,
    onNavigateBack: () -> Unit = {},
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val accentColor = if (isDevolucion) MineAgentCyan else MineAccessoryPurple
    val accentLightColor = if (isDevolucion) MineAgentCyan.copy(alpha = 0.85f) else MineAccessoryPurpleLight

    var cantidadText by remember { mutableStateOf("1") }
    var codeInput by remember { mutableStateOf("") }
    var isHeaderExpanded by remember { mutableStateOf(true) }
    var lastObservedCount by remember { mutableStateOf(0) }
    val barcodeFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var selectedCodeFilter by remember { mutableStateOf<String?>(null) }

    var savedProfiles by remember { mutableStateOf(viewModel.settingsRepository.getPrinterProfiles()) }
    var activeProfile by remember { mutableStateOf(viewModel.settingsRepository.getActivePrinterProfile()) }
    var showPrinterPickerDialog by remember { mutableStateOf(false) }
    var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showSavedReprintDialog by remember { mutableStateOf(false) }
    var copiesPrinted by remember { mutableStateOf(0) }

    var itemToDelete by remember { mutableStateOf<AccessoryItemRecord?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    val anyDialogOpen = showSavedReprintDialog || showPrinterPickerDialog || itemToDelete != null || showExitConfirm

    fun refreshPrinters() {
        savedProfiles = viewModel.settingsRepository.getPrinterProfiles()
        activeProfile = viewModel.settingsRepository.getActivePrinterProfile()
    }

    BackHandler(enabled = uiState.items.isNotEmpty() && !showSavedReprintDialog) {
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

    LaunchedEffect(uiState.items.size) {
        val currentSize = uiState.items.size
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

    LaunchedEffect(isDevolucion) {
        viewModel.setDevolucionMode(isDevolucion)
    }

    LaunchedEffect(valeId) {
        if (valeId > 0L) {
            viewModel.loadExistingVale(valeId)
        }
    }

    LaunchedEffect(vale) {
        if (valeId <= 0L) {
            viewModel.updateHeader(vale, empresa, lugar, transportista, turno, recibio, entrego, voladura)
        }
    }

    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scannedCode ->
            isHeaderExpanded = false
            val qty = cantidadText.toIntOrNull() ?: 1
            viewModel.onAccessoryScanned(scannedCode, qty)
            codeInput = ""
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose {
            ZebraScannerReceiver.unregister(context, receiver)
        }
    }

    val effVale = uiState.vale.ifEmpty { vale }
    val effEmpresa = uiState.empresa.ifEmpty { empresa }
    val effLugar = uiState.lugar.ifEmpty { lugar }
    val folioDisplay = if (effVale.startsWith("ACC", ignoreCase = true)) effVale else "ACC$effVale"
    val displayTotal = if (isDevolucion) abs(uiState.totalPieces) else uiState.totalPieces

    val filterChips = remember(uiState.items, uiState.summaries, accentColor) {
        val list = mutableListOf<ProductFilterChipData>()
        InventoryCategory.accessoryCategories.forEach { cat ->
            val catQty = uiState.items
                .filter { InventoryCategory.inferCategory(ProductType.ACCESORIO, it.code) == cat }
                .sumOf { abs(it.quantity) }
            val catColor = when (cat) {
                InventoryCategory.CORDON_DETONANTE -> MineAgentCyan
                InventoryCategory.CANUELAS -> MineSafetyYellow
                else -> EmpaqueAccesorioColor
            }
            list.add(
                ProductFilterChipData(
                    key = ScanFilterKeys.forCategory(cat),
                    title = cat.shortLabel,
                    empaqueShort = "",
                    count = catQty,
                    accentColor = catColor,
                    isTypeFilter = true
                )
            )
        }
        uiState.summaries.forEach { s ->
            list.add(
                ProductFilterChipData(
                    key = ScanFilterKeys.forProduct(s.code),
                    title = s.code,
                    empaqueShort = "Pzas",
                    count = abs(s.totalQuantity),
                    accentColor = accentColor,
                    isTypeFilter = false
                )
            )
        }
        list
    }

    val filteredItems = remember(uiState.items, selectedCodeFilter) {
        when {
            selectedCodeFilter == null -> uiState.items
            selectedCodeFilter!!.startsWith(ScanFilterKeys.TYPE_PREFIX) -> {
                val catName = selectedCodeFilter!!.removePrefix(ScanFilterKeys.TYPE_PREFIX)
                uiState.items.filter {
                    InventoryCategory.inferCategory(ProductType.ACCESORIO, it.code).name == catName
                }
            }
            selectedCodeFilter!!.startsWith(ScanFilterKeys.PROD_PREFIX) -> {
                val code = selectedCodeFilter!!.removePrefix(ScanFilterKeys.PROD_PREFIX)
                uiState.items.filter { it.code == code }
            }
            else -> uiState.items
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            AutoShrinkText(
                                text = if (isDevolucion) "Dev. Acc: $folioDisplay" else "Accesorios: $folioDisplay",
                                fontWeight = FontWeight.Bold,
                                color = MineTextPrimary,
                                targetTextSize = 15.sp,
                                minTextSize = 12.sp
                            )
                            AutoShrinkText(
                                text = "$effEmpresa • $effLugar",
                                color = accentColor,
                                targetTextSize = 12.sp,
                                minTextSize = 9.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { executePrint() },
                            enabled = !uiState.isPrinting && uiState.items.isNotEmpty(),
                            modifier = Modifier.testTag("accesorios_topbar_print_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Imprimir Ticket",
                                tint = if (uiState.items.isNotEmpty()) MineSafetyYellow else MineTextMuted
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
                            .background(accentColor.copy(alpha = 0.15f))
                            .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (isDevolucion) "Devolución recuperada ($displayTotal pzas)" else "Vale recuperado ($displayTotal pzas)",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentLightColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }

                // 1. CINTA COMPACTA SUPERIOR DE TOTAL Y CANTIDAD
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MineDarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isHeaderExpanded = !isHeaderExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "TOT: $displayTotal pzas",
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "Cant: x${cantidadText.ifEmpty { "1" }}",
                                color = MineTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isHeaderExpanded = !isHeaderExpanded }
                                .testTag("toggle_scan_panel_button"),
                            color = if (isHeaderExpanded) accentColor.copy(alpha = 0.2f) else MineDarkBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isHeaderExpanded) accentColor else MineBorderGray)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isHeaderExpanded) "Ocultar" else "Info",
                                    color = if (isHeaderExpanded) accentColor else MineTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (isHeaderExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isHeaderExpanded) "Colapsar encabezado" else "Expandir encabezado",
                                    tint = if (isHeaderExpanded) accentColor else MineTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 2. ENCABEZADO COLAPSABLE AUTOMÁTICAMENTE AL DETECTAR ACTIVIDAD
                AnimatedVisibility(
                    visible = isHeaderExpanded,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                    exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(160))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("1", "2", "4", "5", "10", "20").forEach { q ->
                                FilterChip(
                                    selected = cantidadText == q,
                                    onClick = { cantidadText = q },
                                    label = { Text(q, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = if (isDevolucion) MineDarkBackground else MineTextPrimary
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MineDarkSurface)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Entregó: ${uiState.entrego.ifEmpty { entrego }}", color = MineTextSecondary, fontSize = 11.sp)
                            Text("Recibió: ${uiState.recibio.ifEmpty { recibio }}", color = MineTextSecondary, fontSize = 11.sp)
                        }

                        DummyAccesoriosScannerBar(
                            onSimulateAccessory = { code ->
                                isHeaderExpanded = false
                                val qty = cantidadText.toIntOrNull() ?: 1
                                viewModel.onAccessoryScanned(code, qty)
                            },
                            accentColor = accentColor
                        )
                    }
                }

                // 3. CASILLA DE CANTIDAD + CÓDIGO DE BARRAS (SIEMPRE VISIBLE Y ACTIVA)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = cantidadText,
                        onValueChange = { cantidadText = it.filter { c -> c.isDigit() } },
                        label = { Text("Cant.", fontSize = 11.sp) },
                        modifier = Modifier.width(74.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MineDarkSurface,
                            unfocusedContainerColor = MineDarkSurface,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = MineBorderGray,
                            focusedTextColor = MineTextPrimary,
                            unfocusedTextColor = MineTextPrimary,
                            focusedLabelColor = accentColor
                        )
                    )

                    AlwaysActiveBarcodeField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        onSubmitBarcode = { scanned ->
                            val qty = cantidadText.toIntOrNull() ?: 1
                            viewModel.onAccessoryScanned(scanned, qty)
                        },
                        accentColor = accentColor,
                        placeholderText = "Código (X...) + ENTER...",
                        focusRequester = barcodeFocusRequester,
                        testTagName = "accesorios_manual_barcode_input",
                        onActivityDetected = {
                            if (isHeaderExpanded) isHeaderExpanded = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                uiState.lastScanMessage?.let { msg ->
                    val isError = msg.startsWith("Error", ignoreCase = true)
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isError) MineErrorRedLight else MineSuccessGreenLight
                    )
                }

                // 2. FILTROS HORIZONTALES POR CÓDIGO DE ACCESORIO
                HorizontalProductFilterRow(
                    totalCount = displayTotal,
                    chips = filterChips,
                    selectedKey = selectedCodeFilter,
                    accentColor = accentColor,
                    onSelectKey = { selectedCodeFilter = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selectedFilterLabel = remember(selectedCodeFilter, filterChips) {
                        filterChips.firstOrNull { it.key == selectedCodeFilter }?.title
                            ?: selectedCodeFilter?.removePrefix(ScanFilterKeys.PROD_PREFIX)?.removePrefix(ScanFilterKeys.TYPE_PREFIX)
                    }
                    Text(
                        text = if (selectedCodeFilter != null) {
                            "FILTRADO: $selectedFilterLabel (${filteredItems.size} reg.)"
                        } else {
                            if (isDevolucion) "ACCESORIOS DEVUELTOS (${uiState.items.size}):" else "ACCESORIOS CAPTURADOS (${uiState.items.size}):"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedCodeFilter != null) accentColor else MineTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedCodeFilter != null) {
                        Text(
                            text = "Ver todos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MineAgentCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { selectedCodeFilter = null }
                        )
                    }
                }

                // 5. LISTADO DE ACCESORIOS
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("accesorios_codes_lazy_column"),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (filteredItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedCodeFilter != null) {
                                        "Sin accesorios para este filtro"
                                    } else {
                                        "Casilla activa: capture cantidad y escanee código (X...)"
                                    },
                                    color = MineTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        items(filteredItems, key = { it.code + it.timestamp }) { item ->
                            AccessoryRowCard(
                                item = item,
                                accentColor = accentColor,
                                onDelete = { itemToDelete = item }
                            )
                        }
                    }
                }

                ActivePrinterMiniBar(
                    activeProfile = activeProfile,
                    totalProfiles = savedProfiles.size,
                    onChangePrinterClick = {
                        withBluetoothPermission { showPrinterPickerDialog = true }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { executePrint() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("accesorios_print_button"),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !uiState.isPrinting && uiState.items.isNotEmpty(),
                        border = BorderStroke(1.dp, accentColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        AutoShrinkText(
                            text = if (uiState.isPrinting) "Imprimiendo..." else "Imprimir",
                            initialTextSize = 12.sp,
                            minTextSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            softWrap = false
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
                        enabled = uiState.items.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("accesorios_save_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDevolucion) MineAgentCyan else MineSuccessGreen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = if (isDevolucion) MineDarkBackground else MineTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AutoShrinkText(
                            text = if (isDevolucion) "Guardar ($displayTotal)" else "Guardar Vale ($displayTotal)",
                            initialTextSize = 13.sp,
                            minTextSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDevolucion) MineDarkBackground else MineTextPrimary,
                            maxLines = 1,
                            softWrap = false
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
            valeTypeTitle = if (isDevolucion) "Devolución de Accesorios" else "Vale de Accesorios",
            folio = folioDisplay,
            itemCountSummary = "$displayTotal piezas",
            activeProfile = activeProfile,
            totalProfiles = savedProfiles.size,
            copiesPrinted = copiesPrinted,
            isPrinting = uiState.isPrinting,
            printStatusMessage = uiState.printStatusMessage,
            accentColor = accentColor,
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

    itemToDelete?.let { target ->
        TwoStepDeleteDialog(
            itemDescription = "Accesorio: ${target.code} (${target.quantity} pzas)",
            itemCode = "Hora: ${target.timestamp.substringAfter(" ")}",
            onConfirmDelete = {
                viewModel.deleteItem(target)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    if (showExitConfirm) {
        ExitScanConfirmDialog(
            itemCount = uiState.items.size,
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
fun AccessoryRowCard(
    item: AccessoryItemRecord,
    accentColor: androidx.compose.ui.graphics.Color = MineAccessoryPurple,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkSurface)
            .border(1.dp, MineBorderGray, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            AutoShrinkText(
                text = item.code,
                initialTextSize = 14.sp,
                minTextSize = 10.sp,
                color = MineTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                softWrap = true
            )
            Text(
                text = "Hora: ${item.timestamp.substringAfter(" ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MineTextMuted,
                fontSize = 12.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${item.quantity} pzas",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (accentColor == MineAgentCyan) MineDarkBackground else MineTextPrimary
                )
            }

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
