package com.ttexplo.mobile.ui.screens.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.license.LicenseManager
import com.ttexplo.mobile.license.LicenseVault
import com.ttexplo.mobile.ui.theme.MineAgentCyan
import com.ttexplo.mobile.ui.theme.MineDarkBackground
import com.ttexplo.mobile.ui.theme.MineDarkSurface
import com.ttexplo.mobile.ui.theme.MineErrorRed
import com.ttexplo.mobile.ui.theme.MineTextPrimary
import com.ttexplo.mobile.ui.theme.MineTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseVaultScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(LicenseVault.load(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var alias by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val deviceId = remember { LicenseManager.getFormattedDeviceId(context) }

    fun reload() {
        entries = LicenseVault.load(context)
        refresh++
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TTExplo", text))
        message = "Copiado al portapapeles"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(LicenseVault.toJson(entries).toByteArray(Charsets.UTF_8))
            }
            message = "Bóveda exportada (${entries.size} licencias)"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val imported = LicenseVault.fromJson(json)
            if (imported.isNotEmpty()) {
                LicenseVault.replaceAll(context, imported)
                reload()
                message = "Bóveda importada (${imported.size} licencias)"
            } else {
                message = "El archivo no contiene licencias válidas"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Licencias guardadas", color = MineTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = MineTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MineDarkBackground)
            )
        },
        containerColor = MineDarkBackground
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineAgentCyan)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TERMINAL ACTUAL", color = MineAgentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(deviceId, color = MineTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { copy(deviceId) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copiar ID de esta terminal")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.weight(1f).testTag("vault_add_license_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar licencia")
                }
                OutlinedButton(
                    onClick = { exportLauncher.launch("ttexplo-licencias.json") },
                    modifier = Modifier.weight(1f)
                ) { Text("Exportar") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Importar") }
            }

            message?.let {
                Text(it, color = MineAgentCyan, fontSize = 12.sp)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    val info = LicenseManager.verifyToken(context, entry.token).getOrNull()
                    val target = info?.deviceId ?: "?"
                    val sameTerminal = target == "*" || target == deviceId
                    val expired = info != null && !info.expiresDate.equals("PERPETUAL", true) &&
                        LicenseManager.getDaysRemaining(info.expiresDate) <= 0
                    val status = when {
                        info == null -> "INVÁLIDA"
                        expired -> "VENCIDA"
                        !sameTerminal -> "OTRA TERMINAL"
                        else -> "VIGENTE"
                    }
                    val statusColor = if (info != null && !expired && sameTerminal) MineAgentCyan else MineErrorRed

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor),
                        modifier = Modifier.testTag("vault_license_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.alias, color = MineTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "$status • ${info?.tier ?: "?"} • $target",
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Vence: ${info?.expiresDate ?: "—"}", color = MineTextMuted, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = {
                                    val result = LicenseManager.saveLicense(context, entry.token)
                                    message = if (result.isSuccess) "Licencia instalada en esta terminal" else result.exceptionOrNull()?.message
                                }) { Text("Instalar") }
                                IconButton(onClick = { copy(entry.token) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MineTextPrimary)
                                }
                                IconButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, entry.token)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Compartir licencia"))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Compartir", tint = MineTextPrimary)
                                }
                                IconButton(onClick = {
                                    LicenseVault.remove(context, entry.id)
                                    reload()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MineErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Agregar licencia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Alias") }, singleLine = true)
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token TTEX:... o clave TTEX-...") }, maxLines = 4)
                    addError?.let { Text(it, color = MineErrorRed, fontSize = 11.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = LicenseVault.add(context, alias, token)
                    if (result.isSuccess) {
                        showAdd = false
                        alias = ""
                        token = ""
                        addError = null
                        reload()
                    } else {
                        addError = result.exceptionOrNull()?.message ?: "Licencia no válida"
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
        )
    }
}
