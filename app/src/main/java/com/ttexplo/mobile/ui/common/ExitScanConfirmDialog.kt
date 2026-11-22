package com.ttexplo.mobile.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.ui.theme.*

/**
 * Diálogo de confirmación al intentar salir con el botón Atrás mientras hay lecturas activas.
 * Evita salidas accidentales por gestos táctiles o botones físicos durante el escaneo en polvorín.
 */
@Composable
fun ExitScanConfirmDialog(
    itemCount: Int,
    savedAsDraft: Boolean,
    onConfirmExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val bodyText = if (savedAsDraft) {
        "Tienes $itemCount elemento(s) escaneado(s) en curso.\n\nTu progreso permanece respaldado como BORRADOR en el equipo y podrás retomarlo al volver a entrar. ¿Deseas salir de la pantalla de escaneo?"
    } else {
        "Tienes $itemCount elemento(s) escaneado(s) SIN GUARDAR.\n\nSi sales ahora sin presionar Guardar, se perderán las lecturas de esta sesión. ¿Seguro que deseas salir?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (savedAsDraft) MineSafetyYellow else MineErrorRedLight
            )
        },
        title = {
            Text(
                text = if (savedAsDraft) "¿Salir del escaneo?" else "¿Salir sin guardar?",
                color = MineTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                text = bodyText,
                color = MineTextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmExit,
                modifier = Modifier.testTag("confirm_exit_scan_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (savedAsDraft) MineSafetyYellow else MineErrorRed,
                    contentColor = if (savedAsDraft) MineDarkBackground else MineTextPrimary
                )
            ) {
                Text(
                    text = if (savedAsDraft) "Sí, salir" else "Salir sin guardar",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_exit_scan_button")
            ) {
                Text("Seguir escaneando", color = MineTextPrimary)
            }
        },
        containerColor = MineDarkSurface
    )
}
