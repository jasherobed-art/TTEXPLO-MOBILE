package com.ttexplo.mobile.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class IndustrialToneSynthesizerTest {

    @Test
    fun testTonesHaveDistinctExpectedDurationsAndNonZeroEnergy() {
        val success = IndustrialToneSynthesizer.generateSuccessTone(100)
        val warning = IndustrialToneSynthesizer.generateWarningTone(100)
        val error = IndustrialToneSynthesizer.generateErrorTone(100)

        // Expected approximate durations: ~142ms, ~335ms, ~480ms at 44100Hz
        assertThat(success.size).isGreaterThan(5500)
        assertThat(warning.size).isGreaterThan(success.size)
        assertThat(error.size).isGreaterThan(warning.size)

        assertThat(maxPeak(success)).isGreaterThan(15000)
        assertThat(maxPeak(warning)).isGreaterThan(15000)
        assertThat(maxPeak(error)).isGreaterThan(15000)
    }

    @Test
    fun testVolumeScalingAndZeroSilence() {
        val full = IndustrialToneSynthesizer.generateSuccessTone(100)
        val half = IndustrialToneSynthesizer.generateSuccessTone(50)
        val silent = IndustrialToneSynthesizer.generateSuccessTone(0)

        val fullPeak = maxPeak(full)
        val halfPeak = maxPeak(half)
        val silentPeak = maxPeak(silent)

        assertThat(silentPeak).isEqualTo(0)
        assertThat(halfPeak).isGreaterThan(0)
        assertThat(halfPeak).isLessThan(fullPeak)
        // Half volume should be close to 50% of full peak
        val ratio = halfPeak.toDouble() / fullPeak.toDouble()
        assertThat(ratio).isGreaterThan(0.45)
        assertThat(ratio).isLessThan(0.55)
    }

    @Test
    fun testFadeInAndFadeOutAvoidBoundaryClicks() {
        val error = IndustrialToneSynthesizer.generateErrorTone(100)
        assertThat(abs(error.first().toInt())).isEqualTo(0)
        assertThat(abs(error.last().toInt())).isLessThan(1500)
    }

    @Test
    fun testWarningToneIsSharpHiLoPattern() {
        val warning = IndustrialToneSynthesizer.generateWarningTone(100)
        // First pulse: 0..125ms (2400 Hz), silence: 125..170ms, second pulse: 170..335ms (550 Hz)
        val hiFreq = estimateFrequencyHz(warning, startMs = 20, endMs = 105)
        val loFreq = estimateFrequencyHz(warning, startMs = 195, endMs = 310)

        assertThat(hiFreq).isWithin(120.0).of(2400.0)
        assertThat(loFreq).isWithin(80.0).of(550.0)
        assertThat(hiFreq).isGreaterThan(loFreq * 3.5)
    }

    @Test
    fun testErrorToneIsIdenticalLowLowPattern() {
        val error = IndustrialToneSynthesizer.generateErrorTone(100)
        // First pulse: 0..190ms (260 Hz), silence: 190..270ms, second pulse: 270..480ms (260 Hz)
        val firstLow = estimateFrequencyHz(error, startMs = 30, endMs = 160)
        val secondLow = estimateFrequencyHz(error, startMs = 300, endMs = 450)

        assertThat(firstLow).isWithin(30.0).of(260.0)
        assertThat(secondLow).isWithin(30.0).of(260.0)
        assertThat(abs(firstLow - secondLow)).isLessThan(15.0)
    }

    @Test
    fun testSuccessToneRemainsAscendingValidConfirmation() {
        val success = IndustrialToneSynthesizer.generateSuccessTone(100)
        // First pulse: 0..55ms (1760 Hz), silence: 55..67ms, second pulse: 67..142ms (2349.3 Hz)
        val firstFreq = estimateFrequencyHz(success, startMs = 10, endMs = 45)
        val secondFreq = estimateFrequencyHz(success, startMs = 80, endMs = 130)

        assertThat(firstFreq).isWithin(100.0).of(1760.0)
        assertThat(secondFreq).isWithin(120.0).of(2349.3)
        assertThat(secondFreq).isGreaterThan(firstFreq)
    }

    private fun estimateFrequencyHz(samples: ShortArray, startMs: Int, endMs: Int): Double {
        val sr = IndustrialToneSynthesizer.SAMPLE_RATE
        val start = (sr * startMs) / 1000
        val end = ((sr * endMs) / 1000).coerceAtMost(samples.size - 1)
        var risingZeroCrossings = 0
        for (i in start until end) {
            if (samples[i] <= 0 && samples[i + 1] > 0) {
                risingZeroCrossings++
            }
        }
        val durationSec = (end - start).toDouble() / sr.toDouble()
        return risingZeroCrossings / durationSec
    }

    private fun maxPeak(samples: ShortArray): Int {
        var max = 0
        for (s in samples) {
            val a = abs(s.toInt())
            if (a > max) max = a
        }
        return max
    }
}
