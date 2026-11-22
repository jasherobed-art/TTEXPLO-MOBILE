package com.ttexplo.mobile.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.ui.common.AutoShrinkText
import com.ttexplo.mobile.ui.common.ScanFeedbackType
import com.ttexplo.mobile.ui.common.SoundFeedbackManager
import com.ttexplo.mobile.ui.theme.*
import kotlin.math.roundToInt

/**
 * Panel de configuración en Ajustes para señales auditivas industriales, vibración háptica,
 * destello visual en pantalla y botones de prueba en vivo.
 */
@Composable
fun AudioSettingsCard(
    settingsRepo: SettingsRepository,
    soundManager: SoundFeedbackManager,
    onPreviewFlash: (ScanFeedbackType) -> Unit,
    modifier: Modifier = Modifier
) {
    var soundEnabled by remember { mutableStateOf(settingsRepo.getScanSoundEnabled()) }
    var vibrationEnabled by remember { mutableStateOf(settingsRepo.getScanVibrationEnabled()) }
    var visualFlashEnabled by remember { mutableStateOf(settingsRepo.getScanVisualFlashEnabled()) }
    var volumePercent by remember { mutableStateOf(settingsRepo.getScanVolumePercent()) }
    var audioStream by remember { mutableStateOf(settingsRepo.getScanAudioStream()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MineDarkSurface),
        border = BorderStroke(1.dp, MineBorderGray),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MineSafetyYellow
                )
                Text(
                    text = "AUDIO, VIBRACIÓN Y ALERTAS VISUALES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MineSafetyYellow,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Tonos industriales sintetizados para distinguir Válido, Duplicado (HI-LO) y Error SEDENA (LOW-LOW) en polvorín.",
                color = MineTextSecondary,
                fontSize = 11.sp
            )

            // Switch 1: Señales auditivas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoShrinkText(
                    text = "Señales auditivas de escaneo:",
                    modifier = Modifier.weight(1f),
                    color = MineTextPrimary,
                    targetTextSize = 13.sp,
                    minTextSize = 9.sp
                )
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = {
                        soundEnabled = it
                        settingsRepo.setScanSoundEnabled(it)
                    },
                    modifier = Modifier.testTag("settings_sound_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MineSafetyYellow,
                        checkedTrackColor = MineSafetyYellow.copy(alpha = 0.5f)
                    )
                )
            }

            // Switch 2: Vibración háptica
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoShrinkText(
                    text = "Vibración háptica diferenciada:",
                    modifier = Modifier.weight(1f),
                    color = MineTextPrimary,
                    targetTextSize = 13.sp,
                    minTextSize = 9.sp
                )
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = {
                        vibrationEnabled = it
                        settingsRepo.setScanVibrationEnabled(it)
                    },
                    modifier = Modifier.testTag("settings_vibration_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MineSafetyYellow,
                        checkedTrackColor = MineSafetyYellow.copy(alpha = 0.5f)
                    )
                )
            }

            // Switch 3: Destello visual en pantalla
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoShrinkText(
                    text = "Destello visual en pantalla (Verde/Ámbar/Rojo):",
                    modifier = Modifier.weight(1f),
                    color = MineTextPrimary,
                    targetTextSize = 13.sp,
                    minTextSize = 8.sp
                )
                Switch(
                    checked = visualFlashEnabled,
                    onCheckedChange = {
                        visualFlashEnabled = it
                        settingsRepo.setScanVisualFlashEnabled(it)
                    },
                    modifier = Modifier.testTag("settings_flash_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MineSafetyYellow,
                        checkedTrackColor = MineSafetyYellow.copy(alpha = 0.5f)
                    )
                )
            }

            // Slider de Volumen de tonos
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volumen de tonos:",
                        color = MineTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$volumePercent%",
                        color = if (soundEnabled) MineSafetyYellow else MineTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = volumePercent.toFloat(),
                    onValueChange = { newVal ->
                        val rounded = (newVal / 5f).roundToInt() * 5
                        volumePercent = rounded.coerceIn(10, 100)
                        settingsRepo.setScanVolumePercent(volumePercent)
                    },
                    valueRange = 10f..100f,
                    enabled = soundEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MineSafetyYellow,
                        activeTrackColor = MineSafetyYellow,
                        inactiveTrackColor = MineBorderGray
                    )
                )
            }

            // Canal de salida de audio (Multimedia vs Alarma / Alta Potencia)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Canal de salida de audio:",
                    color = MineTextSecondary,
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = audioStream == SettingsRepository.AUDIO_STREAM_MEDIA,
                        onClick = {
                            audioStream = SettingsRepository.AUDIO_STREAM_MEDIA
                            settingsRepo.setScanAudioStream(SettingsRepository.AUDIO_STREAM_MEDIA)
                        },
                        enabled = soundEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_stream_media_chip"),
                        label = {
                            AutoShrinkText(
                                text = "Multimedia",
                                targetTextSize = 11.sp,
                                minTextSize = 8.sp
                            )
                        }
                    )
                    FilterChip(
                        selected = audioStream == SettingsRepository.AUDIO_STREAM_ALARM,
                        onClick = {
                            audioStream = SettingsRepository.AUDIO_STREAM_ALARM
                            settingsRepo.setScanAudioStream(SettingsRepository.AUDIO_STREAM_ALARM)
                        },
                        enabled = soundEnabled,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("settings_stream_alarm_chip"),
                        label = {
                            AutoShrinkText(
                                text = "Alarma (Alta Potencia)",
                                targetTextSize = 11.sp,
                                minTextSize = 8.sp
                            )
                        }
                    )
                }
            }

            // Botones de prueba en vivo
            Text(
                text = "Probar señales en vivo (audio + vibración + destello):",
                color = MineTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        soundManager.playSuccess()
                        if (visualFlashEnabled) onPreviewFlash(ScanFeedbackType.SUCCESS)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .testTag("settings_test_sound_valid"),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MineSuccessGreenLight),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MineSuccessGreen.copy(alpha = 0.16f),
                        contentColor = MineSuccessGreenLight
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            AutoShrinkText(
                                text = "Válido",
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 11.sp,
                                minTextSize = 8.sp,
                                color = MineSuccessGreenLight
                            )
                        }
                        AutoShrinkText(
                            text = "AGUDO OK",
                            targetTextSize = 9.sp,
                            minTextSize = 7.sp,
                            color = MineSuccessGreenLight.copy(alpha = 0.85f)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        soundManager.playWarning()
                        if (visualFlashEnabled) onPreviewFlash(ScanFeedbackType.WARNING)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .testTag("settings_test_sound_duplicate"),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MineWarningAmber),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MineWarningAmber.copy(alpha = 0.16f),
                        contentColor = MineWarningAmber
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            AutoShrinkText(
                                text = "Duplicado",
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 11.sp,
                                minTextSize = 8.sp,
                                color = MineWarningAmber
                            )
                        }
                        AutoShrinkText(
                            text = "HI-LO",
                            fontWeight = FontWeight.Bold,
                            targetTextSize = 9.sp,
                            minTextSize = 7.sp,
                            color = MineWarningAmber.copy(alpha = 0.9f)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        soundManager.playError()
                        if (visualFlashEnabled) onPreviewFlash(ScanFeedbackType.ERROR)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .testTag("settings_test_sound_error"),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MineErrorRedLight),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MineErrorRed.copy(alpha = 0.18f),
                        contentColor = MineErrorRedLight
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            AutoShrinkText(
                                text = "Error",
                                fontWeight = FontWeight.Bold,
                                targetTextSize = 11.sp,
                                minTextSize = 8.sp,
                                color = MineErrorRedLight
                            )
                        }
                        AutoShrinkText(
                            text = "LOW-LOW",
                            fontWeight = FontWeight.Bold,
                            targetTextSize = 9.sp,
                            minTextSize = 7.sp,
                            color = MineErrorRedLight.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
