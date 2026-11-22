package com.ttexplo.mobile.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generador determinista de códigos de barras DUMMY con formato SEDENA válido
 * (y casos negativos de prueba) para validar los flujos de escaneo sin pistola láser física.
 */
object DummyBarcodeGenerator {
    private val cajaProductIds = listOf("315", "056", "337", "207", "001")
    private val sacoProductIds = listOf("298", "272")
    private val cartuchoProductIds = listOf("056", "315", "319", "001")
    private val accessoryPrefixes = listOf(
        "XDET-NOEL",
        "XCOR-PENT",
        "XCAN-SEG",
        "XMECH-SEG",
        "XFUL-ELEC"
    )

    val presetAccessories = listOf(
        "Detonador" to "XDET-NOEL-3M",
        "Cordón" to "XCOR-PENT-5G",
        "Cañuela" to "XCAN-SEG-3M",
        "Mecha" to "XMECH-SEG-10M"
    )

    private val cajaCounter = AtomicInteger(1)
    private val sacoCounter = AtomicInteger(1)
    private val cartuchoCounter = AtomicInteger(1)
    private val accesorioCounter = AtomicInteger(1)

    private fun todayDDMMYY(): String {
        return try {
            SimpleDateFormat("ddMMyy", Locale.US).format(Date())
        } catch (_: Exception) {
            "150826"
        }
    }

    /** Caja SEDENA (30 dígitos): [3 ID][6 DDMMYY][6 Inicial][6 Final][9 Serie] */
    fun nextCaja(): String {
        val seq = cajaCounter.getAndIncrement()
        val id = cajaProductIds[(seq - 1) % cajaProductIds.size]
        val fecha = todayDDMMYY()
        val baseInicial = 16000 + ((seq - 1) % 15000) * 50
        val inicial = baseInicial.toString().padStart(6, '0')
        val finalNum = (baseInicial + 47).toString().padStart(6, '0')
        val serie = (5010000 + seq).toString().padStart(9, '0')
        return "$id$fecha$inicial$finalNum$serie"
    }

    /** Saco SEDENA (18 dígitos): [3 ID Agente][6 DDMMYY][9 Serie] */
    fun nextSaco(): String {
        val seq = sacoCounter.getAndIncrement()
        val id = sacoProductIds[(seq - 1) % sacoProductIds.size]
        val fecha = todayDDMMYY()
        val serie = (4800000 + seq).toString().padStart(9, '0')
        return "$id$fecha$serie"
    }

    /** Cartucho / Pieza SEDENA (16 dígitos): [3 ID Alto Explosivo][1 Dig][6 DDMMYY][6 Serie] */
    fun nextCartucho(): String {
        val seq = cartuchoCounter.getAndIncrement()
        val id = cartuchoProductIds[(seq - 1) % cartuchoProductIds.size]
        val fecha = todayDDMMYY()
        val serie = (110000 + (seq % 880000)).toString().padStart(6, '0')
        return "${id}2$fecha$serie"
    }

    /** Accesorio único (prefijo X) para módulos que validan unicidad por pieza (ej. Devoluciones). */
    fun nextAccesorioUnique(): String {
        val seq = accesorioCounter.getAndIncrement()
        val prefix = accessoryPrefixes[(seq - 1) % accessoryPrefixes.size]
        return "$prefix-${seq.toString().padStart(3, '0')}"
    }

    /** Lote mixto de códigos válidos (2 Cajas, 2 Sacos, 1 Cartucho + opcional 1 Accesorio). */
    fun sampleBatchForExplosives(includeAccessory: Boolean = false): List<String> {
        val list = mutableListOf(
            nextCaja(),
            nextCaja(),
            nextSaco(),
            nextSaco(),
            nextCartucho()
        )
        if (includeAccessory) {
            list.add(nextAccesorioUnique())
        }
        return list
    }

    /** Caso negativo: Alto Explosivo (315) en formato Saco (18 dígitos) -> viola candado SEDENA. */
    fun sampleInvalidCoherence(): String = "315${todayDDMMYY()}004801474"

    /** Caso negativo: ID 999 no catalogado -> dispara el botón '+ Agregar ID'. */
    fun sampleUnknownId(): String = "999${todayDDMMYY()}004801474"

    /** Caso negativo: Accesorio en módulo exclusivo de Explosivos. */
    fun sampleForbiddenAccessory(): String = "XDET-PRUEBA-01"

    /** Caso negativo: Código de accesorio inválido sin prefijo 'X'. */
    fun sampleInvalidAccessoryNoX(): String = "900123456"
}

/**
 * Panel plegable de Simulación de Escaneo (MODO PRUEBA) para Entradas, Despacho y Devoluciones.
 * Inicia plegado para no estorbar la operación real con escáner físico Zebra.
 */
@Composable
fun DummyExplosivesScannerBar(
    onSimulateScan: (String) -> Unit,
    lastValidCode: String?,
    allowAccessories: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isRunningBatch by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkSurface)
            .border(
                width = 1.dp,
                color = if (expanded) MineSafetyAmber else MineSafetyAmber.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Cabecera plegable con badge explícito MODO PRUEBA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable { expanded = !expanded }
                .testTag("dummy_scanner_toggle")
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = "Simulador de escaneo",
                    tint = MineSafetyAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Simulador de Escaneo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MineTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MineSafetyAmber.copy(alpha = 0.2f))
                        .border(0.5.dp, MineSafetyAmber, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "MODO PRUEBA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MineSafetyAmber
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Ocultar simulador" else "Mostrar simulador",
                tint = MineSafetyAmber,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MineDarkBackground.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Generar lectura simulada (pasa por validador SEDENA real):",
                    fontSize = 10.sp,
                    color = MineTextSecondary,
                    fontWeight = FontWeight.Medium
                )

                // Fila 1: Lecturas válidas
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        DummyActionChip(
                            label = "+ Caja (30d)",
                            accent = EmpaqueCajaColor,
                            enabled = !isRunningBatch,
                            tag = "dummy_scan_caja",
                            onClick = { onSimulateScan(DummyBarcodeGenerator.nextCaja()) }
                        )
                    }
                    item {
                        DummyActionChip(
                            label = "+ Saco (18d)",
                            accent = EmpaqueSacoColor,
                            enabled = !isRunningBatch,
                            tag = "dummy_scan_saco",
                            onClick = { onSimulateScan(DummyBarcodeGenerator.nextSaco()) }
                        )
                    }
                    item {
                        DummyActionChip(
                            label = "+ Cartucho (16d)",
                            accent = EmpaqueCartuchoColor,
                            enabled = !isRunningBatch,
                            tag = "dummy_scan_cartucho",
                            onClick = { onSimulateScan(DummyBarcodeGenerator.nextCartucho()) }
                        )
                    }
                    if (allowAccessories) {
                        item {
                            DummyActionChip(
                                label = "+ Accesorio (X)",
                                accent = EmpaqueAccesorioColor,
                                enabled = !isRunningBatch,
                                tag = "dummy_scan_accesorio",
                                onClick = { onSimulateScan(DummyBarcodeGenerator.nextAccesorioUnique()) }
                            )
                        }
                    }
                    item {
                        DummyActionChip(
                            label = if (isRunningBatch) "Cargando..." else "+ Lote (${if (allowAccessories) 6 else 5})",
                            accent = MineSuccessGreenLight,
                            icon = Icons.Default.PlaylistAdd,
                            enabled = !isRunningBatch,
                            tag = "dummy_scan_batch",
                            onClick = {
                                if (!isRunningBatch) {
                                    isRunningBatch = true
                                    coroutineScope.launch {
                                        val batch = DummyBarcodeGenerator.sampleBatchForExplosives(allowAccessories)
                                        batch.forEach { code ->
                                            onSimulateScan(code)
                                            delay(140L)
                                        }
                                        isRunningBatch = false
                                    }
                                }
                            }
                        )
                    }
                }

                // Fila 2: Pruebas de candados SEDENA (Duplicado, Error coherencia, ID nuevo)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        DummyActionChip(
                            label = "Duplicado",
                            accent = MineWarningAmber,
                            icon = Icons.Default.ContentCopy,
                            enabled = !isRunningBatch && lastValidCode != null,
                            tag = "dummy_scan_duplicate",
                            onClick = {
                                lastValidCode?.let { onSimulateScan(it) }
                            }
                        )
                    }
                    item {
                        DummyActionChip(
                            label = "Error SEDENA",
                            accent = MineErrorRedLight,
                            icon = Icons.Default.ErrorOutline,
                            enabled = !isRunningBatch,
                            tag = "dummy_scan_sedena_error",
                            onClick = { onSimulateScan(DummyBarcodeGenerator.sampleInvalidCoherence()) }
                        )
                    }
                    item {
                        DummyActionChip(
                            label = "ID Nuevo (999)",
                            accent = MineSafetyYellow,
                            enabled = !isRunningBatch,
                            tag = "dummy_scan_unknown_id",
                            onClick = { onSimulateScan(DummyBarcodeGenerator.sampleUnknownId()) }
                        )
                    }
                    if (!allowAccessories) {
                        item {
                            DummyActionChip(
                                label = "Error Acc (X)",
                                accent = EmpaqueAccesorioColor,
                                icon = Icons.Default.ErrorOutline,
                                enabled = !isRunningBatch,
                                tag = "dummy_scan_forbidden_acc",
                                onClick = { onSimulateScan(DummyBarcodeGenerator.sampleForbiddenAccessory()) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Panel plegable de Simulación de Escaneo (MODO PRUEBA) para el módulo de Accesorios.
 */
@Composable
fun DummyAccesoriosScannerBar(
    onSimulateAccessory: (String) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isRunningBatch by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkSurface)
            .border(
                width = 1.dp,
                color = if (expanded) MineSafetyAmber else MineSafetyAmber.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable { expanded = !expanded }
                .testTag("dummy_accesorios_toggle")
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = "Simulador de accesorios",
                    tint = MineSafetyAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Simulador de Escaneo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MineTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MineSafetyAmber.copy(alpha = 0.2f))
                        .border(0.5.dp, MineSafetyAmber, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "MODO PRUEBA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MineSafetyAmber
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Ocultar simulador" else "Mostrar simulador",
                tint = MineSafetyAmber,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MineDarkBackground.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Simular lectura de accesorio (usa la cantidad seleccionada arriba):",
                    fontSize = 10.sp,
                    color = MineTextSecondary,
                    fontWeight = FontWeight.Medium
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DummyBarcodeGenerator.presetAccessories.forEach { (label, code) ->
                        item {
                            DummyActionChip(
                                label = "+ $label",
                                accent = if (accentColor == MineAccessoryPurple) EmpaqueAccesorioColor else accentColor,
                                enabled = !isRunningBatch,
                                tag = "dummy_acc_${label.lowercase()}",
                                onClick = { onSimulateAccessory(code) }
                            )
                        }
                    }
                    item {
                        DummyActionChip(
                            label = if (isRunningBatch) "Cargando..." else "+ Lote (3)",
                            accent = MineSuccessGreenLight,
                            icon = Icons.Default.PlaylistAdd,
                            enabled = !isRunningBatch,
                            tag = "dummy_acc_batch",
                            onClick = {
                                if (!isRunningBatch) {
                                    isRunningBatch = true
                                    coroutineScope.launch {
                                        DummyBarcodeGenerator.presetAccessories.take(3).forEach { (_, code) ->
                                            onSimulateAccessory(code)
                                            delay(150L)
                                        }
                                        isRunningBatch = false
                                    }
                                }
                            }
                        )
                    }
                    item {
                        DummyActionChip(
                            label = "Error sin X",
                            accent = MineErrorRedLight,
                            icon = Icons.Default.ErrorOutline,
                            enabled = !isRunningBatch,
                            tag = "dummy_acc_error",
                            onClick = { onSimulateAccessory(DummyBarcodeGenerator.sampleInvalidAccessoryNoX()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DummyActionChip(
    label: String,
    accent: Color,
    tag: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val activeColor = if (enabled) accent else MineTextMuted
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .testTag(tag),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, activeColor.copy(alpha = if (enabled) 0.85f else 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = activeColor.copy(alpha = if (enabled) 0.14f else 0.05f),
            contentColor = activeColor,
            disabledContentColor = MineTextMuted
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
