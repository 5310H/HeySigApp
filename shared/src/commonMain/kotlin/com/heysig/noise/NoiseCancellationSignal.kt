package com.heysig.noise

import kotlin.math.*

/** Analysis-only spectral noise suppression. No playback, enrollment or retained noise profile.
 * The lower temporal power quantile estimates a stationary floor, NOT a target-speaker SNR.
 * Competing voices and continuous speech can contaminate this estimate; the trained masking
 * head combines these features with original speech/event evidence, never this ratio alone.
 */
object NoiseCancellationSignal {
    fun features(pcm: ShortArray): DoubleArray {
        require(pcm.size == 48000)
        val size = 512
        val frames = (pcm.size - size) / 256 + 1
        val bins = size / 2 + 1
        val powers = Array(frames) { DoubleArray(bins) }
        val real = DoubleArray(size)
        val imag = DoubleArray(size)
        val floor = DoubleArray(bins)
        val clean = DoubleArray(frames)
        try {
            for (f in 0 until frames) {
                for (i in 0 until size) {
                    real[i] = pcm[f * 256 + i] / 32768.0 * (.5 - .5 * cos(2 * PI * i / (size - 1)))
                    imag[i] = 0.0
                }
                fft(real, imag)
                for (b in 0 until bins) powers[f][b] = real[b] * real[b] + imag[b] * imag[b]
            }
            for (b in 0 until bins) {
                val ordered = DoubleArray(frames) { powers[it][b] }.apply { sort() }
                try { floor[b] = ordered[((frames - 1) * .2).toInt()] }
                finally { ordered.fill(0.0) }
            }
            var total = 0.0; var kept = 0.0; var bandTotal = 0.0; var bandKept = 0.0
            var active = 0
            // Ignore DC/sub-bass and use 125–7500Hz; speech band 250–4000Hz.
            val floorTotal = (4..240).sumOf { floor[it] }
            for (f in 0 until frames) {
                var frameTotal = 0.0
                for (b in 4..240) {
                    val power = powers[f][b]
                    val gain = if (power > 1e-20) (1 - floor[b] / power).coerceIn(.05, 1.0) else .05
                    val retained = power * gain * gain
                    frameTotal += power; clean[f] += retained
                    total += power; kept += retained
                    if (b in 8..128) { bandTotal += power; bandKept += retained }
                }
                if (frameTotal > 2 * floorTotal && frameTotal > 1e-16) active++
            }
            if (total <= 1e-16) return DoubleArray(6)
            val sorted = clean.copyOf().apply { sort() }
            val depth = try {
                (10 * log10(max(sorted[((frames - 1) * .9).toInt()], 1e-20) /
                    max(sorted[((frames - 1) * .1).toInt()], 1e-20)) / 60).coerceIn(0.0, 1.0)
            } finally { sorted.fill(0.0) }
            // Relative envelope fluctuation after suppression; gain invariant, not intelligibility.
            val envelope = DoubleArray(frames) { sqrt(clean[it]) }
            val fluctuation = try {
                val mean = envelope.average()
                (sqrt(envelope.sumOf { (it - mean) * (it - mean) } / frames) / max(mean, 1e-20) / 3).coerceIn(0.0, 1.0)
            } finally { envelope.fill(0.0) }
            val meanFloor = (4..240).sumOf { floor[it] } / 237
            val flatness = if (meanFloor <= 1e-20) 0.0 else
                (exp((4..240).sumOf { ln(max(floor[it], 1e-20)) } / 237) / meanFloor).coerceIn(0.0, 1.0)
            return doubleArrayOf((1 - kept / total).coerceIn(0.0, 1.0),
                (bandKept / max(bandTotal, 1e-20)).coerceIn(0.0, 1.0), depth,
                fluctuation, flatness, active.toDouble() / frames)
        } finally {
            powers.forEach { it.fill(0.0) }; real.fill(0.0); imag.fill(0.0); floor.fill(0.0); clean.fill(0.0)
        }
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val value = real[i]; real[i] = real[j]; real[j] = value }
        }
        var length = 2
        while (length <= real.size) {
            val stepReal = cos(-2 * PI / length); val stepImag = sin(-2 * PI / length)
            for (start in real.indices step length) {
                var wr = 1.0; var wi = 0.0
                for (offset in 0 until length / 2) {
                    val left = start + offset; val right = left + length / 2
                    val vr = real[right] * wr - imag[right] * wi
                    val vi = real[right] * wi + imag[right] * wr
                    real[right] = real[left] - vr; imag[right] = imag[left] - vi
                    real[left] += vr; imag[left] += vi
                    val next = wr * stepReal - wi * stepImag
                    wi = wr * stepImag + wi * stepReal; wr = next
                }
            }
            length *= 2
        }
    }
}
