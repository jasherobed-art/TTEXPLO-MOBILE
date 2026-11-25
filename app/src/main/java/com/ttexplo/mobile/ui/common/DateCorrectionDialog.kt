package com.ttexplo.mobile.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.domain.scanner.isValidManufacturingDate
import com.ttexplo.mobile.ui.theme.*

/** Aviso de fecha de fabricación sospechosa: permite teclear solo los 6 dígitos DDMMYY. */
@Composable
fun DateCorrectionDialog(
    rawCode: String,
    detectedDate: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val input = remember { mutableStateOf("") }
    val formattedDetected = "${detectedDate.substring(0, 2)}/${detectedDate.substring(2, 4)}/${detectedDate.substring(4, 6)}"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MineErrorRedLight
            )
        },
        title = {
            Text(
                text = "FECHA INCONSISTENTE EN EL CÓDIGO",
                color = MineTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Código leído: $rawCode",
                    color = MineTextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Fecha que lee: $formattedDetected",
                    color = MineTextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Debe ser posterior a 2020 y un día válido.",
                    color = MineTextSecondary,
                    fontSize = 13.sp
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("date_correction_reread")
                ) {
                    Text("Volver a leer el código", color = MineSafetyYellow)
                }
                OutlinedTextField(
                    value = input.value,
                    onValueChange = { value -> input.value = value.filter { it.isDigit() }.take(6) },
                    label = { Text("Fecha correcta (DDMMYY)") },
                    placeholder = { Text("DDMMYY") },
                    maxLines = 1,
                    modifier = Modifier.testTag("date_correction_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MineSafetyYellow,
                        focusedTextColor = MineTextPrimary,
                        unfocusedTextColor = MineTextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(input.value) },
                enabled = isValidManufacturingDate(input.value),
                modifier = Modifier.testTag("date_correction_confirm"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MineErrorRed,
                    contentColor = MineTextPrimary
                )
            ) {
                Text("Corregir", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("date_correction_cancel")
            ) {
                Text("Cancelar", color = MineTextPrimary)
            }
        },
        containerColor = MineDarkSurface
    )
}
