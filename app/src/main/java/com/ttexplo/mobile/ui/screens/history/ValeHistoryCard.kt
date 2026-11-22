package com.ttexplo.mobile.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.ui.theme.*

data class HistoryTypeFilterOption(
    val key: String?,
    val label: String,
    val count: Int,
    val accentColor: Color
)

/**
 * Fila horizontal compacta de filtros por tipo de vale para terminales compactas (Munbyn AS01).
 */
@Composable
fun HistoryTypeFilterRow(
    options: List<HistoryTypeFilterOption>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_type_filter_row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(options, key = { it.key ?: "ALL" }) { opt ->
            val isSelected = selectedKey == opt.key
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected && opt.key != null) null else opt.key) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = opt.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) MineDarkBackground.copy(alpha = 0.22f)
                                    else opt.accentColor.copy(alpha = 0.22f)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = opt.count.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) MineDarkBackground else opt.accentColor
                            )
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MineDarkSurface,
                    labelColor = MineTextPrimary,
                    selectedContainerColor = opt.accentColor,
                    selectedLabelColor = MineDarkBackground
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = opt.accentColor.copy(alpha = 0.45f),
                    selectedBorderColor = opt.accentColor
                )
            )
        }
    }
}

/**
 * Tarjeta rediseñada en 2 niveles (encabezado/datos arriba + barra de acciones dedicada abajo)
 * para evitar cualquier encimado en pantallas angostas como la terminal Munbyn AceScan AS01.
 */
@Composable
fun ValeHistoryCard(
    vale: ValeSummaryItem,
    isSelected: Boolean,
    isPrintingThis: Boolean,
    isAnyPrinting: Boolean,
    onToggleSelect: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
    onPrint: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (badgeColor, badgeTextColor, shortTypeLabel) = when (vale.tipo) {
        "DESPACHO" -> Triple(MineHighExplosiveOrange, MineDarkBackground, "DESPACHO")
        "ENTRADA" -> Triple(MineSuccessGreen, MineDarkBackground, "ENTRADA")
        "ACCESORIOS" -> Triple(MineAccessoryPurple, MineTextPrimary, "ACCESORIOS")
        "DEV. ACCESORIOS" -> Triple(MineAgentCyan, MineDarkBackground, "DEV. ACC.")
        "DEVOLUCION" -> Triple(MineSafetyYellow, MineDarkBackground, "DEVOLUCIÓN")
        else -> Triple(MineTextMuted, MineDarkBackground, vale.tipo)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_vale_card_${vale.tipo}_${vale.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MineDarkSurfaceElevated else MineDarkSurface
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MineSafetyYellow else MineBorderGray
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Bloque superior: datos legibles a ancho completo (tocar abre Detalles)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDetails() }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Fila 1: Checkbox + Tipo + Folio + Total de piezas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onToggleSelect,
                        modifier = Modifier.size(32.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MineSafetyYellow,
                            uncheckedColor = MineTextMuted,
                            checkmarkColor = MineDarkBackground
                        )
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = shortTypeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeTextColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text = vale.folio,
                        color = MineTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .border(1.dp, badgeColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${vale.totalItems} pzas",
                            color = if (badgeColor == MineAccessoryPurple) MineAccessoryPurpleLight else badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Fila 2: Descripción a ancho completo (sin encimado)
                Text(
                    text = vale.descripcion,
                    color = MineTextPrimary.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Fila 3: Fecha + acceso visual a Detalles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MineTextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = vale.fecha,
                            color = MineTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Ver detalles",
                            color = MineAgentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Ver detalles del vale",
                            tint = MineAgentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MineBorderGray.copy(alpha = 0.75f))

            // Barra inferior de acciones táctiles separadas (4 columnas iguales, mín. 48dp de alto)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MineDarkBackground.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ValeCardActionCell(
                    label = if (isPrintingThis) "Enviando" else "Imprimir",
                    icon = Icons.Default.Print,
                    tint = MineAgentCyan,
                    enabled = !isAnyPrinting,
                    isLoading = isPrintingThis,
                    onClick = onPrint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_print_vale_${vale.id}")
                )

                ValeCardActionCell(
                    label = "Editar",
                    icon = Icons.Default.Edit,
                    tint = MineSafetyYellow,
                    enabled = !isAnyPrinting,
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_edit_vale_${vale.id}")
                )

                ValeCardActionCell(
                    label = "Enviar",
                    icon = Icons.Default.Share,
                    tint = MineTextSecondary,
                    enabled = !isAnyPrinting,
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_share_vale_${vale.id}")
                )

                ValeCardActionCell(
                    label = "Borrar",
                    icon = Icons.Default.Delete,
                    tint = MineErrorRedLight,
                    enabled = !isAnyPrinting,
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_delete_vale_${vale.id}")
                )
            }
        }
    }
}

@Composable
private fun ValeCardActionCell(
    label: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MineSafetyYellow
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else MineTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (enabled) tint else MineTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}
