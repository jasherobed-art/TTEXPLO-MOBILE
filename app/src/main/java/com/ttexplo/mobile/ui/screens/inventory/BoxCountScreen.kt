package com.ttexplo.mobile.ui.screens.inventory

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.domain.inventory.BoxCountParser
import com.ttexplo.mobile.domain.inventory.BoxCountRecord
import com.ttexplo.mobile.domain.inventory.BoxCountTxtExporter
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AlwaysActiveBarcodeField
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxCountScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var barcodeInput by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<BoxCountRecord?>(null) }
    var kpInput by remember { mutableStateOf("") }
    var piecesInput by remember { mutableStateOf("") }
    var records by remember { mutableStateOf<List<BoxCountRecord>>(emptyList()) }
    var message by remember { mutableStateOf("Casilla activa: dispare el lector o escriba el código de la caja") }
    var messageIsError by remember { mutableStateOf(false) }
    var isHeaderExpanded by remember { mutableStateOf(true) }
    var showDuplicate by remember { mutableStateOf(false) }
    var pendingMismatch by remember { mutableStateOf<BoxCountRecord?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    val totalPieces = records.sumOf { it.pieces }

    fun handleScan(raw: String) {
        val parsed = BoxCountParser.parse(raw.trim()) ?: BoxCountParser.parseCodeOnly(raw.trim())
        if (parsed == null) {
            message = "Código de caja inválido (debe tener 30 dígitos)."
            messageIsError = true
            return
        }
        if (records.any { it.code == parsed.code } ||
            draft?.code == parsed.code ||
            pendingMismatch?.code == parsed.code
        ) {
            showDuplicate = true
            return
        }
        draft = parsed
        kpInput = parsed.kp
        piecesInput = parsed.pieces.toString()
        message = "Caja ${parsed.code.take(3)} capturada · el rango indica ${parsed.rangeCount} piezas."
        messageIsError = false
        isHeaderExpanded = true
    }

    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scanned ->
            if (!showDuplicate && pendingMismatch == null && !showExitConfirm) handleScan(scanned)
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose { ZebraScannerReceiver.unregister(context, receiver) }
    }

    fun exit() {
        if (records.isNotEmpty() || draft != null) showExitConfirm = true else onNavigateBack()
    }

    BackHandler { exit() }

    fun addRecord(record: BoxCountRecord) {
        records = listOf(record) + records
        draft = null
        kpInput = ""
        piecesInput = ""
        message = "Conteo agregado: ${record.pieces} piezas · KP ${record.kp}."
        messageIsError = false
    }

    fun confirmDraft() {
        val current = draft ?: return
        val kp = kpInput.trim()
        val pieces = piecesInput.trim().toIntOrNull()
        if (kp.isEmpty()) {
            message = "Teclea la línea o KP."
            messageIsError = true
            return
        }
        if (pieces == null || pieces !in 1..BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX) {
            message = "Las piezas deben estar entre 1 y ${BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX}."
            messageIsError = true
            return
        }
        val unitWeight = BoxUnitWeightCalculator.calculateUnitWeightKg(25.0, pieces) ?: return
        val record = current.copy(
            kp = kp,
            pieces = pieces,
            timestamp = DateTimeUtils.currentTimestamp(),
            unitWeightKg = unitWeight
        )
        if (pieces != current.rangeCount) pendingMismatch = record else addRecord(record)
    }

    fun exportTxt() {
        if (records.isEmpty()) return
        val fileName = BoxCountTxtExporter.fileName(DateTimeUtils.currentTimestamp().take(10))
        val file = File(context.filesDir, fileName)
        runCatching {
            file.writeText(BoxCountTxtExporter.render(records))
            Toast.makeText(context, "TXT guardado: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }.onFailure {
            message = "No se pudo guardar el TXT."
            messageIsError = true
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = { exportTxt() }, enabled = records.isNotEmpty()) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Exportar TXT",
                            tint = if (records.isNotEmpty()) MineSafetyYellow else MineTextMuted
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
                BoxCountMetric(Modifier.weight(1f), "KP", draft?.kp?.take(3)?.ifEmpty { "-" } ?: "-", MineAgentCyan, MineAgentCyan)
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
                            Text(
                                text = draft?.code?.let { "Caja ${it.take(3)}" } ?: "Datos de la caja",
                                color = MineTextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (draft == null) "Listo para contar" else "Capturada",
                                color = if (draft == null) MineSuccessGreenLight else MineSafetyYellow,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = kpInput,
                                onValueChange = { kpInput = it },
                                label = { Text("Línea / KP") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = piecesInput,
                                onValueChange = { v -> piecesInput = v.filter { it.isDigit() }.take(3) },
                                label = { Text("Piezas contadas") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        draft?.let { current ->
                            Text(
                                text = "Referencia del código: ${current.rangeCount} piezas · PU ${BoxUnitWeightCalculator.formatUnitWeight3(current.unitWeightKg)} kg",
                                color = MineTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            AlwaysActiveBarcodeField(
                value = barcodeInput,
                onValueChange = { barcodeInput = it },
                onSubmitBarcode = {
                    if (!showDuplicate && pendingMismatch == null && !showExitConfirm) handleScan(it)
                },
                accentColor = MineHighExplosiveOrange,
                placeholderText = "Escanear o escribir código + ENTER...",
                focusRequester = focusRequester,
                testTagName = "box_count_input",
                onActivityDetected = { isHeaderExpanded = true }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (messageIsError) MineErrorRed.copy(alpha = 0.24f) else MineSuccessGreen.copy(alpha = 0.2f))
                    .border(1.dp, if (messageIsError) MineErrorRed else MineSuccessGreen, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (messageIsError) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (messageIsError) MineErrorRedLight else MineSuccessGreenLight,
                    modifier = Modifier.size(15.dp)
                )
                Text(message, color = MineTextPrimary, fontSize = 11.sp, maxLines = 2)
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
                    items(records) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                            border = BorderStroke(1.dp, MineBorderGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(record.code, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    "KP ${record.kp} · ${record.pieces} piezas · ${record.timestamp}",
                                    color = MineTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportTxt() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = records.isNotEmpty(),
                    border = BorderStroke(1.dp, MineSafetyYellow),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    AutoShrinkText("EXPORTAR TXT", fontWeight = FontWeight.Bold, targetTextSize = 12.sp, minTextSize = 9.sp, color = MineSafetyYellow, maxLines = 1)
                }
                Button(
                    onClick = { confirmDraft() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = draft != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MineSuccessGreen)
                ) {
                    AutoShrinkText("CONFIRMAR CONTEO", fontWeight = FontWeight.Bold, targetTextSize = 12.sp, minTextSize = 9.sp, color = MineTextPrimary, maxLines = 1)
                }
            }
        }
    }

    if (showDuplicate) {
        AlertDialog(
            onDismissRequest = { showDuplicate = false },
            title = { Text("CÓDIGO REPETIDO") },
            text = { Text("Esa caja ya está en la lista. No se agregó otra vez.") },
            confirmButton = { TextButton(onClick = { showDuplicate = false }) { Text("Entendido") } }
        )
    }

    pendingMismatch?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingMismatch = null },
            title = { Text("REVISA EL CONTEO") },
            text = { Text("Tecleaste ${record.pieces} piezas pero el código indica ${record.rangeCount}. ¿Es correcto?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingMismatch = null
                    addRecord(record)
                }) { Text("Sí, son ${record.pieces}") }
            },
            dismissButton = { TextButton(onClick = { pendingMismatch = null }) { Text("Corregir") } }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("¿SALIR DEL CONTEO?") },
            text = { Text("Se perderán ${records.size} conteos que aún no exportaste.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onNavigateBack()
                }) { Text("Salir") }
            },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Seguir contando") } }
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
