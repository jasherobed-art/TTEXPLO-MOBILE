package com.ttexplo.mobile.ui.screens.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.data.local.entity.SyncSessionDirection
import com.ttexplo.mobile.data.local.entity.SyncSessionEntity
import com.ttexplo.mobile.domain.inventory.SyncConflict
import com.ttexplo.mobile.domain.inventory.SyncConflictSeverity
import com.ttexplo.mobile.domain.inventory.SyncMergeReport
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.theme.*

@Composable
internal fun ReceiveSyncTab(
    uiState: InventorySyncUiState,
    onScannerData: (String) -> Unit,
    onResetReceiver: () -> Unit,
    onPickJsonFile: () -> Unit,
    onSimulateSingleQr: () -> Unit,
    onSimulateMultiPartStep: () -> Unit,
    onJumpToConflicts: () -> Unit
) {
    val context = LocalContext.current
    var manualInput by remember { mutableStateOf("") }
    var showDemoPanel by remember { mutableStateOf(false) }

    // Escucha nativa del lector óptico 2D Zebra en tiempo real mientras la pestaña Recibir está visible
    val latestCallback by rememberUpdatedState(onScannerData)
    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scanned ->
            latestCallback(scanned)
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose {
            ZebraScannerReceiver.unregister(context, receiver)
        }
    }

    val rec = uiState.receiverState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Estado del lector láser Zebra 2D
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (rec.isError) MineErrorRed else MineSafetyYellow.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MineSafetyYellow,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "LECTOR ZEBRA ACTIVO",
                                color = MineSafetyYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = "Acepta QR V3 Alta Capacidad, V2 y multi-parte en cualquier orden",
                                color = MineTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (rec.sessionId != null) {
                        TextButton(
                            onClick = onResetReceiver,
                            modifier = Modifier.testTag("sync_receiver_reset_button")
                        ) {
                            Text("Reiniciar", color = MineErrorRedLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Fichas visuales de bloques recibidos ([✓ 1] [✓ 2] [• 3])
                if (rec.sessionId != null && rec.totalParts > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SESIÓN ${rec.sessionId} • ${rec.receivedParts.size} de ${rec.totalParts} bloques",
                            color = MineAgentCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = { rec.receivedParts.size.toFloat() / rec.totalParts.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MineSuccessGreenLight,
                            trackColor = MineDarkBackground
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (part in 1..rec.totalParts) {
                                val received = part in rec.receivedParts
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (received) MineSuccessGreen.copy(alpha = 0.25f) else MineDarkBackground)
                                        .border(
                                            1.dp,
                                            if (received) MineSuccessGreenLight else MineBorderGray,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (received) "✓ $part" else "• $part",
                                        color = if (received) MineSuccessGreenLight else MineTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                rec.statusMessage?.let { status ->
                    Text(
                        text = status,
                        color = if (rec.isError) MineErrorRedLight else MineTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Botón principal: Importar archivo JSON
        Button(
            onClick = onPickJsonFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sync_import_json_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MineSafetyYellow,
                contentColor = MineDarkBackground
            )
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("IMPORTAR ARCHIVO .JSON DE OTRA TERMINAL", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }

        // Reporte instantáneo de la última fusión
        uiState.lastMergeReport?.let { report ->
            SyncMergeReportCard(
                report = report,
                onJumpToConflicts = onJumpToConflicts
            )
        }

        // Entrada manual / pegado de bloque QR o JSON
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
            border = BorderStroke(1.dp, MineBorderGray)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ENTRADA MANUAL / PEGADO (QR O JSON)",
                    color = MineTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    placeholder = { Text("Pegar cadena TTEX3:... / TTEXSYNC|V2|... o JSON", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_manual_input"),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineAgentCyan,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )
                OutlinedButton(
                    onClick = {
                        if (manualInput.isNotBlank()) {
                            onScannerData(manualInput)
                            manualInput = ""
                        }
                    },
                    enabled = manualInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_manual_submit_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MineAgentCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineAgentCyan)
                ) {
                    Text("PROCESAR BLOQUE / JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (com.ttexplo.mobile.license.LicenseManager.isDemoModeAllowed(androidx.compose.ui.platform.LocalContext.current)) {
        // Panel plegable MODO PRUEBA (simulador de segunda terminal ZBR-POLV-02)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
            border = BorderStroke(1.dp, MineSafetyAmber.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDemoPanel = !showDemoPanel }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("sync_demo_toggle"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = MineSafetyAmber, modifier = Modifier.size(18.dp))
                        Text(
                            text = "MODO PRUEBA • Simular Escáner Remoto (ZBR-POLV-02)",
                            color = MineSafetyAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Icon(
                        imageVector = if (showDemoPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showDemoPanel) "Ocultar simulador" else "Mostrar simulador",
                        tint = MineSafetyAmber
                    )
                }
                AnimatedVisibility(visible = showDemoPanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Prueba la recepción instantánea en 1 solo QR V3 (Base45 + GZIP nivel 9) o el reensamblado multi-parte fuera de orden con detección de conflicto SEDENA.",
                            color = MineTextSecondary,
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onSimulateSingleQr,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("sync_demo_single_qr_button"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MineSafetyAmber,
                                    contentColor = MineDarkBackground
                                )
                            ) {
                                Text("1 Solo QR (V3)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            OutlinedButton(
                                onClick = onSimulateMultiPartStep,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("sync_demo_next_qr_button"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MineSafetyAmber),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyAmber)
                            ) {
                                val label = if (uiState.demoChunksRemaining > 0) {
                                    "Siguiente (${uiState.demoChunksRemaining})"
                                } else {
                                    "Multi-QR paso a paso"
                                }
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SyncMergeReportCard(
    report: SyncMergeReport,
    onJumpToConflicts: () -> Unit
) {
    val hasConflicts = report.conflicts.isNotEmpty()
    val borderColor = if (hasConflicts) MineErrorRed else MineSuccessGreenLight

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_merge_report_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MineDarkSurfaceElevated),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RESULTADO DE FUSIÓN UUID",
                    color = borderColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = report.remoteDeviceId,
                    color = MineAgentCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MergeMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Nuevos",
                    value = "+${report.eventsMerged + report.codeEventsMerged}",
                    accent = MineSuccessGreenLight
                )
                MergeMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Duplicados",
                    value = "${report.eventsSkippedDuplicate + report.codeEventsSkippedDuplicate}",
                    accent = MineTextSecondary
                )
                MergeMetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Conflictos",
                    value = "${report.conflicts.size}",
                    accent = if (hasConflicts) MineErrorRedLight else MineSuccessGreenLight
                )
            }

            Text(
                text = "Cantidades: +${report.eventsMerged} nuevos (${report.eventsSkippedDuplicate} omit.) • Códigos SEDENA: +${report.codeEventsMerged} nuevos (${report.codeEventsSkippedDuplicate} omit.)",
                color = MineTextSecondary,
                fontSize = 11.sp
            )
            if (report.catalogCalibrationsUpdated > 0) {
                Text(
                    text = "Calibraciones de peso unitario actualizadas: ${report.catalogCalibrationsUpdated} producto(s)",
                    color = MineAgentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (hasConflicts) {
                Button(
                    onClick = onJumpToConflicts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_jump_to_conflicts_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineErrorRed,
                        contentColor = MineTextPrimary
                    )
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REVISAR ${report.conflicts.size} CONFLICTO(S) DETECTADO(S)", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MergeMetricPill(
    modifier: Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkBackground)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = label, color = MineTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun ConflictsAndHistoryTab(
    uiState: InventorySyncUiState,
    onInspectProduct: (String) -> Unit,
    onMarkConflictReviewed: (String) -> Unit,
    onRestoreReviewed: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // SECCIÓN 1: CONFLICTOS ACTIVOS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALERTAS DE CONFLICTO (${uiState.activeConflicts.size})",
                    color = if (uiState.activeConflicts.isNotEmpty()) MineErrorRedLight else MineSuccessGreenLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                if (uiState.reviewedConflictsCount > 0) {
                    TextButton(onClick = onRestoreReviewed) {
                        Text(
                            text = "Ver ${uiState.reviewedConflictsCount} revisado(s)",
                            color = MineAgentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (uiState.activeConflicts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                    border = BorderStroke(1.dp, MineSuccessGreenLight.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MineSuccessGreenLight, modifier = Modifier.size(26.dp))
                        Column {
                            Text(
                                text = "Sin conflictos operativos pendientes",
                                color = MineTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "No se detectaron dobles despachos SEDENA, conteos paralelos ni saldos negativos.",
                                color = MineTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        } else {
            items(
                items = uiState.activeConflicts,
                key = { it.conflictId }
            ) { conflict ->
                ConflictAlertCard(
                    conflict = conflict,
                    onInspectProduct = { onInspectProduct(conflict.productId) },
                    onMarkReviewed = { onMarkConflictReviewed(conflict.conflictId) }
                )
            }
        }

        item {
            HorizontalDivider(color = MineOutline.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
        }

        item {
            // SECCIÓN 2: HISTORIAL DE CRUCES
            Text(
                text = "HISTORIAL DE SINCRONIZACIONES (${uiState.syncSessions.size})",
                color = MineAgentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp
            )
        }

        if (uiState.syncSessions.isEmpty()) {
            item {
                Text(
                    text = "Aún no se han registrado cruces QR o JSON en esta terminal.",
                    color = MineTextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            items(
                items = uiState.syncSessions,
                key = { it.sessionUuid }
            ) { session ->
                SyncSessionHistoryCard(session = session)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConflictAlertCard(
    conflict: SyncConflict,
    onInspectProduct: () -> Unit,
    onMarkReviewed: () -> Unit
) {
    val accent = when (conflict.severity) {
        SyncConflictSeverity.CRITICAL -> MineErrorRedLight
        SyncConflictSeverity.WARNING -> MineSafetyAmber
        SyncConflictSeverity.ALERT -> MineHighExplosiveOrange
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_conflict_card_${conflict.productId}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
        border = BorderStroke(1.5.dp, accent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.2f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${conflict.severity.label} • ${conflict.type.label}",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = conflict.productId,
                    color = MineTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = conflict.title,
                color = MineTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            conflict.barcode?.let { code ->
                Text(
                    text = "SERIE: $code",
                    color = MineSafetyYellow,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = conflict.detail,
                color = MineTextSecondary,
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onInspectProduct,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MineAgentCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineAgentCyan)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Kardex", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onMarkReviewed,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MineSuccessGreenLight),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSuccessGreenLight)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Revisado", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SyncSessionHistoryCard(session: SyncSessionEntity) {
    val isImport = session.direction == SyncSessionDirection.IMPORT_QR ||
        session.direction == SyncSessionDirection.IMPORT_JSON
    val badgeColor = if (isImport) MineSuccessGreenLight else MineAgentCyan
    val directionLabel = when (session.direction) {
        SyncSessionDirection.EXPORT_QR -> "ENVIADO QR"
        SyncSessionDirection.EXPORT_JSON -> "EXPORTADO JSON"
        SyncSessionDirection.IMPORT_QR -> "RECIBIDO QR"
        SyncSessionDirection.IMPORT_JSON -> "IMPORTADO JSON"
        else -> session.direction
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
        border = BorderStroke(1.dp, MineBorderGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = directionLabel,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = session.timestampIso.take(16),
                    color = MineTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "Terminal: ${session.remoteDeviceId} • ${session.polvorin}",
                color = MineTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = session.summaryNote,
                color = MineTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
