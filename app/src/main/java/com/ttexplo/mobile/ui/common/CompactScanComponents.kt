package com.ttexplo.mobile.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.BarcodeItem
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.ui.theme.*

data class ProductFilterChipData(
    val key: String,
    val title: String,
    val empaqueShort: String,
    val count: Int,
    val accentColor: Color,
    val isTypeFilter: Boolean = false
)

/**
 * Evento emitido cuando el operador vuelve a escanear un código que ya existe en la sesión actual.
 * Permite desplazar el LazyColumn automáticamente hasta la fila existente y resaltarla en rojo.
 */
data class DuplicateCodeAlert(
    val code: String,
    val attemptCount: Int = 1,
    val eventId: Long = System.nanoTime()
)

/**
 * Insignia roja de alerta para filas resaltadas dentro del LazyColumn al detectar códigos duplicados.
 */
@Composable
fun DuplicateBadgeChip(
    attemptCount: Int = 1,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MineErrorRed)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Código duplicado",
            tint = MineTextPrimary,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = if (attemptCount > 1) "DUPLICADO ×$attemptCount" else "DUPLICADO",
            style = MaterialTheme.typography.labelSmall,
            color = MineTextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Prefijos estándar para combinar filtros por Tipo de Producto y por Producto específico
 * dentro de la misma fila LazyRow.
 */
object ScanFilterKeys {
    const val TYPE_PREFIX = "TYPE:"
    const val PROD_PREFIX = "PROD:"

    fun forProductType(type: ProductType): String = "$TYPE_PREFIX${type.name}"
    fun forCategory(category: InventoryCategory): String = "$TYPE_PREFIX${category.name}"
    fun forProduct(productKey: String): String = "$PROD_PREFIX$productKey"

    fun matchesBarcodeItem(item: BarcodeItem, selectedKey: String?, useIdKey: Boolean = false): Boolean {
        if (selectedKey == null) return true
        return when {
            selectedKey.startsWith(TYPE_PREFIX) -> {
                val targetType = selectedKey.removePrefix(TYPE_PREFIX)
                item.productInfo.type.name == targetType
            }
            selectedKey.startsWith(PROD_PREFIX) -> {
                val targetProd = selectedKey.removePrefix(PROD_PREFIX)
                val itemProdKey = if (useIdKey) {
                    "${item.productInfo.id}|${item.productInfo.presentation}|${item.empaque.name}"
                } else {
                    "${item.productInfo.name}|${item.productInfo.presentation}|${item.empaque.name}"
                }
                itemProdKey == targetProd
            }
            else -> true
        }
    }
}

/**
 * Construye los chips de tipo de producto + productos capturados para la fila LazyRow.
 */
fun buildExplosiveFilterChips(
    items: List<BarcodeItem>,
    includeAccessoriesType: Boolean = false,
    useIdKey: Boolean = false
): List<ProductFilterChipData> {
    val chips = mutableListOf<ProductFilterChipData>()

    val altoCount = items.count { it.productInfo.type == ProductType.ALTO_EXPLOSIVO }
    val agenteCount = items.count { it.productInfo.type == ProductType.AGENTE }

    chips.add(
        ProductFilterChipData(
            key = ScanFilterKeys.forProductType(ProductType.ALTO_EXPLOSIVO),
            title = "Alto Exp.",
            empaqueShort = "",
            count = altoCount,
            accentColor = MineHighExplosiveOrange,
            isTypeFilter = true
        )
    )
    chips.add(
        ProductFilterChipData(
            key = ScanFilterKeys.forProductType(ProductType.AGENTE),
            title = "Agente",
            empaqueShort = "",
            count = agenteCount,
            accentColor = MineInfoText,
            isTypeFilter = true
        )
    )
    if (includeAccessoriesType) {
        val accCount = items.count { it.productInfo.type == ProductType.ACCESORIO }
        chips.add(
            ProductFilterChipData(
                key = ScanFilterKeys.forProductType(ProductType.ACCESORIO),
                title = "Accesorios",
                empaqueShort = "",
                count = accCount,
                accentColor = EmpaqueAccesorioColor,
                isTypeFilter = true
            )
        )
    }

    val grouped = items.groupBy { item ->
        val prodIdOrName = if (useIdKey) item.productInfo.id else item.productInfo.name
        Triple(prodIdOrName, item.productInfo.presentation, item.empaque)
    }.entries.sortedByDescending { it.value.size }

    for ((triple, group) in grouped) {
        val first = group.first()
        val empShort = when (triple.third) {
            TipoEmpaque.CAJA -> "C"
            TipoEmpaque.SACO -> "S"
            TipoEmpaque.PIEZA -> "U"
            TipoEmpaque.ACCESORIO -> "A"
            else -> ""
        }
        val color = when (triple.third) {
            TipoEmpaque.CAJA -> EmpaqueCajaColor
            TipoEmpaque.SACO -> EmpaqueSacoColor
            TipoEmpaque.PIEZA -> EmpaqueCartuchoColor
            TipoEmpaque.ACCESORIO -> EmpaqueAccesorioColor
            else -> MineSafetyYellow
        }
        val prodKey = "${triple.first}|${triple.second}|${triple.third.name}"
        chips.add(
            ProductFilterChipData(
                key = ScanFilterKeys.forProduct(prodKey),
                title = first.productInfo.name,
                empaqueShort = empShort,
                count = group.size,
                accentColor = color,
                isTypeFilter = false
            )
        )
    }

    return chips
}

/**
 * Cinta compacta superior para pantallas de escaneo en terminales rugerizadas compactas
 * (Munbyn AceScan AS01 / Zebra). Muestra métricas en 1 sola fila junto con el control
 * para expandir/colapsar el encabezado detallado (que también se colapsa automáticamente
 * al detectar actividad de escaneo o desplazamiento).
 */
@Composable
fun CompactScanMetricsRibbon(
    totalCount: Int,
    cajasCount: Int,
    sacosCount: Int,
    cartuchosCount: Int,
    accentColor: Color,
    isPanelExpanded: Boolean,
    onTogglePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MineDarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTogglePanel() }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RibbonStatPill("TOT", totalCount, accentColor)
                RibbonStatPill("C", cajasCount, MineSafetyYellow)
                RibbonStatPill("S", sacosCount, MineAgentCyan)
                RibbonStatPill("U", cartuchosCount, MineHighExplosiveOrange)
            }

            Surface(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTogglePanel() }
                    .testTag("toggle_scan_panel_button"),
                color = if (isPanelExpanded) accentColor.copy(alpha = 0.2f) else MineDarkBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isPanelExpanded) accentColor else MineOutline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isPanelExpanded) "Ocultar" else "Info",
                        color = if (isPanelExpanded) accentColor else MineTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isPanelExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isPanelExpanded) "Colapsar encabezado" else "Expandir encabezado",
                        tint = if (isPanelExpanded) accentColor else MineTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RibbonStatPill(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MineDarkBackground)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = MineTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Casilla de captura de códigos de barras SIEMPRE ACTIVA y visible.
 * - Mantiene foco automático mediante [FocusRequester] para lectores HID / Keyboard Wedge (Munbyn AS01).
 * - Detecta salto de línea (\n / \r) o tecla física Enter para procesar el código al instante
 *   sin perder foco ni ocultar la casilla.
 * - Dispara [onActivityDetected] al escribir o capturar para colapsar el encabezado superior.
 */
@Composable
fun AlwaysActiveBarcodeField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmitBarcode: (String) -> Unit,
    accentColor: Color,
    placeholderText: String = "Listo para escanear",
    focusRequester: FocusRequester,
    testTagName: String,
    enabled: Boolean = true,
    onActivityDetected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    // El gatillo HID envía el código y ENTER en el mismo instante; la recomposición de `value`
    // puede llegar tarde, así que este estado se actualiza dentro de onValueChange.
    var liveText by remember { mutableStateOf(value) }

    fun submitCurrent(raw: String) {
        if (!enabled) return
        val clean = raw.replace("\r", "").replace("\n", "").trim()
        if (clean.isNotEmpty()) {
            onActivityDetected()
            onSubmitBarcode(clean)
            liveText = ""
            onValueChange("")
        }
        runCatching { focusRequester.requestFocus() }
    }

    fun submitLatest() = submitCurrent(liveText)

    OutlinedTextField(
        value = value,
        enabled = enabled,
        onValueChange = { incoming ->
            if (!enabled) return@OutlinedTextField
            liveText = incoming
            if (incoming.contains('\n') || incoming.contains('\r')) {
                val segments = incoming.split('\n', '\r')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    onActivityDetected()
                    segments.forEach { seg -> onSubmitBarcode(seg) }
                }
                liveText = ""
                onValueChange("")
                runCatching { focusRequester.requestFocus() }
            } else {
                if (incoming.isNotEmpty()) {
                    onActivityDetected()
                }
                onValueChange(incoming)
            }
        },
        placeholder = {
            Text(
                text = placeholderText,
                fontSize = 12.sp,
                color = MineTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) MineSuccessGreenLight else accentColor)
                )
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Casilla de escaneo activa",
                    tint = if (isFocused) MineSuccessGreenLight else accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (liveText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            liveText = ""
                            onValueChange("")
                            runCatching { focusRequester.requestFocus() }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar código",
                            tint = MineTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { submitLatest() },
                    enabled = enabled && liveText.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("${testTagName}_submit")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Capturar código",
                        tint = if (liveText.isNotBlank()) accentColor else MineTextMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
            onSend = { submitLatest() },
            onDone = { submitLatest() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MineDarkSurface,
            unfocusedContainerColor = MineDarkSurface,
            focusedBorderColor = if (isFocused) MineSuccessGreenLight else accentColor,
            unfocusedBorderColor = accentColor.copy(alpha = 0.65f),
            focusedTextColor = MineTextPrimary,
            unfocusedTextColor = MineTextPrimary,
            cursorColor = accentColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> isFocused = state.isFocused }
            .onPreviewKeyEvent { keyEvent ->
                if (!enabled) return@onPreviewKeyEvent false
                if (
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                    (keyEvent.type == KeyEventType.KeyDown || keyEvent.type == KeyEventType.KeyUp)
                ) {
                    submitLatest()
                    true
                } else {
                    false
                }
            }
            .testTag(testTagName)
    )
}

/**
 * Fila horizontal compacta (`LazyRow`) con chips para filtrar por tipo de producto y por
 * producto específico. Siempre visible en 1 sola línea para mantener el listado de códigos amplio.
 */
@Composable
fun HorizontalProductFilterRow(
    totalCount: Int,
    chips: List<ProductFilterChipData>,
    selectedKey: String?,
    accentColor: Color,
    onSelectKey: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("horizontal_product_filter_row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        item(key = "ALL_FILTER") {
            val allSelected = selectedKey == null
            FilterChip(
                selected = allSelected,
                onClick = { onSelectKey(null) },
                label = {
                    Text(
                        text = "Todos ($totalCount)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MineDarkSurface,
                    labelColor = MineTextSecondary,
                    selectedContainerColor = accentColor,
                    selectedLabelColor = MineDarkBackground
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = allSelected,
                    borderColor = MineOutline,
                    selectedBorderColor = accentColor
                )
            )
        }

        items(chips, key = { it.key }) { chip ->
            val isSelected = selectedKey == chip.key
            FilterChip(
                selected = isSelected,
                onClick = { onSelectKey(if (isSelected) null else chip.key) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = chip.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 128.dp)
                        )
                        if (chip.empaqueShort.isNotBlank()) {
                            Text(
                                text = chip.empaqueShort,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MineDarkBackground.copy(alpha = 0.85f) else chip.accentColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) MineDarkBackground.copy(alpha = 0.22f)
                                    else chip.accentColor.copy(alpha = 0.22f)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (chip.isTypeFilter) "${chip.count}" else "×${chip.count}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) MineDarkBackground else chip.accentColor
                            )
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MineDarkSurface,
                    labelColor = MineTextPrimary,
                    selectedContainerColor = chip.accentColor,
                    selectedLabelColor = MineDarkBackground
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = chip.accentColor.copy(alpha = 0.5f),
                    selectedBorderColor = chip.accentColor
                )
            )
        }
    }
}
