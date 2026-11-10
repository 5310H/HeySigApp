package com.heysig.masking

import com.heysig.noise.NoiseCancellationSignal

/** Bounded binary stdin protocol for development recordings; stdout contains six scalars only. */
fun main() {
    val input = System.`in`.buffered()
    val raw = ByteArray(96000)
    val pcm = ShortArray(48000)
    try {
        while (true) {
            var count = 0
            while (count < raw.size) {
                val read = input.read(raw, count, raw.size - count)
                if (read < 0) { check(count == 0) { "Incomplete development PCM frame" }; return }
                count += read
            }
            for (i in pcm.indices) pcm[i] = ((raw[2*i].toInt() and 255) or (raw[2*i+1].toInt() shl 8)).toShort()
            println(NoiseCancellationSignal.features(pcm).joinToString(","))
            raw.fill(0); pcm.fill(0)
        }
    } finally { raw.fill(0); pcm.fill(0) }
}
