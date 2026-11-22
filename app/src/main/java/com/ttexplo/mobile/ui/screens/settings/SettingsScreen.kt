package com.ttexplo.mobile.ui.screens.settings

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.domain.export.FlatFileFormatter
import com.ttexplo.mobile.domain.model.*
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.printer.BluetoothPrinterManager
import com.ttexplo.mobile.printer.PaperWidth
import com.ttexplo.mobile.printer.TicketGenerator
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.ScanFlashEvent
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as TTExploApp).database
    private val catalogRepo = CatalogRepositoryImpl(db.productoDao())
    val settingsRepo = SettingsRepository(application)
    val printerManager = BluetoothPrinterManager(application)

    private val _productCount = MutableStateFlow(0)
    val productCount: StateFlow<Int> = _productCount.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshCount()
    }

    fun refreshCount() {
        viewModelScope.launch {
            _productCount.value = catalogRepo.countProducts()
        }
    }

    fun loadDefaultAssetCatalog(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csvContent = context.assets.open("catalogo_productos.csv").bufferedReader().use { it.readText() }
                val imported = catalogRepo.importCsv(csvContent)
                _productCount.value = catalogRepo.countProducts()
                _statusMessage.value = "Catálogo recargado: $imported productos."
            } catch (e: Exception) {
                _statusMessage.value = "Error al cargar catálogo: ${e.message}"
            }
        }
    }

    fun importCsvFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val imported = catalogRepo.importCsv(content)
                _productCount.value = catalogRepo.countProducts()
                _statusMessage.value = "Catálogo actualizado: $imported productos importados."
            } catch (e: Exception) {
                _statusMessage.value = "Error al importar CSV: ${e.message}"
            }
        }
    }

    fun buildExportText(context: Context, onReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                val despachos = db.despachoDao().getAllDespachos()
                for (d in despachos) {
                    val items = db.despachoDao().getItemsForDespacho(d.id).map {
                        ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo, it.productoNombre, it.presentacion)
                    }
                    val rec = DespachoRecord(d.vale, d.empresa, d.lugar, d.transportista, d.turno, d.voladura, items)
                    sb.append(FlatFileFormatter.formatDespacho(rec)).append("\r\n")
                }

                val entradas = db.entradaDao().getAllEntradas()
                for (e in entradas) {
                    val items = db.entradaDao().getItemsForEntrada(e.id).map {
                        ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                    }
                    val rec = EntradaRecord(e.proveedor, e.polvorin, e.factura, items)
                    sb.append(FlatFileFormatter.formatEntrada(rec)).append("\r\n")
                }

                val accesorios = db.accesorioDao().getAllVales()
                for (a in accesorios) {
                    val items = db.accesorioDao().getItemsForVale(a.id).map {
                        AccessoryItemRecord(it.codigoAccesorio, it.cantidad, it.fechaEscaneo)
                    }
                    val rec = AccesoriosRecord(a.vale, a.empresa, a.lugar, a.transportista, a.turno, a.recibio, a.entrego, a.voladura, items)
                    sb.append(FlatFileFormatter.formatAccesorios(rec)).append("\r\n")
                }

                val devoluciones = db.devolucionDao().getAllDevoluciones()
                for (dev in devoluciones) {
                    val items = db.devolucionDao().getItemsForDevolucion(dev.id).map {
                        ScannedCodeRecord(it.codigoBarras, it.fechaEscaneo)
                    }
                    val rec = DevolucionRecord(dev.empleadoEntrega, dev.empleadoRecibe, items)
                    sb.append(FlatFileFormatter.formatDevolucion(rec)).append("\r\n")
                }

                withContext(Dispatchers.Main) {
                    onReady(sb.toString().trim())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error al compilar exportación: ${e.message}"
                }
            }
        }
    }

    fun sendTestPrint(format: String = "ESCPOS", onResult: (String) -> Unit) {
        val paired = printerManager.getPairedPrinters()
        if (paired.isEmpty()) {
            onResult("No hay dispositivos Bluetooth vinculados en Android.")
            return
        }
        val target = printerManager.findPreferredPrinter() ?: paired.first()
        val targetName = try { target.name ?: target.address } catch (_: Exception) { target.address }

        viewModelScope.launch {
            val bytes = when (format) {
                "CPCL" -> TicketGenerator.generateCpclTestTicket(
                    paperWidth = settingsRepo.getPaperWidth(),
                    mineName = settingsRepo.getMineName()
                )
                "PLAIN" -> TicketGenerator.generatePlainTestTicket(
                    paperWidth = settingsRepo.getPaperWidth(),
                    mineName = settingsRepo.getMineName()
                )
                else -> TicketGenerator.generateTestTicket(
                    paperWidth = settingsRepo.getPaperWidth(),
                    mineName = settingsRepo.getMineName(),
                    permitNumber = settingsRepo.getPermitNumber()
                )
            }
            val result = printerManager.printBytes(target.address, bytes)
            if (result.isSuccess) {
                onResult("¡Ticket de prueba [$format] enviado exitosamente a $targetName!")
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Error de comunicación"
                onResult("Error al imprimir en $targetName: $err")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateCatalog: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateToLicense: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val productCount by viewModel.productCount.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    var licenseCheck by remember { mutableStateOf(com.ttexplo.mobile.license.LicenseManager.checkLicense(context)) }
    val formattedDeviceId = remember { com.ttexplo.mobile.license.LicenseManager.getFormattedDeviceId(context) }
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                licenseCheck = com.ttexplo.mobile.license.LicenseManager.checkLicense(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var mineName by remember { mutableStateOf(viewModel.settingsRepo.getMineName()) }
    var permitNumber by remember { mutableStateOf(viewModel.settingsRepo.getPermitNumber()) }
    var ticketHeader by remember { mutableStateOf(viewModel.settingsRepo.getTicketHeader()) }
    var paperWidth by remember { mutableStateOf(viewModel.settingsRepo.getPaperWidth()) }

    var preferredMac by remember { mutableStateOf(viewModel.settingsRepo.getPreferredPrinterMac()) }
    var preferredName by remember { mutableStateOf(viewModel.settingsRepo.getPreferredPrinterName()) }
    var printerProtocol by remember { mutableStateOf(viewModel.settingsRepo.getPrinterProtocol()) }
    var printSignatures by remember { mutableStateOf(viewModel.settingsRepo.getPrintSignatures()) }
    var printBreakdown by remember { mutableStateOf(viewModel.settingsRepo.getPrintBreakdown()) }
    var printCodeDetails by remember { mutableStateOf(viewModel.settingsRepo.getPrintCodeDetails()) }

    var showPrinterDialog by remember { mutableStateOf(false) }
    var isTestingPrint by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var previewFlashEvent by remember { mutableStateOf<ScanFlashEvent?>(null) }

    val previewSoundManager = remember(context) { SoundFeedbackManager(context.applicationContext) }
    DisposableEffect(previewSoundManager) {
        onDispose { previewSoundManager.release() }
    }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importCsvFromUri(context, it) }
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
                Toast.makeText(context, "Archivo plano guardado exitosamente", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else true
        if (granted) {
            showPrinterDialog = true
        } else {
            Toast.makeText(context, "Se requiere permiso de Bluetooth para listar impresoras", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoShrinkText(
                            text = "Configuración y Sistema",
                            color = MineTextPrimary,
                            fontWeight = FontWeight.Bold,
                            targetTextSize = 18.sp,
                            minTextSize = 14.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MineSafetyYellow.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MineSafetyYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MineTextPrimary
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
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            statusMsg?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MineSafetyYellow)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        color = MineSafetyYellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ==========================================
            // SECCIÓN 0: AUDIO, VIBRACIÓN Y ALERTAS VISUALES
            // ==========================================
            AudioSettingsCard(
                settingsRepo = viewModel.settingsRepo,
                soundManager = previewSoundManager,
                onPreviewFlash = { type ->
                    previewFlashEvent = ScanFlashEvent(type = type)
                }
            )

            // ==========================================
            // SECCIÓN 1: IMPRESORA TÉRMICA & FORMATO
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = MineSafetyYellow)
                        Text(
                            text = "IMPRESORA TÉRMICA Y FORMATO",
                            style = MaterialTheme.typography.labelLarge,
                            color = MineSafetyYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Impresora vinculada
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MineDarkBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AutoShrinkText(
                                    text = if (preferredName.isNotBlank()) preferredName else "Sin impresora predeterminada",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MineTextPrimary,
                                    targetTextSize = 13.sp,
                                    minTextSize = 10.sp
                                )
                                AutoShrinkText(
                                    text = if (preferredMac.isNotBlank()) preferredMac else "Se usará la primera impresora Bluetooth vinculada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MineTextSecondary,
                                    targetTextSize = 11.sp,
                                    minTextSize = 8.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val hasPerm = viewModel.printerManager.hasBluetoothPermission()
                                    if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        btPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.BLUETOOTH_CONNECT,
                                                Manifest.permission.BLUETOOTH_SCAN
                                            )
                                        )
                                    } else {
                                        showPrinterDialog = true
                                    }
                                },
                                modifier = Modifier.testTag("settings_pair_printer_button"),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow, contentColor = MineDarkBackground)
                            ) {
                                AutoShrinkText("Vincular", fontWeight = FontWeight.Bold, targetTextSize = 11.sp, minTextSize = 8.sp)
                            }
                        }
                    }

                    // Ancho de papel
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ancho de papel:", color = MineTextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = paperWidth == PaperWidth.WIDTH_3_INCH,
                                onClick = {
                                    paperWidth = PaperWidth.WIDTH_3_INCH
                                    viewModel.settingsRepo.setPaperWidth(PaperWidth.WIDTH_3_INCH)
                                },
                                label = { AutoShrinkText("3 Pulgadas (80mm)", targetTextSize = 11.sp, minTextSize = 8.sp) }
                            )
                            FilterChip(
                                selected = paperWidth == PaperWidth.WIDTH_4_INCH,
                                onClick = {
                                    paperWidth = PaperWidth.WIDTH_4_INCH
                                    viewModel.settingsRepo.setPaperWidth(PaperWidth.WIDTH_4_INCH)
                                },
                                label = { AutoShrinkText("4 Pulgadas (104mm)", targetTextSize = 11.sp, minTextSize = 8.sp) }
                            )
                        }
                    }

                    // Protocolo de impresión
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Formato / Protocolo del ticket:", color = MineTextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = printerProtocol == "CPCL",
                                onClick = {
                                    printerProtocol = "CPCL"
                                    viewModel.settingsRepo.setPrinterProtocol("CPCL")
                                },
                                label = { AutoShrinkText("Zebra CPCL", targetTextSize = 11.sp, minTextSize = 8.sp) }
                            )
                            FilterChip(
                                selected = printerProtocol == "ESCPOS",
                                onClick = {
                                    printerProtocol = "ESCPOS"
                                    viewModel.settingsRepo.setPrinterProtocol("ESCPOS")
                                },
                                label = { AutoShrinkText("Térmica ESC/POS", targetTextSize = 11.sp, minTextSize = 8.sp) }
                            )
                            FilterChip(
                                selected = printerProtocol == "PLAIN",
                                onClick = {
                                    printerProtocol = "PLAIN"
                                    viewModel.settingsRepo.setPrinterProtocol("PLAIN")
                                },
                                label = { AutoShrinkText("Texto RAW", targetTextSize = 11.sp, minTextSize = 8.sp) }
                            )
                        }
                    }

                    // Switches de opciones
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText("Incluir líneas de firmas:", modifier = Modifier.weight(1f), color = MineTextPrimary, targetTextSize = 13.sp, minTextSize = 9.sp)
                        Switch(
                            checked = printSignatures,
                            onCheckedChange = {
                                printSignatures = it
                                viewModel.settingsRepo.setPrintSignatures(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MineSafetyYellow,
                                checkedTrackColor = MineSafetyYellow.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText("Desglose Cajas / Sacos / Cartuchos:", modifier = Modifier.weight(1f), color = MineTextPrimary, targetTextSize = 13.sp, minTextSize = 8.sp)
                        Switch(
                            checked = printBreakdown,
                            onCheckedChange = {
                                printBreakdown = it
                                viewModel.settingsRepo.setPrintBreakdown(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MineSafetyYellow,
                                checkedTrackColor = MineSafetyYellow.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText("Listado de códigos con fecha/hora:", modifier = Modifier.weight(1f), color = MineTextPrimary, targetTextSize = 13.sp, minTextSize = 8.sp)
                        Switch(
                            checked = printCodeDetails,
                            onCheckedChange = {
                                printCodeDetails = it
                                viewModel.settingsRepo.setPrintCodeDetails(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MineSafetyYellow,
                                checkedTrackColor = MineSafetyYellow.copy(alpha = 0.5f)
                            )
                        )
                    }

                    // Panel de pruebas directas
                    Text(
                        text = "Pruebas directas de impresión:",
                        color = MineTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Si la impresora recibe el dato pero no avanza papel, pruebe con 'Texto Plano' o 'ESC/POS' para verificar la respuesta del cabezal.",
                        color = MineTextMuted,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val hasPerm = viewModel.printerManager.hasBluetoothPermission()
                                if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    btPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                                } else {
                                    isTestingPrint = true
                                    viewModel.sendTestPrint("ESCPOS") { msg ->
                                        isTestingPrint = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isTestingPrint,
                            modifier = Modifier.weight(1f).height(42.dp).testTag("settings_test_escpos_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MineSafetyYellow),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                        ) {
                            AutoShrinkText("Prueba ESC/POS", fontWeight = FontWeight.Bold, targetTextSize = 11.sp, minTextSize = 8.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val hasPerm = viewModel.printerManager.hasBluetoothPermission()
                                if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    btPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                                } else {
                                    isTestingPrint = true
                                    viewModel.sendTestPrint("PLAIN") { msg ->
                                        isTestingPrint = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isTestingPrint,
                            modifier = Modifier.weight(1f).height(42.dp).testTag("settings_test_plain_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MineAgentCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineAgentCyan)
                        ) {
                            AutoShrinkText("Prueba Texto", fontWeight = FontWeight.Bold, targetTextSize = 11.sp, minTextSize = 8.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val hasPerm = viewModel.printerManager.hasBluetoothPermission()
                                if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    btPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                                } else {
                                    isTestingPrint = true
                                    viewModel.sendTestPrint("CPCL") { msg ->
                                        isTestingPrint = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isTestingPrint,
                            modifier = Modifier.weight(1f).height(42.dp).testTag("settings_test_cpcl_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MineTextSecondary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineTextPrimary)
                        ) {
                            AutoShrinkText("Prueba CPCL", fontWeight = FontWeight.Bold, targetTextSize = 11.sp, minTextSize = 8.sp)
                        }
                    }
                }
            }

            // ==========================================
            // SECCIÓN 2: DATOS DE MINA Y ENCABEZADOS
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, tint = MineSafetyYellow)
                        Text(
                            text = "DATOS DE LA MINA Y ENCABEZADOS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MineSafetyYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = mineName,
                        onValueChange = {
                            mineName = it
                            viewModel.settingsRepo.setMineName(it)
                        },
                        label = { Text("Nombre de la Mina / Empresa") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_mine_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MineSafetyYellow,
                            focusedTextColor = MineTextPrimary,
                            unfocusedTextColor = MineTextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = permitNumber,
                        onValueChange = {
                            permitNumber = it
                            viewModel.settingsRepo.setPermitNumber(it)
                        },
                        label = { Text("Número de Permiso General (SEDENA)") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_permit_number_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MineSafetyYellow,
                            focusedTextColor = MineTextPrimary,
                            unfocusedTextColor = MineTextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = ticketHeader,
                        onValueChange = {
                            ticketHeader = it
                            viewModel.settingsRepo.setTicketHeader(it)
                        },
                        label = { Text("Encabezado Adicional / Nota Polvorín") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_ticket_header_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MineSafetyYellow,
                            focusedTextColor = MineTextPrimary,
                            unfocusedTextColor = MineTextPrimary
                        )
                    )
                }
            }

            // ==========================================
            // SECCIÓN 3: CATÁLOGO DE EXPLOSIVOS
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = MineSafetyYellow)
                            AutoShrinkText(
                                text = "CATÁLOGO DE PRODUCTOS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MineSafetyYellow,
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 14.sp,
                                minTextSize = 10.sp,
                                maxLines = 2,
                                softWrap = true
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MineSafetyYellow.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            AutoShrinkText(
                                text = "$productCount activos",
                                color = MineSafetyYellow,
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 11.sp,
                                minTextSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateCatalog,
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("settings_view_catalog_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MineDarkBackground, contentColor = MineTextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MineSafetyYellow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        AutoShrinkText("Ver Catálogo Completo y Consultar Códigos", targetTextSize = 12.sp, minTextSize = 9.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.loadDefaultAssetCatalog(context) },
                            modifier = Modifier.weight(1f).height(40.dp).testTag("settings_reload_catalog_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MineTextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            AutoShrinkText("Recargar Base", color = MineTextPrimary, targetTextSize = 11.sp, minTextSize = 8.sp)
                        }

                        OutlinedButton(
                            onClick = { csvPicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/csv")) },
                            modifier = Modifier.weight(1f).height(40.dp).testTag("settings_import_csv_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MineSafetyYellow)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = MineSafetyYellow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            AutoShrinkText("Importar CSV", color = MineSafetyYellow, targetTextSize = 11.sp, minTextSize = 8.sp)
                        }
                    }
                }
            }

            // ==========================================
            // SECCIÓN 4: EXPORTACIÓN & ARCHIVO PLANO
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = MineSuccessGreen)
                        Text(
                            text = "EXPORTACIÓN Y ARCHIVO PLANO (.TXT)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MineSuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.buildExportText(context) { text ->
                                if (text.isBlank()) {
                                    Toast.makeText(context, "No hay movimientos registrados para exportar", Toast.LENGTH_SHORT).show()
                                } else {
                                    pendingExportText = text
                                    val timestamp = DateTimeUtils.currentTimestamp().replace(":", "-").replace(" ", "_")
                                    createFileLauncher.launch("TTEXPLO_$timestamp.txt")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_export_txt_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MineSuccessGreen, contentColor = MineTextPrimary)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        AutoShrinkText(
                            text = "GUARDAR ARCHIVO PLANO (CARPETA / USB)",
                            fontWeight = FontWeight.Bold,
                            targetTextSize = 12.sp,
                            minTextSize = 9.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateHistory,
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("settings_view_history_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MineTextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MineSafetyYellow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        AutoShrinkText("Consultar Historial de Vales & Consolidado", targetTextSize = 12.sp, minTextSize = 9.sp)
                    }
                }
            }

            // ==========================================
            // SECCIÓN 5: LICENCIA Y DISPOSITIVO (HARDWARE ID)
            // ==========================================
            val isLicActive = licenseCheck.status == com.ttexplo.mobile.license.LicenseStatus.VALID ||
                    licenseCheck.status == com.ttexplo.mobile.license.LicenseStatus.EXPIRING_SOON
            val licColor = when (licenseCheck.status) {
                com.ttexplo.mobile.license.LicenseStatus.VALID -> MineSuccessGreen
                com.ttexplo.mobile.license.LicenseStatus.EXPIRING_SOON -> MineHighExplosiveOrange
                else -> MineErrorRed
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, licColor.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = MineAgentCyan)
                            AutoShrinkText(
                                text = "LICENCIA Y HARDWARE ID",
                                style = MaterialTheme.typography.labelLarge,
                                color = MineAgentCyan,
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 14.sp,
                                minTextSize = 10.sp,
                                maxLines = 2,
                                softWrap = true
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(licColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            AutoShrinkText(
                                text = when (licenseCheck.status) {
                                    com.ttexplo.mobile.license.LicenseStatus.VALID -> "AUTORIZADA"
                                    com.ttexplo.mobile.license.LicenseStatus.EXPIRING_SOON -> "VENCE EN ${licenseCheck.daysRemaining}D"
                                    com.ttexplo.mobile.license.LicenseStatus.EXPIRED -> "VENCIDA"
                                    com.ttexplo.mobile.license.LicenseStatus.DEVICE_MISMATCH -> "TERMINAL NO AUTORIZADA"
                                    com.ttexplo.mobile.license.LicenseStatus.CORRUPTED -> "FIRMA INVÁLIDA"
                                    com.ttexplo.mobile.license.LicenseStatus.UNLICENSED -> "SIN LICENCIA"
                                },
                                color = licColor,
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 11.sp,
                                minTextSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Fila con el Android ID y botón copiar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MineDarkBackground)
                            .border(1.dp, MineBorderGray, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "IDENTIFICADOR ÚNICO DE ESTE DISPOSITIVO:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MineTextMuted,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedDeviceId,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MineSafetyYellow,
                                fontSize = 15.sp
                            )
                            Button(
                                onClick = {
                                    clipboardManager.setPrimaryClip(
                                        android.content.ClipData.newPlainText("Device ID", formattedDeviceId)
                                    )
                                    Toast.makeText(context, "ID copiado: $formattedDeviceId", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("settings_copy_device_id_button"),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MineAgentCyan, contentColor = MineDarkBackground),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPIAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Información de cliente si está activo
                    licenseCheck.info?.let { info ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cliente autorizado:", color = MineTextSecondary, fontSize = 11.sp)
                            Text(info.client, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Unidad Minera:", color = MineTextSecondary, fontSize = 11.sp)
                            Text(info.mine, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vigencia / Caducidad:", color = MineTextSecondary, fontSize = 11.sp)
                            Text(
                                if (info.expiresDate == "PERPETUAL") "VITALICIA" else info.expiresDate.take(10),
                                color = licColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToLicense,
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("settings_manage_license_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLicActive) MineDarkBackground else MineSafetyYellow,
                            contentColor = if (isLicActive) MineTextPrimary else MineDarkBackground
                        ),
                        border = if (isLicActive) androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray) else null
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = if (isLicActive) MineSafetyYellow else MineDarkBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        AutoShrinkText(
                            text = if (isLicActive) "Gestionar o Renovar Licencia" else "Activar Licencia Ahora",
                            fontWeight = FontWeight.Bold,
                            targetTextSize = 12.sp,
                            minTextSize = 9.sp
                        )
                    }
                }
            }

            // ==========================================
            // SECCIÓN 6: INFORMACIÓN DEL SISTEMA
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MineSafetyYellow)
                        Text(
                            text = "INFORMACIÓN DEL SISTEMA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MineSafetyYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = MineBorderGray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText("Versión instalada:", modifier = Modifier.weight(0.45f), color = MineTextSecondary, targetTextSize = 12.sp, minTextSize = 8.sp)
                        AutoShrinkText("v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", modifier = Modifier.weight(0.55f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = MineTextPrimary, fontWeight = FontWeight.Bold, targetTextSize = 12.sp, minTextSize = 8.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText("Salida térmica:", modifier = Modifier.weight(0.4f), color = MineTextSecondary, targetTextSize = 12.sp, minTextSize = 8.sp)
                        AutoShrinkText("Zebra CPCL • 4\" y 3\" • POS RAW", modifier = Modifier.weight(0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = MineTextPrimary, fontWeight = FontWeight.Bold, targetTextSize = 12.sp, minTextSize = 8.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText("Lector integrado:", modifier = Modifier.weight(0.45f), color = MineTextSecondary, targetTextSize = 12.sp, minTextSize = 8.sp)
                        AutoShrinkText("Zebra SE4710 / Cámara Trasera", modifier = Modifier.weight(0.55f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = MineSuccessGreenLight, fontWeight = FontWeight.Bold, targetTextSize = 12.sp, minTextSize = 8.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText("Base de datos:", modifier = Modifier.weight(0.45f), color = MineTextSecondary, targetTextSize = 12.sp, minTextSize = 8.sp)
                        AutoShrinkText("100% Offline (SQLite Room)", modifier = Modifier.weight(0.55f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = MineAgentCyan, fontWeight = FontWeight.Bold, targetTextSize = 12.sp, minTextSize = 8.sp)
                    }
                }
            }
        }
    }
    ScanFlashOverlay(
        event = previewFlashEvent,
        enabled = true
    )
    }

    if (showPrinterDialog) {
        var pairedList by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

        fun refreshDevices() {
            pairedList = viewModel.printerManager.getPairedPrinters()
        }

        LaunchedEffect(Unit) {
            refreshDevices()
        }

        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Impresoras Bluetooth", color = MineTextPrimary, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { refreshDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = MineSafetyYellow)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!viewModel.printerManager.isBluetoothEnabled()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MineErrorRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .border(1.dp, MineErrorRed, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                "El Bluetooth del teléfono está apagado. Por favor actívelo para detectar su impresora.",
                                color = MineErrorRed,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (!viewModel.printerManager.hasBluetoothPermission()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MineWarningAmber.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .border(1.dp, MineWarningAmber, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                "Permiso de Bluetooth no concedido en el teléfono.",
                                color = MineWarningAmber,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (pairedList.isEmpty()) {
                        Text(
                            "No se detectaron dispositivos vinculados en Android. Asegúrese de haber emparejado la impresora en la configuración de Bluetooth de su teléfono/escáner primero.",
                            color = MineTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Abra Ajustes > Bluetooth en su teléfono", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow, contentColor = MineDarkBackground),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir Ajustes de Bluetooth", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            "Dispositivos vinculados encontrados (${pairedList.size}):",
                            color = MineTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Column(
                            modifier = Modifier
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pairedList.forEach { dev ->
                                val isSelected = dev.address.equals(preferredMac, ignoreCase = true)
                                val devName = try { dev.name ?: "Dispositivo sin nombre" } catch (e: SecurityException) { "Dispositivo Bluetooth" }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            preferredMac = dev.address
                                            preferredName = devName
                                            viewModel.settingsRepo.setPreferredPrinterMac(dev.address)
                                            viewModel.settingsRepo.setPreferredPrinterName(preferredName)
                                            showPrinterDialog = false
                                            Toast.makeText(context, "Impresora seleccionada: $preferredName", Toast.LENGTH_SHORT).show()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MineDarkBackground else MineDarkSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MineSafetyYellow else MineBorderGray)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, tint = if (isSelected) MineSafetyYellow else MineTextMuted)
                                        Column(modifier = Modifier.weight(1f)) {
                                            AutoShrinkText(devName, color = MineTextPrimary, fontWeight = FontWeight.Bold, targetTextSize = 13.sp, minTextSize = 9.sp)
                                            AutoShrinkText(dev.address, color = MineTextSecondary, targetTextSize = 11.sp, minTextSize = 8.sp)
                                        }
                                        if (isSelected) {
                                            Text("Activa", color = MineSafetyYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (preferredMac.isNotBlank()) {
                    TextButton(onClick = {
                        preferredMac = ""
                        preferredName = ""
                        viewModel.settingsRepo.setPreferredPrinterMac("")
                        viewModel.settingsRepo.setPreferredPrinterName("")
                        showPrinterDialog = false
                    }) {
                        Text("Desvincular", color = MineErrorRed)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrinterDialog = false }) {
                    Text("Cerrar", color = MineTextMuted)
                }
            },
            containerColor = MineDarkSurface
        )
    }
}
