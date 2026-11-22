package com.ttexplo.mobile.ui.screens.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.inventory.InventoryCodeItem
import com.ttexplo.mobile.domain.inventory.InventoryCodeStatus
import com.ttexplo.mobile.domain.inventory.InventoryEventType
import com.ttexplo.mobile.domain.inventory.InventoryStockItem
import com.ttexplo.mobile.domain.inventory.KardexEntry
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*
import kotlin.math.abs

private enum class CodeFilterMode(val label: String) {
    EN_POLVORIN("En Polvorín"),
    DESPACHADO("Despachados"),
    TODOS("Todos")
}

/**
 * Hoja de detalle por producto con 2 pestañas:
 * - Pestaña 1: Kardex cronológico con saldo corrido, folio y UUID de evento.
 * - Pestaña 2 (Alto Explosivo y Agente Explosivo): Inventario individual de códigos SEDENA
 *   (`En Polvorín` vs `Despachados`) con filtro y buscador por serie.
 */
@Composable
fun ProductKardexSheet(
    stockItem: InventoryStockItem,
    entries: List<KardexEntry>,
    codeItems: List<InventoryCodeItem> = emptyList(),
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onAdjustProduct: () -> Unit
) {
    val supportsCodes = stockItem.category.supportsBarcodeLedger
    var activeTab by remember(stockItem.productId, initialTab) {
        mutableStateOf(if (supportsCodes && initialTab == 1) 1 else 0)
    }
    var codeFilter by remember { mutableStateOf(CodeFilterMode.EN_POLVORIN) }
    var codeSearchQuery by remember { mutableStateOf("") }

    val inStockCount = remember(codeItems) { codeItems.count { it.status == InventoryCodeStatus.EN_POLVORIN } }
    val dispatchedCount = remember(codeItems) { codeItems.count { it.status == InventoryCodeStatus.DESPACHADO } }

    val filteredCodes = remember(codeItems, codeFilter, codeSearchQuery) {
        val q = codeSearchQuery.trim().lowercase()
        codeItems.filter { item ->
            val matchesStatus = when (codeFilter) {
                CodeFilterMode.EN_POLVORIN -> item.status == InventoryCodeStatus.EN_POLVORIN
                CodeFilterMode.DESPACHADO -> item.status == InventoryCodeStatus.DESPACHADO
                CodeFilterMode.TODOS -> true
            }
            val matchesQuery = q.isEmpty() ||
                item.barcode.lowercase().contains(q) ||
                item.lastReferenceFolio.lowercase().contains(q)
            matchesStatus && matchesQuery
        }
    }

    val familyAccent = categoryAccentColor(stockItem.category)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .testTag("product_kardex_dialog"),
            shape = RoundedCornerShape(14.dp),
            color = MineDarkSurface,
            border = BorderStroke(1.5.dp, familyAccent.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
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
                        Icon(
                            imageVector = if (activeTab == 1) Icons.Default.QrCode2 else Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = familyAccent
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            AutoShrinkText(
                                text = "[${stockItem.productId}] ${stockItem.productName}",
                                initialTextSize = 15.sp,
                                minTextSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MineTextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "${stockItem.category.label} • ${entries.size} mov. UUID",
                                fontSize = 11.sp,
                                color = familyAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar detalle", tint = MineTextMuted)
                    }
                }

                // Resumen de existencia actual + botón Ajuste Rápido
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MineDarkBackground)
                        .border(1.dp, MineSafetyYellow.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EXISTENCIA ACTUAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MineTextMuted
                        )
                        AutoShrinkText(
                            text = stockItem.visualEquivalence,
                            initialTextSize = 16.sp,
                            minTextSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MineSafetyYellow,
                            maxLines = 1
                        )
                        if (stockItem.unitWeightKg > 0.0) {
                            Text(
                                text = "Peso unitario: ${BoxUnitWeightCalculator.formatUnitWeight3(stockItem.unitWeightKg)} kg/cart. (${stockItem.cartridgesPerBox} cart./caja)",
                                fontSize = 10.sp,
                                color = MineSuccessGreenLight
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onAdjustProduct,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MineSafetyYellow.copy(alpha = 0.2f),
                            contentColor = MineSafetyYellow
                        )
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajustar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Selector de 2 Pestañas (solo para Alto Explosivo y Agente Explosivo)
                if (supportsCodes) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("product_detail_tab_kardex"),
                            leadingIcon = {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = {
                                Text("Kardex (${entries.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MineAgentCyan,
                                selectedLabelColor = MineDarkBackground,
                                selectedLeadingIconColor = MineDarkBackground,
                                containerColor = MineDarkBackground,
                                labelColor = MineTextSecondary
                            )
                        )
                        FilterChip(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("product_detail_tab_codes"),
                            leadingIcon = {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = {
                                Text("Códigos ($inStockCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MineSuccessGreenLight,
                                selectedLabelColor = MineDarkBackground,
                                selectedLeadingIconColor = MineDarkBackground,
                                containerColor = MineDarkBackground,
                                labelColor = MineTextSecondary
                            )
                        )
                    }
                }

                if (activeTab == 0 || !supportsCodes) {
                    // PESTAÑA 1: KARDEX CRONOLÓGICO
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.eventUuid }) { entry ->
                            KardexEntryRow(entry = entry)
                        }
                    }
                } else {
                    // PESTAÑA 2: INVENTARIO INDIVIDUAL DE CÓDIGOS SEDENA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CodeStatPill(
                            modifier = Modifier.weight(1f),
                            label = "EN POLVORÍN",
                            count = inStockCount,
                            color = MineSuccessGreenLight
                        )
                        CodeStatPill(
                            modifier = Modifier.weight(1f),
                            label = "DESPACHADOS",
                            count = dispatchedCount,
                            color = MineHighExplosiveOrangeLight
                        )
                        CodeStatPill(
                            modifier = Modifier.weight(1f),
                            label = "TRAZADOS",
                            count = codeItems.size,
                            color = MineAgentCyan
                        )
                    }

                    // Aviso transparente si hay saldo físico cargado sin código individual
                    if (abs(stockItem.stockQuantity) > 1e-6 && inStockCount == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MineSafetyAmber.copy(alpha = 0.14f))
                                .border(0.75.dp, MineSafetyAmber.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MineSafetyAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Saldo físico cargado por conteo rápido (${stockItem.visualEquivalence}) sin series escaneadas aún. Las Entradas y Devoluciones registrarán cada código aquí.",
                                fontSize = 11.sp,
                                color = MineTextSecondary
                            )
                        }
                    }

                    // Filtros de estado + buscador por serie
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CodeFilterMode.entries.forEach { mode ->
                            val selected = codeFilter == mode
                            FilterChip(
                                selected = selected,
                                onClick = { codeFilter = mode },
                                label = {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (mode) {
                                        CodeFilterMode.EN_POLVORIN -> MineSuccessGreenLight
                                        CodeFilterMode.DESPACHADO -> MineHighExplosiveOrangeLight
                                        CodeFilterMode.TODOS -> MineAgentCyan
                                    },
                                    selectedLabelColor = MineDarkBackground,
                                    containerColor = MineDarkBackground,
                                    labelColor = MineTextSecondary
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = codeSearchQuery,
                        onValueChange = { codeSearchQuery = it },
                        label = { Text("Buscar por serie, últimos dígitos o folio...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MineSafetyYellow) },
                        trailingIcon = {
                            if (codeSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { codeSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = MineTextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_codes_search_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MineSafetyYellow,
                            unfocusedBorderColor = MineBorderGray,
                            focusedTextColor = MineTextPrimary,
                            unfocusedTextColor = MineTextPrimary
                        )
                    )

                    if (filteredCodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (codeItems.isEmpty()) {
                                    "Aún no hay códigos SEDENA trazados para este producto.\nEscanee Entradas, Despachos o Devoluciones para ver las series aquí."
                                } else {
                                    "Sin códigos SEDENA para el filtro seleccionado."
                                },
                                color = MineTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredCodes, key = { it.barcode }) { codeItem ->
                                SedenaCodeRow(item = codeItem)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeStatPill(
    modifier: Modifier,
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkBackground)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = count.toString(),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = MineTextPrimary
        )
    }
}

@Composable
private fun SedenaCodeRow(item: InventoryCodeItem) {
    val isInStock = item.status == InventoryCodeStatus.EN_POLVORIN
    val statusColor = if (isInStock) MineSuccessGreenLight else MineHighExplosiveOrangeLight
    val empaqueColor = when (item.empaque) {
        TipoEmpaque.CAJA -> EmpaqueCajaColor
        TipoEmpaque.SACO -> EmpaqueSacoColor
        TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
        else -> MineTextSecondary
    }
    val empaqueLabel = when (item.empaque) {
        TipoEmpaque.CAJA -> "CAJA 30d"
        TipoEmpaque.SACO -> "SACO 18d"
        TipoEmpaque.PIEZA -> "CART. ${item.barcode.length}d"
        else -> item.empaque.label.uppercase()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkBackground)
            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(empaqueColor.copy(alpha = 0.18f))
                    .border(0.75.dp, empaqueColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = empaqueLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = empaqueColor
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.2f))
                    .border(0.75.dp, statusColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.status.label.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor
                )
            }
        }

        AutoShrinkText(
            text = item.barcode,
            initialTextSize = 13.sp,
            minTextSize = 10.sp,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.ExtraBold,
            color = MineTextPrimary,
            maxLines = 1
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.lastActionType.shortBadge} ${item.lastReferenceFolio}".trim(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MineSafetyYellow
            )
            if (item.weightKg > 0.0) {
                Text(
                    text = "${BoxUnitWeightCalculator.formatDecimal2(item.weightKg)} kg",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MineTextSecondary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.lastUpdatedIso,
                fontSize = 9.sp,
                color = MineTextMuted
            )
            Text(
                text = "Movs: ${item.movementCount} • UUID: ${item.lastEventUuid.take(8)}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MineTextMuted
            )
        }
    }
}

@Composable
private fun KardexEntryRow(entry: KardexEntry) {
    val badgeColor = when (entry.eventType) {
        InventoryEventType.INITIAL_STOCK -> MineAgentCyan
        InventoryEventType.PHYSICAL_ADJUSTMENT -> MineSafetyYellow
        InventoryEventType.ENTRADA -> MineSuccessGreenLight
        InventoryEventType.DEVOLUCION -> MineSuccessGreenLight
        InventoryEventType.DESPACHO -> MineHighExplosiveOrange
        InventoryEventType.SALIDA_ACCESORIOS -> EmpaqueAccesorioColor
        InventoryEventType.DEVOLUCION_ACCESORIOS -> MineAgentCyan
        InventoryEventType.REVERSAL -> MineErrorRedLight
    }

    val isPositive = entry.deltaQuantity >= 0.0
    val deltaSign = if (isPositive) "+" else ""
    val formattedDelta = if (entry.unit == "PZA") {
        "$deltaSign${BoxUnitWeightCalculator.formatPieces(entry.deltaQuantity)} PZA"
    } else {
        "$deltaSign${BoxUnitWeightCalculator.formatDecimal2(entry.deltaQuantity)} ${entry.unit}"
    }
    val formattedBalance = if (entry.unit == "PZA") {
        "${BoxUnitWeightCalculator.formatPieces(entry.runningBalance)} PZA"
    } else {
        "${BoxUnitWeightCalculator.formatDecimal2(entry.runningBalance)} ${entry.unit}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkBackground)
            .border(1.dp, MineBorderGray, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .border(0.75.dp, badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = entry.eventType.shortBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor
                    )
                }
                if (entry.referenceFolio.isNotBlank()) {
                    Text(
                        text = entry.referenceFolio,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MineTextPrimary
                    )
                }
            }

            Text(
                text = formattedDelta,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = if (isPositive) MineSuccessGreenLight else MineErrorRedLight
            )
        }

        if (entry.notes.isNotBlank()) {
            Text(
                text = entry.notes,
                fontSize = 12.sp,
                color = MineTextSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.timestampIso,
                fontSize = 10.sp,
                color = MineTextMuted
            )
            Text(
                text = "Saldo: $formattedBalance",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MineSafetyYellow
            )
        }

        // Metadata UUID & Sync Readiness
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UUID: ${entry.eventUuid.take(8)} • Term: ${entry.deviceId.take(9)}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MineTextMuted
            )
            Text(
                text = if (entry.syncStatus == "SYNCED") "SYNC OK" else "LOCAL PENDIENTE",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (entry.syncStatus == "SYNCED") MineSuccessGreenLight else MineAgentCyan
            )
        }
    }
}
