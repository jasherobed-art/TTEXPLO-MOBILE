package com.ttexplo.mobile.ui.screens.entradas

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.DummyExplosivesScannerBar
import com.ttexplo.mobile.ui.common.ExitScanConfirmDialog
import com.ttexplo.mobile.ui.common.ProductEditDialog
import com.ttexplo.mobile.ui.common.ScanFlashOverlay
import com.ttexplo.mobile.ui.common.TwoStepDeleteDialog
import com.ttexplo.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntradasScanScreen(
    proveedor: String,
    polvorin: String,
    factura: String,
    entradaId: Long = 0L,
    viewModel: EntradasViewModel,
    onNavigateBack: () -> Unit = {},
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val productSummaries by viewModel.productSummaries.collectAsStateWithLifecycle()
    var manualInput by remember { mutableStateOf("") }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var catalogAddInitialId by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<BarcodeItem?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.scannedItems.isNotEmpty()) {
        showExitConfirm = true
    }

    LaunchedEffect(entradaId) {
        if (entradaId > 0L) {
            viewModel.loadExistingEntrada(entradaId)
        }
    }

    LaunchedEffect(proveedor, polvorin, factura) {
        if (entradaId <= 0L) {
            viewModel.updateHeader(proveedor, polvorin, factura)
        }
    }

    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scannedBarcode ->
            viewModel.onBarcodeScanned(scannedBarcode)
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose {
            ZebraScannerReceiver.unregister(context, receiver)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        AutoShrinkText(
                            text = "Entradas: Polvorín ${uiState.polvorin.ifEmpty { polvorin }}",
                            targetTextSize = 15.sp,
                            minTextSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MineTextPrimary
                        )
                        AutoShrinkText(
                            text = "Prov: ${uiState.proveedor.ifEmpty { proveedor }} • Factura: ${uiState.factura.ifEmpty { factura }}",
                            targetTextSize = 12.sp,
                            minTextSize = 9.sp,
                            color = MineSafetyYellow
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Draft Restored Banner (Crash / Power-off recovery)
            if (uiState.wasRestoredFromDraft) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MineHighExplosiveOrange.copy(alpha = 0.15f))
                        .border(1.dp, MineHighExplosiveOrange, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MineHighExplosiveOrange)
                    Text(
                        text = "Vale en progreso recuperado (${uiState.scannedItems.size} ítems)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MineHighExplosiveOrangeLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // CONTADOR COMPACTO OPTIMIZADO (TOTAL, CAJAS, SACOS, CARTUCHOS)
            val totalCajas = uiState.scannedItems.count { it.empaque == TipoEmpaque.CAJA || it.code.length == 30 }
            val totalSacos = uiState.scannedItems.count { it.empaque == TipoEmpaque.SACO || it.code.length == 18 }
            val totalCartuchos = uiState.scannedItems.count { it.empaque == TipoEmpaque.PIEZA || it.code.length in 16..17 }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText(
                            text = "Factura: ${uiState.factura.ifEmpty { factura }} • Prov: ${uiState.proveedor.ifEmpty { proveedor }}",
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            color = MineTextSecondary,
                            targetTextSize = 12.sp,
                            minTextSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.scannedItems.isEmpty()) "Listo para escanear" else "${uiState.scannedItems.size} ingresados",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.scannedItems.isEmpty()) MineSuccessGreenLight else MineSafetyYellow,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 4 Contadores en 1 sola fila compacta con colores normalizados
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactMetric(
                            modifier = Modifier.weight(1f),
                            label = "TOTAL",
                            count = uiState.scannedItems.size,
                            borderColor = MineBorderGray,
                            countColor = EmpaqueTotalColor
                        )
                        CompactMetric(
                            modifier = Modifier.weight(1f),
                            label = "CAJAS",
                            count = totalCajas,
                            borderColor = EmpaqueCajaColor,
                            countColor = EmpaqueCajaColor
                        )
                        CompactMetric(
                            modifier = Modifier.weight(1f),
                            label = "SACOS",
                            count = totalSacos,
                            borderColor = EmpaqueSacoColor,
                            countColor = EmpaqueSacoColor
                        )
                        CompactMetric(
                            modifier = Modifier.weight(1f),
                            label = "CARTUCHOS",
                            count = totalCartuchos,
                            borderColor = EmpaqueCartuchoColor,
                            countColor = EmpaqueCartuchoColor
                        )
                    }
                }
            }

            // 1. CASILLA DE ESCANEO COMPACTA
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                placeholder = { Text("Escanear código de barras o entrada manual...", fontSize = 13.sp, color = MineTextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (manualInput.isNotBlank()) {
                            viewModel.onBarcodeScanned(manualInput.trim())
                            manualInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedPlaceholderColor = MineTextMuted
                )
            )

            // Simulador de escaneo (MODO PRUEBA plegable)
            DummyExplosivesScannerBar(
                onSimulateScan = { code -> viewModel.onBarcodeScanned(code) },
                lastValidCode = uiState.scannedItems.firstOrNull()?.code,
                allowAccessories = false
            )

            // 2. BANNER COMPACTO DE RESULTADO
            uiState.latestScanResult?.let { result ->
                val isError = !result.isValid
                val isDup = result.isDuplicate
                val bannerBg = when {
                    isError -> MineErrorRed.copy(alpha = 0.2f)
                    isDup -> MineWarningAmber.copy(alpha = 0.2f)
                    else -> MineSuccessGreen.copy(alpha = 0.2f)
                }
                val bannerBorder = when {
                    isError -> MineErrorRed
                    isDup -> MineWarningAmber
                    else -> MineSuccessGreen
                }
                val bannerTint = when {
                    isError -> MineErrorRed
                    isDup -> MineWarningAmber
                    else -> MineSuccessGreenLight
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isError || isDup -> Icons.Default.Warning
                            else -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = bannerTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MineTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (result.unknownId != null) {
                        Button(
                            onClick = {
                                catalogAddInitialId = result.unknownId
                                showCatalogDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MineSafetyYellow, contentColor = MineDarkBackground),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+ Agregar ID", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }

            var selectedSummaryKey by remember { mutableStateOf<Triple<String, String, TipoEmpaque>?>(null) }

            val filteredItems = remember(uiState.scannedItems, selectedSummaryKey) {
                if (selectedSummaryKey == null) {
                    uiState.scannedItems
                } else {
                    val (id, pres, emp) = selectedSummaryKey!!
                    uiState.scannedItems.filter {
                        it.productInfo.id == id && it.productInfo.presentation == pres && it.empaque == emp
                    }
                }
            }

            // 3. DESGLOSE POR PRODUCTO (CUADRÍCULA DE 2 COLUMNAS DINÁMICA E INTERACTIVA)
            if (productSummaries.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 145.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    productSummaries.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { summary ->
                                val key = Triple(summary.id, summary.presentation, summary.empaque)
                                val isSelected = selectedSummaryKey == key
                                Box(modifier = Modifier.weight(1f)) {
                                    ProductCounterCard(
                                        summary = summary,
                                        isSelected = isSelected,
                                        onClick = {
                                            selectedSummaryKey = if (isSelected) null else key
                                        }
                                    )
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Encabezado de lecturas con filtro interactivo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedSummaryKey != null) {
                    val selectedSummary = productSummaries.firstOrNull {
                        it.id == selectedSummaryKey!!.first &&
                        it.presentation == selectedSummaryKey!!.second &&
                        it.empaque == selectedSummaryKey!!.third
                    }
                    Text(
                        text = "FILTRADO: ${selectedSummary?.name ?: ""} (${filteredItems.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineSafetyYellow,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "[VER TODOS (${uiState.scannedItems.size})]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineAgentCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { selectedSummaryKey = null }
                            .padding(4.dp)
                    )
                } else {
                    Text(
                        text = "HISTORIAL DE LECTURAS (${uiState.scannedItems.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toca un producto para filtrar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MineTextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            // 4. Lista de códigos escaneados (muestra filteredItems, se ajusta automáticamente)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredItems, key = { it.code + it.timestamp }) { item ->
                    EntradaScannedItemCard(
                        item = item,
                        onDelete = { itemToDelete = item }
                    )
                }
            }

            // Botón inferior para Finalizar
            Button(
                onClick = { viewModel.finishAndSave(onFinished) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MineSuccessGreen,
                    contentColor = MineTextPrimary
                )
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                AutoShrinkText(
                    text = "GUARDAR Y FINALIZAR ENTRADA (${uiState.scannedItems.size})",
                    fontWeight = FontWeight.Bold,
                    targetTextSize = 14.sp,
                    minTextSize = 11.sp,
                    color = MineTextPrimary
                )
            }
        }
    }
    ScanFlashOverlay(
        event = uiState.latestFlashEvent,
        enabled = viewModel.isVisualFlashEnabled()
    )
    }

    if (showCatalogDialog) {
        ProductEditDialog(
            initialId = catalogAddInitialId,
            initial = null,
            onDismiss = { showCatalogDialog = false },
            onSave = { entity ->
                viewModel.registerProductInCatalog(entity)
                showCatalogDialog = false
            }
        )
    }

    itemToDelete?.let { target ->
        TwoStepDeleteDialog(
            itemDescription = "${target.productInfo.name} (${target.productInfo.presentation})",
            itemCode = "Cód: ${target.code}",
            onConfirmDelete = {
                viewModel.deleteItem(target)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    if (showExitConfirm) {
        ExitScanConfirmDialog(
            itemCount = uiState.scannedItems.size,
            savedAsDraft = true,
            onConfirmExit = {
                showExitConfirm = false
                onNavigateBack()
            },
            onDismiss = { showExitConfirm = false }
        )
    }
}

@Composable
private fun CompactMetric(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    borderColor: Color,
    countColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MineDarkBackground)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = countColor,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MineTextMuted,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Tarjeta de contador individual por producto con AutoShrinkText
 * y badge de empaque de alto contraste.
 */
@Composable
private fun ProductCounterCard(
    summary: ProductSummary,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    // Colores normalizados según el tipo de empaque:
    // CAJA: Amarillo Oro (#FFD600)
    // SACO: Cian Eléctrico (#00E5FF)
    // CARTUCHO / PIEZA: Coral / Naranja Rojizo (#FF916F)
    // ACCESORIO: Morado (#BA68C8)
    val empaqueColor = when (summary.empaque) {
        TipoEmpaque.CAJA -> EmpaqueCajaColor
        TipoEmpaque.SACO -> EmpaqueSacoColor
        TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
        TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
        else -> MineBorderGray
    }
    val empaqueLabel = when (summary.empaque) {
        TipoEmpaque.SACO -> "Sacos"
        TipoEmpaque.PIEZA -> "Cartuchos"
        else -> "Cajas"
    }

    val borderWidth = if (isSelected) 2.dp else 1.dp
    val borderColor = if (isSelected) empaqueColor else empaqueColor.copy(alpha = 0.7f)
    val cardBg = if (isSelected) empaqueColor.copy(alpha = 0.18f) else MineDarkSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(cardBg)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge de conteo siempre a la izquierda con el color de su empaque
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(empaqueColor)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${summary.count}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MineDarkBackground
                )
                Text(
                    text = empaqueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MineDarkBackground,
                    fontSize = 9.sp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            AutoShrinkText(
                text = summary.name,
                initialTextSize = 12.sp,
                minTextSize = 8.sp,
                color = if (isSelected) empaqueColor else MineTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            AutoShrinkText(
                text = if (summary.presentation.isNotBlank()) summary.presentation else "ID: ${summary.id}",
                initialTextSize = 10.sp,
                minTextSize = 7.sp,
                color = MineTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EntradaScannedItemCard(
    item: BarcodeItem,
    onDelete: () -> Unit
) {
    val empaqueColor = when (item.empaque) {
        TipoEmpaque.CAJA -> EmpaqueCajaColor
        TipoEmpaque.SACO -> EmpaqueSacoColor
        TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
        TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
        else -> MineBorderGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkSurface)
            .border(1.dp, empaqueColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(empaqueColor.copy(alpha = 0.2f))
                        .border(0.5.dp, empaqueColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.empaque.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = empaqueColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                AutoShrinkText(
                    text = item.productInfo.name,
                    initialTextSize = 13.sp,
                    minTextSize = 9.sp,
                    color = MineTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            AutoShrinkText(
                text = "Medida: ${item.productInfo.presentation} • ${item.productInfo.type.label}",
                initialTextSize = 11.sp,
                minTextSize = 8.sp,
                color = MineTextSecondary,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
            AutoShrinkText(
                text = "Cód: ${item.code}",
                initialTextSize = 11.sp,
                minTextSize = 8.sp,
                color = MineTextSecondary,
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.timestamp.substringAfter(" "),
                style = MaterialTheme.typography.bodyMedium,
                color = MineTextMuted,
                fontSize = 12.sp
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MineErrorRed
                )
            }
        }
    }
}
