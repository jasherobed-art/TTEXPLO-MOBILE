package com.ttexplo.mobile.ui.screens.despacho

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
import com.ttexplo.mobile.data.local.entity.DespachoEntity
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DespachoHeaderScreen(
    onNavigateBack: () -> Unit,
    onContinueToScan: (vale: String, empresa: String, lugar: String, transportista: String, turno: String, voladura: String) -> Unit
) {
    var vale by rememberSaveable { mutableStateOf("") }
    var empresa by rememberSaveable { mutableStateOf("") }
    var lugar by rememberSaveable { mutableStateOf("") }
    var transportista by rememberSaveable { mutableStateOf("") }
    var turno by rememberSaveable { mutableStateOf("1") }
    var voladura by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val db = remember { (context.applicationContext as TTExploApp).database }
    val scope = rememberCoroutineScope()
    var activeDraft by remember { mutableStateOf<DespachoEntity?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        activeDraft = withContext(Dispatchers.IO) { db.despachoDao().getActiveDraft() }
    }

    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AutoShrinkText(
                        text = "2. Salidas - Vale de Despacho",
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
                            text = "VALE DE DESPACHO EN CURSO",
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
                                        draft.transportista, draft.turno, draft.voladura
                                    )
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
                    itemDescription = "Borrador de Vale de Salida • Vale: ${draft.vale}",
                    itemCode = "Empresa: ${draft.empresa} • Lugar: ${draft.lugar}",
                    onConfirmDelete = {
                        scope.launch(Dispatchers.IO) {
                            db.despachoDao().deleteDespacho(draft.id)
                            activeDraft = null
                        }
                        showDiscardDialog = false
                    },
                    onDismiss = { showDiscardDialog = false }
                )
            }
            Text(
                text = "Capture los datos del vale de entrega para salida a mina:",
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
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineOutline,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            // Empresa
            OutlinedTextField(
                value = empresa,
                onValueChange = {
                    empresa = it
                    showError = false
                },
                label = { Text("Empresa (Entidad que cargará el barreno)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Characters
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineOutline,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            // Lugar
            OutlinedTextField(
                value = lugar,
                onValueChange = {
                    lugar = it
                    showError = false
                },
                label = { Text("Lugar (Ubicación de uso ej. 576-S/RMP...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Characters
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineOutline,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            // Transportista
            OutlinedTextField(
                value = transportista,
                onValueChange = {
                    transportista = it
                    showError = false
                },
                label = { Text("Transportista (Encargado del traslado)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Words
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineOutline,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            // Turno & Voladura row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = turno,
                    onValueChange = { turno = it },
                    label = { Text("Turno (1, 2, 3)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineSafetyYellow,
                        unfocusedBorderColor = MineOutline,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary,
                        focusedLabelColor = MineSafetyYellow
                    )
                )

                OutlinedTextField(
                    value = voladura,
                    onValueChange = { voladura = it },
                    label = { Text("Voladura / Mina") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (vale.isNotBlank() && empresa.isNotBlank() && lugar.isNotBlank() && transportista.isNotBlank()) {
                                onContinueToScan(vale.trim(), empresa.trim(), lugar.trim(), transportista.trim(), turno.trim(), voladura.trim())
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
            }

            if (showError) {
                Text(
                    text = "Por favor capture los campos principales (Vale, Empresa, Lugar y Transportista).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MineErrorText
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (vale.isNotBlank() && empresa.isNotBlank() && lugar.isNotBlank() && transportista.isNotBlank()) {
                        onContinueToScan(vale.trim(), empresa.trim(), lugar.trim(), transportista.trim(), turno.trim(), voladura.trim())
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MineHighExplosiveOrange)
            ) {
                AutoShrinkText(
                    text = "CONTINUAR A DESPACHO ↵",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MineTextPrimary,
                    targetTextSize = 15.sp,
                    minTextSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MineTextPrimary
                )
            }
        }
    }
}
