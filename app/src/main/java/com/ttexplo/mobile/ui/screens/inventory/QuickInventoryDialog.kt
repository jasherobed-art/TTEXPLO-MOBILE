package com.ttexplo.mobile.ui.screens.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.inventory.InventoryStockItem
import com.ttexplo.mobile.domain.inventory.QuickStockMode
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductInfo
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.scanner.ZebraScannerReceiver
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.DummyBarcodeGenerator
import com.ttexplo.mobile.ui.theme.*
import kotlin.math.abs

data class QuickStockSubmission(
    val productId: String,
    val productName: String,
    val presentation: String,
    val productType: ProductType,
    val inventoryCategory: InventoryCategory,
    val unit: String,
    val mode: QuickStockMode,
    val targetQuantity: Double,
    val cajasCount: Int,
    val sacosCount: Int,
    val cartuchosCount: Int,
    val boxWeightKg: Double,
    val unitWeightKg: Double,
    val cartridgesPerBox: Int,
    val notes: String
)

/**
 * Modal de Carga Rápida de Inventario Inicial / Ajuste Físico en piso.
 * Soporta las 5 familias SEDENA separadas, auto-sugiere familia y unidad al escanear
 * códigos `X...` nuevos (permitiendo editarla), y autocalibra peso unitario en cajas de 30 dígitos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickInventoryDialog(
    catalogProducts: List<ProductoEntity>,
    existingStockByProduct: Map<String, Double>,
    initialItem: InventoryStockItem?,
    scanFeedback: SampleScanFeedback?,
    onScanSample: (String, (ProductInfo, TipoEmpaque) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (QuickStockSubmission) -> Unit
) {
    val context = LocalContext.current

    var sampleBarcodeInput by remember { mutableStateOf("") }
    var showCatalogDropdown by remember { mutableStateOf(false) }
    var showTestSamples by remember { mutableStateOf(false) }

    var selectedId by remember { mutableStateOf(initialItem?.productId ?: "") }
    var selectedName by remember { mutableStateOf(initialItem?.productName ?: "") }
    var selectedPresentation by remember { mutableStateOf(initialItem?.presentation ?: "") }
    var selectedType by remember { mutableStateOf(initialItem?.productType ?: ProductType.ALTO_EXPLOSIVO) }
    var selectedCategory by remember {
        mutableStateOf(
            initialItem?.category ?: InventoryCategory.ALTO_EXPLOSIVO
        )
    }
    var selectedUnit by remember { mutableStateOf(initialItem?.unit ?: selectedCategory.defaultUnit) }

    var boxWeightText by remember {
        mutableStateOf(
            initialItem?.boxWeightKg?.takeIf { it > 0.0 }?.toString() ?: "25.0"
        )
    }
    var cartridgesPerBoxText by remember {
        mutableStateOf(
            initialItem?.cartridgesPerBox?.takeIf { it > 0 }?.toString() ?: ""
        )
    }
    var unitWeightKg by remember { mutableStateOf(initialItem?.unitWeightKg ?: 0.0) }

    var stockMode by remember { mutableStateOf(QuickStockMode.SET_PHYSICAL_COUNT) }

    // Cantidades físicas capturadas en piso
    var cajasInput by remember { mutableStateOf("") }
    var cartuchosInput by remember { mutableStateOf("") }
    var sacosInput by remember { mutableStateOf("") }
    var extraKgInput by remember { mutableStateOf("") }
    var accessoryQtyInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    fun applyResolvedProduct(info: ProductInfo) {
        selectedId = info.id.uppercase()
        selectedName = info.name
        selectedPresentation = info.presentation
        selectedType = info.type
        selectedCategory = info.inventoryCategory
        selectedUnit = when {
            info.type == ProductType.ACCESORIO ->
                info.unit.uppercase().takeIf { it == "PZA" || it == "MTS" } ?: info.inventoryCategory.defaultUnit
            else -> "KG"
        }
        if (info.type != ProductType.ACCESORIO) {
            boxWeightText = if (info.weightKg > 0.0) info.weightKg.toString() else "25.0"
            cartridgesPerBoxText = if (info.cartridgesPerBox > 0) info.cartridgesPerBox.toString() else ""
            unitWeightKg = info.unitWeightKg
        } else {
            boxWeightText = "0"
            cartridgesPerBoxText = ""
            unitWeightKg = 0.0
        }
    }

    // Escucha del escáner físico Zebra DataWedge mientras el diálogo está abierto
    DisposableEffect(context) {
        val receiver = ZebraScannerReceiver { scannedCode ->
            onScanSample(scannedCode) { resolved, _ ->
                applyResolvedProduct(resolved)
            }
        }
        ZebraScannerReceiver.register(context, receiver)
        onDispose {
            ZebraScannerReceiver.unregister(context, receiver)
        }
    }

    // Cálculo automático en vivo
    val parsedBoxWeight = boxWeightText.toDoubleOrNull() ?: 0.0
    val parsedCartsPerBox = cartridgesPerBoxText.toIntOrNull() ?: 0
    val effectiveUnitWeight = remember(parsedBoxWeight, parsedCartsPerBox, unitWeightKg) {
        when {
            parsedBoxWeight > 0.0 && parsedCartsPerBox > 0 ->
                BoxUnitWeightCalculator.calculateUnitWeightKg(parsedBoxWeight, parsedCartsPerBox) ?: unitWeightKg
            else -> unitWeightKg
        }
    }

    val cajasInt = cajasInput.toIntOrNull() ?: 0
    val cartuchosInt = cartuchosInput.toIntOrNull() ?: 0
    val sacosInt = sacosInput.toIntOrNull() ?: 0
    val extraKgDouble = extraKgInput.toDoubleOrNull() ?: 0.0
    val accessoryQtyDouble = accessoryQtyInput.toDoubleOrNull() ?: 0.0

    val calculatedTotal: Double = when (selectedType) {
        ProductType.ALTO_EXPLOSIVO -> BoxUnitWeightCalculator.calculateExplosiveWeightKg(
            fullBoxes = cajasInt,
            looseCartridges = cartuchosInt,
            boxWeightKg = parsedBoxWeight,
            unitWeightKg = effectiveUnitWeight
        )
        ProductType.AGENTE -> BoxUnitWeightCalculator.calculateAgentWeightKg(
            fullSacks = sacosInt,
            extraKg = extraKgDouble,
            sackWeightKg = parsedBoxWeight
        )
        ProductType.ACCESORIO -> BoxUnitWeightCalculator.roundTo4Decimals(accessoryQtyDouble.coerceAtLeast(0.0))
        ProductType.DESCONOCIDO -> 0.0
    }

    val currentStock = existingStockByProduct[selectedId.uppercase()] ?: 0.0
    val projectedDiff = when (stockMode) {
        QuickStockMode.SET_PHYSICAL_COUNT -> BoxUnitWeightCalculator.roundTo4Decimals(calculatedTotal - currentStock)
        QuickStockMode.ADD_INITIAL_STOCK -> calculatedTotal
    }

    val canSubmit = selectedId.isNotBlank() && when (selectedType) {
        ProductType.ALTO_EXPLOSIVO -> {
            val needsCalibrationForLoose = cartuchosInt > 0 && effectiveUnitWeight <= 0.0
            !needsCalibrationForLoose && (stockMode == QuickStockMode.SET_PHYSICAL_COUNT || calculatedTotal > 0.0)
        }
        ProductType.AGENTE -> stockMode == QuickStockMode.SET_PHYSICAL_COUNT || calculatedTotal > 0.0
        ProductType.ACCESORIO -> stockMode == QuickStockMode.SET_PHYSICAL_COUNT || calculatedTotal > 0.0
        ProductType.DESCONOCIDO -> false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .imePadding()
                .heightIn(max = 780.dp),
            shape = RoundedCornerShape(14.dp),
            color = MineDarkSurface,
            border = BorderStroke(1.5.dp, MineSafetyYellow.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header del Modal
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
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MineSafetyYellow
                        )
                        Column {
                            Text(
                                text = "Carga Rápida / Ajuste Físico",
                                color = MineTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Escanee 1 muestra o elija del catálogo (5 Familias)",
                                color = MineTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MineTextMuted)
                    }
                }

                HorizontalDivider(color = MineBorderGray)

                // Cuerpo desplazable
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // PASO 1: ESCANEAR 1 CÓDIGO DE MUESTRA O ELEGIR DEL CATÁLOGO
                    Text(
                        text = "1. IDENTIFICAR PRODUCTO (MUESTRA O CATÁLOGO)",
                        color = MineSafetyYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = sampleBarcodeInput,
                            onValueChange = { sampleBarcodeInput = it },
                            label = { Text("Escanear 1 código (Caja, Saco o X...)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_inv_sample_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (sampleBarcodeInput.isNotBlank()) {
                                        val code = sampleBarcodeInput.trim()
                                        sampleBarcodeInput = ""
                                        onScanSample(code) { resolved, _ ->
                                            applyResolvedProduct(resolved)
                                        }
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MineSafetyYellow,
                                unfocusedBorderColor = MineBorderGray,
                                focusedTextColor = MineTextPrimary,
                                unfocusedTextColor = MineTextPrimary
                            )
                        )
                        FilledTonalButton(
                            onClick = {
                                if (sampleBarcodeInput.isNotBlank()) {
                                    val code = sampleBarcodeInput.trim()
                                    sampleBarcodeInput = ""
                                    onScanSample(code) { resolved, _ ->
                                        applyResolvedProduct(resolved)
                                    }
                                }
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MineSafetyYellow.copy(alpha = 0.2f),
                                contentColor = MineSafetyYellow
                            )
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Identificar muestra")
                        }
                    }

                    // Selector del catálogo + muestras dummy plegables
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showCatalogDropdown = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("quick_inv_catalog_picker"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MineAgentCyan.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MineAgentCyan)
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Elegir del Catálogo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            DropdownMenu(
                                expanded = showCatalogDropdown,
                                onDismissRequest = { showCatalogDropdown = false },
                                modifier = Modifier
                                    .background(MineDarkSurfaceElevated)
                                    .heightIn(max = 300.dp)
                            ) {
                                catalogProducts.forEach { prod ->
                                    val cat = prod.resolvedCategory()
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "[${prod.idProducto}] ${prod.producto}",
                                                    color = MineTextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "${cat.label} • ${prod.presentacion} • ${prod.unidad}",
                                                    color = categoryAccentColor(cat),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            applyResolvedProduct(prod.toDomain())
                                            showCatalogDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showTestSamples = !showTestSamples },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MineSafetyAmber.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyAmber)
                        ) {
                            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prueba", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Botones rápidos de prueba para las 5 familias
                    AnimatedVisibility(visible = showTestSamples) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                AssistChip(
                                    onClick = {
                                        onScanSample(DummyBarcodeGenerator.nextCaja()) { resolved, _ ->
                                            applyResolvedProduct(resolved)
                                        }
                                    },
                                    label = { Text("Alto Exp. (Caja 30d)", fontSize = 11.sp, color = MineHighExplosiveOrange) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        onScanSample(DummyBarcodeGenerator.nextSaco()) { resolved, _ ->
                                            applyResolvedProduct(resolved)
                                        }
                                    },
                                    label = { Text("Agente (Saco 18d)", fontSize = 11.sp, color = EmpaqueSacoColor) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        onScanSample("XCOR-PENT-5G") { resolved, _ ->
                                            applyResolvedProduct(resolved)
                                        }
                                    },
                                    label = { Text("Cordón (MTS)", fontSize = 11.sp, color = MineAgentCyan) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        onScanSample("XCAN-SEG-3M") { resolved, _ ->
                                            applyResolvedProduct(resolved)
                                        }
                                    },
                                    label = { Text("Cañuela (PZA)", fontSize = 11.sp, color = MineSafetyYellow) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        onScanSample("XDET-NOEL-3M") { resolved, _ ->
                                            applyResolvedProduct(resolved)
                                        }
                                    },
                                    label = { Text("Detonador (PZA)", fontSize = 11.sp, color = EmpaqueAccesorioColor) }
                                )
                            }
                        }
                    }

                    // Feedback de muestra escaneada
                    scanFeedback?.let { fb ->
                        val bg = if (fb.isSuccess) MineSuccessGreen.copy(alpha = 0.2f) else MineErrorRedBackground
                        val border = if (fb.isSuccess) MineSuccessGreenLight else MineErrorRed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (fb.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = border,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = fb.message,
                                color = MineTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Tarjeta del producto seleccionado
                    if (selectedId.isNotBlank()) {
                        val catAccent = categoryAccentColor(selectedCategory)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MineDarkBackground)
                                .border(1.dp, catAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AutoShrinkText(
                                    text = "[$selectedId] $selectedName",
                                    initialTextSize = 14.sp,
                                    minTextSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MineSafetyYellow,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(catAccent.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = selectedCategory.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = catAccent
                                    )
                                }
                            }
                            if (selectedPresentation.isNotBlank()) {
                                Text(
                                    text = "Presentación: $selectedPresentation",
                                    fontSize = 11.sp,
                                    color = MineTextSecondary
                                )
                            }
                            Text(
                                text = "Saldo actual en sistema: ${
                                    if (selectedUnit == "PZA") "${BoxUnitWeightCalculator.formatPieces(currentStock)} PZA"
                                    else "${BoxUnitWeightCalculator.formatDecimal2(currentStock)} $selectedUnit"
                                }",
                                fontSize = 11.sp,
                                color = MineTextMuted
                            )
                        }

                        // PASO 2: MODO DE CARGA
                        Text(
                            text = "2. MODO DE REGISTRO",
                            color = MineSafetyYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickStockMode.entries.forEach { mode ->
                                val isSel = stockMode == mode
                                FilterChip(
                                    selected = isSel,
                                    onClick = { stockMode = mode },
                                    modifier = Modifier.weight(1f),
                                    label = {
                                        AutoShrinkText(
                                            text = mode.label,
                                            initialTextSize = 11.sp,
                                            minTextSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) MineDarkBackground else MineTextPrimary
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MineSafetyYellow,
                                        containerColor = MineDarkBackground
                                    )
                                )
                            }
                        }

                        // PASO 3: CAPTURA FÍSICA EN PISO
                        Text(
                            text = "3. CANTIDAD FÍSICA EN PISO",
                            color = MineSafetyYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        when (selectedType) {
                            ProductType.ALTO_EXPLOSIVO -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = cajasInput,
                                        onValueChange = { if (it.all { ch -> ch.isDigit() }) cajasInput = it },
                                        label = { Text("Cajas completas") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quick_inv_cajas_input"),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmpaqueCajaColor,
                                            unfocusedBorderColor = MineBorderGray,
                                            focusedTextColor = MineTextPrimary,
                                            unfocusedTextColor = MineTextPrimary
                                        )
                                    )
                                    OutlinedTextField(
                                        value = cartuchosInput,
                                        onValueChange = { if (it.all { ch -> ch.isDigit() }) cartuchosInput = it },
                                        label = { Text("Cartuchos sueltos") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quick_inv_cartuchos_input"),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmpaqueCartuchoColor,
                                            unfocusedBorderColor = MineBorderGray,
                                            focusedTextColor = MineTextPrimary,
                                            unfocusedTextColor = MineTextPrimary
                                        )
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = boxWeightText,
                                        onValueChange = { boxWeightText = it },
                                        label = { Text("Peso caja (kg)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MineSafetyYellow,
                                            unfocusedBorderColor = MineBorderGray,
                                            focusedTextColor = MineTextPrimary,
                                            unfocusedTextColor = MineTextPrimary
                                        )
                                    )
                                    OutlinedTextField(
                                        value = cartridgesPerBoxText,
                                        onValueChange = { if (it.all { ch -> ch.isDigit() } && it.length <= 3) cartridgesPerBoxText = it },
                                        label = { Text("Cart./caja") },
                                        placeholder = { Text("ej. 100") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MineSafetyYellow,
                                            unfocusedBorderColor = MineBorderGray,
                                            focusedTextColor = MineTextPrimary,
                                            unfocusedTextColor = MineTextPrimary
                                        )
                                    )
                                }

                                if (effectiveUnitWeight > 0.0) {
                                    Text(
                                        text = "Peso unitario activo: ${BoxUnitWeightCalculator.formatUnitWeight3(effectiveUnitWeight)} kg/cartucho",
                                        fontSize = 11.sp,
                                        color = MineSuccessGreenLight,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (cartuchosInt > 0) {
                                    Text(
                                        text = "Para calcular cartuchos sueltos, escanee 1 caja (30d) o capture 'Cart./caja'.",
                                        fontSize = 11.sp,
                                        color = MineErrorRedLight,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            ProductType.AGENTE -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = sacosInput,
                                        onValueChange = { if (it.all { ch -> ch.isDigit() }) sacosInput = it },
                                        label = { Text("Sacos completos") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quick_inv_sacos_input"),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmpaqueSacoColor,
                                            unfocusedBorderColor = MineBorderGray,
                                            focusedTextColor = MineTextPrimary,
                                            unfocusedTextColor = MineTextPrimary
                                        )
                                    )
                                    OutlinedTextField(
                                        value = boxWeightText,
                                        onValueChange = { boxWeightText = it },
                                        label = { Text("Peso saco (kg)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MineSafetyYellow,
                                            unfocusedBorderColor = MineBorderGray,
                                            focusedTextColor = MineTextPrimary,
                                            unfocusedTextColor = MineTextPrimary
                                        )
                                    )
                                }
                                OutlinedTextField(
                                    value = extraKgInput,
                                    onValueChange = { extraKgInput = it },
                                    label = { Text("Kg sueltos / fracción adicional (opcional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MineSafetyYellow,
                                        unfocusedBorderColor = MineBorderGray,
                                        focusedTextColor = MineTextPrimary,
                                        unfocusedTextColor = MineTextPrimary
                                    )
                                )
                            }

                            ProductType.ACCESORIO -> {
                                // Selector editable de las 3 familias de accesorios (auto-sugerido, editable)
                                Text(
                                    text = "Familia SEDENA (sugerida automáticamente, puede cambiarla):",
                                    color = MineTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    InventoryCategory.accessoryCategories.forEach { accCat ->
                                        val isSel = selectedCategory == accCat
                                        val accent = categoryAccentColor(accCat)
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                selectedCategory = accCat
                                                selectedUnit = accCat.defaultUnit
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("quick_inv_cat_${accCat.name.lowercase()}"),
                                            label = {
                                                AutoShrinkText(
                                                    text = accCat.shortLabel,
                                                    initialTextSize = 11.sp,
                                                    minTextSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) MineDarkBackground else MineTextPrimary
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accent,
                                                containerColor = MineDarkBackground
                                            )
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = selectedName,
                                    onValueChange = { selectedName = it },
                                    label = { Text("Descripción del accesorio") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = categoryAccentColor(selectedCategory),
                                        unfocusedBorderColor = MineBorderGray,
                                        focusedTextColor = MineTextPrimary,
                                        unfocusedTextColor = MineTextPrimary
                                    )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Unidad:", color = MineTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    FilterChip(
                                        selected = selectedUnit == "PZA",
                                        onClick = { selectedUnit = "PZA" },
                                        label = { Text("PZA (Piezas)", fontWeight = FontWeight.Bold) }
                                    )
                                    FilterChip(
                                        selected = selectedUnit == "MTS",
                                        onClick = { selectedUnit = "MTS" },
                                        label = { Text("MTS (Metros)", fontWeight = FontWeight.Bold) }
                                    )
                                }
                                OutlinedTextField(
                                    value = accessoryQtyInput,
                                    onValueChange = { accessoryQtyInput = it },
                                    label = { Text("Cantidad física en piso ($selectedUnit)") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("quick_inv_accessory_qty_input"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (selectedUnit == "MTS") KeyboardType.Decimal else KeyboardType.Number
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = categoryAccentColor(selectedCategory),
                                        unfocusedBorderColor = MineBorderGray,
                                        focusedTextColor = MineTextPrimary,
                                        unfocusedTextColor = MineTextPrimary
                                    )
                                )
                            }

                            ProductType.DESCONOCIDO -> Unit
                        }

                        // Nota opcional
                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            label = { Text("Observación / Turno / Auditoría (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MineSafetyYellow,
                                unfocusedBorderColor = MineBorderGray,
                                focusedTextColor = MineTextPrimary,
                                unfocusedTextColor = MineTextPrimary
                            )
                        )

                        // PREVISUALIZACIÓN EN VIVO DEL CÁLCULO AUTOMÁTICO
                        val previewEquiv = BoxUnitWeightCalculator.formatVisualEquivalence(
                            stock = calculatedTotal,
                            unit = selectedUnit,
                            productType = selectedType,
                            boxWeightKg = parsedBoxWeight,
                            unitWeightKg = effectiveUnitWeight,
                            cartridgesPerBox = parsedCartsPerBox
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MineSafetyYellow.copy(alpha = 0.14f))
                                .border(1.5.dp, MineSafetyYellow, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TOTAL CALCULADO (${selectedCategory.label.uppercase()})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MineSafetyYellow
                            )
                            AutoShrinkText(
                                text = previewEquiv,
                                initialTextSize = 18.sp,
                                minTextSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MineTextPrimary
                            )
                            val diffSign = if (projectedDiff >= 0) "+" else ""
                            val diffStr = if (selectedUnit == "PZA") {
                                "$diffSign${BoxUnitWeightCalculator.formatPieces(projectedDiff)} PZA"
                            } else {
                                "$diffSign${BoxUnitWeightCalculator.formatDecimal2(projectedDiff)} $selectedUnit"
                            }
                            Text(
                                text = if (stockMode == QuickStockMode.SET_PHYSICAL_COUNT) {
                                    "Evento de ajuste neto: $diffStr (Nuevo saldo: ${
                                        if (selectedUnit == "PZA") "${BoxUnitWeightCalculator.formatPieces(calculatedTotal)} PZA"
                                        else "${BoxUnitWeightCalculator.formatDecimal2(calculatedTotal)} $selectedUnit"
                                    })"
                                } else {
                                    "Evento de suma: $diffStr"
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (abs(projectedDiff) < 1e-6) MineTextSecondary else MineSuccessGreenLight
                            )
                        }
                    }
                }

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(0.4f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancelar", color = MineTextSecondary)
                    }
                    Button(
                        enabled = canSubmit,
                        onClick = {
                            onConfirm(
                                QuickStockSubmission(
                                    productId = selectedId.trim().uppercase(),
                                    productName = selectedName.trim().ifBlank { selectedId },
                                    presentation = selectedPresentation.trim(),
                                    productType = selectedType,
                                    inventoryCategory = selectedCategory,
                                    unit = selectedUnit,
                                    mode = stockMode,
                                    targetQuantity = calculatedTotal,
                                    cajasCount = cajasInt,
                                    sacosCount = sacosInt,
                                    cartuchosCount = cartuchosInt,
                                    boxWeightKg = parsedBoxWeight,
                                    unitWeightKg = effectiveUnitWeight,
                                    cartridgesPerBox = parsedCartsPerBox,
                                    notes = notesInput.trim()
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(0.6f)
                            .height(48.dp)
                            .testTag("quick_inv_confirm_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MineSafetyYellow,
                            contentColor = MineDarkBackground
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("REGISTRAR EVENTO", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
