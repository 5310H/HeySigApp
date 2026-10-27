package com.heysig.voice

import org.junit.Assert.*
import org.junit.Test
import com.heysig.voice.WatchVoiceTurn.Stage.*

class WatchVoiceTurnTest {
    @Test fun promptAndCaptureHappenOncePerShake() {
        val s=WatchVoiceTurn(); val id=s.begin(100)!!
        assertNull(s.begin(101))
        assertTrue(s.advance(id,CONNECTING,PROMPTING))
        assertTrue(s.advance(id,PROMPTING,LISTENING))
        assertFalse(s.advance(id,PROMPTING,LISTENING))
        assertNull(s.begin(102))
        assertTrue(s.advance(id,LISTENING,SENDING))
        assertTrue(s.advance(id,SENDING,REPLYING))
        s.finish(id); assertFalse(s.active(id)); assertNotNull(s.begin(103))
    }
    @Test fun stopExpiryAndOldCallbacksCannotSend() {
        val s=WatchVoiceTurn(); val old=s.begin(100)!!
        assertFalse(s.fresh(old,99)); assertTrue(s.fresh(old,60_099)); assertFalse(s.fresh(old,60_100))
        s.cancel(); val next=s.begin(200)!!
        assertFalse(s.advance(old,CONNECTING,LISTENING)); s.finish(old)
        assertTrue(s.active(next)); assertTrue(s.advance(next,CONNECTING,LISTENING))
    }
    @Test fun phoneChoicesAreIndependentAndMissingMeansOff() {
        assertEquals(WatchSpeechChoices(),WatchSpeechChoices.received(null,null))
        assertEquals(WatchSpeechChoices(true,false),WatchSpeechChoices.received(true,false))
        assertEquals(WatchSpeechChoices(false,true),WatchSpeechChoices.received(false,true))
        assertEquals(WatchSpeechChoices(false,false),WatchSpeechChoices.received(false,null))
    }
}
