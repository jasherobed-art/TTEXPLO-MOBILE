package com.ttexplo.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.ttexplo.mobile.ui.theme.*

/**
 * Diálogo de confirmación en 2 pasos secuenciales para prevenir
 * borrados accidentales de códigos o borradores en operación de campo/mina.
 */
@Composable
fun TwoStepDeleteDialog(
    itemDescription: String,
    itemCode: String? = null,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MineDarkSurface,
        titleContentColor = MineTextPrimary,
        shape = RoundedCornerShape(12.dp),
        icon = {
            if (step == 1) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MineSafetyYellow,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MineErrorRed,
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (step == 1) MineSafetyYellow.copy(alpha = 0.2f) else MineErrorRed.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = stringResource(if (step == 1) R.string.delete_step1 else R.string.delete_step2),
                        color = if (step == 1) MineSafetyYellow else MineErrorRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                AutoShrinkText(
                    text = stringResource(if (step == 1) R.string.delete_title_step1 else R.string.delete_title_step2),
                    fontWeight = FontWeight.Bold,
                    color = MineTextPrimary,
                    targetTextSize = 16.sp,
                    minTextSize = 13.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(if (step == 1) R.string.delete_body_step1 else R.string.delete_body_step2),
                    color = MineTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MineDarkBackground)
                        .border(
                            1.dp,
                            if (step == 1) MineBorderGray else MineErrorRed.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = itemDescription,
                            color = MineTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 2
                        )
                        if (!itemCode.isNullOrBlank()) {
                            Text(
                                text = itemCode,
                                color = if (step == 1) MineSafetyYellow else MineErrorRed,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineSafetyYellow,
                        contentColor = MineDarkBackground
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete_continue),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else {
                Button(
                    onClick = {
                        onConfirmDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MineErrorRed,
                        contentColor = MineTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete_confirm),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MineTextSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MineBorderGray)
            ) {
                Text(
                    text = stringResource(R.string.delete_cancel),
                    fontSize = 12.sp
                )
            }
        }
    )
}
