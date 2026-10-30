package com.heysig.voice

/** Three separated peaks with hysteresis and a ten-second cooldown. */
class ShakeGate private constructor(
    private val peakG: Double,
    private val resetG: Double,
    private val windowMs: Long
) {
    constructor(): this(2.7, 1.8, 1200)
    companion object {
        fun phone(peakG: Double): ShakeGate {
            require(peakG.isFinite() && peakG in 1.8..4.0)
            return ShakeGate(peakG,peakG*2/3,1200)
        }
        /** Wrist motion needs a different threshold from a handheld phone. */
        fun watch() = ShakeGate(1.8, 1.3, 1800)
    }
    private var first = -1L
    private var lastPeak = -1000L
    private var lastTrigger = -10000L
    private var peaks = 0
    private var elevated = false
    fun sample(g: Double, now: Long): Boolean {
        if(!g.isFinite()) return false
        if(g < resetG) { elevated=false;return false }
        if(g < peakG || elevated) return false
        elevated=true
        if(now-lastTrigger < 10000 || now-lastPeak < 180) return false
        if(first < 0 || now-first > windowMs) { first=now;peaks=0 }
        lastPeak=now
        if(++peaks < 3) return false
        peaks=0;first=-1;lastTrigger=now
        return true
    }
}
