package com.heysig.voice

import org.junit.Test
import org.junit.Assert.*

class WatchShakeGateTest {
    @Test fun moderateSeparatedWristPeaksTriggerWithinLongerWindow() {
        val watch=ShakeGate.watch()
        assertFalse(watch.sample(1.9,1000));assertFalse(watch.sample(1.0,1100))
        assertFalse(watch.sample(1.9,1800));assertFalse(watch.sample(1.0,1900))
        assertTrue(watch.sample(1.9,2600))
        val phone=ShakeGate()
        assertFalse(phone.sample(1.9,1000));assertFalse(phone.sample(1.0,1100))
        assertFalse(phone.sample(1.9,1800));assertFalse(phone.sample(1.0,1900))
        assertFalse(phone.sample(1.9,2600))
    }
    @Test fun ordinaryMotionAndSustainedAccelerationDoNotTrigger() {
        val gate=ShakeGate.watch()
        for(t in 0L..2000L step 20) assertFalse(gate.sample(1.7,t))
        for(t in 2100L..4000L step 20) assertFalse(gate.sample(2.0,t))
        assertFalse(gate.sample(Double.NaN,4100))
        assertFalse(gate.sample(Double.POSITIVE_INFINITY,4200))
    }
    @Test fun peaksMustResetAndBeSeparatedAndRecent() {
        val gate=ShakeGate.watch()
        assertFalse(gate.sample(2.0,0));assertFalse(gate.sample(1.0,20))
        assertFalse(gate.sample(2.0,100));assertFalse(gate.sample(1.0,120))
        assertFalse(gate.sample(2.0,400));assertFalse(gate.sample(1.4,500))
        assertFalse(gate.sample(2.0,800));assertFalse(gate.sample(1.0,900))
        // The old partial sequence expires, so this is the first new peak.
        assertFalse(gate.sample(2.0,2400));assertFalse(gate.sample(1.0,2500))
        assertFalse(gate.sample(2.0,2800));assertFalse(gate.sample(1.0,2900))
        assertTrue(gate.sample(2.0,3200))
    }
    @Test fun repeatedShakesRespectTenSecondCooldown() {
        val gate=ShakeGate.watch()
        fun peak(t: Long): Boolean { gate.sample(1.0,t-20);return gate.sample(2.0,t) }
        assertFalse(peak(1000));assertFalse(peak(1400));assertTrue(peak(1800))
        assertFalse(peak(2200));assertFalse(peak(2600));assertFalse(peak(3000))
        assertFalse(peak(11800));assertFalse(peak(12200));assertTrue(peak(12600))
    }
}
