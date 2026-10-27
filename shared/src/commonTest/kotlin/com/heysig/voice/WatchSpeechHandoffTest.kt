package com.heysig.voice

import org.junit.Assert.*
import org.junit.Test

class WatchSpeechHandoffTest {
    @Test fun freshResultIsOneUseAndKeepsAlternatives() {
        val s = WatchSpeechHandoff()
        s.begin(10)
        assertTrue(s.recognizing)
        assertTrue(s.receive(listOf("status", "read status"), 100))
        assertFalse(s.recognizing)
        val result = s.take(101)!!
        assertEquals(listOf("status", "read status"), result.alternatives)
        assertTrue(result.fresh(15_099))
        assertFalse(result.fresh(15_100)) // Includes time spent reconnecting.
        assertNull(s.take(102))
    }
    @Test fun lateCancelledOrUnsolicitedResultsCannotSend() {
        val s = WatchSpeechHandoff()
        assertFalse(s.receive(listOf("status"), 0))
        s.begin(0); assertFalse(s.receive(listOf("status"), 120_000))
        s.begin(0); assertFalse(s.receive(listOf("status"), -1))
        s.begin(0); s.cancel(); assertFalse(s.receive(listOf("status"), 1))
        s.begin(0); assertTrue(s.receive(listOf("status"), 1))
        s.cancel(); assertNull(s.take(2))
        s.begin(0); assertTrue(s.receive(listOf("status"), 1))
        assertNull(s.take(15_001))
    }
    @Test fun emptyInvalidAndReplacedWordsCannotLeakThrough() {
        val s = WatchSpeechHandoff()
        for (words in listOf(emptyList(), listOf(" "), listOf("status", ""), listOf("x".repeat(513)))) {
            s.begin(0); assertFalse(s.receive(words, 1)); assertNull(s.take(2))
        }
        s.begin(0); assertTrue(s.receive(listOf("status"), 1))
        s.begin(2); assertNull(s.take(3))
        assertTrue(s.receive(List(8) { "status" }, 4))
        assertEquals(5, s.take(5)!!.alternatives.size)
    }
}
