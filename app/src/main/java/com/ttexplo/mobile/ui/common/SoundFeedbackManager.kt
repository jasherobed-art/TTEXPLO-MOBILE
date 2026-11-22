package com.ttexplo.mobile.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ttexplo.mobile.data.repository.SettingsRepository
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Sintetizador PCM 16-bit mono (44.1 kHz) de tonos industriales de alto contraste
 * diseñados para distinguirse en entornos ruidosos de mina y polvorín sin archivos externos.
 */
object IndustrialToneSynthesizer {
    const val SAMPLE_RATE = 44100
    private const val MAX_AMPLITUDE = 30000.0 // Margen de seguridad contra clipping en Short

    /**
     * Tono VÁLIDO: doble confirmación aguda ascendente limpia (1760 Hz -> 2349 Hz, ~140 ms).
     */
    fun generateSuccessTone(volumePercent: Int = 100): ShortArray {
        val gain = (volumePercent.coerceIn(0, 100) / 100.0)
        return buildSequence(
            segments = listOf(
                ToneSegment(freqHz = 1760.0, durationMs = 55, harmonicMix = 0.25, harshness = 1.0),
                ToneSegment(freqHz = 0.0, durationMs = 12, harmonicMix = 0.0, harshness = 1.0),
                ToneSegment(freqHz = 2349.3, durationMs = 75, harmonicMix = 0.25, harshness = 1.0)
            ),
            gain = gain
        )
    }

    /**
     * Tono DUPLICADO (HI-LO estilo Zebra): golpe agudo nítido a grave marcado (2400 Hz -> 550 Hz, ~335 ms).
     */
    fun generateWarningTone(volumePercent: Int = 100): ShortArray {
        val gain = (volumePercent.coerceIn(0, 100) / 100.0)
        return buildSequence(
            segments = listOf(
                ToneSegment(freqHz = 2400.0, durationMs = 125, harmonicMix = 0.28, harshness = 1.2),
                ToneSegment(freqHz = 0.0, durationMs = 45, harmonicMix = 0.0, harshness = 1.0),
                ToneSegment(freqHz = 550.0, durationMs = 165, harmonicMix = 0.42, harshness = 1.5)
            ),
            gain = gain
        )
    }

    /**
     * Tono ERROR SEDENA (LOW-LOW): doble pulso grave idéntico y potente (260 Hz -> 260 Hz, ~480 ms).
     */
    fun generateErrorTone(volumePercent: Int = 100): ShortArray {
        val gain = (volumePercent.coerceIn(0, 100) / 100.0)
        return buildSequence(
            segments = listOf(
                ToneSegment(freqHz = 260.0, durationMs = 190, harmonicMix = 0.55, harshness = 2.4),
                ToneSegment(freqHz = 0.0, durationMs = 80, harmonicMix = 0.0, harshness = 1.0),
                ToneSegment(freqHz = 260.0, durationMs = 210, harmonicMix = 0.55, harshness = 2.4)
            ),
            gain = gain
        )
    }

    private data class ToneSegment(
        val freqHz: Double,
        val durationMs: Int,
        val harmonicMix: Double,
        val harshness: Double
    )

    private fun buildSequence(segments: List<ToneSegment>, gain: Double): ShortArray {
        val totalSamples = segments.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
        val buffer = ShortArray(totalSamples)
        var offset = 0

        for (seg in segments) {
            val count = (SAMPLE_RATE * seg.durationMs) / 1000
            if (seg.freqHz <= 0.0 || gain <= 0.0) {
                offset += count
                continue
            }
            val fadeSamples = min(count / 4, (SAMPLE_RATE * 5) / 1000).coerceAtLeast(1)
            val omega = 2.0 * PI * seg.freqHz / SAMPLE_RATE

            for (i in 0 until count) {
                val env = when {
                    i < fadeSamples -> i.toDouble() / fadeSamples
                    i > count - fadeSamples -> (count - i).toDouble() / fadeSamples
                    else -> 1.0
                }
                val fundamental = sin(omega * i)
                // Armónicos impares para proyección fuerte en altavoz de terminal industrial
                val harmonic = sin(omega * 3.0 * i) * seg.harmonicMix
                val shaped = if (seg.harshness > 1.0) {
                    tanh((fundamental + harmonic) * seg.harshness)
                } else {
                    (fundamental + harmonic) / (1.0 + seg.harmonicMix)
                }
                val sample = (shaped * env * gain * MAX_AMPLITUDE)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[offset + i] = sample.toShort()
            }
            offset += count
        }
        return buffer
    }
}

class SoundFeedbackManager(private val context: Context) {

    private val settings = SettingsRepository(context)
    private val audioExecutor = Executors.newSingleThreadExecutor()
    private val trackLock = Any()

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var isReleased = false

    private var fallbackToneGenerator: ToneGenerator? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun isVisualFlashEnabled(): Boolean = settings.getScanVisualFlashEnabled()

    fun playSuccess() {
        val soundOn = settings.getScanSoundEnabled()
        val vibOn = settings.getScanVibrationEnabled()
        val vol = settings.getScanVolumePercent()
        val stream = settings.getScanAudioStream()

        if (soundOn) {
            playIndustrialTone(
                samples = IndustrialToneSynthesizer.generateSuccessTone(vol),
                fallbackTone = ToneGenerator.TONE_PROP_BEEP,
                fallbackDurationMs = 140,
                volumePercent = vol,
                streamMode = stream
            )
        }
        if (vibOn) {
            vibrate(80L, amplitude = 200)
        }
    }

    fun playWarning() {
        val soundOn = settings.getScanSoundEnabled()
        val vibOn = settings.getScanVibrationEnabled()
        val vol = settings.getScanVolumePercent()
        val stream = settings.getScanAudioStream()

        if (soundOn) {
            playIndustrialTone(
                samples = IndustrialToneSynthesizer.generateWarningTone(vol),
                fallbackTone = ToneGenerator.TONE_PROP_NACK,
                fallbackDurationMs = 340,
                volumePercent = vol,
                streamMode = stream
            )
        }
        if (vibOn) {
            vibratePattern(
                pattern = longArrayOf(0, 150, 80, 150),
                amplitudes = intArrayOf(0, 220, 0, 220)
            )
        }
    }

    fun playError() {
        val soundOn = settings.getScanSoundEnabled()
        val vibOn = settings.getScanVibrationEnabled()
        val vol = settings.getScanVolumePercent()
        val stream = settings.getScanAudioStream()

        if (soundOn) {
            playIndustrialTone(
                samples = IndustrialToneSynthesizer.generateErrorTone(vol),
                fallbackTone = ToneGenerator.TONE_SUP_ERROR,
                fallbackDurationMs = 480,
                volumePercent = vol,
                streamMode = stream
            )
        }
        if (vibOn) {
            vibratePattern(
                pattern = longArrayOf(0, 250, 90, 250),
                amplitudes = intArrayOf(0, 255, 0, 255)
            )
        }
    }

    private fun playIndustrialTone(
        samples: ShortArray,
        fallbackTone: Int,
        fallbackDurationMs: Int,
        volumePercent: Int,
        streamMode: String
    ) {
        if (isReleased) return
        try {
            audioExecutor.execute {
                if (isReleased) return@execute
                val played = tryPlayAudioTrack(samples, streamMode)
                if (!played) {
                    playFallbackTone(fallbackTone, fallbackDurationMs, volumePercent, streamMode)
                }
            }
        } catch (_: Exception) {
            // Executor already shut down
        }
    }

    private fun tryPlayAudioTrack(samples: ShortArray, streamMode: String): Boolean {
        return try {
            val usage = if (streamMode == SettingsRepository.AUDIO_STREAM_ALARM) {
                AudioAttributes.USAGE_ALARM
            } else {
                AudioAttributes.USAGE_MEDIA
            }
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(IndustrialToneSynthesizer.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val bufferBytes = samples.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return false
            }

            synchronized(trackLock) {
                stopActiveTrackLocked()
                activeTrack = track
            }

            val written = track.write(samples, 0, samples.size)
            if (written <= 0) {
                synchronized(trackLock) {
                    if (activeTrack === track) {
                        stopActiveTrackLocked()
                    } else {
                        try { track.release() } catch (_: Exception) {}
                    }
                }
                return false
            }

            track.play()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun playFallbackTone(tone: Int, durationMs: Int, volumePercent: Int, streamMode: String) {
        try {
            val androidStream = if (streamMode == SettingsRepository.AUDIO_STREAM_ALARM) {
                AudioManager.STREAM_ALARM
            } else {
                AudioManager.STREAM_MUSIC
            }
            fallbackToneGenerator?.release()
            fallbackToneGenerator = ToneGenerator(androidStream, volumePercent.coerceIn(10, 100))
            fallbackToneGenerator?.startTone(tone, durationMs)
        } catch (_: Exception) {
            // Ignore fallback audio error
        }
    }

    private fun stopActiveTrackLocked() {
        val current = activeTrack ?: return
        activeTrack = null
        try {
            if (current.playState == AudioTrack.PLAYSTATE_PLAYING) {
                current.stop()
            }
        } catch (_: Exception) {
            // Ignore stop error
        }
        try {
            current.release()
        } catch (_: Exception) {
            // Ignore release error
        }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (vibrator?.hasAmplitudeControl() == true) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amp))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {
            // Ignore vibration error
        }
    }

    private fun vibratePattern(pattern: LongArray, amplitudes: IntArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vibrator?.hasAmplitudeControl() == true) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
                } else {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
            // Ignore vibration error
        }
    }

    fun release() {
        isReleased = true
        synchronized(trackLock) {
            stopActiveTrackLocked()
        }
        try {
            fallbackToneGenerator?.release()
            fallbackToneGenerator = null
        } catch (_: Exception) {
            // Ignore release error
        }
        try {
            audioExecutor.shutdownNow()
        } catch (_: Exception) {
            // Ignore shutdown error
        }
    }
}
