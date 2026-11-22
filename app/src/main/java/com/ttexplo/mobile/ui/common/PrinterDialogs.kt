package com.ttexplo.mobile.ui.common

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.printer.BluetoothPrinterManager
import com.ttexplo.mobile.printer.PaperWidth
import com.ttexplo.mobile.printer.PrinterProfile
import com.ttexplo.mobile.ui.theme.*

/**
 * Barra compacta de impresora activa con botón "Cambiar" de 1 toque.
 */
@Composable
fun ActivePrinterMiniBar(
    activeProfile: PrinterProfile?,
    totalProfiles: Int,
    onChangePrinterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onChangePrinterClick() }
            .testTag("active_printer_mini_bar"),
        color = MineDarkSurface,
        border = BorderStroke(
            1.dp,
            if (activeProfile != null) MineSafetyYellow.copy(alpha = 0.45f) else MineBorderGray
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = "Impresora activa",
                tint = if (activeProfile != null) MineSafetyYellow else MineTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                AutoShrinkText(
                    text = activeProfile?.displayTitle() ?: "Sin impresora configurada",
                    color = MineTextPrimary,
                    fontWeight = FontWeight.Bold,
                    targetTextSize = 12.sp,
                    minTextSize = 9.sp,
                    maxLines = 1
                )
                AutoShrinkText(
                    text = if (activeProfile != null) {
                        "${activeProfile.shortSpecSummary()} • ${activeProfile.macAddress}"
                    } else {
                        "Toque para elegir una impresora Bluetooth"
                    },
                    color = MineTextSecondary,
                    targetTextSize = 10.sp,
                    minTextSize = 8.sp,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MineSafetyYellow.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (totalProfiles > 1) "Cambiar ($totalProfiles)" else "Elegir",
                    color = MineSafetyYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Selector rápido de impresora Bluetooth:
 * Muestra los perfiles guardados en Ajustes (con su protocolo y ancho propios) y también
 * permite elegir cualquier dispositivo Bluetooth vinculado en Android.
 */
@Composable
fun QuickPrinterSelectorDialog(
    title: String = "Seleccionar Impresora",
    onSelectProfile: (PrinterProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val printerManager = remember { BluetoothPrinterManager(context) }

    var savedProfiles by remember { mutableStateOf(settingsRepo.getPrinterProfiles()) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    fun refreshAll() {
        savedProfiles = settingsRepo.getPrinterProfiles()
        pairedDevices = printerManager.getPairedPrinters()
    }

    LaunchedEffect(Unit) { refreshAll() }

    val savedMacs = remember(savedProfiles) { savedProfiles.map { it.macAddress.uppercase() }.toSet() }
    val unsavedPaired = remember(pairedDevices, savedMacs) {
        pairedDevices.filter { it.address.uppercase() !in savedMacs }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MineDarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = MineSafetyYellow)
                    Text(title, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                IconButton(onClick = { refreshAll() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = MineSafetyYellow)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (savedProfiles.isNotEmpty()) {
                    Text(
                        text = "IMPRESORAS GUARDADAS (${savedProfiles.size})",
                        color = MineSafetyYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    savedProfiles.forEach { profile ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsRepo.setActivePrinterProfile(profile.id)
                                    onSelectProfile(profile.copy(isDefault = true))
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (profile.isDefault) MineSafetyYellow.copy(alpha = 0.12f) else MineDarkBackground
                            ),
                            border = BorderStroke(
                                width = if (profile.isDefault) 1.5.dp else 1.dp,
                                color = if (profile.isDefault) MineSafetyYellow else MineBorderGray
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (profile.isDefault) Icons.Default.CheckCircle else Icons.Default.Print,
                                    contentDescription = null,
                                    tint = if (profile.isDefault) MineSafetyYellow else MineTextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoShrinkText(
                                        text = profile.displayTitle(),
                                        color = MineTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        targetTextSize = 13.sp,
                                        minTextSize = 10.sp
                                    )
                                    AutoShrinkText(
                                        text = "${profile.shortSpecSummary()} • ${profile.macAddress}",
                                        color = MineTextSecondary,
                                        targetTextSize = 11.sp,
                                        minTextSize = 8.sp
                                    )
                                }
                                if (profile.isDefault) {
                                    Text(
                                        text = "ACTIVA",
                                        color = MineSafetyYellow,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (unsavedPaired.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "OTROS DISPOSITIVOS VINCULADOS (${unsavedPaired.size})",
                        color = MineTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    unsavedPaired.forEach { dev ->
                        val devName = try {
                            dev.name ?: "Impresora Bluetooth"
                        } catch (_: SecurityException) {
                            "Dispositivo Bluetooth"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newProfile = PrinterProfile(
                                        alias = devName,
                                        macAddress = dev.address,
                                        deviceName = devName,
                                        protocol = settingsRepo.getPrinterProtocol(),
                                        paperWidth = settingsRepo.getPaperWidth(),
                                        isDefault = true
                                    )
                                    settingsRepo.upsertPrinterProfile(newProfile, setAsActive = true)
                                    onSelectProfile(newProfile)
                                },
                            colors = CardDefaults.cardColors(containerColor = MineDarkBackground),
                            border = BorderStroke(1.dp, MineBorderGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MineAgentCyan, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoShrinkText(devName, color = MineTextPrimary, fontWeight = FontWeight.SemiBold, targetTextSize = 13.sp, minTextSize = 10.sp)
                                    AutoShrinkText(dev.address, color = MineTextMuted, targetTextSize = 10.sp, minTextSize = 8.sp)
                                }
                                Text("Usar", color = MineAgentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (savedProfiles.isEmpty() && unsavedPaired.isEmpty()) {
                    Text(
                        text = if (!printerManager.isBluetoothEnabled()) {
                            "Bluetooth apagado. Encienda Bluetooth en la terminal para ver sus impresoras."
                        } else {
                            "No hay impresoras guardadas ni vinculadas. Empareje una impresora en Bluetooth de Android o agréguela en Ajustes."
                        },
                        color = MineTextSecondary,
                        fontSize = 12.sp
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
                        Text("Abrir Bluetooth de Android", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = MineTextSecondary)
            }
        }
    )
}

/**
 * Diálogo mostrado inmediatamente al guardar un vale.
 * Permite imprimir o reimprimir copias adicionales del ticket sin salir ni re-escanear,
 * además de cambiar rápidamente de impresora Bluetooth si hay varias configuradas.
 */
@Composable
fun SavedValeReprintDialog(
    valeTypeTitle: String,
    folio: String,
    itemCountSummary: String,
    activeProfile: PrinterProfile?,
    totalProfiles: Int,
    copiesPrinted: Int,
    isPrinting: Boolean,
    printStatusMessage: String?,
    accentColor: Color = MineSafetyYellow,
    onPrintClick: () -> Unit,
    onChangePrinterClick: () -> Unit,
    onFinishClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isPrinting) onFinishClick() },
        properties = DialogProperties(
            dismissOnBackPress = !isPrinting,
            dismissOnClickOutside = false
        ),
        containerColor = MineDarkSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MineSuccessGreenLight,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AutoShrinkText(
                    text = "$valeTypeTitle Guardado",
                    color = MineTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    targetTextSize = 18.sp,
                    minTextSize = 14.sp
                )
                AutoShrinkText(
                    text = "Folio: $folio • $itemCountSummary",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    targetTextSize = 13.sp,
                    minTextSize = 10.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "El vale se guardó correctamente en el historial e inventario. ¿Desea imprimir el ticket térmico ahora?",
                    color = MineTextSecondary,
                    fontSize = 12.sp
                )

                ActivePrinterMiniBar(
                    activeProfile = activeProfile,
                    totalProfiles = totalProfiles,
                    onChangePrinterClick = onChangePrinterClick
                )

                if (copiesPrinted > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MineSuccessGreen.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = MineSuccessGreenLight, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (copiesPrinted == 1) "1 ticket impreso" else "$copiesPrinted copias impresas",
                            color = MineSuccessGreenLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!printStatusMessage.isNullOrBlank()) {
                    val isError = printStatusMessage.startsWith("Error", ignoreCase = true) ||
                        printStatusMessage.startsWith("Sin impresora", ignoreCase = true)
                    Text(
                        text = printStatusMessage,
                        color = if (isError) MineErrorRed else MineSafetyYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onPrintClick,
                    enabled = !isPrinting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("saved_vale_reprint_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineSafetyYellow,
                        contentColor = MineDarkBackground
                    )
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MineDarkBackground,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("IMPRIMIENDO...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        AutoShrinkText(
                            text = if (copiesPrinted == 0) "Imprimir Ticket" else "Reimprimir Ticket (Copia #${copiesPrinted + 1})",
                            fontWeight = FontWeight.ExtraBold,
                            color = MineDarkBackground,
                            targetTextSize = 13.sp,
                            minTextSize = 10.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onFinishClick,
                enabled = !isPrinting,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("saved_vale_finish_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MineSuccessGreen,
                    contentColor = MineTextPrimary
                )
            ) {
                Text("FINALIZAR", fontWeight = FontWeight.Bold)
            }
        }
    )
}
