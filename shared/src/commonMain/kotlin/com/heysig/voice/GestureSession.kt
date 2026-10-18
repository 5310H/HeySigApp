package com.heysig.voice

/** In-memory authority only: process death cannot restore a gesture or pending tap. */
class GestureSession {
    private var started: Long? = null
    private var token: String? = null
    private var offeredAt = 0L
    fun start(now: Long) { stop(); started = now }
    fun active(now: Long): Boolean {
        val start = started ?: return false
        return (now - start) in 0 until 8*60*60*1000L
    }
    fun offer(value: String, now: Long): Boolean {
        if(!active(now) || value.isEmpty()) return false
        token = value; offeredAt = now; return true
    }
    fun consume(value: String, now: Long): Boolean {
        val valid = active(now) && value == token && now-offeredAt in 0..30_000
        token = null
        return valid
    }
    fun stop() { started = null; token = null }
}
