package com.ttexplo.mobile.ui.screens.accesorios

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
import com.ttexplo.mobile.data.local.entity.AccesorioValeEntity
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccesoriosHeaderScreen(
    isDevolucion: Boolean = false,
    onNavigateBack: () -> Unit,
    onContinueToScan: (vale: String, empresa: String, lugar: String, transportista: String, turno: String, recibio: String, entrego: String, voladura: String) -> Unit
) {
    var vale by remember { mutableStateOf("") }
    var empresa by remember { mutableStateOf("") }
    var lugar by remember { mutableStateOf("") }
    var transportista by remember { mutableStateOf("") }
    var turno by remember { mutableStateOf("1") }
    var recibio by remember { mutableStateOf("") }
    var entrego by remember { mutableStateOf("") }
    var voladura by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val db = remember { (context.applicationContext as TTExploApp).database }
    val scope = rememberCoroutineScope()
    var activeDraft by remember { mutableStateOf<AccesorioValeEntity?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        activeDraft = withContext(Dispatchers.IO) { db.accesorioDao().getActiveDraft() }
    }

    val focusManager = LocalFocusManager.current
    val accentColor = if (isDevolucion) MineAgentCyan else MineAccessoryPurple

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AutoShrinkText(
                        text = if (isDevolucion) "5. Devolución de Accesorios - Vale" else "3. Salida de Accesorios - Vale",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDevolucion) MineAgentCyan else MineTextPrimary,
                        targetTextSize = 18.sp,
                        minTextSize = 13.sp,
                        maxLines = 2,
                        softWrap = true
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
                            text = "VALE DE ARTIFICIOS EN CURSO",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MineSafetyYellow,
                            targetTextSize = 15.sp,
                            minTextSize = 12.sp
                        )
                        AutoShrinkText(
                            text = "Vale: ${draft.vale} • Lugar: ${draft.lugar} • Turno: ${draft.turno}",
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
                                    onContinueToScan(
                                        draft.vale, draft.empresa, draft.lugar,
                                        draft.transportista, draft.turno, draft.recibio,
                                        draft.entrego, draft.voladura
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow, contentColor = MineDarkBackground)
                            ) {
                                AutoShrinkText("REANUDAR TRABAJO", fontWeight = FontWeight.Bold, targetTextSize = 13.sp, minTextSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { showDiscardDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MineErrorRed)
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
                    itemDescription = "Borrador de Vale de Accesorios • Vale: ${draft.vale}",
                    itemCode = "Empresa: ${draft.empresa} • Lugar: ${draft.lugar}",
                    onConfirmDelete = {
                        scope.launch(Dispatchers.IO) {
                            db.accesorioDao().deleteVale(draft.id)
                            activeDraft = null
                        }
                        showDiscardDialog = false
                    },
                    onDismiss = { showDiscardDialog = false }
                )
            }
            Text(
                text = if (isDevolucion) "Capture los datos del vale para devolución de accesorios:" else "Capture los datos del vale para salida de artificios:",
                style = MaterialTheme.typography.bodyMedium,
                color = MineTextSecondary
            )

            // Vale
            OutlinedTextField(
                value = vale,
                onValueChange = {
                    vale = it
                    showError = false
                },
                label = { Text("Número de Vale / Referencia (ej. 7712)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Characters
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = accentColor
                )
            )

            // Empresa & Lugar
            OutlinedTextField(
                value = empresa,
                onValueChange = { empresa = it },
                label = { Text("Empresa (Quien carga el barreno)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = accentColor
                )
            )

            OutlinedTextField(
                value = lugar,
                onValueChange = { lugar = it },
                label = { Text("Lugar (Donde se usará)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = accentColor
                )
            )

            OutlinedTextField(
                value = transportista,
                onValueChange = { transportista = it },
                label = { Text("Transportista") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = accentColor
                )
            )

            // Recibió & Entregó
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = recibio,
                    onValueChange = { recibio = it },
                    label = { Text("Recibió") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary,
                        focusedLabelColor = accentColor
                    )
                )

                OutlinedTextField(
                    value = entrego,
                    onValueChange = { entrego = it },
                    label = { Text("Entregó") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary,
                        focusedLabelColor = accentColor
                    )
                )
            }

            // Turno & Voladura
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = turno,
                    onValueChange = { turno = it },
                    label = { Text("Turno") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary,
                        focusedLabelColor = accentColor
                    )
                )

                OutlinedTextField(
                    value = voladura,
                    onValueChange = { voladura = it },
                    label = { Text("Voladura / Ref") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (vale.isNotBlank() && empresa.isNotBlank()) {
                                onContinueToScan(vale.trim(), empresa.trim(), lugar.trim(), transportista.trim(), turno.trim(), recibio.trim(), entrego.trim(), voladura.trim())
                            } else {
                                showError = true
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

            if (showError) {
                Text(
                    text = "Por favor ingrese al menos el número de vale y la empresa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MineErrorRed
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (vale.isNotBlank() && empresa.isNotBlank()) {
                        onContinueToScan(vale.trim(), empresa.trim(), lugar.trim(), transportista.trim(), turno.trim(), recibio.trim(), entrego.trim(), voladura.trim())
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDevolucion) MineAgentCyan else MineAccessoryPurple,
                    contentColor = if (isDevolucion) MineDarkBackground else MineTextPrimary
                )
            ) {
                AutoShrinkText(
                    text = if (isDevolucion) "CONTINUAR A DEVOLUCIÓN DE ACCESORIOS ↵" else "CONTINUAR A ACCESORIOS ↵",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDevolucion) MineDarkBackground else MineTextPrimary,
                    targetTextSize = 15.sp,
                    minTextSize = 11.sp,
                    maxLines = 2,
                    softWrap = true,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = if (isDevolucion) MineDarkBackground else MineTextPrimary
                )
            }
        }
    }
}
