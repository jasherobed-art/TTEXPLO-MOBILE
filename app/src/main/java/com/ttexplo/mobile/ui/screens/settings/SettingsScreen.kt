package com.ttexplo.mobile.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
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
    var lockPortrait by remember { mutableStateOf(viewModel.settingsRepo.getLockPortrait()) }

    var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var previewFlashEvent by remember { mutableStateOf<ScanFlashEvent?>(null) }
    var expandedSections by remember { mutableStateOf(setOf("license")) }
    fun toggleSection(key: String) {
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }

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
            pendingBtAction?.invoke()
            pendingBtAction = null
        } else {
            pendingBtAction = null
            Toast.makeText(context, "Se requiere permiso de Bluetooth para gestionar impresoras", Toast.LENGTH_LONG).show()
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
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
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
            // SECCIÓN 0: PANTALLA
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MANTENER PANTALLA EN VERTICAL",
                            color = MineTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Evita que la pantalla gire sola. Desactívalo si necesitas ver en horizontal.",
                            color = MineTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = lockPortrait,
                        onCheckedChange = { enabled ->
                            lockPortrait = enabled
                            viewModel.settingsRepo.setLockPortrait(enabled)
                            (context as? Activity)?.requestedOrientation = if (enabled) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    )
                }
            }

            // ==========================================
            // SECCIÓN 0: AUDIO, VIBRACIÓN Y ALERTAS VISUALES
            // ==========================================
            CollapsibleSettingsSection("AUDIO, VIBRACIÓN Y ALERTAS VISUALES", "audio" in expandedSections, { toggleSection("audio") }) {
                AudioSettingsCard(
                    settingsRepo = viewModel.settingsRepo,
                    soundManager = previewSoundManager,
                    onPreviewFlash = { type ->
                        previewFlashEvent = ScanFlashEvent(type = type)
                    }
                )
            }

            // ==========================================
            // SECCIÓN 1: MÚLTIPLES IMPRESORAS TÉRMICAS & FORMATO
            // ==========================================
            CollapsibleSettingsSection("MÚLTIPLES IMPRESORAS TÉRMICAS & FORMATO", "printer" in expandedSections, { toggleSection("printer") }) {
                PrinterProfilesSettingsCard(
                    settingsRepo = viewModel.settingsRepo,
                    printerManager = viewModel.printerManager,
                    onRequestBluetoothPermission = { onGranted ->
                        val hasPerm = viewModel.printerManager.hasBluetoothPermission()
                        if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            pendingBtAction = onGranted
                            btPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            )
                        } else {
                            onGranted()
                        }
                    }
                )
            }

            // ==========================================
            // SECCIÓN 2: DATOS DE MINA Y ENCABEZADOS
            // ==========================================
            CollapsibleSettingsSection("DATOS DE MINA Y ENCABEZADOS", "mine" in expandedSections, { toggleSection("mine") }) {
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
            }

            // ==========================================
            // SECCIÓN 3: CATÁLOGO DE EXPLOSIVOS
            // ==========================================
            CollapsibleSettingsSection("CATÁLOGO DE PRODUCTOS", "catalog" in expandedSections, { toggleSection("catalog") }) {
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
            }

            // ==========================================
            // SECCIÓN 4: EXPORTACIÓN & ARCHIVO PLANO
            // ==========================================
            CollapsibleSettingsSection("EXPORTACIÓN Y ARCHIVO PLANO", "export" in expandedSections, { toggleSection("export") }) {
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

            CollapsibleSettingsSection("LICENCIA Y HARDWARE ID", "license" in expandedSections, { toggleSection("license") }) {
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
            }

            // ==========================================
            // SECCIÓN 6: INFORMACIÓN DEL SISTEMA
            // ==========================================
            CollapsibleSettingsSection("INFORMACIÓN DEL SISTEMA", "system" in expandedSections, { toggleSection("system") }) {
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
                        AutoShrinkText("CPCL • ESC/POS • 58/80/104mm (2\"/3\"/4\")", modifier = Modifier.weight(0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = MineTextPrimary, fontWeight = FontWeight.Bold, targetTextSize = 12.sp, minTextSize = 8.sp)
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
    }
    ScanFlashOverlay(
        event = previewFlashEvent,
        enabled = true
    )
    }
}

@Composable
private fun CollapsibleSettingsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MineDarkSurface)
                .border(1.dp, MineBorderGray, RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = MineSafetyYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Colapsar $title" else "Expandir $title",
                tint = MineSafetyYellow
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
