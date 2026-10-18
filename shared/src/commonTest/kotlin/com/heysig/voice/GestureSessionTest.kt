package com.heysig.voice

import org.junit.Assert.*
import org.junit.Test

class GestureSessionTest {
    @Test fun tapsAreFreshOneUseAndSessionBound() {
        val s=GestureSession()
        assertFalse(s.offer("a",0)); s.start(100)
        assertTrue(s.offer("a",200)); assertTrue(s.consume("a",30_200))
        assertFalse(s.consume("a",30_200))
        assertTrue(s.offer("b",40_000)); assertFalse(s.consume("b",70_001))
        assertTrue(s.offer("c",80_000)); s.stop(); assertFalse(s.consume("c",80_001))
        s.start(90_000); assertTrue(s.offer("d",90_001))
        assertFalse(s.consume("d",90_000))
        assertFalse(s.active(90_000+8*60*60*1000L))
    }
    @Test fun replacingOrRestartingInvalidatesOldOffers() {
        val s=GestureSession(); s.start(0)
        assertTrue(s.offer("old",1)); assertTrue(s.offer("new",2))
        assertFalse(s.consume("old",3)); assertFalse(s.consume("new",4))
        assertTrue(s.offer("last",5)); s.start(6)
        assertFalse(s.consume("last",7)); assertFalse(GestureSession().consume("last",7))
    }
}
