package com.ttexplo.mobile.ui.screens.inventory

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttexplo.mobile.domain.inventory.QrDensityMode
import com.ttexplo.mobile.domain.inventory.SyncExportPackage
import com.ttexplo.mobile.domain.inventory.SyncExportScope
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventorySyncScreen(
    initialTab: Int = 0,
    onNavigateBack: () -> Unit,
    onInspectProduct: (String) -> Unit,
    viewModel: InventorySyncViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var pendingJsonPackage by remember { mutableStateOf<SyncExportPackage?>(null) }

    // Auto-carrusel QR cuando está activo en la pestaña Enviar
    LaunchedEffect(uiState.isAutoPlay, uiState.autoPlayIntervalMs, selectedTab, uiState.exportPackage) {
        val total = uiState.exportPackage?.qrPackage?.chunks?.size ?: 0
        if (uiState.isAutoPlay && selectedTab == 0 && total > 1) {
            while (true) {
                delay(uiState.autoPlayIntervalMs)
                viewModel.nextQr()
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val pkg = pendingJsonPackage
        if (uri != null && pkg != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pkg.jsonPretty.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                viewModel.recordJsonExported(pkg, markPendingAsSynced = true)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar JSON: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }.orEmpty()
                viewModel.importJsonFileContent(text)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al abrir JSON: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        AutoShrinkText(
                            text = "Sincronización Multi-Escáner",
                            initialTextSize = 18.sp,
                            minTextSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MineTextPrimary
                        )
                        AutoShrinkText(
                            text = "100% Offline • ID: ${uiState.deviceId}",
                            initialTextSize = 11.sp,
                            minTextSize = 9.sp,
                            color = MineAgentCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("sync_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar a inventario",
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
        ) {
            // PESTAÑAS PRINCIPALES
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MineDarkSurface,
                contentColor = MineAgentCyan
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("sync_tab_send"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Enviar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    selectedContentColor = MineAgentCyan,
                    unselectedContentColor = MineTextSecondary
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        viewModel.stopAutoPlay()
                        selectedTab = 1
                    },
                    modifier = Modifier.testTag("sync_tab_receive"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Recibir", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    selectedContentColor = MineSafetyYellow,
                    unselectedContentColor = MineTextSecondary
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        viewModel.stopAutoPlay()
                        selectedTab = 2
                    },
                    modifier = Modifier.testTag("sync_tab_conflicts"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val conflictCount = uiState.activeConflicts.size
                            Icon(
                                imageVector = if (conflictCount > 0) Icons.Default.WarningAmber else Icons.Default.History,
                                contentDescription = null,
                                tint = if (conflictCount > 0) MineErrorRedLight else LocalContentColor.current,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (conflictCount > 0) "Alertas ($conflictCount)" else "Historial",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (conflictCount > 0) MineErrorRedLight else LocalContentColor.current
                            )
                        }
                    },
                    selectedContentColor = if (uiState.activeConflicts.isNotEmpty()) MineErrorRedLight else MineSuccessGreenLight,
                    unselectedContentColor = MineTextSecondary
                )
            }

            // BANNER GLOBAL DE CONFIRMACIÓN / ALERTA
            AnimatedVisibility(visible = uiState.bannerMessage != null) {
                uiState.bannerMessage?.let { msg ->
                    val bg = if (uiState.bannerIsError) MineErrorRedBackground else MineSuccessGreen.copy(alpha = 0.2f)
                    val border = if (uiState.bannerIsError) MineErrorRed else MineSuccessGreenLight
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(1.dp, border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.bannerIsError) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (uiState.bannerIsError) MineErrorRedLight else MineSuccessGreenLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = msg,
                            color = MineTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearBanner() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar aviso",
                                tint = MineTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // CONTENIDO DE LA PESTAÑA ACTIVA
            when (selectedTab) {
                0 -> SendSyncTab(
                    uiState = uiState,
                    onSelectScope = { viewModel.selectExportScope(it) },
                    onSelectDensity = { viewModel.selectDensityMode(it) },
                    onPrevQr = { viewModel.prevQr() },
                    onNextQr = { viewModel.nextQr() },
                    onToggleAutoPlay = { viewModel.toggleAutoPlay() },
                    onToggleSpeed = { viewModel.toggleAutoPlayInterval() },
                    onMarkShared = { viewModel.confirmQrShared(markPendingAsSynced = true) },
                    onExportJson = { pkg ->
                        pendingJsonPackage = pkg
                        val ts = DateTimeUtils.currentTimestamp().replace(":", "-").replace(" ", "_")
                        exportJsonLauncher.launch("TTEXPLO_SYNC_${pkg.qrPackage.sessionId}_$ts.json")
                    }
                )
                1 -> ReceiveSyncTab(
                    uiState = uiState,
                    onScannerData = { viewModel.onScannerInputReceived(it) },
                    onResetReceiver = { viewModel.resetQrReceiver() },
                    onPickJsonFile = {
                        importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onSimulateSingleQr = { viewModel.simulateSingleQrDemo(includeConflict = true) },
                    onSimulateMultiPartStep = { viewModel.simulateNextDemoQr(includeConflict = true) },
                    onJumpToConflicts = { selectedTab = 2 }
                )
                2 -> ConflictsAndHistoryTab(
                    uiState = uiState,
                    onInspectProduct = onInspectProduct,
                    onMarkConflictReviewed = { viewModel.markConflictReviewed(it) },
                    onRestoreReviewed = { viewModel.restoreReviewedConflicts() }
                )
            }
        }
    }
}

@Composable
private fun SendSyncTab(
    uiState: InventorySyncUiState,
    onSelectScope: (SyncExportScope) -> Unit,
    onSelectDensity: (QrDensityMode) -> Unit,
    onPrevQr: () -> Unit,
    onNextQr: () -> Unit,
    onToggleAutoPlay: () -> Unit,
    onToggleSpeed: () -> Unit,
    onMarkShared: () -> Unit,
    onExportJson: (SyncExportPackage) -> Unit
) {
    val pkg = uiState.exportPackage
    val totalEvents = (pkg?.eventUuids?.size ?: 0) + (pkg?.codeEventUuids?.size ?: 0)
    val chunks = pkg?.qrPackage?.chunks.orEmpty()
    val safeIdx = uiState.activeQrIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
    val currentChunk = chunks.getOrNull(safeIdx)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Selector de alcance del paquete
        Text(
            text = "ALCANCE DEL PAQUETE A COMPARTIR",
            color = MineAgentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SyncExportScope.entries.forEach { scope ->
                val selected = uiState.selectedScope == scope
                FilterChip(
                    selected = selected,
                    onClick = { onSelectScope(scope) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sync_scope_${scope.name.lowercase()}"),
                    label = {
                        AutoShrinkText(
                            text = when (scope) {
                                SyncExportScope.PENDING_ONLY -> "Pendientes"
                                SyncExportScope.LAST_24_HOURS -> "24 horas"
                                SyncExportScope.FULL_HISTORY -> "Todo"
                            },
                            initialTextSize = 12.sp,
                            minTextSize = 10.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selected) MineDarkBackground else MineTextPrimary,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MineAgentCyan,
                        selectedLabelColor = MineDarkBackground,
                        containerColor = MineDarkSurface,
                        labelColor = MineTextSecondary
                    )
                )
            }
        }

        // Selector discreto de capacidad QR V3 (Alta capacidad 1,400 por defecto vs Lectura fácil 600)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DENSIDAD:",
                color = MineTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            QrDensityMode.entries.forEach { mode ->
                val selected = uiState.densityMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onSelectDensity(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sync_density_${mode.name.lowercase()}"),
                    label = {
                        AutoShrinkText(
                            text = mode.shortLabel,
                            initialTextSize = 11.sp,
                            minTextSize = 9.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selected) MineDarkBackground else MineTextSecondary,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MineSafetyYellow,
                        selectedLabelColor = MineDarkBackground,
                        containerColor = MineDarkSurface,
                        labelColor = MineTextSecondary
                    )
                )
            }
        }

        if (uiState.isBuildingExport) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MineAgentCyan)
            }
            return@Column
        }

        if (pkg == null || totalEvents == 0 || currentChunk == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = BorderStroke(1.dp, MineBorderGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MineSuccessGreenLight,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (uiState.selectedScope == SyncExportScope.PENDING_ONLY) {
                            "Todo sincronizado — sin eventos pendientes nuevos"
                        } else {
                            "No hay movimientos registrados en este rango"
                        },
                        color = MineTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Si vas a cargar un escáner nuevo o re-enviar un turno anterior, cambia el alcance a '24 horas' o 'Todo'.",
                        color = MineTextSecondary,
                        fontSize = 12.sp
                    )
                    if (uiState.selectedScope != SyncExportScope.FULL_HISTORY) {
                        OutlinedButton(
                            onClick = { onSelectScope(SyncExportScope.FULL_HISTORY) },
                            border = BorderStroke(1.dp, MineAgentCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineAgentCyan),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("EMPAQUETAR HISTORIAL COMPLETO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            return@Column
        }

        // Resumen del paquete comprimido V3 + insignia Alta Capacidad Base45
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MineDarkSurface)
                .border(1.dp, MineAgentCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "QR ${currentChunk.partIndex} de ${currentChunk.totalParts}",
                        color = MineSafetyYellow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MineSuccessGreen.copy(alpha = 0.25f))
                            .border(0.75.dp, MineSuccessGreenLight, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "V3 ALTA CAPACIDAD · BASE45",
                            color = MineSuccessGreenLight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Text(
                    text = "SESIÓN ${pkg.qrPackage.sessionId}",
                    color = MineAgentCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pkg.eventUuids.size} mov. + ${pkg.codeEventUuids.size} cód. SEDENA",
                    color = MineTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val savedPct = if (pkg.qrPackage.rawJsonBytes > 0) {
                    (((pkg.qrPackage.rawJsonBytes - pkg.qrPackage.compressedEncodedChars).toDouble() / pkg.qrPackage.rawJsonBytes) * 100.0)
                        .toInt().coerceIn(0, 99)
                } else {
                    0
                }
                Text(
                    text = "${pkg.qrPackage.rawJsonBytes} B → ${pkg.qrPackage.compressedEncodedChars} chars (-$savedPct%)",
                    color = MineTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Visor QR de alto contraste (modo QR Alfanumérico V3 + EC Level L/M)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            QrCodeCanvas(
                content = currentChunk.rawText,
                useLowErrorCorrection = pkg.qrPackage.useLowErrorCorrection,
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .testTag("sync_qr_canvas"),
                accessibilityLabel = "Código QR V3 parte ${currentChunk.partIndex} de ${currentChunk.totalParts}, sesión ${pkg.qrPackage.sessionId}"
            )
        }

        // Controles multi-parte (>= 48dp táctiles)
        if (chunks.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrevQr,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sync_qr_prev_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MineBorderGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineTextPrimary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "QR anterior")
                    Text("Anterior", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = onToggleAutoPlay,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp)
                        .testTag("sync_qr_autoplay_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isAutoPlay) MineSafetyAmber else MineAgentCyan,
                        contentColor = MineDarkBackground
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isAutoPlay) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.isAutoPlay) "Pausar" else "Carrusel",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onNextQr,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sync_qr_next_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MineBorderGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineTextPrimary)
                ) {
                    Text("Siguiente", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "QR siguiente")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "El escáner receptor puede leer los ${chunks.size} QRs en cualquier orden • CRC32 ${pkg.qrPackage.crc32Hex}",
                    color = MineTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onToggleSpeed,
                    modifier = Modifier.testTag("sync_qr_speed_button")
                ) {
                    val secs = if (uiState.autoPlayIntervalMs <= 1500L) "1.5s" else "2.5s"
                    Text("Velocidad: $secs", color = MineAgentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text(
                text = "Todo el lote cabe en 1 solo QR V3 Alfanumérico (CRC32 ${pkg.qrPackage.crc32Hex}). Apunta el lector Zebra desde la otra terminal para sincronizar con un solo disparo.",
                color = MineTextSecondary,
                fontSize = 11.sp
            )
        }

        // Botones de acción: Marcar compartido + Exportar archivo JSON
        Button(
            onClick = onMarkShared,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sync_mark_shared_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MineSuccessGreen,
                contentColor = MineTextPrimary
            )
        ) {
            Icon(Icons.Default.TaskAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("MARCAR LOTE QR COMO COMPARTIDO", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }

        OutlinedButton(
            onClick = { onExportJson(pkg) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sync_export_json_button"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MineAgentCyan),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineAgentCyan)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("EXPORTAR ARCHIVO .JSON (RESPALDO / USB)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
