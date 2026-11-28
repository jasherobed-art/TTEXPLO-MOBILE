package com.ttexplo.mobile.ui.screens.download

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttexplo.mobile.net.DownloadServer
import kotlinx.coroutines.delay
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*

/**
 * Centro de descarga por WiFi. Enciende un servidor local con PIN y muestra la
 * dirección que se abre desde la PC. Solo lectura.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadCenterScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val server = remember { DownloadServer(context) }
    var running by remember { mutableStateOf(false) }
    val clients by server.clients.collectAsStateWithLifecycle()
    val lastClientAt by server.lastClientAt.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pin by remember { mutableStateOf(server.pin) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            now = System.currentTimeMillis()
        }
    }
    val ip = remember { DownloadServer.deviceIp() }
    val url = "http://$ip:8080"
    val recentlyConnected = lastClientAt > 0L && now - lastClientAt < 60_000L
    val connected = running && (clients > 0 || recentlyConnected)
    val statusColor = when {
        !running -> MineTextMuted
        connected -> MineSuccessGreenLight
        else -> MineSafetyYellow
    }
    val statusText = when {
        !running -> "SERVIDOR APAGADO"
        clients > 0 -> "CONECTADO · $clients PC"
        connected -> "CONECTADO · PC RECIENTE"
        else -> "ESPERANDO CONEXIÓN"
    }

    // Al entrar al Centro de descarga, el servidor arranca automáticamente.
    LaunchedEffect(Unit) {
        if (!server.running) {
            runCatching { server.start() }
                .onSuccess { running = true }
                .onFailure {
                    Toast.makeText(context, "No se pudo iniciar: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    DisposableEffect(server) {
        onDispose { server.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AutoShrinkText(
                        text = "Centro de descarga (WiFi)",
                        color = MineTextPrimary,
                        fontWeight = FontWeight.Bold,
                        targetTextSize = 17.sp,
                        minTextSize = 13.sp
                    )
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (running) MineSuccessGreen else MineBorderGray
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = if (running) {
                            "Abre esta dirección en el navegador de la PC que esté en la misma red WiFi."
                        } else {
                            "Enciende el servidor para descargar vales, inventario y catálogo desde la PC."
                        },
                        color = MineTextSecondary,
                        fontSize = 12.sp
                    )

                    Text("DIRECCIÓN", color = MineTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MineDarkBackground)
                            .border(1.dp, MineBorderGray, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = url,
                            color = MineSafetyYellow,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("URL", url))
                            Toast.makeText(context, "Dirección copiada", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("COPIAR", color = MineAgentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("PIN DE ACCESO", color = MineTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MineDarkBackground)
                            .border(1.dp, MineBorderGray, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = pin.toCharArray().joinToString(" "),
                            color = MineSafetyYellow,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        TextButton(onClick = { server.regeneratePin(); pin = server.pin }) {
                            Text("REGENERAR", color = MineAgentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!running) {
                            Button(
                                onClick = {
                                    runCatching { server.start() }
                                        .onSuccess { running = true }
                                        .onFailure {
                                            Toast.makeText(context, "No se pudo iniciar: ${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("download_start_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MineSuccessGreen, contentColor = MineTextPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("ENCENDER", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { server.stop(); running = false },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("download_stop_button"),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MineErrorRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MineErrorText),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("APAGAR", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("QUÉ PUEDES DESCARGAR", color = MineSafetyYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("• Vales de entradas, salidas, devoluciones y accesorios (CSV)", color = MineTextSecondary, fontSize = 12.sp)
                    Text("• Inventario y movimientos (CSV)", color = MineTextSecondary, fontSize = 12.sp)
                    Text("• Catálogo de productos (CSV)", color = MineTextSecondary, fontSize = 12.sp)
                    Text("• Paquete completo (JSON)", color = MineTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Solo lectura: desde la PC no se puede editar ni borrar nada.",
                        color = MineTextMuted,
                        fontSize = 11.sp
                    )
                    if (running) {
                        Text(
                            "Clientes conectados ahora: $clients",
                            color = MineSuccessGreenLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Tutorial animado de primera vez, con opción de no volver a mostrarlo.
    val tutorialPrefs = remember { context.getSharedPreferences("ttexplo_prefs", Context.MODE_PRIVATE) }
    var showTutorial by remember {
        mutableStateOf(!tutorialPrefs.getBoolean("download_tutorial_seen", false))
    }
    var dontShowAgain by remember { mutableStateOf(true) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(showTutorial) {
        while (showTutorial) {
            delay(2600)
            tutorialStep = (tutorialStep + 1) % 3
        }
    }

    fun closeTutorial() {
        if (dontShowAgain) {
            tutorialPrefs.edit().putBoolean("download_tutorial_seen", true).apply()
        }
        showTutorial = false
    }

    if (showTutorial) {
        val steps = listOf(
            Triple("1. Enciende el servidor", "Toca ENCENDER. La terminal te da una dirección y un PIN.", Icons.Default.Wifi),
            Triple("2. Conéctate desde la PC", "En la PC, en la misma red WiFi, abre http://IP:8080 y escribe el PIN.", Icons.Default.Computer),
            Triple("3. Descarga tus datos", "Vales (.txt y CSV), inventario y catálogo. Varias PC pueden conectarse a la vez.", Icons.Default.CloudDownload)
        )
        val stepColors = listOf(MineAgentCyan, MineSafetyYellow, MineSuccessGreenLight)
        Dialog(onDismissRequest = { closeTutorial() }) {
            Surface(shape = RoundedCornerShape(18.dp), color = MineDarkSurface) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "CÓMO DESCARGAR POR WIFI",
                        color = MineSafetyYellow,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Crossfade(targetState = tutorialStep, label = "tutorial") { s ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(steps[s].third, contentDescription = null, tint = stepColors[s], modifier = Modifier.size(64.dp))
                            Text(steps[s].first, color = MineTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                            Text(steps[s].second, color = MineTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { i ->
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (i == tutorialStep) MineSafetyYellow else MineBorderGray)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                        Text("No volver a mostrar", color = MineTextSecondary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { if (tutorialStep < 2) tutorialStep++ else closeTutorial() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow, contentColor = MineDarkBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (tutorialStep < 2) "SIGUIENTE" else "ENTENDIDO", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { closeTutorial() }) {
                        Text("Omitir", color = MineTextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
