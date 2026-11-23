package com.ttexplo.mobile.ui.screens.entradas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.data.local.entity.EntradaEntity
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntradasHeaderScreen(
    onNavigateBack: () -> Unit,
    onContinueToScan: (proveedor: String, polvorin: String, factura: String) -> Unit
) {
    var proveedor by rememberSaveable { mutableStateOf("") }
    var polvorin by rememberSaveable { mutableStateOf("") }
    var factura by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val db = remember { (context.applicationContext as TTExploApp).database }
    val scope = rememberCoroutineScope()
    var activeDraft by remember { mutableStateOf<EntradaEntity?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        activeDraft = withContext(Dispatchers.IO) { db.entradaDao().getActiveDraft() }
    }

    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AutoShrinkText(
                        text = "1. Entradas - Datos de la factura.",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MineTextPrimary,
                        targetTextSize = 18.sp,
                        minTextSize = 14.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            activeDraft?.let { draft ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MineSafetyYellow)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoShrinkText(
                            text = "TRABAJO PENDIENTE ENCONTRADO",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MineSafetyYellow,
                            targetTextSize = 15.sp,
                            minTextSize = 12.sp
                        )
                        AutoShrinkText(
                            text = "Factura: ${draft.factura} • Polvorín: ${draft.polvorin} • Proveedor: ${draft.proveedor}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MineTextPrimary,
                            targetTextSize = 13.sp,
                            minTextSize = 10.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onContinueToScan(draft.proveedor, draft.polvorin, draft.factura)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow, contentColor = MineDarkBackground)
                            ) {
                                AutoShrinkText("REANUDAR TRABAJO", fontWeight = FontWeight.Bold, targetTextSize = 13.sp, minTextSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { showDiscardDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MineErrorText)
                            ) {
                                Text("Descartar")
                            }
                        }
                    }
                }
            }

            if (showDiscardDialog && activeDraft != null) {
                val draft = activeDraft!!
                TwoStepDeleteDialog(
                    itemDescription = "Borrador de Entrada • Factura: ${draft.factura}",
                    itemCode = "Proveedor: ${draft.proveedor} • Polvorín: ${draft.polvorin}",
                    onConfirmDelete = {
                        scope.launch(Dispatchers.IO) {
                            db.entradaDao().deleteEntrada(draft.id)
                            activeDraft = null
                        }
                        showDiscardDialog = false
                    },
                    onDismiss = { showDiscardDialog = false }
                )
            }
            Text(
                text = "Ingrese la información para registrar el lote en polvorín:",
                style = MaterialTheme.typography.bodyMedium,
                color = MineTextSecondary
            )

            // 1. Proveedores
            OutlinedTextField(
                value = proveedor,
                onValueChange = {
                    proveedor = it
                    showError = false
                },
                label = { Text("Número de Proveedor (ej. 1 )") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Characters
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineOutline,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            // 2. Polvorín
            OutlinedTextField(
                value = polvorin,
                onValueChange = {
                    polvorin = it
                    showError = false
                },
                label = { Text("Número de Polvorín (ej. 1, 2, 3)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineOutline,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            // 3. Factura o Remisión
            OutlinedTextField(
                value = factura,
                onValueChange = {
                    factura = it
                    showError = false
                },
                label = { Text("Factura o Remisión") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Characters
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (proveedor.isNotBlank() && polvorin.isNotBlank() && factura.isNotBlank()) {
                            onContinueToScan(proveedor.trim(), polvorin.trim(), factura.trim())
                        } else {
                            showError = true
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineOutline,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            if (showError) {
                Text(
                    text = "Por favor complete los 3 campos antes de continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MineErrorText
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (proveedor.isNotBlank() && polvorin.isNotBlank() && factura.isNotBlank()) {
                        onContinueToScan(proveedor.trim(), polvorin.trim(), factura.trim())
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow)
            ) {
                AutoShrinkText(
                    text = "CONTINUAR A ESCANEO ↵",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MineDarkBackground,
                    targetTextSize = 15.sp,
                    minTextSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MineDarkBackground
                )
            }
        }
    }
}
