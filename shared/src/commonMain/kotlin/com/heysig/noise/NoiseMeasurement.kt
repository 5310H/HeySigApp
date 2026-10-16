package com.heysig.noise

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Relative digital measurements only. No speech, noise-source or SPL inference. */
data class NoiseMeasurement(
    val sampleRate: Int,
    val sampleCount: Long,
    val frameCount: Int,
    val rmsDbfs: Double,
    val backgroundProxyDbfs: Double,
    val upperFrameDbfs: Double,
    val peakDbfs: Double,
    val clippedFraction: Double,
    val zeroCrossingRate: Double,
    val flags: Set<MeasurementFlag>
) {
    val durationSeconds: Double get() = sampleCount.toDouble() / sampleRate
}

enum class MeasurementFlag { ALL_ZERO, CLIPPING, DC_OFFSET, SHORT_CAPTURE }

/** One-second windows; only scalar frame statistics survive add(), never PCM. */
class NoiseAccumulator(private val sampleRate: Int, private val expectedSeconds: Int = 5) {
    init { require(sampleRate in 8000..192000); require(expectedSeconds in 1..10) }
    private var finished: NoiseMeasurement? = null
    private var count = 0L
    private var frameSamples = 0
    private var sum = 0.0
    private var squares = 0.0
    private var frameSum = 0.0
    private var frameSquares = 0.0
    private var peak = 0.0
    private var clipped = 0L
    private var crossings = 0L
    private var previousSign = 0
    private val frames = mutableListOf<Double>()

    fun add(pcm: ShortArray, length: Int) {
        check(finished == null) { "Capture already finalized" }
        require(length in 0..pcm.size)
        require(count + length <= sampleRate.toLong() * expectedSeconds) { "Capture exceeds bounded duration" }
        for (i in 0 until length) {
            val raw = pcm[i].toInt()
            val value = raw / 32768.0
            count++; frameSamples++
            sum += value; squares += value * value
            frameSum += value; frameSquares += value * value
            peak = max(peak, abs(value))
            if (abs(raw) >= 32760) clipped++
            val sign = when { raw > 0 -> 1; raw < 0 -> -1; else -> 0 }
            if (sign != 0) {
                if (previousSign != 0 && sign != previousSign) crossings++
                previousSign = sign
            }
            if (frameSamples == sampleRate) finishFrame()
        }
    }

    private fun finishFrame() {
        if (frameSamples == 0) return
        val mean = frameSum / frameSamples
        frames.add(db(sqrt(max(0.0, frameSquares / frameSamples - mean * mean))))
        frameSamples = 0; frameSum = 0.0; frameSquares = 0.0
    }

    fun measurement(): NoiseMeasurement {
        finished?.let { return it }
        require(count > 0) { "No samples received" }
        finishFrame()
        val mean = sum / count
        val sorted = frames.sorted()
        // This lower-frame quantile is a mixture statistic, not measured background noise/SNR.
        fun percentile(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
        val fraction = clipped.toDouble() / count
        val flags = mutableSetOf<MeasurementFlag>()
        if (peak == 0.0) flags.add(MeasurementFlag.ALL_ZERO)
        if (fraction >= 0.001) flags.add(MeasurementFlag.CLIPPING)
        if (abs(mean) > 0.05) flags.add(MeasurementFlag.DC_OFFSET)
        if (count < sampleRate.toLong() * expectedSeconds) flags.add(MeasurementFlag.SHORT_CAPTURE)
        return NoiseMeasurement(sampleRate, count, frames.size,
            db(sqrt(max(0.0, squares / count - mean * mean))), percentile(0.2), percentile(0.8),
            db(peak), fraction, crossings.toDouble() / max(1L, count - 1), flags.toSet()).also { finished = it }
    }

    companion object {
        // A finite display floor, not proof of environmental silence.
        private fun db(value: Double) = 20.0 * log10(max(value, 0.000001))
    }
}
