package com.ttexplo.mobile.ui.screens.inventory

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.ConteoCajaEntity
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.inventory.BoxCountFolio
import com.ttexplo.mobile.domain.inventory.BoxCountItem
import com.ttexplo.mobile.domain.inventory.BoxCountParser
import com.ttexplo.mobile.domain.inventory.BoxCountVale
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.printer.BluetoothPrinterManager
import com.ttexplo.mobile.printer.PrinterProfile
import com.ttexplo.mobile.printer.ValePrintHelper
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AlwaysActiveBarcodeField
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.QuickPrinterSelectorDialog
import com.ttexplo.mobile.ui.common.SavedValeReprintDialog
import com.ttexplo.mobile.ui.common.ScanFeedbackType
import com.ttexplo.mobile.ui.common.ScanFlashEvent
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxCountScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TTExploApp
    val dao = remember { app.database.boxCountDao() }
    val catalogRepo = remember { CatalogRepositoryImpl(app.database.productoDao()) }
    val settingsRepo = remember { SettingsRepository(context) }
    val printerManager = remember { BluetoothPrinterManager(context) }
    val sound = remember { SoundFeedbackManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var barcodeInput by remember { mutableStateOf("") }
    var kpInput by remember { mutableStateOf("") }
    var piecesInput by remember { mutableStateOf("") }
    var records by remember { mutableStateOf<List<BoxCountItem>>(emptyList()) }
    var savedVale by remember { mutableStateOf<BoxCountVale?>(null) }
    var message by remember { mutableStateOf("Llena los datos para habilitar escáner y entrada manual.") }
    var messageIsError by remember { mutableStateOf(false) }
    var isHeaderExpanded by remember { mutableStateOf(true) }
    var showDuplicate by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<BoxCountItem?>(null) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var copiesPrinted by remember { mutableStateOf(0) }
    var printStatusMessage by remember { mutableStateOf<String?>(null) }
    var activeProfile by remember { mutableStateOf(settingsRepo.getActivePrinterProfile()) }
    var showPrinterSelector by remember { mutableStateOf(false) }
    var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var flashEvent by remember { mutableStateOf<ScanFlashEvent?>(null) }

    val totalPieces = records.sumOf { it.piezas }
    val folioDisplay = savedVale?.folio ?: "-"
    val scanEnabled = kpInput.length == 1 && kpInput[0].isDigit() &&
        piecesInput.toIntOrNull() in 1..BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX
    val displayedIsError = messageIsError && !scanEnabled
    val displayedMessage = if (scanEnabled && !messageIsError)
        "Escáner y entrada manual listos: dispare el lector o escriba código + ENTER."
    else message

    val btPrintLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else true
        if (granted) pendingBtAction?.invoke()
        else Toast.makeText(context, "Se requiere permiso de Bluetooth para imprimir", Toast.LENGTH_LONG).show()
        pendingBtAction = null
    }

    fun withBluetoothPermission(action: () -> Unit) {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingBtAction = action
            btPrintLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            )
        } else action()
    }

    fun isBusy() = showDuplicate || showExitConfirm || itemToDelete != null || showSavedDialog

    fun addItem(item: BoxCountItem) {
        records = listOf(item) + records
        piecesInput = ""
        savedVale = null
        message = "Conteo agregado automáticamente: ${item.piezas} piezas · KP ${item.kp}."
        messageIsError = false
        isHeaderExpanded = true
    }

    fun handleScan(raw: String) {
        val kp = kpInput.trim()
        val pieces = piecesInput.trim().toIntOrNull()
        val parsed = BoxCountParser.parse(raw.trim()) ?: BoxCountParser.parseCodeOnly(raw.trim())
        if (parsed == null || kp.length != 1 || !kp[0].isDigit() || pieces == null || pieces !in 1..BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX) {
            sound.playError()
            flashEvent = ScanFlashEvent(type = ScanFeedbackType.ERROR)
            message = "Código de caja inválido o datos incompletos (KP de 1 dígito y piezas 1-${BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX})."
            messageIsError = true
            return
        }
        if (records.any { it.code == parsed.code }) {
            sound.playWarning()
            flashEvent = ScanFlashEvent(type = ScanFeedbackType.WARNING)
            showDuplicate = true
            return
        }
        sound.playSuccess()
        flashEvent = ScanFlashEvent(type = ScanFeedbackType.SUCCESS)
        scope.launch {
            val product = catalogRepo.lookupBarcode(parsed.code)
            val description = listOf(product.name, product.presentation)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            addItem(
                BoxCountItem(
                    code = parsed.code,
                    descripcion = description,
                    kp = kp,
                    piezas = pieces,
                    fechaHora = DateTimeUtils.currentTimestamp()
                )
            )
        }
    }

    DisposableEffect(context, scanEnabled) {
        val receiver = ZebraScannerReceiver { scanned ->
            if (scanEnabled && !isBusy()) handleScan(scanned)
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose { ZebraScannerReceiver.unregister(context, receiver) }
    }

    fun exit() {
        if (records.isNotEmpty()) showExitConfirm = true else onNavigateBack()
    }

    BackHandler { exit() }

    fun saveCount() {
        if (records.isEmpty()) return
        scope.launch {
            val dateCompact = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val folio = BoxCountFolio.build(dateCompact, dao.countFolios("CC-$dateCompact-") + 1)
            dao.insertAll(records.map {
                ConteoCajaEntity(
                    folio = folio,
                    codigo = it.code,
                    descripcion = it.descripcion,
                    kp = it.kp,
                    piezas = it.piezas,
                    fechaHora = it.fechaHora
                )
            })
            savedVale = BoxCountVale(folio, records)
            copiesPrinted = 0
            printStatusMessage = null
            showSavedDialog = true
            message = "Conteo guardado en el historial con folio $folio."
            messageIsError = false
        }
    }

    fun printCount(profile: PrinterProfile? = null) {
        withBluetoothPermission {
            val target = profile ?: activeProfile
            if (target == null && printerManager.getPairedPrinters().size > 1) {
                showPrinterSelector = true
                return@withBluetoothPermission
            }
            val vale = savedVale ?: return@withBluetoothPermission
            scope.launch {
                val outcome = ValePrintHelper.printBoxCount(context, vale, target)
                printStatusMessage = outcome.message
                if (outcome.success) copiesPrinted += 1
                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        AutoShrinkText(
                            text = "4. CONTEO EN CAJA",
                            fontWeight = FontWeight.Bold,
                            color = MineTextPrimary,
                            targetTextSize = 15.sp,
                            minTextSize = 12.sp
                        )
                        AutoShrinkText(
                            text = "$totalPieces piezas contadas",
                            color = MineSafetyYellow,
                            targetTextSize = 12.sp,
                            minTextSize = 9.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { exit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = { printCount() }, enabled = savedVale != null) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Imprimir conteo",
                            tint = if (savedVale != null) MineSafetyYellow else MineTextMuted
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoxCountMetric(Modifier.weight(1f), "TOTAL", records.size, MineBorderGray, MineTextPrimary)
                BoxCountMetric(Modifier.weight(1f), "CAJAS", records.size, MineHighExplosiveOrange, MineHighExplosiveOrange)
                BoxCountMetric(Modifier.weight(1f), "PIEZAS", totalPieces, MineSafetyYellow, MineSafetyYellow)
                BoxCountMetric(Modifier.weight(1f), "KP", kpInput.take(1).ifEmpty { "-" }, MineAgentCyan, MineAgentCyan)
            }

            AnimatedVisibility(
                visible = isHeaderExpanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(160))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                    border = BorderStroke(1.dp, MineBorderGray),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Datos de la caja", color = MineTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Listo para contar", color = MineSuccessGreenLight, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = kpInput,
                                onValueChange = { v ->
                                    kpInput = v.filter { it.isDigit() }.take(1)
                                    if (messageIsError) { messageIsError = false; message = "Llena los datos para habilitar escáner y entrada manual." }
                                },
                                label = { Text("Línea / KP") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = piecesInput,
                                onValueChange = { v ->
                                    piecesInput = v.filter { it.isDigit() }.take(4)
                                    if (messageIsError) { messageIsError = false; message = "Llena los datos para habilitar escáner y entrada manual." }
                                },
                                label = { Text("Piezas contadas") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            AlwaysActiveBarcodeField(
                value = barcodeInput,
                onValueChange = { barcodeInput = it },
                onSubmitBarcode = {
                    if (scanEnabled && !isBusy()) handleScan(it)
                },
                accentColor = MineHighExplosiveOrange,
                placeholderText = if (scanEnabled) "Escanear o escribir código + ENTER..." else "Llena los datos para habilitar escáner y entrada manual",
                focusRequester = focusRequester,
                testTagName = "box_count_input",
                enabled = scanEnabled,
                onActivityDetected = { isHeaderExpanded = true }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (displayedIsError) MineErrorRed.copy(alpha = 0.24f) else MineSuccessGreen.copy(alpha = 0.2f))
                    .border(1.dp, if (displayedIsError) MineErrorRed else MineSuccessGreen, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (displayedIsError) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (displayedIsError) MineErrorRedLight else MineSuccessGreenLight,
                    modifier = Modifier.size(15.dp)
                )
                Text(displayedMessage, color = MineTextPrimary, fontSize = 11.sp, maxLines = 2)
            }

            Text(
                text = "CÓDIGOS CONTADOS (${records.size})",
                color = MineTextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (records.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("Casilla activa: dispare el lector o escriba un código", color = MineTextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    items(records) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                            border = BorderStroke(1.dp, MineBorderGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(item.code, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(item.descripcion, color = MineTextSecondary, fontSize = 11.sp, maxLines = 2)
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = item.kp,
                                        onValueChange = { v ->
                                            val next = v.filter { it.isDigit() }.take(1)
                                            if (next.length == 1) records = records.map { if (it.code == item.code) it.copy(kp = next) else it }
                                        },
                                        label = { Text("KP") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    OutlinedTextField(
                                        value = item.piezas.toString(),
                                        onValueChange = { v ->
                                            val next = v.filter { it.isDigit() }.take(4).toIntOrNull()
                                            if (next != null && next in 1..BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX) {
                                                records = records.map { if (it.code == item.code) it.copy(piezas = next) else it }
                                            }
                                        },
                                        label = { Text("Piezas") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { itemToDelete = item }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Borrar código", tint = MineErrorRedLight)
                                    }
                                }
                                Text(item.fechaHora, color = MineTextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { printCount() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = savedVale != null,
                    border = BorderStroke(1.dp, MineSafetyYellow),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    AutoShrinkText("IMPRIMIR", fontWeight = FontWeight.Bold, targetTextSize = 12.sp, minTextSize = 9.sp, color = MineSafetyYellow, maxLines = 1)
                }
                Button(
                    onClick = { saveCount() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = records.isNotEmpty() && savedVale == null,
                    colors = ButtonDefaults.buttonColors(containerColor = MineSuccessGreen)
                ) {
                    AutoShrinkText(
                        text = "CONFIRMAR CONTEO",
                        fontWeight = FontWeight.Bold,
                        targetTextSize = 12.sp,
                        minTextSize = 9.sp,
                        color = MineTextPrimary,
                        maxLines = 1
                    )
                }
            }
        }
        ScanFlashOverlay(
            event = flashEvent,
            enabled = sound.isVisualFlashEnabled()
        )
    }

    if (showDuplicate) {
        AlertDialog(
            onDismissRequest = { showDuplicate = false },
            title = { Text("CÓDIGO REPETIDO") },
            text = { Text("Esa caja ya está en la lista. No se agregó otra vez.") },
            confirmButton = { TextButton(onClick = { showDuplicate = false }) { Text("Entendido") } }
        )
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("¿BORRAR ESTE CÓDIGO?") },
            text = { Text("Se quitará ${item.code} del conteo.") },
            confirmButton = {
                TextButton(onClick = {
                    records = records.filter { it.code != item.code }
                    itemToDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Conservar") } }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("¿SALIR DEL CONTEO?") },
            text = { Text("Se perderán ${records.size} conteos que aún no guardaste.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onNavigateBack()
                }) { Text("Salir") }
            },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Seguir contando") } }
        )
    }

    savedVale?.let { vale ->
        if (showSavedDialog) {
            val ticketPreview = remember(vale, activeProfile) {
                runCatching { ValePrintHelper.previewBoxCount(context, vale, activeProfile) }.getOrDefault("")
            }
            SavedValeReprintDialog(
                valeTypeTitle = "Conteo en caja",
                folio = vale.folio,
                itemCountSummary = "${vale.items.size} cajas · ${vale.totalPiezas} piezas",
                activeProfile = activeProfile,
                totalProfiles = settingsRepo.getPrinterProfiles().size,
                copiesPrinted = copiesPrinted,
                isPrinting = false,
                printStatusMessage = printStatusMessage,
                ticketPreview = ticketPreview,
                onPrintClick = { printCount(activeProfile) },
                onChangePrinterClick = { withBluetoothPermission { showPrinterSelector = true } },
                onFinishClick = {
                    showSavedDialog = false
                    onNavigateBack()
                }
            )
        }
    }

    if (showPrinterSelector) {
        QuickPrinterSelectorDialog(
            onSelectProfile = { selected ->
                activeProfile = selected
                showPrinterSelector = false
                Toast.makeText(context, "Impresora activa: ${selected.displayTitle()}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPrinterSelector = false }
        )
    }
}

@Composable
private fun BoxCountMetric(
    modifier: Modifier,
    label: String,
    value: Any,
    labelColor: Color,
    valueColor: Color
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = labelColor, fontSize = 10.sp)
    }
}
