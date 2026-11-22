package com.ttexplo.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.ttexplo.mobile.license.LicenseManager
import com.ttexplo.mobile.license.LicenseStatus
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEntradas: () -> Unit,
    onNavigateToDespacho: () -> Unit,
    onNavigateToAccesorios: () -> Unit,
    onNavigateToDevoluciones: () -> Unit,
    onNavigateToDevolucionAccesorios: () -> Unit = {},
    onNavigateToInventory: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToLicense: () -> Unit = {}
) {
    val context = LocalContext.current
    var licenseCheck by remember { mutableStateOf(LicenseManager.checkLicense(context)) }

    // Re-verificar licencia cada vez que HomeScreen vuelve al primer plano (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                licenseCheck = LicenseManager.checkLicense(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isOperationAllowed = licenseCheck.status == LicenseStatus.VALID || licenseCheck.status == LicenseStatus.EXPIRING_SOON

    fun handleGuardedAction(action: () -> Unit) {
        if (isOperationAllowed) {
            action()
        } else {
            onNavigateToLicense()
        }
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
                            AutoShrinkText(
                                text = "T&T EXPLO MOBILE",
                                initialTextSize = 18.sp,
                                minTextSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MineSafetyYellow
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MineSafetyYellow.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MineSafetyYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        AutoShrinkText(
                            text = "Control de Explosivos en Sitio",
                            initialTextSize = 12.sp,
                            minTextSize = 10.sp,
                            color = MineTextSecondary,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración",
                            tint = MineTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MineDarkBackground
                )
            )
        },
        containerColor = MineDarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BANNER DE AVISO DE LICENCIA (SI FALTA <= 30 DÍAS O ESTÁ VENCIDA/BLOQUEADA)
            if (licenseCheck.status == LicenseStatus.EXPIRING_SOON) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToLicense() },
                    colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MineHighExplosiveOrange),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MineHighExplosiveOrange,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AVISO: LICENCIA PRÓXIMA A VENCER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MineHighExplosiveOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Faltan ${licenseCheck.daysRemaining} días para el vencimiento (${licenseCheck.info?.expiresDate?.take(10)}). Toque aquí para renovar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MineTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MineHighExplosiveOrange
                        )
                    }
                }
            } else if (!isOperationAllowed) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToLicense() },
                    colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MineErrorRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MineErrorRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TERMINAL BLOQUEADA POR LICENCIA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MineErrorRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = licenseCheck.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MineTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = onNavigateToLicense,
                            colors = ButtonDefaults.buttonColors(containerColor = MineErrorRed, contentColor = MineTextPrimary),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("ACTIVAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(
                text = "OPERACIONES PRINCIPALES",
                style = MaterialTheme.typography.labelLarge,
                color = MineTextMuted,
                fontWeight = FontWeight.Bold
            )

            // 1. Entradas de Alto y Agente
            MenuActionCard(
                title = "1. Entradas de Alto y Agente",
                subtitle = "Recepción e ingreso de sacos y cajas a polvorín",
                accentColor = MineSafetyYellow,
                icon = Icons.Default.MoveToInbox,
                onClick = { handleGuardedAction(onNavigateToEntradas) }
            )

            // 2. Salidas / Despacho de Alto y Agente
            MenuActionCard(
                title = "2. Salidas de Alto y Agente",
                subtitle = "Despacho a barrenos de mina con vale e impresión",
                accentColor = MineHighExplosiveOrange,
                icon = Icons.Default.LocalShipping,
                onClick = { handleGuardedAction(onNavigateToDespacho) }
            )

            // 3. Salida de Accesorios
            MenuActionCard(
                title = "3. Salida de Accesorios",
                subtitle = "Detonadores, cordón detonante y artificios",
                accentColor = MineAccessoryPurple,
                icon = Icons.Default.ElectricBolt,
                onClick = { handleGuardedAction(onNavigateToAccesorios) }
            )

            HorizontalDivider(color = MineBorderGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "GESTIÓN Y REPORTES",
                style = MaterialTheme.typography.labelLarge,
                color = MineTextMuted,
                fontWeight = FontWeight.Bold
            )

            // 4. Devolución de Alto y Agente
            MenuActionCard(
                title = "4. Devolución de Alto y Agente",
                subtitle = "Reingreso de explosivos y agentes sobrantes a polvorín",
                accentColor = MineAgentBlue,
                icon = Icons.Default.AssignmentReturn,
                isCompact = true,
                onClick = { handleGuardedAction(onNavigateToDevoluciones) }
            )

            // 5. Devolución de Accesorios
            MenuActionCard(
                title = "5. Devolución de Accesorios",
                subtitle = "Reintegro de detonadores y accesorios con cantidad negativa (-)",
                accentColor = MineAgentCyan,
                icon = Icons.Default.AssignmentReturn,
                isCompact = true,
                onClick = { handleGuardedAction(onNavigateToDevolucionAccesorios) }
            )

            // 6. Inventario en Polvorín (En Vivo)
            MenuActionCard(
                title = "6. Inventario en Polvorín (En Vivo)",
                subtitle = "Existencias en KG, PZA y MTS · Carga rápida, peso unitario y Kardex",
                accentColor = MineSuccessGreenLight,
                icon = Icons.Default.Inventory2,
                isCompact = false,
                onClick = { handleGuardedAction(onNavigateToInventory) }
            )

            // 7. Historial de Vales
            MenuActionCard(
                title = "7. Historial de Vales & Consolidado",
                subtitle = "Consultar vales guardados y exportar en un solo archivo",
                accentColor = MineSafetyYellow,
                icon = Icons.Default.History,
                isCompact = true,
                onClick = onNavigateToHistory
            )

            // 8. Configuración del Sistema
            MenuActionCard(
                title = "8. Configuración y Personalización",
                subtitle = "Impresora térmica, datos de mina, catálogo y exportar",
                accentColor = MineAgentCyan,
                icon = Icons.Default.Settings,
                isCompact = true,
                onClick = onNavigateToSettings
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer de versión de la aplicación
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T&T EXPLO MOBILE • Versión ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MineTextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MenuActionCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    isCompact: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isCompact) 74.dp else 88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MineDarkSurface)
            .border(1.dp, MineBorderGray, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (isCompact) 42.dp else 50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            AutoShrinkText(
                text = title,
                initialTextSize = if (isCompact) 15.sp else 17.sp,
                minTextSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MineTextPrimary,
                maxLines = 2,
                softWrap = true
            )
            Spacer(modifier = Modifier.height(2.dp))
            AutoShrinkText(
                text = subtitle,
                initialTextSize = 12.sp,
                minTextSize = 9.sp,
                color = MineTextSecondary,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                softWrap = true
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MineTextMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}
