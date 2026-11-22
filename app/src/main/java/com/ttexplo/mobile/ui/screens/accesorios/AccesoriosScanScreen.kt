package com.ttexplo.mobile.ui.screens.accesorios

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttexplo.mobile.domain.model.AccessoryItemRecord
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.DummyAccesoriosScannerBar
import com.ttexplo.mobile.ui.common.ExitScanConfirmDialog
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.theme.*

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
    var itemToDelete by remember { mutableStateOf<AccessoryItemRecord?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.items.isNotEmpty()) {
        showExitConfirm = true
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
            viewModel.printTicket { msg ->
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

    // Hardware Zebra SE4710 listener
    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scannedCode ->
            val qty = cantidadText.toIntOrNull() ?: 1
            viewModel.onAccessoryScanned(scannedCode, qty)
            codeInput = ""
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
                            text = if (isDevolucion) "Devolución: ${if (vale.startsWith("ACC")) vale else "ACC$vale"}" else "Accesorios: ${if (vale.startsWith("ACC")) vale else "ACC$vale"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MineTextPrimary
                        )
                        Text(
                            text = "$empresa • $lugar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentColor
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { requestPrint() },
                        enabled = !uiState.isPrinting && uiState.items.isNotEmpty()
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Draft Restored Banner (Crash / Power-off recovery)
            if (uiState.wasRestoredFromDraft) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = accentColor)
                    Text(
                        text = if (isDevolucion) "Devolución en progreso recuperada (${kotlin.math.abs(uiState.totalPieces)} pzas)" else "Vale en progreso recuperado (${uiState.totalPieces} pzas)",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentLightColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Big total counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MineDarkSurface)
                    .border(2.dp, accentColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isDevolucion) "TOTAL DEVUELTO" else "TOTAL ARTIFICIOS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MineTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (isDevolucion) kotlin.math.abs(uiState.totalPieces) else uiState.totalPieces}",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 50.sp),
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Recibió: $recibio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MineTextSecondary
                    )
                    Text(
                        text = "Entregó: $entrego",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MineTextSecondary
                    )
                }
            }

            // Input Row: Cantidad + Código de Barras (Manual / Keystroke)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = cantidadText,
                    onValueChange = { cantidadText = it.filter { c -> c.isDigit() } },
                    label = { Text(if (isDevolucion) "1. Cant. Devolución" else "1. Cantidad") },
                    modifier = Modifier.width(if (isDevolucion) 140.dp else 110.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary,
                        focusedLabelColor = accentColor
                    )
                )

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text(if (isDevolucion) "2. Código + ENTER" else "2. Código (X...) + ENTER") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (codeInput.isNotBlank()) {
                                val qty = cantidadText.toIntOrNull() ?: 1
                                viewModel.onAccessoryScanned(codeInput.trim(), qty)
                                codeInput = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary,
                        focusedLabelColor = accentColor
                    )
                )
            }

            // Quick quantity buttons for convenience
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("1", "2", "4", "5", "10", "20").forEach { q ->
                    FilterChip(
                        selected = cantidadText == q,
                        onClick = { cantidadText = q },
                        label = { Text(q, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = if (isDevolucion) MineDarkBackground else MineTextPrimary
                        )
                    )
                }
            }

            // Simulador de escaneo (MODO PRUEBA plegable)
            DummyAccesoriosScannerBar(
                onSimulateAccessory = { code ->
                    val qty = cantidadText.toIntOrNull() ?: 1
                    viewModel.onAccessoryScanned(code, qty)
                },
                accentColor = accentColor
            )

            // Last scan feedback
            uiState.lastScanMessage?.let { msg ->
                val isError = msg.startsWith("Error", ignoreCase = true)
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) MineErrorRedLight else MineSuccessGreenLight
                )
            }

            // Desglose individual de accesorios (auto-ajustable)
            if (uiState.summaries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDevolucion) "DESGLOSE DE ACCESORIOS DEVUELTOS:" else "DESGLOSE POR TIPO DE ARTIFICIO:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MineTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.summaries.size} tipo(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentLightColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pairs = uiState.summaries.chunked(2)
                    pairs.forEach { rowSummaries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSummaries.forEach { summary ->
                                Box(modifier = Modifier.weight(1f)) {
                                    AccessorySummaryCard(summary = summary, accentColor = accentColor)
                                }
                            }
                            if (rowSummaries.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Items List
            Text(
                text = if (isDevolucion) "ACCESORIOS DEVUELTOS EN ESTE VALE:" else "ACCESORIOS EN ESTE VALE:",
                style = MaterialTheme.typography.labelLarge,
                color = MineTextMuted,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.items, key = { it.code + it.timestamp }) { item ->
                    AccessoryRowCard(
                        item = item,
                        accentColor = accentColor,
                        onDelete = { itemToDelete = item }
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { requestPrint() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !uiState.isPrinting && uiState.items.isNotEmpty(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    AutoShrinkText(
                        text = if (uiState.isPrinting) "Imprimiendo..." else "Imprimir Ticket",
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
                        viewModel.finishAndSave(onFinished)
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDevolucion) MineAgentCyan else MineSuccessGreen
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = if (isDevolucion) MineDarkBackground else MineTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    AutoShrinkText(
                        text = if (isDevolucion) "Guardar Devolución (${kotlin.math.abs(uiState.totalPieces)})" else "Guardar Vale (${uiState.totalPieces})",
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
fun AccessorySummaryCard(
    summary: AccessorySummary,
    accentColor: androidx.compose.ui.graphics.Color = MineAccessoryPurple
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkSurface)
            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            AutoShrinkText(
                text = summary.code,
                initialTextSize = 13.sp,
                minTextSize = 9.sp,
                color = MineTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                softWrap = true
            )
            AutoShrinkText(
                text = "${summary.countEntries} registro(s)",
                initialTextSize = 11.sp,
                minTextSize = 8.sp,
                color = MineTextSecondary,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                softWrap = true
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${summary.totalQuantity}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (accentColor == MineAgentCyan) MineDarkBackground else MineTextPrimary,
                    fontSize = 15.sp
                )
                Text(
                    text = "Pzas",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (accentColor == MineAgentCyan) MineDarkBackground else MineTextPrimary,
                    fontSize = 9.sp
                )
            }
        }
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
