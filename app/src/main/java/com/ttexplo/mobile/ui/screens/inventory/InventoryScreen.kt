package com.ttexplo.mobile.ui.screens.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttexplo.mobile.domain.inventory.BoxUnitWeightCalculator
import com.ttexplo.mobile.domain.inventory.InventoryDashboardTotals
import com.ttexplo.mobile.domain.inventory.InventoryStockItem
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*

internal fun categoryAccentColor(category: InventoryCategory): Color = when (category) {
    InventoryCategory.ALTO_EXPLOSIVO -> MineHighExplosiveOrange
    InventoryCategory.AGENTE_EXPLOSIVO -> EmpaqueSacoColor
    InventoryCategory.CORDON_DETONANTE -> MineAgentCyan
    InventoryCategory.CANUELAS -> MineSafetyYellow
    InventoryCategory.DETONADORES -> EmpaqueAccesorioColor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSync: (initialTab: Int) -> Unit = {},
    onNavigateToLicenseVault: () -> Unit = {},
    initialInspectProductId: String? = null,
    onConsumeInspectProductId: () -> Unit = {},
    viewModel: InventoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedKardexProductId by viewModel.selectedKardexProductId.collectAsStateWithLifecycle()
    val selectedKardexInitialTab by viewModel.selectedKardexInitialTab.collectAsStateWithLifecycle()
    val selectedKardexEntries by viewModel.selectedKardexEntries.collectAsStateWithLifecycle()
    val selectedProductCodes by viewModel.selectedProductCodes.collectAsStateWithLifecycle()
    val sampleScanFeedback by viewModel.sampleScanFeedback.collectAsStateWithLifecycle()

    var showQuickDialog by remember { mutableStateOf(false) }
    var quickDialogInitialProduct by remember { mutableStateOf<InventoryStockItem?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(initialInspectProductId) {
        if (!initialInspectProductId.isNullOrBlank()) {
            viewModel.openKardex(initialInspectProductId, initialTab = 0)
            onConsumeInspectProductId()
        }
    }

    val groupedSections = remember(uiState.filteredItems) {
        InventoryCategory.entries.mapNotNull { cat ->
            val sectionItems = uiState.filteredItems.filter { it.category == cat }
            if (sectionItems.isEmpty()) null else cat to sectionItems
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        AutoShrinkText(
                            text = "Inventario en Polvorín",
                            initialTextSize = 18.sp,
                            minTextSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MineTextPrimary
                        )
                        AutoShrinkText(
                            text = "${uiState.polvorinName} • 5 Familias SEDENA",
                            initialTextSize = 11.sp,
                            minTextSize = 9.sp,
                            color = MineAgentCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
                actions = {
                    BadgedBox(
                        badge = {
                            if (uiState.activeConflictCount > 0) {
                                Badge(containerColor = MineErrorRed) {
                                    Text("${uiState.activeConflictCount}", color = MineTextPrimary, fontSize = 10.sp)
                                }
                            } else if (uiState.totals.pendingSyncEventCount > 0) {
                                Badge(containerColor = MineAgentCyan) {
                                    Text(
                                        text = if (uiState.totals.pendingSyncEventCount > 99) "99+" else "${uiState.totals.pendingSyncEventCount}",
                                        color = MineDarkBackground,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val targetTab = if (uiState.activeConflictCount > 0) 2 else 0
                                onNavigateToSync(targetTab)
                            },
                            modifier = Modifier.testTag("inventory_open_sync_center_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Abrir Centro de Sincronización Multi-Escáner",
                                tint = if (uiState.activeConflictCount > 0) MineErrorRedLight else MineAgentCyan
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("inventory_more_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Más opciones",
                                tint = MineTextPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Licencias") },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = MineAgentCyan)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigateToLicenseVault()
                                },
                                modifier = Modifier.testTag("inventory_license_vault_item")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MineDarkBackground)
            )
        },
        bottomBar = {
            Surface(
                color = MineDarkBackground,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        quickDialogInitialProduct = null
                        viewModel.clearSampleScanFeedback()
                        showQuickDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("inventory_quick_load_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineSafetyYellow,
                        contentColor = MineDarkBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CARGA RÁPIDA / AJUSTE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        },
        containerColor = MineDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // TABLERO SUPERIOR EN VIVO — 5 FAMILIAS SEPARADAS
            FiveFamilyTotalsDashboard(
                totals = uiState.totals,
                selectedFilter = uiState.selectedFilter,
                onSelectCategory = { filter -> viewModel.toggleCategoryFilter(filter) }
            )

            // FRANJA DE ESTADO MULTI-ESCÁNER / ALERTAS DE CONFLICTO
            if (uiState.activeConflictCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MineErrorRedBackground)
                        .border(1.dp, MineErrorRed, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToSync(2) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("inventory_conflict_alert_strip"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MineErrorRedLight, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${uiState.activeConflictCount} conflicto(s) multi-escáner detectado(s) • Toca para revisar",
                            color = MineErrorRedLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MineErrorRedLight, modifier = Modifier.size(18.dp))
                }
            } else if (uiState.totals.pendingSyncEventCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MineAgentCyan.copy(alpha = 0.12f))
                        .border(1.dp, MineAgentCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .clickable { onNavigateToSync(0) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("inventory_pending_sync_strip"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = MineAgentCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${uiState.totals.pendingSyncEventCount} evento(s) UUID listos para cruzar por QR Zebra o JSON",
                            color = MineAgentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MineAgentCyan, modifier = Modifier.size(16.dp))
                }
            }

            // BANNER DE CONFIRMACIÓN DE EVENTO UUID
            AnimatedVisibility(visible = uiState.statusBanner != null) {
                uiState.statusBanner?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MineSuccessGreen.copy(alpha = 0.22f))
                            .border(1.dp, MineSuccessGreenLight, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MineSuccessGreenLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = msg,
                            color = MineTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusBanner() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar aviso",
                                tint = MineTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // BUSCADOR
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Buscar por ID, producto, familia o código X...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MineSafetyYellow)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = MineTextMuted)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_search_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MineSafetyYellow,
                    unfocusedBorderColor = MineBorderGray,
                    focusedTextColor = MineTextPrimary,
                    unfocusedTextColor = MineTextPrimary,
                    focusedLabelColor = MineSafetyYellow
                )
            )

            // FILTROS RÁPIDOS DE LAS 5 FAMILIAS
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(InventoryFilter.entries) { filter ->
                    val selected = uiState.selectedFilter == filter
                    val chipAccent = when (filter) {
                        InventoryFilter.ALTO_EXPLOSIVO -> MineHighExplosiveOrange
                        InventoryFilter.AGENTE_EXPLOSIVO -> EmpaqueSacoColor
                        InventoryFilter.CORDON_DETONANTE -> MineAgentCyan
                        InventoryFilter.CANUELAS -> MineSafetyYellow
                        InventoryFilter.DETONADORES -> EmpaqueAccesorioColor
                        else -> MineSafetyYellow
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectFilter(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipAccent,
                            selectedLabelColor = MineDarkBackground,
                            containerColor = MineDarkSurface,
                            labelColor = MineTextSecondary
                        )
                    )
                }
            }

            // LISTA AGRUPADA EN SECCIONES POR FAMILIA
            if (uiState.filteredItems.isEmpty()) {
                InventoryEmptyState(
                    hasAnyEvents = uiState.allItems.isNotEmpty(),
                    onOpenQuickLoad = {
                        quickDialogInitialProduct = null
                        viewModel.clearSampleScanFeedback()
                        showQuickDialog = true
                    }
                )
            } else {
                val showGroupHeaders = uiState.selectedFilter == InventoryFilter.ALL ||
                    uiState.selectedFilter == InventoryFilter.IN_STOCK

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showGroupHeaders) {
                        groupedSections.forEach { (category, sectionItems) ->
                            item(key = "header_${category.name}") {
                                FamilySectionHeader(
                                    category = category,
                                    items = sectionItems
                                )
                            }
                            items(sectionItems, key = { it.productId }) { item ->
                                InventoryProductCard(
                                    item = item,
                                    onOpenKardex = { viewModel.openKardex(item.productId, initialTab = 0) },
                                    onOpenCodes = { viewModel.openKardex(item.productId, initialTab = 1) },
                                    onAdjust = {
                                        quickDialogInitialProduct = item
                                        viewModel.clearSampleScanFeedback()
                                        showQuickDialog = true
                                    }
                                )
                            }
                        }
                    } else {
                        items(uiState.filteredItems, key = { it.productId }) { item ->
                            InventoryProductCard(
                                item = item,
                                onOpenKardex = { viewModel.openKardex(item.productId, initialTab = 0) },
                                onOpenCodes = { viewModel.openKardex(item.productId, initialTab = 1) },
                                onAdjust = {
                                    quickDialogInitialProduct = item
                                    viewModel.clearSampleScanFeedback()
                                    showQuickDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // MODAL DE CARGA RÁPIDA DE INVENTARIO INICIAL / AJUSTE FÍSICO
    if (showQuickDialog) {
        QuickInventoryDialog(
            catalogProducts = uiState.catalogProducts,
            existingStockByProduct = uiState.allItems.associate { it.productId.uppercase() to it.stockQuantity },
            initialItem = quickDialogInitialProduct,
            scanFeedback = sampleScanFeedback,
            onScanSample = { code, onResolved ->
                viewModel.processSampleBarcode(code, onResolved)
            },
            onDismiss = { showQuickDialog = false },
            onConfirm = { request ->
                viewModel.submitQuickPhysicalCount(
                    productId = request.productId,
                    productName = request.productName,
                    presentation = request.presentation,
                    productType = request.productType,
                    inventoryCategory = request.inventoryCategory,
                    unit = request.unit,
                    mode = request.mode,
                    targetQuantity = request.targetQuantity,
                    cajasCount = request.cajasCount,
                    sacosCount = request.sacosCount,
                    cartuchosCount = request.cartuchosCount,
                    boxWeightKg = request.boxWeightKg,
                    unitWeightKg = request.unitWeightKg,
                    cartridgesPerBox = request.cartridgesPerBox,
                    notes = request.notes,
                    onDone = { showQuickDialog = false }
                )
            }
        )
    }

    // HOJA DE DETALLE POR PRODUCTO (TAB 1: KARDEX | TAB 2: CÓDIGOS SEDENA)
    selectedKardexProductId?.let { prodId ->
        val productItem = uiState.allItems.find { it.productId == prodId }
        if (productItem != null) {
            ProductKardexSheet(
                stockItem = productItem,
                entries = selectedKardexEntries,
                codeItems = selectedProductCodes,
                initialTab = selectedKardexInitialTab,
                onDismiss = { viewModel.closeKardex() },
                onAdjustProduct = {
                    viewModel.closeKardex()
                    quickDialogInitialProduct = productItem
                    viewModel.clearSampleScanFeedback()
                    showQuickDialog = true
                }
            )
        }
    }
}

@Composable
private fun FiveFamilyTotalsDashboard(
    totals: InventoryDashboardTotals,
    selectedFilter: InventoryFilter,
    onSelectCategory: (InventoryFilter) -> Unit
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(MineDarkSurfaceElevated, MineDarkSurface)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(gradient)
            .border(1.dp, MineBorderGray, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // FILA 1: ALTO EXPLOSIVO (KG + códigos) y AGENTE EXPLOSIVO (KG + códigos)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FamilyKpiCard(
                modifier = Modifier.weight(1f),
                title = "ALTO EXPLOSIVO",
                value = "${BoxUnitWeightCalculator.formatDecimal2(totals.altoExplosivoKg)} kg",
                subtitle = "${totals.altoCodesInStock} cód. en polvorín",
                accent = MineHighExplosiveOrange,
                isSelected = selectedFilter == InventoryFilter.ALTO_EXPLOSIVO,
                tag = "kpi_alto_explosivo",
                onClick = { onSelectCategory(InventoryFilter.ALTO_EXPLOSIVO) }
            )
            FamilyKpiCard(
                modifier = Modifier.weight(1f),
                title = "AGENTE EXPLOSIVO",
                value = "${BoxUnitWeightCalculator.formatDecimal2(totals.agenteExplosivoKg)} kg",
                subtitle = "${totals.agenteCodesInStock} cód. en polvorín",
                accent = EmpaqueSacoColor,
                isSelected = selectedFilter == InventoryFilter.AGENTE_EXPLOSIVO,
                tag = "kpi_agente_explosivo",
                onClick = { onSelectCategory(InventoryFilter.AGENTE_EXPLOSIVO) }
            )
        }

        // FILA 2: CORDÓN DETONANTE (MTS), CAÑUELAS (PZA), DETONADORES (PZA)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FamilyKpiCard(
                modifier = Modifier.weight(1f),
                title = "CORDÓN DET.",
                value = BoxUnitWeightCalculator.formatDecimal2(totals.cordonDetonanteMts),
                subtitle = "MTS",
                accent = MineAgentCyan,
                isSelected = selectedFilter == InventoryFilter.CORDON_DETONANTE,
                tag = "kpi_cordon_detonante",
                compact = true,
                onClick = { onSelectCategory(InventoryFilter.CORDON_DETONANTE) }
            )
            FamilyKpiCard(
                modifier = Modifier.weight(1f),
                title = "CAÑUELAS",
                value = BoxUnitWeightCalculator.formatPieces(totals.canuelasPza),
                subtitle = "PZA",
                accent = MineSafetyYellow,
                isSelected = selectedFilter == InventoryFilter.CANUELAS,
                tag = "kpi_canuelas",
                compact = true,
                onClick = { onSelectCategory(InventoryFilter.CANUELAS) }
            )
            FamilyKpiCard(
                modifier = Modifier.weight(1f),
                title = "DETONADORES",
                value = BoxUnitWeightCalculator.formatPieces(totals.detonadoresPza),
                subtitle = "PZA",
                accent = EmpaqueAccesorioColor,
                isSelected = selectedFilter == InventoryFilter.DETONADORES,
                tag = "kpi_detonadores",
                compact = true,
                onClick = { onSelectCategory(InventoryFilter.DETONADORES) }
            )
        }

        // Telemetría compacta de calibración + doble libro UUID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MineDarkBackground.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Scale,
                    contentDescription = null,
                    tint = MineSuccessGreenLight,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${totals.activeProductCount} SKUs • ${totals.calibratedUnitWeightCount} calibrados",
                    color = MineTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = MineAgentCyan,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "UUID: ${totals.totalEventCount} mov. · ${totals.totalCodeEventCount} cód.",
                    color = MineAgentCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FamilyKpiCard(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    isSelected: Boolean,
    tag: String,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) accent.copy(alpha = 0.18f) else MineDarkBackground)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accent else accent.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 7.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AutoShrinkText(
            text = title,
            initialTextSize = if (compact) 9.sp else 10.sp,
            minTextSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        AutoShrinkText(
            text = value,
            initialTextSize = if (compact) 16.sp else 18.sp,
            minTextSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MineTextPrimary,
            maxLines = 1
        )
        AutoShrinkText(
            text = subtitle,
            initialTextSize = 9.sp,
            minTextSize = 8.sp,
            color = if (compact) accent.copy(alpha = 0.85f) else MineTextSecondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun FamilySectionHeader(
    category: InventoryCategory,
    items: List<InventoryStockItem>
) {
    val accent = categoryAccentColor(category)
    val subtotal = BoxUnitWeightCalculator.roundTo4Decimals(items.sumOf { it.stockQuantity })
    val subtotalText = when (category) {
        InventoryCategory.ALTO_EXPLOSIVO, InventoryCategory.AGENTE_EXPLOSIVO -> {
            val codes = items.sumOf { it.codesInPolvorinCount }
            val codePart = if (codes > 0) " • $codes cód." else ""
            "${BoxUnitWeightCalculator.formatDecimal2(subtotal)} kg$codePart"
        }
        InventoryCategory.CORDON_DETONANTE -> "${BoxUnitWeightCalculator.formatDecimal2(subtotal)} MTS"
        InventoryCategory.CANUELAS, InventoryCategory.DETONADORES -> "${BoxUnitWeightCalculator.formatPieces(subtotal)} PZA"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(0.75.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Text(
                text = category.label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "(${items.size})",
                fontSize = 11.sp,
                color = MineTextSecondary
            )
        }
        Text(
            text = subtotalText,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = MineTextPrimary
        )
    }
}

@Composable
private fun InventoryProductCard(
    item: InventoryStockItem,
    onOpenKardex: () -> Unit,
    onOpenCodes: () -> Unit,
    onAdjust: () -> Unit
) {
    val accentColor = categoryAccentColor(item.category)
    val isNegative = item.stockQuantity < -1e-6

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenKardex)
            .testTag("inventory_card_${item.productId}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
        border = BorderStroke(
            width = if (isNegative) 1.5.dp else 1.dp,
            color = if (isNegative) MineErrorRed else accentColor.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Encabezado del producto
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.productId,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MineDarkBackground,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        AutoShrinkText(
                            text = item.productName,
                            initialTextSize = 15.sp,
                            minTextSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MineTextPrimary,
                            maxLines = 1
                        )
                        val subtitleLine = listOf(item.presentation, item.category.label)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        AutoShrinkText(
                            text = subtitleLine,
                            initialTextSize = 11.sp,
                            minTextSize = 9.sp,
                            color = MineTextSecondary,
                            maxLines = 1
                        )
                    }
                }

                // Badge de códigos SEDENA para Alto Explosivo y Agente Explosivo
                if (item.category.supportsBarcodeLedger) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MineSuccessGreen.copy(alpha = 0.2f))
                            .border(0.75.dp, MineSuccessGreenLight.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenCodes)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${item.codesInPolvorinCount} cód.",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = MineSuccessGreenLight
                        )
                    }
                }
            }

            // Equivalencia Visual Principal (ej. "1,003.00 kg · 40 cajas + 12 cart.")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isNegative) MineErrorRedBackground else MineDarkBackground)
                    .border(
                        1.dp,
                        if (isNegative) MineErrorRed else accentColor.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                AutoShrinkText(
                    text = item.visualEquivalence,
                    initialTextSize = 17.sp,
                    minTextSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isNegative) MineErrorRedLight else MineSafetyYellow,
                    maxLines = 1
                )
            }

            // Información de calibración y último evento
            val calibrationText = when (item.category) {
                InventoryCategory.ALTO_EXPLOSIVO -> {
                    if (item.unitWeightKg > 0.0) {
                        val u = BoxUnitWeightCalculator.formatUnitWeight3(item.unitWeightKg)
                        val c = if (item.cartridgesPerBox > 0) "${item.cartridgesPerBox} cart./caja · " else ""
                        "Caja ${item.boxWeightKg} kg • $c$u kg/cart."
                    } else {
                        "Caja ${item.boxWeightKg} kg • Escanee 1 caja (30d) para peso unit."
                    }
                }
                InventoryCategory.AGENTE_EXPLOSIVO -> "Saco ${item.boxWeightKg} kg • Trazabilidad por código 18d"
                InventoryCategory.CORDON_DETONANTE -> "Familia Cordón Detonante • Unidad ${item.unit}"
                InventoryCategory.CANUELAS -> "Familia Cañuelas • Unidad ${item.unit}"
                InventoryCategory.DETONADORES -> "Familia Detonadores • Unidad ${item.unit}"
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = calibrationText,
                    fontSize = 11.sp,
                    color = if (item.category == InventoryCategory.ALTO_EXPLOSIVO && item.unitWeightKg <= 0.0) {
                        MineSafetyAmber
                    } else {
                        MineTextSecondary
                    },
                    maxLines = 1
                )
                val codeSummary = if (item.category.supportsBarcodeLedger && (item.codesInPolvorinCount > 0 || item.codesDispatchedCount > 0)) {
                    " • ${item.codesInPolvorinCount} en polv. / ${item.codesDispatchedCount} desp."
                } else {
                    ""
                }
                Text(
                    text = "${item.eventCount} mov.$codeSummary • ${item.lastUpdatedIso.take(16)}",
                    fontSize = 10.sp,
                    color = MineTextMuted,
                    maxLines = 1
                )
            }

            // Botones de acción (Kardex, Códigos para Alto/Agente, Ajustar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenKardex,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("inventory_kardex_btn_${item.productId}"),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MineAgentCyan.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineAgentCyan)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kardex", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (item.category.supportsBarcodeLedger) {
                    OutlinedButton(
                        onClick = onOpenCodes,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("inventory_codes_btn_${item.productId}"),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MineSuccessGreenLight.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSuccessGreenLight)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Códigos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onAdjust,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("inventory_adjust_btn_${item.productId}"),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MineSafetyYellow.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MineSafetyYellow)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajustar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InventoryEmptyState(
    hasAnyEvents: Boolean,
    onOpenQuickLoad: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MineDarkSurface)
                .border(1.dp, MineSafetyYellow.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = MineSafetyYellow,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = if (hasAnyEvents) "Sin coincidencias para esta familia o búsqueda" else "Polvorín sin existencias registradas",
            color = MineTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = if (hasAnyEvents) {
                "Pruebe limpiando el buscador o cambiando a la vista 'Todas' las familias."
            } else {
                "Escanee 1 código de muestra o elija un producto del catálogo para cargar el inventario inicial por familia en segundos."
            },
            color = MineTextSecondary,
            fontSize = 13.sp
        )
        if (!hasAnyEvents) {
            Button(
                onClick = onOpenQuickLoad,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MineSafetyYellow,
                    contentColor = MineDarkBackground
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CARGAR INVENTARIO INICIAL", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
