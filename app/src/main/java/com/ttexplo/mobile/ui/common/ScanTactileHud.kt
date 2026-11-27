package com.ttexplo.mobile.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.domain.inventory.WeightFormat
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import com.ttexplo.mobile.ui.theme.*

/**
 * HUD táctil de escaneo: contexto + 4 casillas (TOT / Cajas / Sacos / Cartuchos)
 * con conteo y peso exacto a 3 decimales. Reemplaza la barra de contadores anterior.
 */
@Composable
fun ScanTactileHud(
    totalCount: Int,
    totalKg: Double,
    cajasCount: Int,
    cajasKg: Double,
    sacosCount: Int,
    sacosKg: Double,
    cartuchosCount: Int,
    cartuchosKg: Double,
    accentColor: Color,
    activeProductLabel: String?,
    isManualVisible: Boolean,
    onToggleManual: () -> Unit,
    isPanelExpanded: Boolean,
    onTogglePanel: () -> Unit,
    selectedPackaging: TipoEmpaque?,
    onSelectPackaging: (TipoEmpaque?) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MineDarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (activeProductLabel != null) accentColor else MineBorderGray)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Fila 1: contexto + botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = activeProductLabel ?: "GLOBAL",
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (activeProductLabel != null || selectedPackaging != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .clickable { onResetFilters() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar filtros", tint = accentColor, modifier = Modifier.size(12.dp))
                            Text("Global", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HudIconButton(
                    active = isManualVisible,
                    accentColor = accentColor,
                    onClick = onToggleManual,
                    content = { Icon(Icons.Default.Keyboard, contentDescription = "Teclado", tint = if (isManualVisible) accentColor else MineTextSecondary, modifier = Modifier.size(18.dp)) }
                )
                HudIconButton(
                    active = isPanelExpanded,
                    accentColor = accentColor,
                    onClick = onTogglePanel,
                    content = {
                        Icon(
                            imageVector = if (isPanelExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isPanelExpanded) "Ocultar panel" else "Mostrar panel",
                            tint = if (isPanelExpanded) accentColor else MineTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Fila 2: rejilla de 4 casillas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudCell(Modifier.weight(1f), "TOT", totalCount, totalKg, MineSafetyYellow, selectedPackaging == null, true) {
                    onSelectPackaging(null)
                }
                HudCell(Modifier.weight(1f), "Cajas", cajasCount, cajasKg, EmpaqueCajaColor, selectedPackaging == TipoEmpaque.CAJA, cajasCount > 0) {
                    onSelectPackaging(if (selectedPackaging == TipoEmpaque.CAJA) null else TipoEmpaque.CAJA)
                }
                HudCell(Modifier.weight(1f), "Sacos", sacosCount, sacosKg, EmpaqueSacoColor, selectedPackaging == TipoEmpaque.SACO, sacosCount > 0) {
                    onSelectPackaging(if (selectedPackaging == TipoEmpaque.SACO) null else TipoEmpaque.SACO)
                }
                HudCell(Modifier.weight(1f), "Cartuchos", cartuchosCount, cartuchosKg, EmpaqueCartuchoColor, selectedPackaging == TipoEmpaque.PIEZA, cartuchosCount > 0) {
                    onSelectPackaging(if (selectedPackaging == TipoEmpaque.PIEZA) null else TipoEmpaque.PIEZA)
                }
            }
        }
    }
}

@Composable
private fun HudIconButton(
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accentColor.copy(alpha = 0.15f) else MineDarkSurfaceElevated)
            .border(1.dp, if (active) accentColor else MineBorderGray, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun HudCell(
    modifier: Modifier,
    label: String,
    count: Int,
    kg: Double,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .heightIn(min = 70.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) color.copy(alpha = 0.13f) else MineDarkBackground)
            .border(if (selected) 2.dp else 1.dp, if (selected) color else MineBorderGray, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            }
        }
        Text(
            text = count.toString(),
            color = MineTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            maxLines = 1
        )
        Text(
            text = WeightFormat.kg3(kg) + " kg",
            color = MineSuccessText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
