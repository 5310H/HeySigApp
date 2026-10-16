package com.heysig.noise

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

fun main() {
    val rate = 16000
    fun tone(amplitude: Double) = ShortArray(rate * 5) {
        (sin(2 * PI * 1000 * it / rate) * amplitude * 32767).toInt().toShort()
    }
    fun measure(pcm: ShortArray, chunk: Int = 1024): NoiseMeasurement {
        val accumulator = NoiseAccumulator(rate)
        pcm.asList().chunked(chunk).forEach { accumulator.add(it.toShortArray(), it.size) }
        return accumulator.measurement()
    }
    val full = measure(tone(0.5))
    check(abs(full.rmsDbfs - (-9.031)) < 0.02) { "Sine RMS must use amplitude/sqrt(2)" }
    val half = measure(tone(0.25))
    check(abs((full.rmsDbfs - half.rmsDbfs) - 6.0206) < 0.02)
    check(full.flags.isEmpty() && full.sampleCount == 80000L && full.frameCount == 5)
    check(abs(full.zeroCrossingRate - 0.125) < 0.001)
    check(abs(measure(tone(0.5), 313).rmsDbfs - full.rmsDbfs) < 1e-10)
    val zero = measure(ShortArray(80000))
    check(zero.rmsDbfs.isFinite() && MeasurementFlag.ALL_ZERO in zero.flags)
    val dc = measure(ShortArray(80000) { 8000 })
    check(MeasurementFlag.DC_OFFSET in dc.flags && dc.rmsDbfs == -120.0)
    val clipped = measure(ShortArray(80000) { if (it % 2 == 0) 32767 else -32768 })
    check(MeasurementFlag.CLIPPING in clipped.flags && clipped.clippedFraction == 1.0)
    check(MeasurementFlag.SHORT_CAPTURE in measure(ShortArray(100)).flags)
    check(runCatching { NoiseAccumulator(rate).measurement() }.isFailure)
    check(runCatching { NoiseAccumulator(rate).add(ShortArray(80001), 80001) }.isFailure)
    val changing = ShortArray(80000) { i ->
        (sin(2 * PI * 1000 * i / rate) * (if (i < rate * 3) 0.05 else 0.5) * 32767).toInt().toShort()
    }
    val mixed = measure(changing)
    check(mixed.upperFrameDbfs - mixed.backgroundProxyDbfs > 19.9)
    println("PASS: RMS scaling, chunk boundaries, frame variation, clipping, DC, zero, short and bounded captures")
}
