package com.ttexplo.mobile.ui.screens.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.ttexplo.mobile.license.LicenseManager
import com.ttexplo.mobile.license.LicenseStatus
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onLicenseActivated: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    var licenseState by remember { mutableStateOf(LicenseManager.checkLicense(context)) }
    var tokenInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val formattedDeviceId = remember { LicenseManager.getFormattedDeviceId(context) }
    val rawDeviceId = remember { LicenseManager.getRawDeviceId(context) }

    // Función para activar token o contenido .lic
    fun processTokenActivation(rawToken: String) {
        val trimmed = rawToken.trim()
        if (trimmed.isBlank()) {
            errorMessage = "Ingrese el token 'TTEX:...', importe su archivo .lic o escanee el código QR."
            return
        }

        val result = LicenseManager.saveLicense(context, trimmed)
        if (result.isSuccess) {
            val info = result.getOrThrow()
            tokenInput = info.rawToken
            licenseState = LicenseManager.checkLicense(context)
            errorMessage = null
            successMessage = "¡Licencia activada con éxito para ${info.client} (${info.tier})!"
            Toast.makeText(context, "Licencia activada correctamente", Toast.LENGTH_SHORT).show()
        } else {
            val err = result.exceptionOrNull()?.message ?: "Error al validar la licencia."
            errorMessage = err
            successMessage = null
            Toast.makeText(context, "Error: $err", Toast.LENGTH_LONG).show()
        }
    }

    // Selector de archivo .lic / .json / .txt (compatible con "GUARDAR .LIC" del generador web)
    val licFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }.orEmpty()
                if (content.isBlank()) {
                    errorMessage = "El archivo seleccionado está vacío."
                } else {
                    tokenInput = content.trim()
                    processTokenActivation(content)
                }
            } catch (e: Exception) {
                errorMessage = "No se pudo leer el archivo .lic: ${e.message}"
            }
        }
    }

    // Escucha de escáner Zebra / OEM DataWedge
    DisposableEffect(Unit) {
        val receiver = ZebraScannerReceiver { barcode ->
            if (barcode.isNotBlank()) {
                tokenInput = barcode.trim()
                processTokenActivation(barcode)
            }
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose {
            ZebraScannerReceiver.unregister(context, receiver)
        }
    }

    val isLicensedAndActive = licenseState.status == LicenseStatus.VALID || licenseState.status == LicenseStatus.EXPIRING_SOON

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MineSafetyYellow
                        )
                        Column {
                            AutoShrinkText(
                                text = "LICENCIAMIENTO DE SOFTWARE",
                                initialTextSize = 16.sp,
                                minTextSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MineSafetyYellow
                            )
                            AutoShrinkText(
                                text = "T&T Explo Mobile • Candado y Terminal",
                                initialTextSize = 11.sp,
                                minTextSize = 9.sp,
                                color = MineTextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("license_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Regresar",
                                tint = MineTextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MineDarkBackground)
            )
        },
        containerColor = MineDarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ==========================================
            // TARJETA 1: IDENTIFICADOR DE HARDWARE (ANDROID ID)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineAgentCyan.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = MineAgentCyan)
                        Text(
                            text = "IDENTIFICADOR DE HARDWARE (DEVICE ID)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MineAgentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Use este Device ID exacto en su Generador de Licencias (o seleccione 'Cualquier Terminal (*)' en el generador):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MineTextSecondary,
                        fontSize = 12.sp
                    )

                    // Bloque con el ID destacado
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MineDarkBackground)
                            .border(1.dp, MineBorderGray, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formattedDeviceId,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = MineSafetyYellow
                                )
                                Text(
                                    text = "Raw: $rawDeviceId",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MineTextMuted
                                )
                            }

                            Button(
                                onClick = {
                                    clipboardManager.setPrimaryClip(
                                        ClipData.newPlainText("Android ID", formattedDeviceId)
                                    )
                                    Toast.makeText(context, "ID copiado: $formattedDeviceId", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("license_copy_device_id_button"),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MineAgentCyan,
                                    contentColor = MineDarkBackground
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPIAR ID", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // TARJETA 2: ESTADO ACTUAL DE LA LICENCIA
            // ==========================================
            val statusColor = when (licenseState.status) {
                LicenseStatus.VALID -> MineSuccessGreen
                LicenseStatus.EXPIRING_SOON -> MineHighExplosiveOrange
                LicenseStatus.EXPIRED -> MineErrorRed
                LicenseStatus.DEVICE_MISMATCH -> MineErrorRed
                LicenseStatus.UNLICENSED -> MineHighExplosiveOrange
                LicenseStatus.CORRUPTED -> MineErrorRed
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                imageVector = if (isLicensedAndActive) Icons.Default.VerifiedUser else Icons.Default.Warning,
                                contentDescription = null,
                                tint = statusColor
                            )
                            Text(
                                text = "ESTADO DE LICENCIA",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = when (licenseState.status) {
                                    LicenseStatus.VALID -> "AUTORIZADA"
                                    LicenseStatus.EXPIRING_SOON -> "PRÓXIMA A VENCER"
                                    LicenseStatus.EXPIRED -> "VENCIDA"
                                    LicenseStatus.DEVICE_MISMATCH -> "DISPOSITIVO NO AUTORIZADO"
                                    LicenseStatus.UNLICENSED -> "SIN LICENCIA"
                                    LicenseStatus.CORRUPTED -> "FIRMA INVÁLIDA"
                                },
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = licenseState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MineTextPrimary,
                        fontSize = 12.sp
                    )

                    // Si hay licencia instalada, mostrar resumen
                    licenseState.info?.let { info ->
                        HorizontalDivider(color = MineBorderGray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cliente:", color = MineTextSecondary, fontSize = 12.sp)
                            Text(info.client, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Mina / Faena:", color = MineTextSecondary, fontSize = 12.sp)
                            Text(info.mine, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Modalidad:", color = MineTextSecondary, fontSize = 12.sp)
                            Text(info.tier, color = MineSafetyYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vigencia:", color = MineTextSecondary, fontSize = 12.sp)
                            Text(
                                if (info.expiresDate.equals("PERPETUAL", ignoreCase = true)) "VITALICIA" else info.expiresDate.take(10),
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        if (!info.expiresDate.equals("PERPETUAL", ignoreCase = true)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Días restantes:", color = MineTextSecondary, fontSize = 12.sp)
                                Text("${licenseState.daysRemaining} días", color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // TARJETA 3: ACTIVAR NUEVA LICENCIA
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MineSafetyYellow)
                        Text(
                            text = "ACTIVAR / RENOVAR LICENCIA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MineSafetyYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Ingrese la clave corta firmada (ej. TTEX-A271122-ALL-XXXXXXXX), pegue el token completo (TTEX:...), importe el archivo .lic o escanee el QR con su terminal Zebra:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MineTextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = {
                            tokenInput = it
                            errorMessage = null
                        },
                        label = { Text("Clave corta (TTEX-...) o Token (TTEX:...)") },
                        placeholder = { Text("Ej. TTEX-PERP-ALL-8A3F1C92 o TTEX:eyJ...") },
                        modifier = Modifier.fillMaxWidth().testTag("license_token_input"),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MineSafetyYellow,
                            focusedTextColor = MineTextPrimary,
                            unfocusedTextColor = MineTextPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        tokenInput = text.trim()
                                        errorMessage = null
                                    } else {
                                        Toast.makeText(context, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("license_paste_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = MineTextPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            AutoShrinkText("Pegar", targetTextSize = 12.sp, minTextSize = 9.sp, color = MineTextPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                licFilePicker.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("license_import_file_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MineAgentCyan)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MineAgentCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            AutoShrinkText("Importar .LIC", targetTextSize = 12.sp, minTextSize = 9.sp, color = MineAgentCyan)
                        }
                    }

                    Button(
                        onClick = { processTokenActivation(tokenInput) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("license_activate_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MineSafetyYellow,
                            contentColor = MineDarkBackground
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ACTIVAR LICENCIA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MineErrorRed.copy(alpha = 0.15f))
                                .border(1.dp, MineErrorRed, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MineErrorRed)
                                Text(
                                    text = errorMessage ?: "",
                                    color = MineErrorRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (successMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MineSuccessGreen.copy(alpha = 0.15f))
                                .border(1.dp, MineSuccessGreen, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MineSuccessGreen)
                                Text(
                                    text = successMessage ?: "",
                                    color = MineSuccessGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // BOTÓN CONTINUAR / ENTRAR SI ESTÁ LICENCIADO
            // ==========================================
            if (isLicensedAndActive) {
                Button(
                    onClick = {
                        onLicenseActivated()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("license_enter_app_button"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineSuccessGreen,
                        contentColor = MineTextPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INGRESAR A LA APLICACIÓN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
