package com.ttexplo.mobile.ui.screens.settings

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.printer.BluetoothPrinterManager
import com.ttexplo.mobile.printer.PaperWidth
import com.ttexplo.mobile.printer.PrinterProfile
import com.ttexplo.mobile.printer.ValePrintHelper
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PrinterProfilesSettingsCard(
    settingsRepo: SettingsRepository,
    printerManager: BluetoothPrinterManager,
    onRequestBluetoothPermission: (onGranted: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profiles by remember { mutableStateOf(settingsRepo.getPrinterProfiles()) }
    var printSignatures by remember { mutableStateOf(settingsRepo.getPrintSignatures()) }
    var printBreakdown by remember { mutableStateOf(settingsRepo.getPrintBreakdown()) }
    var printCodeDetails by remember { mutableStateOf(settingsRepo.getPrintCodeDetails()) }

    var editingProfile by remember { mutableStateOf<PrinterProfile?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var testingProfileId by remember { mutableStateOf<String?>(null) }
    var isTestingActiveProtocol by remember { mutableStateOf(false) }

    fun refreshProfiles() {
        profiles = settingsRepo.getPrinterProfiles()
    }

    val activeProfile = profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
        border = BorderStroke(1.dp, MineBorderGray),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(Icons.Default.Print, contentDescription = null, tint = MineSafetyYellow)
                    AutoShrinkText(
                        text = "IMPRESORAS BLUETOOTH (${profiles.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MineSafetyYellow,
                        fontWeight = FontWeight.Bold,
                        targetTextSize = 14.sp,
                        minTextSize = 10.sp
                    )
                }

                Button(
                    onClick = {
                        onRequestBluetoothPermission {
                            editingProfile = null
                            showEditorDialog = true
                        }
                    },
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("settings_add_printer_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineSafetyYellow,
                        contentColor = MineDarkBackground
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Agregar", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }

            if (profiles.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MineDarkBackground),
                    border = BorderStroke(1.dp, MineBorderGray),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No hay impresoras guardadas todavía",
                            color = MineTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Agregue una o varias impresoras térmicas Bluetooth (ej. Polvorín 1, Unidad Móvil, Respaldo) con su propio protocolo (CPCL / ESC/POS) y ancho de papel.",
                            color = MineTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile ->
                        val isBusy = testingProfileId == profile.id
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (profile.isDefault) MineSafetyYellow.copy(alpha = 0.08f) else MineDarkBackground
                            ),
                            border = BorderStroke(
                                width = if (profile.isDefault) 1.5.dp else 1.dp,
                                color = if (profile.isDefault) MineSafetyYellow else MineBorderGray
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AutoShrinkText(
                                                text = profile.displayTitle(),
                                                color = MineTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                targetTextSize = 14.sp,
                                                minTextSize = 11.sp
                                            )
                                            if (profile.isDefault) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MineSafetyYellow)
                                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "ACTIVA",
                                                        color = MineDarkBackground,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${profile.shortSpecSummary()} • ${profile.macAddress}",
                                            color = MineTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!profile.isDefault) {
                                        OutlinedButton(
                                            onClick = {
                                                settingsRepo.setActivePrinterProfile(profile.id)
                                                refreshProfiles()
                                                Toast.makeText(
                                                    context,
                                                    "Impresora activa: ${profile.displayTitle()}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            border = BorderStroke(1.dp, MineSuccessGreenLight),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSuccessGreenLight)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            AutoShrinkText("Activar", fontWeight = FontWeight.Bold, targetTextSize = 11.sp, minTextSize = 9.sp)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onRequestBluetoothPermission {
                                                testingProfileId = profile.id
                                                scope.launch {
                                                    val res = ValePrintHelper.printTestForProfile(context, profile)
                                                    testingProfileId = null
                                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        border = BorderStroke(1.dp, MineSafetyYellow),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                                    ) {
                                        if (isBusy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MineSafetyYellow
                                            )
                                        } else {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            AutoShrinkText("Probar", fontWeight = FontWeight.Bold, targetTextSize = 11.sp, minTextSize = 9.sp)
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            onRequestBluetoothPermission {
                                                editingProfile = profile
                                                showEditorDialog = true
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Editar impresora",
                                            tint = MineAgentCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            settingsRepo.deletePrinterProfile(profile.id)
                                            refreshProfiles()
                                            Toast.makeText(context, "Impresora eliminada", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Eliminar impresora",
                                            tint = MineErrorRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MineBorderGray.copy(alpha = 0.5f))

            // Opciones globales de contenido del ticket
            Text(
                text = "CONTENIDO DEL TICKET TÉRMICO",
                color = MineTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoShrinkText(
                    "Incluir líneas de firmas:",
                    modifier = Modifier.weight(1f),
                    color = MineTextPrimary,
                    targetTextSize = 13.sp,
                    minTextSize = 9.sp
                )
                Switch(
                    checked = printSignatures,
                    onCheckedChange = {
                        printSignatures = it
                        settingsRepo.setPrintSignatures(it)
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
                AutoShrinkText(
                    "Desglose Cajas / Sacos / Cartuchos:",
                    modifier = Modifier.weight(1f),
                    color = MineTextPrimary,
                    targetTextSize = 13.sp,
                    minTextSize = 8.sp
                )
                Switch(
                    checked = printBreakdown,
                    onCheckedChange = {
                        printBreakdown = it
                        settingsRepo.setPrintBreakdown(it)
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
                AutoShrinkText(
                    "Listado de códigos con fecha/hora:",
                    modifier = Modifier.weight(1f),
                    color = MineTextPrimary,
                    targetTextSize = 13.sp,
                    minTextSize = 8.sp
                )
                Switch(
                    checked = printCodeDetails,
                    onCheckedChange = {
                        printCodeDetails = it
                        settingsRepo.setPrintCodeDetails(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MineSafetyYellow,
                        checkedTrackColor = MineSafetyYellow.copy(alpha = 0.5f)
                    )
                )
            }

            if (activeProfile != null) {
                HorizontalDivider(color = MineBorderGray.copy(alpha = 0.5f))
                Text(
                    text = "Diagnóstico rápido en impresora activa (${activeProfile.displayTitle()}):",
                    color = MineTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ESCPOS" to "ESC/POS",
                        "CPCL" to "CPCL",
                        "PLAIN" to "RAW"
                    ).forEach { (proto, label) ->
                        OutlinedButton(
                            onClick = {
                                onRequestBluetoothPermission {
                                    isTestingActiveProtocol = true
                                    scope.launch {
                                        val res = ValePrintHelper.printTestForProfile(
                                            context = context,
                                            profile = activeProfile,
                                            overrideProtocol = proto
                                        )
                                        isTestingActiveProtocol = false
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isTestingActiveProtocol,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            border = BorderStroke(1.dp, MineBorderGray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineTextPrimary)
                        ) {
                            AutoShrinkText(
                                text = "Test $label",
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 11.sp,
                                minTextSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        PrinterProfileEditorDialog(
            initialProfile = editingProfile,
            printerManager = printerManager,
            onSave = { profile, makeActive ->
                settingsRepo.upsertPrinterProfile(profile, setAsActive = makeActive)
                refreshProfiles()
                showEditorDialog = false
                editingProfile = null
                Toast.makeText(context, "Impresora guardada: ${profile.displayTitle()}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showEditorDialog = false
                editingProfile = null
            }
        )
    }
}

@Composable
private fun PrinterProfileEditorDialog(
    initialProfile: PrinterProfile?,
    printerManager: BluetoothPrinterManager,
    onSave: (PrinterProfile, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var alias by remember { mutableStateOf(initialProfile?.alias ?: "") }
    var macAddress by remember { mutableStateOf(initialProfile?.macAddress ?: "") }
    var deviceName by remember { mutableStateOf(initialProfile?.deviceName ?: "") }
    var protocol by remember { mutableStateOf(initialProfile?.protocol ?: "ESCPOS") }
    var paperWidth by remember { mutableStateOf(initialProfile?.paperWidth ?: PaperWidth.WIDTH_3_INCH) }
    var setAsDefault by remember { mutableStateOf(initialProfile?.isDefault ?: true) }
    var showError by remember { mutableStateOf(false) }

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    fun refreshPaired() {
        pairedDevices = printerManager.getPairedPrinters()
    }
    LaunchedEffect(Unit) { refreshPaired() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MineDarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialProfile == null) "Agregar Impresora" else "Editar Impresora",
                    color = MineTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                IconButton(onClick = { refreshPaired() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar Bluetooth", tint = MineSafetyYellow)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Selección rápida desde dispositivos vinculados en Android
                Text(
                    text = "1. ELIJA UN DISPOSITIVO BLUETOOTH VINCULADO:",
                    color = MineSafetyYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                if (pairedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MineDarkBackground)
                            .border(1.dp, MineBorderGray, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (!printerManager.isBluetoothEnabled()) {
                                    "Bluetooth apagado. Enciéndalo o ingrese la MAC manualmente abajo."
                                } else {
                                    "Sin dispositivos vinculados detectados. Empareje primero en Bluetooth de Android o capture la MAC abajo."
                                },
                                color = MineTextSecondary,
                                fontSize = 11.sp
                            )
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Abra Ajustes > Bluetooth", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MineSafetyYellow),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abrir Bluetooth de Android", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        pairedDevices.forEach { dev ->
                            val name = try { dev.name ?: "Impresora BT" } catch (_: SecurityException) { "Dispositivo BT" }
                            val selected = macAddress.equals(dev.address, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        macAddress = dev.address
                                        deviceName = name
                                        if (alias.isBlank()) alias = name
                                        showError = false
                                        // Sugerencia inteligente de protocolo según nombre
                                        val lower = name.lowercase()
                                        if (lower.contains("zebra") || lower.contains("zq") || lower.contains("rw") || lower.contains("ql")) {
                                            protocol = "CPCL"
                                        }
                                    },
                                color = if (selected) MineSafetyYellow.copy(alpha = 0.16f) else MineDarkBackground,
                                border = BorderStroke(1.dp, if (selected) MineSafetyYellow else MineBorderGray),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Print,
                                        contentDescription = null,
                                        tint = if (selected) MineSafetyYellow else MineTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(dev.address, color = MineTextSecondary, fontSize = 10.sp)
                                    }
                                    if (selected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MineSafetyYellow, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Alias y MAC
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Alias / Nombre en polvorín") },
                    placeholder = { Text("Ej. Polvorín 1 - Munbyn 80mm") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("printer_editor_alias_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        capitalization = KeyboardCapitalization.Words
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineSafetyYellow,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )

                OutlinedTextField(
                    value = macAddress,
                    onValueChange = {
                        macAddress = it.uppercase()
                        showError = false
                    },
                    label = { Text("Dirección MAC Bluetooth") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("printer_editor_mac_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineSafetyYellow,
                        unfocusedBorderColor = MineBorderGray,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )

                // Protocolo
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Protocolo de impresión:", color = MineTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "ESCPOS" to "ESC/POS",
                            "CPCL" to "CPCL",
                            "PLAIN" to "RAW"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = protocol == key,
                                onClick = { protocol = key },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }

                // Ancho de papel (58mm / 2", 80mm / 3", 104mm / 4")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Ancho de papel (mm / pulgadas):",
                        color = MineTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaperWidth.entries.forEach { width ->
                            val isSelected = paperWidth == width
                            Surface(
                                onClick = { paperWidth = width },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 50.dp)
                                    .testTag("printer_width_${width.name.lowercase()}"),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MineSafetyYellow.copy(alpha = 0.16f) else MineDarkBackground,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MineSafetyYellow else MineBorderGray
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = width.shortLabel,
                                        color = if (isSelected) MineSafetyYellow else MineTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = width.inchesText,
                                        color = if (isSelected) MineSafetyYellow else MineTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Usar como impresora activa", color = MineTextPrimary, fontSize = 12.sp)
                    Switch(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MineSafetyYellow,
                            checkedTrackColor = MineSafetyYellow.copy(alpha = 0.5f)
                        )
                    )
                }

                if (showError) {
                    Text(
                        text = "Seleccione un dispositivo Bluetooth o ingrese una dirección MAC válida.",
                        color = MineErrorRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanMac = macAddress.trim()
                    if (cleanMac.isBlank()) {
                        showError = true
                        return@Button
                    }
                    val finalProfile = PrinterProfile(
                        id = initialProfile?.id ?: java.util.UUID.randomUUID().toString(),
                        alias = alias.trim().ifBlank { deviceName.ifBlank { cleanMac } },
                        macAddress = cleanMac,
                        deviceName = deviceName.ifBlank { alias.trim() },
                        protocol = protocol,
                        paperWidth = paperWidth,
                        isDefault = setAsDefault
                    )
                    onSave(finalProfile, setAsDefault)
                },
                modifier = Modifier.testTag("printer_editor_save_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MineSafetyYellow,
                    contentColor = MineDarkBackground
                )
            ) {
                Text("Guardar", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MineTextMuted)
            }
        }
    )
}
