package com.heysig.voice

/** Ephemeral recognition result. Never persists words or replays a submitted command. */
class WatchSpeechHandoff {
    data class Result(val alternatives: List<String>, val capturedAt: Long) {
        fun fresh(now: Long) = now - capturedAt in 0 until 15_000L
    }
    private var startedAt: Long? = null
    private var pending: Result? = null
    val recognizing: Boolean get() = startedAt != null
    fun begin(now: Long) { cancel(); startedAt = now }
    fun receive(words: List<String>, now: Long): Boolean {
        val start = startedAt
        cancel()
        if (start == null || now - start !in 0 until 120_000L) return false
        val choices = words.take(5).toList()
        if (choices.isEmpty() || choices.any { it.isBlank() || it.length > 512 }) return false
        pending = Result(choices, now)
        return true
    }
    fun take(now: Long): Result? {
        val result = pending
        pending = null
        return result?.takeIf { it.fresh(now) }
    }
    fun cancel() { startedAt = null; pending = null }
}
