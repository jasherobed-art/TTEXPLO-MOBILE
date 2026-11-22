package com.ttexplo.mobile.ui.screens.devolucion

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.DevolucionEntity
import com.ttexplo.mobile.data.local.entity.DevolucionItemEntity
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.domain.scanner.ScanResult
import com.ttexplo.mobile.domain.scanner.ScanValidator
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.DummyExplosivesScannerBar
import com.ttexplo.mobile.ui.common.ExitScanConfirmDialog
import com.ttexplo.mobile.ui.common.ProductEditDialog
import com.ttexplo.mobile.ui.common.ScanFeedbackType
import com.ttexplo.mobile.ui.common.ScanFlashEvent
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
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
    private val validator = ScanValidator(catalog)
    private val sound = SoundFeedbackManager(application)
    private val scanMutex = Mutex()

    private val _items = MutableStateFlow<List<BarcodeItem>>(emptyList())
    val items: StateFlow<List<BarcodeItem>> = _items.asStateFlow()

    private val _latestScanResult = MutableStateFlow<ScanResult?>(null)
    val latestScanResult: StateFlow<ScanResult?> = _latestScanResult.asStateFlow()

    private val _latestFlashEvent = MutableStateFlow<ScanFlashEvent?>(null)
    val latestFlashEvent: StateFlow<ScanFlashEvent?> = _latestFlashEvent.asStateFlow()

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
                    _latestFlashEvent.value = ScanFlashEvent(type = ScanFeedbackType.WARNING)
                } else {
                    sound.playSuccess()
                    _latestFlashEvent.value = ScanFlashEvent(type = ScanFeedbackType.SUCCESS)
                    _items.value = listOf(BarcodeItem(res.rawBarcode, res.timestamp, res.productInfo, res.empaque)) + _items.value
                }
            }
        }
    }

    fun deleteItem(item: BarcodeItem) {
        _items.value = _items.value.filterNot { it.code == item.code && it.timestamp == item.timestamp }
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
    val items by viewModel.items.collectAsStateWithLifecycle()
    val latestScanResult by viewModel.latestScanResult.collectAsStateWithLifecycle()
    val latestFlashEvent by viewModel.latestFlashEvent.collectAsStateWithLifecycle()

    var empleadoEntrega by remember { mutableStateOf("") }
    var empleadoRecibe by remember { mutableStateOf("") }
    var manualInput by remember { mutableStateOf("") }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var catalogAddInitialId by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<BarcodeItem?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = items.isNotEmpty()) {
        showExitConfirm = true
    }

    LaunchedEffect(devolucionId) {
        if (devolucionId > 0L) {
            viewModel.loadExistingDevolucion(devolucionId) { ent, rec ->
                empleadoEntrega = ent
                empleadoRecibe = rec
            }
        }
    }

    // Register Zebra Scanner Receiver
    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scannedCode ->
            viewModel.onScanned(scannedCode)
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
                title = { Text("Devoluciones a Polvorín", color = MineTextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (items.isNotEmpty()) {
                            showExitConfirm = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = MineTextPrimary)
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Inputs (en fila para ahorrar espacio vertical)
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineAgentBlue,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )
            }

            // CONTADOR COMPACTO OPTIMIZADO (TOTAL, CAJAS, SACOS, CARTUCHOS)
            val totalCajas = items.count { it.empaque == TipoEmpaque.CAJA || it.code.length == 30 }
            val totalSacos = items.count { it.empaque == TipoEmpaque.SACO || it.code.length == 18 }
            val totalCartuchos = items.count { it.empaque == TipoEmpaque.PIEZA || it.code.length in 16..17 }

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
                        AutoShrinkText(
                            text = "DEVOLUCIÓN DE MATERIALES",
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            color = MineTextSecondary,
                            targetTextSize = 12.sp,
                            minTextSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (items.isEmpty()) "Listo para pistolear" else "${items.size} devueltos",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (items.isEmpty()) MineSuccessGreenLight else MineSafetyYellow,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 4 Contadores en 1 sola fila compacta con colores normalizados
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactMetric(
                            modifier = Modifier.weight(1f),
                            label = "TOTAL",
                            count = items.size,
                            borderColor = MineBorderGray,
                            countColor = EmpaqueTotalColor
                        )
                        CompactMetric(
                            modifier = Modifier.weight(1f),
                            label = "CAJAS",
                            count = totalCajas,
                            borderColor = EmpaqueCajaColor,
                            countColor = EmpaqueCajaColor
                        )
                        CompactMetric(
                            modifier = Modifier.weight(1f),
                            label = "SACOS",
                            count = totalSacos,
                            borderColor = EmpaqueSacoColor,
                            countColor = EmpaqueSacoColor
                        )
                        CompactMetric(
                            modifier = Modifier.weight(1f),
                            label = "CARTUCHOS",
                            count = totalCartuchos,
                            borderColor = EmpaqueCartuchoColor,
                            countColor = EmpaqueCartuchoColor
                        )
                    }
                }
            }

            // CASILLA DE ESCANEO ACTIVO (Inmediatamente debajo del contador principal)
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                label = { Text("Escaneo activo (o teclee código + ENTER)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (manualInput.isNotBlank()) {
                            viewModel.onScanned(manualInput.trim())
                            manualInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineAgentBlue,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineAgentBlue
                )
            )

            // Simulador de escaneo (MODO PRUEBA plegable)
            DummyExplosivesScannerBar(
                onSimulateScan = { code -> viewModel.onScanned(code) },
                lastValidCode = items.firstOrNull()?.code,
                allowAccessories = true
            )

            // Banner compacto de resultado
            latestScanResult?.let { result ->
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
                        imageVector = if (isError || isDup) Icons.Default.Warning else Icons.Default.CheckCircle,
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

            // Desglose individual de productos devueltos
            val devSummaries = remember(items) {
                items.groupBy { Triple(it.productInfo.name, it.productInfo.presentation, it.empaque) }
                    .map { (key, group) ->
                        DevolucionMarker(
                            productName = key.first,
                            presentation = key.second,
                            empaque = key.third,
                            count = group.size
                        )
                    }
                    .sortedWith(compareBy<DevolucionMarker> { it.empaque.ordinal }.thenByDescending { it.count })
            }

            if (devSummaries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DESGLOSE POR PRODUCTO Y MEDIDA:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MineTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${devSummaries.size} producto(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineAgentBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pairs = devSummaries.chunked(2)
                    pairs.forEach { rowPairs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPairs.forEach { itemSummary ->
                                val empaqueColor = when (itemSummary.empaque) {
                                    TipoEmpaque.CAJA -> EmpaqueCajaColor
                                    TipoEmpaque.SACO -> EmpaqueSacoColor
                                    TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
                                    TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
                                    else -> MineBorderGray
                                }
                                val empaqueLabel = when (itemSummary.empaque) {
                                    TipoEmpaque.SACO -> "Sacos"
                                    TipoEmpaque.PIEZA -> "Cartuchos"
                                    TipoEmpaque.ACCESORIO -> "Acces."
                                    else -> "Cajas"
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MineDarkSurface)
                                            .border(1.dp, empaqueColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                            AutoShrinkText(
                                                text = itemSummary.productName,
                                                initialTextSize = 13.sp,
                                                minTextSize = 9.sp,
                                                color = MineTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            AutoShrinkText(
                                                text = if (itemSummary.presentation.isNotBlank()) "Medida: ${itemSummary.presentation}" else "Devolución",
                                                initialTextSize = 11.sp,
                                                minTextSize = 8.sp,
                                                color = MineTextSecondary,
                                                fontWeight = FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(empaqueColor)
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "${itemSummary.count}",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MineDarkBackground,
                                                    fontSize = 15.sp
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
                                    }
                                }
                            }
                            if (rowPairs.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Scanned Items List
            Text(
                text = "MATERIALES DEVUELTOS (${items.size}):",
                style = MaterialTheme.typography.labelSmall,
                color = MineTextMuted,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.code + it.timestamp }) { item ->
                    val empaqueColor = when (item.empaque) {
                        TipoEmpaque.CAJA -> EmpaqueCajaColor
                        TipoEmpaque.SACO -> EmpaqueSacoColor
                        TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
                        TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
                        else -> MineBorderGray
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, empaqueColor.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
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
                                        fontWeight = FontWeight.Bold,
                                        color = MineTextPrimary,
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
                                    fontWeight = FontWeight.Normal,
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

            // Save button
            Button(
                onClick = {
                    viewModel.save(empleadoEntrega.trim(), empleadoRecibe.trim(), onNavigateBack)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = items.isNotEmpty() && empleadoEntrega.isNotBlank() && empleadoRecibe.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MineSuccessGreen)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = MineTextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GUARDAR DEVOLUCIÓN (${items.size} PIEZAS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MineTextPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }
    ScanFlashOverlay(
        event = latestFlashEvent,
        enabled = viewModel.isVisualFlashEnabled()
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
}

data class DevolucionMarker(
    val productName: String,
    val presentation: String,
    val empaque: TipoEmpaque,
    val count: Int
)

@Composable
private fun CompactMetric(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    borderColor: androidx.compose.ui.graphics.Color,
    countColor: androidx.compose.ui.graphics.Color
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
