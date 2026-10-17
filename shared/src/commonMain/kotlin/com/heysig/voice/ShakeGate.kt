package com.heysig.voice

/** Three separated acceleration peaks in 1.2 seconds; ten-second cooldown. */
class ShakeGate {
    private var first = -1L
    private var lastPeak = -1000L
    private var lastTrigger = -10000L
    private var peaks = 0
    private var elevated = false
    fun sample(g: Double, now: Long): Boolean {
        if(!g.isFinite()) return false
        if(g < 1.8) { elevated=false;return false }
        if(g < 2.7 || elevated) return false
        elevated=true
        if(now-lastTrigger < 10000 || now-lastPeak < 180) return false
        if(first < 0 || now-first > 1200) { first=now;peaks=0 }
        lastPeak=now
        if(++peaks < 3) return false
        peaks=0;first=-1;lastTrigger=now
        return true
    }
}
