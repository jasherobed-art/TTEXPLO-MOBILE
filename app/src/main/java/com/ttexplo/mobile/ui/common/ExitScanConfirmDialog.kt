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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
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
        stringResource(R.string.scan_exit_body_draft, itemCount)
    } else {
        stringResource(R.string.scan_exit_body_unsaved, itemCount)
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
                text = stringResource(if (savedAsDraft) R.string.scan_exit_title_draft else R.string.scan_exit_title_unsaved),
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
                    text = stringResource(if (savedAsDraft) R.string.scan_exit_confirm_draft else R.string.scan_exit_confirm_unsaved),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_exit_scan_button")
            ) {
                Text(stringResource(R.string.scan_exit_dismiss), color = MineTextPrimary)
            }
        },
        containerColor = MineDarkSurface
    )
}
