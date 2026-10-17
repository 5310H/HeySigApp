package com.heysig.voice

import org.junit.Test
import org.junit.Assert.*

class HandsFreeTest {
    @Test fun shakeRejectsOrdinaryMovementAndNeedsSeparatedPeaks() {
        val gate=ShakeGate()
        for(t in 0L..2000L step 50) assertFalse(gate.sample(1.8,t))
        assertFalse(gate.sample(Double.NaN,2100))
        assertFalse(gate.sample(3.0,3000));assertFalse(gate.sample(3.0,3250))
        assertFalse(gate.sample(1.0,3300));assertFalse(gate.sample(3.0,3500))
        assertFalse(gate.sample(1.0,3600));assertTrue(gate.sample(3.0,3750))
        assertFalse(gate.sample(1.0,3800));assertFalse(gate.sample(3.0,4000))
        assertFalse(gate.sample(1.0,14000));assertFalse(gate.sample(3.0,14250))
        assertFalse(gate.sample(1.0,14300));assertFalse(gate.sample(3.0,14500))
        assertFalse(gate.sample(1.0,14600));assertTrue(gate.sample(3.0,14750))
        val spaced=ShakeGate()
        assertFalse(spaced.sample(3.0,0));assertFalse(spaced.sample(1.0,100))
        assertFalse(spaced.sample(3.0,1500));assertFalse(spaced.sample(1.0,1600))
        assertFalse(spaced.sample(3.0,3000))
    }
    @Test fun offInvalidatesWakeAndRecognition() {
        val s=HandsFreeSession(); s.on(); val token=s.generation
        assertTrue(s.wake(token)); val command=s.generation; s.off()
        assertFalse(s.accepts(command)); assertFalse(s.complete(command)); assertFalse(s.wake(token))
        assertEquals(HandsFreeSession.Mode.OFF,s.mode)
        s.on(); assertFalse(s.wake(token))
    }
    @Test fun pauseRequiresDeadlineAndCurrentGeneration() {
        val s=HandsFreeSession();s.on();s.pause(10)
        val token=s.generation
        assertFalse(s.resume(1_800_009,token));assertTrue(s.resume(1_800_010,token))
        s.pause(2_000_000);val old=s.generation;s.off()
        assertFalse(s.resume(4_000_000,old));assertEquals(HandsFreeSession.Mode.OFF,s.mode)
    }
    @Test fun onlyWakeArmsOneCommand() {
        val s=HandsFreeSession();s.on();val token=s.generation
        assertFalse(s.accepts(token));assertTrue(s.wake(token));assertFalse(s.wake(token))
        val command=s.generation
        assertTrue(s.complete(command));assertFalse(s.accepts(command));assertFalse(s.complete(command))
        assertTrue(s.wake(s.generation));assertFalse(s.accepts(command))
    }
    @Test fun controlsDoNotMatchOrdinaryAidCommands() {
        assertEquals(HandsFreeControl.OFF,HandsFreeControl.parse("Stop listening!"))
        assertEquals(HandsFreeControl.PAUSE,HandsFreeControl.parse("pause for 30 minutes"))
        for(text in listOf("stop","program two","volume eight","please stop listening later")) assertNull(HandsFreeControl.parse(text))
    }
}
