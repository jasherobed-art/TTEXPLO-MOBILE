package com.ttexplo.mobile.ui.screens.devolucion

import android.Manifest
import android.app.Application
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.DevolucionEntity
import com.ttexplo.mobile.data.local.entity.DevolucionItemEntity
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.model.DevolucionRecord
import com.ttexplo.mobile.domain.model.ScannedCodeRecord
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.domain.scanner.ScanResult
import com.ttexplo.mobile.domain.scanner.ScanValidator
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.domain.scanner.correctBarcodeDate
import com.ttexplo.mobile.printer.BluetoothPrinterManager
import com.ttexplo.mobile.printer.PrinterProfile
import com.ttexplo.mobile.printer.ValePrintHelper
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AlwaysActiveBarcodeField
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.CompactScanMetricsRibbon
import com.ttexplo.mobile.ui.common.DateCorrectionDialog
import com.ttexplo.mobile.ui.common.DuplicateBadgeChip
import com.ttexplo.mobile.ui.common.DuplicateCodeAlert
import com.ttexplo.mobile.ui.common.DummyExplosivesScannerBar
import com.ttexplo.mobile.ui.common.ExitScanConfirmDialog
import com.ttexplo.mobile.ui.common.HorizontalProductFilterRow
import com.ttexplo.mobile.ui.common.ProductEditDialog
import com.ttexplo.mobile.ui.common.QuickPrinterSelectorDialog
import com.ttexplo.mobile.ui.common.SavedValeReprintDialog
import com.ttexplo.mobile.ui.common.ScanFeedbackType
import com.ttexplo.mobile.ui.common.ScanFilterKeys
import com.ttexplo.mobile.ui.common.ScanFlashEvent
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.common.buildExplosiveFilterChips
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DevolucionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TTExploApp
    private val db = app.database
    private val inventoryRepository = app.inventoryRepository
    private val catalog = CatalogRepositoryImpl(db.productoDao())
    val settingsRepository = SettingsRepository(application)
    val printerManager = BluetoothPrinterManager(application)
    private val validator = ScanValidator(catalog)
    private val sound = SoundFeedbackManager(application)
    private val scanMutex = Mutex()

    private val _items = MutableStateFlow<List<BarcodeItem>>(emptyList())
    val items: StateFlow<List<BarcodeItem>> = _items.asStateFlow()

    private val _duplicateReads = MutableStateFlow<List<BarcodeItem>>(emptyList())
    val duplicateReads: StateFlow<List<BarcodeItem>> = _duplicateReads.asStateFlow()

    private val _duplicateAttemptCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val duplicateAttemptCounts: StateFlow<Map<String, Int>> = _duplicateAttemptCounts.asStateFlow()

    private val _lastDuplicateAlert = MutableStateFlow<DuplicateCodeAlert?>(null)
    val lastDuplicateAlert: StateFlow<DuplicateCodeAlert?> = _lastDuplicateAlert.asStateFlow()

    private val _latestScanResult = MutableStateFlow<ScanResult?>(null)
    val latestScanResult: StateFlow<ScanResult?> = _latestScanResult.asStateFlow()

    private val _latestFlashEvent = MutableStateFlow<ScanFlashEvent?>(null)
    val latestFlashEvent: StateFlow<ScanFlashEvent?> = _latestFlashEvent.asStateFlow()

    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    private val _printStatusMessage = MutableStateFlow<String?>(null)
    val printStatusMessage: StateFlow<String?> = _printStatusMessage.asStateFlow()

    fun isVisualFlashEnabled(): Boolean = sound.isVisualFlashEnabled()

    var existingDevolucionId: Long? = null
    var initialEntrega: String = ""
    var initialRecibe: String = ""

    fun loadExistingDevolucion(id: Long, onLoaded: (String, String) -> Unit = { _, _ -> }) {
        if (id <= 0) return
        viewModelScope.launch {
            val dev = db.devolucionDao().getDevolucionById(id)
            if (dev != null) {
                existingDevolucionId = dev.id
                initialEntrega = dev.empleadoEntrega
                initialRecibe = dev.empleadoRecibe
                val dbItems = db.devolucionDao().getItemsForDevolucion(dev.id)
                val domainItems = dbItems.map { entity ->
                    val prod = catalog.lookupBarcode(entity.codigoBarras)
                    val emp = when (entity.codigoBarras.length) {
                        30 -> TipoEmpaque.CAJA
                        18 -> TipoEmpaque.SACO
                        16, 17 -> TipoEmpaque.PIEZA
                        else -> if (entity.codigoBarras.startsWith("X", ignoreCase = true)) TipoEmpaque.ACCESORIO else TipoEmpaque.DESCONOCIDO
                    }
                    BarcodeItem(entity.codigoBarras, entity.fechaEscaneo, prod, emp)
                }
                _items.value = domainItems
                onLoaded(dev.empleadoEntrega, dev.empleadoRecibe)
            }
        }
    }

    fun registerProductInCatalog(entity: ProductoEntity) {
        viewModelScope.launch {
            db.productoDao().insert(entity)
            val pendingCode = _latestScanResult.value?.rawBarcode
            if (!pendingCode.isNullOrBlank() && _latestScanResult.value?.unknownId == entity.idProducto) {
                onScanned(pendingCode)
            }
        }
    }

    fun onScanned(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            scanMutex.withLock {
                val session = _items.value.map { it.code }.toSet()
                val res = validator.processScan(trimmed, session, allowAccessories = true)
                _latestScanResult.value = res
                if (!res.isValid) {
                    sound.playError()
                    _latestFlashEvent.value = ScanFlashEvent(type = ScanFeedbackType.ERROR)
                } else if (res.isDuplicate) {
                    sound.playWarning()
                    val currentCounts = _duplicateAttemptCounts.value
                    val nextAttempt = (currentCounts[res.rawBarcode] ?: 0) + 1
                    _duplicateAttemptCounts.value = currentCounts + (res.rawBarcode to nextAttempt)
                    _duplicateReads.value = listOf(
                        BarcodeItem(
                            code = res.rawBarcode,
                            timestamp = res.timestamp,
                            productInfo = res.productInfo,
                            empaque = res.empaque,
                            isDuplicateRead = true
                        )
                    ) + _duplicateReads.value
                    _lastDuplicateAlert.value = DuplicateCodeAlert(
                        code = res.rawBarcode,
                        attemptCount = nextAttempt
                    )
                    _latestFlashEvent.value = ScanFlashEvent(type = ScanFeedbackType.ERROR)
                } else {
                    sound.playSuccess()
                    _latestFlashEvent.value = ScanFlashEvent(type = ScanFeedbackType.SUCCESS)
                    _items.value = listOf(BarcodeItem(res.rawBarcode, res.timestamp, res.productInfo, res.empaque)) + _items.value
                    _duplicateReads.value = emptyList()
                    _duplicateAttemptCounts.value = emptyMap()
                    _lastDuplicateAlert.value = null
                }
            }
        }
    }

    fun deleteItem(item: BarcodeItem) {
        if (item.isDuplicateRead) {
            _duplicateReads.value = _duplicateReads.value.filterNot {
                it.code == item.code && it.timestamp == item.timestamp
            }
            val left = (_duplicateAttemptCounts.value[item.code] ?: 1) - 1
            _duplicateAttemptCounts.value = if (left <= 0) {
                _duplicateAttemptCounts.value - item.code
            } else {
                _duplicateAttemptCounts.value + (item.code to left)
            }
            if (left <= 0 && _lastDuplicateAlert.value?.code == item.code) {
                _lastDuplicateAlert.value = null
            }
            return
        }
        _items.value = _items.value.filterNot { it.code == item.code && it.timestamp == item.timestamp }
        _duplicateReads.value = _duplicateReads.value.filterNot { it.code == item.code }
        _duplicateAttemptCounts.value = _duplicateAttemptCounts.value - item.code
        if (_lastDuplicateAlert.value?.code == item.code) {
            _lastDuplicateAlert.value = null
        }
    }

    fun save(entrega: String, recibe: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val currentId = existingDevolucionId
            val devId = if (currentId != null) {
                db.devolucionDao().deleteItemsForDevolucion(currentId)
                db.devolucionDao().deleteDevolucion(currentId)
                db.devolucionDao().insertDevolucion(
                    DevolucionEntity(
                        id = currentId,
                        empleadoEntrega = entrega,
                        empleadoRecibe = recibe,
                        fechaCreacion = DateTimeUtils.currentTimestamp(),
                        estado = "FINALIZADO"
                    )
                )
            } else {
                db.devolucionDao().insertDevolucion(
                    DevolucionEntity(
                        empleadoEntrega = entrega,
                        empleadoRecibe = recibe,
                        fechaCreacion = DateTimeUtils.currentTimestamp(),
                        estado = "FINALIZADO"
                    )
                )
            }
            val currentItems = _items.value
            val entities = currentItems.map {
                DevolucionItemEntity(
                    devolucionId = devId,
                    codigoBarras = it.code,
                    fechaEscaneo = it.timestamp
                )
            }
            db.devolucionDao().insertItems(entities)
            if (currentItems.isNotEmpty()) {
                inventoryRepository.recordDevolucion(
                    devolucionId = devId,
                    empleadoEntrega = entrega,
                    empleadoRecibe = recibe,
                    items = currentItems
                )
            }
            onComplete()
        }
    }

    fun printTicket(
        entrega: String,
        recibe: String,
        profile: PrinterProfile? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        val record = DevolucionRecord(
            empleadoEntrega = entrega.ifBlank { "SIN ESPECIFICAR" },
            empleadoRecibe = recibe.ifBlank { "POLVORIN" },
            items = _items.value.map {
                ScannedCodeRecord(
                    code = it.code,
                    timestamp = it.timestamp,
                    productName = it.productInfo.name,
                    presentation = it.productInfo.presentation
                )
            }
        )
        viewModelScope.launch {
            _isPrinting.value = true
            _printStatusMessage.value = null
            val outcome = ValePrintHelper.printDevolucion(app, record, profile)
            _isPrinting.value = false
            _printStatusMessage.value = outcome.message
            onComplete(outcome.success, outcome.message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sound.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevolucionScreen(
    onNavigateBack: () -> Unit,
    devolucionId: Long = 0L,
    viewModel: DevolucionViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val duplicateReads by viewModel.duplicateReads.collectAsStateWithLifecycle()
    val duplicateAttemptCounts by viewModel.duplicateAttemptCounts.collectAsStateWithLifecycle()
    val lastDuplicateAlert by viewModel.lastDuplicateAlert.collectAsStateWithLifecycle()
    val latestScanResult by viewModel.latestScanResult.collectAsStateWithLifecycle()
    val latestFlashEvent by viewModel.latestFlashEvent.collectAsStateWithLifecycle()
    val isPrinting by viewModel.isPrinting.collectAsStateWithLifecycle()
    val printStatusMessage by viewModel.printStatusMessage.collectAsStateWithLifecycle()

    var empleadoEntrega by remember { mutableStateOf("") }
    var empleadoRecibe by remember { mutableStateOf("") }
    var manualInput by remember { mutableStateOf("") }
    var isHeaderExpanded by remember { mutableStateOf(true) }
    var lastObservedCount by remember { mutableStateOf(0) }
    val barcodeFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var selectedFilterKey by remember { mutableStateOf<String?>(null) }
    var lastHandledDuplicateEventId by remember { mutableStateOf<Long?>(null) }

    var savedProfiles by remember { mutableStateOf(viewModel.settingsRepository.getPrinterProfiles()) }
    var activeProfile by remember { mutableStateOf(viewModel.settingsRepository.getActivePrinterProfile()) }
    var showPrinterPickerDialog by remember { mutableStateOf(false) }
    var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showSavedReprintDialog by remember { mutableStateOf(false) }
    var copiesPrinted by remember { mutableStateOf(0) }

    var showCatalogDialog by remember { mutableStateOf(false) }
    var catalogAddInitialId by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<BarcodeItem?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var dateCorrectionTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val anyDialogOpen = showSavedReprintDialog || showPrinterPickerDialog || showCatalogDialog || itemToDelete != null || showExitConfirm || dateCorrectionTarget != null

    fun refreshPrinters() {
        savedProfiles = viewModel.settingsRepository.getPrinterProfiles()
        activeProfile = viewModel.settingsRepository.getActivePrinterProfile()
    }

    BackHandler(enabled = items.isNotEmpty() && !showSavedReprintDialog) {
        showExitConfirm = true
    }

    LaunchedEffect(Unit) {
        refreshPrinters()
        if (devolucionId > 0L) {
            runCatching { barcodeFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(anyDialogOpen) {
        if (!anyDialogOpen && !isHeaderExpanded) {
            runCatching { barcodeFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(items.size) {
        val currentSize = items.size
        if (currentSize > 0 && currentSize != lastObservedCount) {
            isHeaderExpanded = false
            if (currentSize > lastObservedCount) {
                runCatching { listState.animateScrollToItem(0) }
            }
            if (!anyDialogOpen) {
                runCatching { barcodeFocusRequester.requestFocus() }
            }
        }
        lastObservedCount = currentSize
    }

    LaunchedEffect(listState.isScrollInProgress, listState.firstVisibleItemIndex) {
        if ((listState.isScrollInProgress || listState.firstVisibleItemIndex > 0) && isHeaderExpanded) {
            isHeaderExpanded = false
        }
    }

    LaunchedEffect(devolucionId) {
        if (devolucionId > 0L) {
            viewModel.loadExistingDevolucion(devolucionId) { ent, rec ->
                empleadoEntrega = ent
                empleadoRecibe = rec
                if (ent.isNotBlank() && rec.isNotBlank()) {
                    isHeaderExpanded = false
                }
            }
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
            viewModel.printTicket(
                entrega = empleadoEntrega.trim(),
                recibe = empleadoRecibe.trim(),
                profile = target
            ) { ok, msg ->
                if (ok && trackCopy) copiesPrinted += 1
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scannedCode ->
            if (dateCorrectionTarget == null) {
                isHeaderExpanded = false
                viewModel.onScanned(scannedCode)
            }
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose {
            ZebraScannerReceiver.unregister(context, receiver)
        }
    }

    LaunchedEffect(latestScanResult) {
        val result = latestScanResult
        val invalidDate = result?.invalidDate
        if (result != null && invalidDate != null) {
            dateCorrectionTarget = result.rawBarcode to invalidDate
        }
    }

    val totalCajas = items.count { it.empaque == TipoEmpaque.CAJA || it.code.length == 30 }
    val totalSacos = items.count { it.empaque == TipoEmpaque.SACO || it.code.length == 18 }
    val totalCartuchos = items.count { it.empaque == TipoEmpaque.PIEZA || it.code.length in 16..17 }

    val filterChips = remember(items) {
        buildExplosiveFilterChips(
            items = items,
            includeAccessoriesType = true,
            useIdKey = false
        )
    }

    val filteredItems = remember(items, duplicateReads, selectedFilterKey) {
        (duplicateReads + items).filter { item ->
            ScanFilterKeys.matchesBarcodeItem(item, selectedFilterKey, useIdKey = false)
        }
    }

    val codeFrequencies = remember(items) {
        items.groupingBy { it.code }.eachCount()
    }

    // Al detectar un código duplicado, garantiza visibilidad en LazyColumn y desplaza hasta él
    LaunchedEffect(lastDuplicateAlert?.eventId, filteredItems) {
        val alert = lastDuplicateAlert ?: return@LaunchedEffect
        if (lastHandledDuplicateEventId == alert.eventId) return@LaunchedEffect

        isHeaderExpanded = false
        val indexInFiltered = filteredItems.indexOfFirst { it.code == alert.code }
        if (indexInFiltered >= 0) {
            lastHandledDuplicateEventId = alert.eventId
            runCatching { listState.animateScrollToItem(indexInFiltered) }
            if (!anyDialogOpen) {
                runCatching { barcodeFocusRequester.requestFocus() }
            }
        } else if (selectedFilterKey != null && items.any { it.code == alert.code }) {
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
                                text = "Devoluciones a Polvorín",
                                color = MineTextPrimary,
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 16.sp,
                                minTextSize = 13.sp
                            )
                            if (empleadoEntrega.isNotBlank() || empleadoRecibe.isNotBlank()) {
                                AutoShrinkText(
                                    text = "${empleadoEntrega.ifBlank { "Entrega" }} → ${empleadoRecibe.ifBlank { "Recibe" }}",
                                    color = MineAgentCyan,
                                    targetTextSize = 11.sp,
                                    minTextSize = 9.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (items.isNotEmpty()) showExitConfirm = true else onNavigateBack()
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = MineTextPrimary)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { executePrint() },
                            enabled = !isPrinting && items.isNotEmpty(),
                            modifier = Modifier.testTag("devolucion_topbar_print_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Imprimir Ticket",
                                tint = if (items.isNotEmpty()) MineSafetyYellow else MineTextMuted
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
                // 1. CINTA COMPACTA SUPERIOR
                CompactScanMetricsRibbon(
                    totalCount = items.size,
                    cajasCount = totalCajas,
                    sacosCount = totalSacos,
                    cartuchosCount = totalCartuchos,
                    accentColor = MineAgentCyan,
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = empleadoEntrega,
                                onValueChange = { empleadoEntrega = it },
                                label = { Text("Entrega material") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MineAgentBlue,
                                    unfocusedBorderColor = MineBorderGray,
                                    focusedTextColor = MineTextPrimary,
                                    unfocusedTextColor = MineTextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = empleadoRecibe,
                                onValueChange = { empleadoRecibe = it },
                                label = { Text("Recibe en polvorín") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (empleadoEntrega.isNotBlank() && empleadoRecibe.isNotBlank()) {
                                            isHeaderExpanded = false
                                        }
                                        runCatching { barcodeFocusRequester.requestFocus() }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MineAgentBlue,
                                    unfocusedBorderColor = MineBorderGray,
                                    focusedTextColor = MineTextPrimary,
                                    unfocusedTextColor = MineTextPrimary
                                )
                            )
                        }

                        if (com.ttexplo.mobile.license.LicenseManager.isDemoModeAllowed(androidx.compose.ui.platform.LocalContext.current)) {
                            DummyExplosivesScannerBar(
                                onSimulateScan = { code ->
                                    isHeaderExpanded = false
                                    viewModel.onScanned(code)
                                },
                                lastValidCode = items.firstOrNull()?.code,
                                allowAccessories = true
                            )
                        }
                    }
                }

                // 3. CASILLA DE CAPTURA DE CÓDIGOS DE BARRAS (SIEMPRE VISIBLE Y ACTIVA)
                AlwaysActiveBarcodeField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    onSubmitBarcode = { code ->
                        if (dateCorrectionTarget == null) viewModel.onScanned(code)
                    },
                    accentColor = MineAgentCyan,
                    placeholderText = "Escanear o escribir devolución + ENTER...",
                    focusRequester = barcodeFocusRequester,
                    testTagName = "devolucion_manual_barcode_input",
                    onActivityDetected = {
                        if (isHeaderExpanded) isHeaderExpanded = false
                    }
                )

                // Banner compacto de resultado
                latestScanResult?.let { result ->
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
                                Text("+ ID", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // 2. FILTROS HORIZONTALES POR PRODUCTO (1 sola línea visible)
                HorizontalProductFilterRow(
                    totalCount = items.size,
                    chips = filterChips,
                    selectedKey = selectedFilterKey,
                    accentColor = MineAgentCyan,
                    onSelectKey = { selectedFilterKey = it; runCatching { barcodeFocusRequester.requestFocus() } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedFilterKey != null) {
                            "FILTRADOS: ${filteredItems.size} DE ${items.size}"
                        } else {
                            "MATERIALES DEVUELTOS (${items.size}):"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedFilterKey != null) MineAgentCyan else MineTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedFilterKey != null) {
                        Text(
                            text = "Ver todos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MineSafetyYellow,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { selectedFilterKey = null }
                        )
                    }
                }

                // 5. LISTADO DE CÓDIGOS DEVUELTOS
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("devolucion_codes_lazy_column"),
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
                                    text = if (selectedFilterKey != null) {
                                        "Sin códigos para este filtro de tipo/producto"
                                    } else {
                                        "Casilla activa: dispare el lector o escriba devoluciones"
                                    },
                                    color = MineTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        items(filteredItems) { item ->
                            val empaqueColor = when (item.empaque) {
                                TipoEmpaque.CAJA -> EmpaqueCajaColor
                                TipoEmpaque.SACO -> EmpaqueSacoColor
                                TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
                                TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
                                else -> MineBorderGray
                            }
                            val dupAttempts = duplicateAttemptCounts[item.code] ?: 0
                            val isDuplicateHighlighted = dupAttempts > 0 || (codeFrequencies[item.code] ?: 0) > 1
                            val cardBg = if (isDuplicateHighlighted) MineErrorRedBackground else MineDarkSurface
                            val cardBorderColor = if (isDuplicateHighlighted) MineErrorRed else empaqueColor.copy(alpha = 0.6f)
                            val cardBorderWidth = if (isDuplicateHighlighted) 2.dp else 1.dp

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(if (isDuplicateHighlighted) "duplicate_highlighted_item_${item.code}" else "scanned_item_${item.code}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(cardBorderWidth, cardBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
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
                                                DuplicateBadgeChip(attemptCount = dupAttempts.coerceAtLeast(1))
                                            }
                                            AutoShrinkText(
                                                text = item.productInfo.name,
                                                initialTextSize = 13.sp,
                                                minTextSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MineTextPrimary,
                                                maxLines = 1
                                            )
                                        }
                                        AutoShrinkText(
                                            text = "Medida: ${item.productInfo.presentation} • ${item.productInfo.type.label}",
                                            initialTextSize = 11.sp,
                                            minTextSize = 8.sp,
                                            color = if (isDuplicateHighlighted) MineErrorRedLight else MineTextSecondary,
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
                                    IconButton(onClick = { itemToDelete = item }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MineErrorRed)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { executePrint() },
                        enabled = !isPrinting && items.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("devolucion_print_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MineSafetyYellow),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        AutoShrinkText(
                            text = if (isPrinting) "Imprimiendo..." else "Imprimir",
                            fontWeight = FontWeight.Bold,
                            targetTextSize = 12.sp,
                            minTextSize = 9.sp,
                            color = MineSafetyYellow
                        )
                    }

                    Button(
                        onClick = {
                            if (empleadoEntrega.isBlank() || empleadoRecibe.isBlank()) {
                                isHeaderExpanded = true
                                Toast.makeText(context, "Capture quién entrega y quién recibe", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.save(empleadoEntrega.trim(), empleadoRecibe.trim()) {
                                refreshPrinters()
                                copiesPrinted = 0
                                showSavedReprintDialog = true
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("devolucion_save_button"),
                        shape = RoundedCornerShape(8.dp),
                        enabled = items.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MineSuccessGreen)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = MineTextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        AutoShrinkText(
                            text = "Guardar Dev. (${items.size})",
                            fontWeight = FontWeight.Bold,
                            color = MineTextPrimary,
                            targetTextSize = 13.sp,
                            minTextSize = 10.sp
                        )
                    }
                }
            }
        }
        ScanFlashOverlay(
            event = latestFlashEvent,
            enabled = viewModel.isVisualFlashEnabled()
        )
    }

    if (showSavedReprintDialog) {
        val folio = if (empleadoEntrega.startsWith("DEV", ignoreCase = true)) empleadoEntrega else "DEV$empleadoEntrega"
        SavedValeReprintDialog(
            valeTypeTitle = "Vale de Devolución",
            folio = folio,
            itemCountSummary = "${items.size} piezas",
            activeProfile = activeProfile,
            totalProfiles = savedProfiles.size,
            copiesPrinted = copiesPrinted,
            isPrinting = isPrinting,
            printStatusMessage = printStatusMessage,
            accentColor = MineAgentCyan,
            onPrintClick = { executePrint(trackCopy = true) },
            onChangePrinterClick = {
                withBluetoothPermission { showPrinterPickerDialog = true }
            },
            onFinishClick = {
                showSavedReprintDialog = false
                onNavigateBack()
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
            itemCount = items.size,
            savedAsDraft = false,
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
                if (correctedCode != null) viewModel.onScanned(correctedCode)
            },
            onDismiss = { dateCorrectionTarget = null }
        )
    }
}
