package com.heysig.sleep

/** Elapsed time only. A timer never edits the saved daily routine. */
object TherapyTimer {
    val minutes = (5..60 step 5).toList()
    fun due(startElapsed: Long, durationMinutes: Int): Long {
        require(startElapsed >= 0 && durationMinutes in minutes) { "Choose 5 to 60 minutes in 5-minute steps" }
        require(startElapsed <= Long.MAX_VALUE-durationMinutes*60_000L)
        return startElapsed+durationMinutes*60_000L
    }
}
