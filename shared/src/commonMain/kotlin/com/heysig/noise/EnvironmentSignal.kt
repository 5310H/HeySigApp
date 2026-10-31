package com.heysig.noise

import kotlin.math.abs
import kotlin.math.sqrt

/** Digital gain for a second inference view, never playback, SPL or calibration. */
object EnvironmentSignal {
    fun quietVerificationGain(pcm: ShortArray): Double? {
        require(pcm.size in 48_000..160_000)
        var energy=0.0
        var peak=0
        for(sample in pcm) {
            val value=sample.toInt()
            energy+=value.toDouble()*value
            peak=maxOf(peak,abs(value))
        }
        if(peak==0) return null // No acoustic evidence, including a muted microphone.
        val rms=sqrt(energy/pcm.size)/32768.0
        // At most 24 dB of digital gain, without clipping or attenuating the input.
        return minOf(16.0,0.05/rms,0.95*32768.0/peak).coerceAtLeast(1.0)
    }
}
