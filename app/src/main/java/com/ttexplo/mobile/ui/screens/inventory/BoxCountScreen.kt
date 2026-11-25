package com.ttexplo.mobile.ui.screens.inventory

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
    var message by remember { mutableStateOf("Escanea o escribe el código de la caja.") }
    var showDuplicate by remember { mutableStateOf(false) }
    var pendingMismatch by remember { mutableStateOf<BoxCountRecord?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    fun handleScan(raw: String) {
        val parsed = BoxCountParser.parse(raw.trim()) ?: BoxCountParser.parseCodeOnly(raw.trim())
        if (parsed == null) {
            message = "Código de caja inválido (debe tener 30 dígitos)."
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
        message = "Caja ${parsed.code.take(3)} · el rango indica ${parsed.rangeCount} piezas."
    }

    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scanned ->
            if (!showDuplicate && pendingMismatch == null && !showExitConfirm) handleScan(scanned)
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose { ZebraScannerReceiver.unregister(context, receiver) }
    }

    BackHandler {
        if (records.isNotEmpty() || draft != null) showExitConfirm = true else onNavigateBack()
    }

    fun addRecord(record: BoxCountRecord) {
        records = listOf(record) + records
        draft = null
        kpInput = ""
        piecesInput = ""
        message = "Conteo agregado: ${record.pieces} piezas · KP ${record.kp}."
    }

    fun confirmDraft() {
        val current = draft ?: return
        val kp = kpInput.trim()
        val pieces = piecesInput.trim().toIntOrNull()
        if (kp.isEmpty()) {
            message = "Teclea la línea o KP."
            return
        }
        if (pieces == null || pieces !in 1..BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX) {
            message = "Las piezas deben estar entre 1 y ${BoxUnitWeightCalculator.MAX_CARTRIDGES_PER_BOX}."
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("4. CONTEO EN CAJA", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (records.isNotEmpty() || draft != null) showExitConfirm = true else onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(message, color = MineTextSecondary, fontSize = 13.sp)

            AlwaysActiveBarcodeField(
                value = barcodeInput,
                onValueChange = { barcodeInput = it },
                onSubmitBarcode = {
                    if (!showDuplicate && pendingMismatch == null && !showExitConfirm) handleScan(it)
                },
                accentColor = MineSafetyYellow,
                placeholderText = "Escanear código de caja + ENTER...",
                focusRequester = focusRequester,
                testTagName = "box_count_input"
            )

            draft?.let { current ->
                Text("Código: ${current.code}", color = MineTextPrimary, fontSize = 13.sp)
                OutlinedTextField(
                    value = kpInput,
                    onValueChange = { kpInput = it },
                    label = { Text("Línea / KP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = piecesInput,
                    onValueChange = { v -> piecesInput = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Piezas contadas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Referencia del código: ${current.rangeCount} piezas · PU: ${BoxUnitWeightCalculator.formatUnitWeight3(current.unitWeightKg)} kg",
                    color = MineTextSecondary,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { confirmDraft() }, modifier = Modifier.weight(1f)) {
                        Text("Confirmar conteo")
                    }
                    OutlinedButton(onClick = {
                        draft = null
                        kpInput = ""
                        piecesInput = ""
                        message = "Listo para otra lectura."
                    }, modifier = Modifier.weight(1f)) {
                        Text("Volver a leer")
                    }
                }
            }

            if (records.isNotEmpty()) {
                Text("CONTEOS (${records.size})", fontWeight = FontWeight.Bold, color = MineTextPrimary)
                records.forEach { record ->
                    Text(
                        "${record.code.take(3)} · KP ${record.kp} · ${record.pieces} pzas · ${record.timestamp}",
                        color = MineTextPrimary,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = {
                        val fileName = BoxCountTxtExporter.fileName(DateTimeUtils.currentTimestamp().take(10))
                        val file = File(context.filesDir, fileName)
                        file.writeText(BoxCountTxtExporter.render(records))
                        Toast.makeText(context, "TXT guardado: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("EXPORTAR TXT")
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
