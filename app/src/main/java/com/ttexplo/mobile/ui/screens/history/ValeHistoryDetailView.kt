package com.ttexplo.mobile.ui.screens.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.printer.PrinterProfile
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*

data class ValeDetailCodeItem(
    val index: Int,
    val code: String,
    val productTitle: String,
    val presentation: String,
    val timestamp: String,
    val quantity: Int? = null
)

data class ValeDetailData(
    val summary: ValeSummaryItem,
    val headerFields: List<Pair<String, String>>,
    val groupedSummary: List<Pair<String, Int>>,
    val codes: List<ValeDetailCodeItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValeHistoryDetailView(
    detail: ValeDetailData,
    activeProfile: PrinterProfile?,
    totalProfiles: Int,
    isPrinting: Boolean,
    copiesPrinted: Int,
    printStatusMessage: String?,
    onBack: () -> Unit,
    onReprintClick: () -> Unit,
    onChangePrinterClick: () -> Unit,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit,
    showEdit: Boolean = true
) {
    BackHandler {
        if (!isPrinting) onBack()
    }

    val (badgeColor, badgeTextColor) = when (detail.summary.tipo) {
        "DESPACHO" -> MineHighExplosiveOrange to MineDarkBackground
        "ENTRADA" -> MineSuccessGreen to MineDarkBackground
        "ACCESORIOS" -> MineAccessoryPurple to MineTextPrimary
        "DEV. ACCESORIOS" -> MineAgentCyan to MineDarkBackground
        "DEVOLUCION" -> MineSafetyYellow to MineDarkBackground
        else -> MineTextMuted to MineDarkBackground
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = detail.summary.tipo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeTextColor
                                )
                            }
                            AutoShrinkText(
                                text = detail.summary.folio,
                                color = MineTextPrimary,
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 16.sp,
                                minTextSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "Detalles del Vale • ${detail.summary.totalItems} ítems",
                            color = MineTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isPrinting,
                        modifier = Modifier.testTag("history_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al historial",
                            tint = MineTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onReprintClick,
                        enabled = !isPrinting,
                        modifier = Modifier.testTag("history_detail_topbar_reprint_button")
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MineSafetyYellow
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Reimprimir Ticket",
                                tint = MineSafetyYellow
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MineDarkBackground)
            )
        },
        bottomBar = {
            Surface(
                color = MineDarkSurface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (copiesPrinted > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MineSuccessGreen.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = MineSuccessGreenLight,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (copiesPrinted == 1) "1 ticket reimpreso" else "$copiesPrinted copias reimpresas",
                                color = MineSuccessGreenLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!printStatusMessage.isNullOrBlank()) {
                        val isError = printStatusMessage.startsWith("Error", ignoreCase = true) ||
                            printStatusMessage.startsWith("Sin impresora", ignoreCase = true)
                        Text(
                            text = printStatusMessage,
                            color = if (isError) MineErrorRedLight else MineSafetyYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Botón de acceso rápido principal para reimpresión desde detalles del historial
                    Button(
                        onClick = onReprintClick,
                        enabled = !isPrinting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("history_detail_reprint_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MineSafetyYellow,
                            contentColor = MineDarkBackground
                        )
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MineDarkBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("REIMPRIMIENDO TICKET...", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AutoShrinkText(
                                text = if (copiesPrinted == 0) "Reimprimir Ticket" else "Reimprimir Copia #${copiesPrinted + 1}",
                                fontWeight = FontWeight.ExtraBold,
                                color = MineDarkBackground,
                                targetTextSize = 14.sp,
                                minTextSize = 11.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showEdit) {
                            OutlinedButton(
                                onClick = onEditClick,
                                enabled = !isPrinting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("history_detail_edit_button"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MineBorderGray),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MineTextPrimary)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = MineSafetyYellow, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Editar", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = onShareClick,
                            enabled = !isPrinting,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("history_detail_share_button"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MineBorderGray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MineTextPrimary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MineAgentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        containerColor = MineDarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .testTag("history_detail_codes_list"),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Datos generales del vale
            item(key = "header_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                    border = BorderStroke(1.dp, MineBorderGray),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "DATOS GENERALES DEL VALE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MineSafetyYellow,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(color = MineBorderGray)
                        detail.headerFields.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = MineTextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AutoShrinkText(
                                    text = value.ifBlank { "—" },
                                    color = MineTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    targetTextSize = 12.sp,
                                    minTextSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 2. Resumen agrupado de materiales
            if (detail.groupedSummary.isNotEmpty()) {
                item(key = "summary_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RESUMEN DE MATERIALES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MineDarkBackground)
                                        .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "TOTAL: ${detail.summary.totalItems} PZAS",
                                        color = MineTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            HorizontalDivider(color = MineBorderGray)
                            detail.groupedSummary.forEach { (productDesc, qty) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoShrinkText(
                                        text = productDesc,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        color = MineTextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        targetTextSize = 12.sp,
                                        minTextSize = 10.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "×$qty",
                                        color = MineSafetyYellow,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Título del listado de códigos escaneados
            item(key = "codes_header") {
                Text(
                    text = "LISTADO DE CÓDIGOS (${detail.codes.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MineSafetyYellow,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (detail.codes.isEmpty()) {
                item(key = "codes_empty") {
                    Text(
                        text = "No hay códigos registrados en este vale.",
                        color = MineTextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(detail.codes, key = { "${it.index}_${it.code}_${it.timestamp}" }) { item ->
                    ValeDetailCodeRow(item = item, accentColor = badgeColor)
                }
            }
        }
    }
}

@Composable
private fun ValeDetailCodeRow(
    item: ValeDetailCodeItem,
    accentColor: Color
) {
    val idxLabel = String.format("%02d", item.index)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MineDarkSurface)
            .border(1.dp, MineBorderGray, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "[$idxLabel]",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                AutoShrinkText(
                    text = item.code,
                    color = MineTextPrimary,
                    fontWeight = FontWeight.Bold,
                    targetTextSize = 12.sp,
                    minTextSize = 9.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            AutoShrinkText(
                text = if (item.presentation.isNotBlank()) {
                    "${item.productTitle} (${item.presentation})"
                } else {
                    item.productTitle
                },
                color = MineTextSecondary,
                targetTextSize = 11.sp,
                minTextSize = 9.sp,
                maxLines = 1
            )
            if (item.timestamp.isNotBlank()) {
                Text(
                    text = "Hora: ${item.timestamp}",
                    color = MineTextMuted,
                    fontSize = 10.sp
                )
            }
        }

        if (item.quantity != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "CANT: ${item.quantity}",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
