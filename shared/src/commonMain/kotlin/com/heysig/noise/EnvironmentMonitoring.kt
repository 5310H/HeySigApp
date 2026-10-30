package com.heysig.noise

/** Reject windows that might include our own tones, including bounded watch transit uncertainty. */
fun environmentSampleAfterPlayback(capturedAt: Long, durationSeconds: Int, uncertaintyMillis: Long, lastPlaybackAt: Long?): Boolean {
    require(capturedAt>=0 && durationSeconds in 3..10 && uncertaintyMillis in 0..5000)
    return lastPlaybackAt==null || capturedAt-durationSeconds*1000L-uncertaintyMillis > lastPlaybackAt+2000
}

data class EnvironmentCaptureAccess(val foreground: Boolean, val unlocked: Boolean,
                                    val permissionGranted: Boolean, val microphoneAvailable: Boolean) {
    fun blocked(): String? = when {
        !foreground || !unlocked -> "Keep HeySig visible and unlocked for monitoring"
        !permissionGranted -> "Microphone permission is required"
        !microphoneAvailable -> "Microphone muted, unavailable or call active"
        else -> null
    }
}

/** Compact samples must belong to a live connection/session; a reconnect starts fresh. */
data class WatchEnvironmentSession(val id: String, val lastReceivedAt: Long, val sequence: Long = -1, val connected: Boolean = true) {
    init { require(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(id));require(lastReceivedAt>=0 && sequence>=-1) }
    fun receive(session: String, nextSequence: Long, ageMillis: Long, now: Long): WatchEnvironmentSession {
        require(connected && session==id && nextSequence>=0 && nextSequence>sequence) { "Watch session expired or sample already used" }
        require(ageMillis in 0..5000 && now>=lastReceivedAt) { "Watch sample expired or clock changed" }
        return copy(lastReceivedAt=now,sequence=nextSequence)
    }
    fun expired(now: Long, schedule: SamplingSchedule) = !connected || now<lastReceivedAt ||
        now-lastReceivedAt > (schedule.intervalSeconds*2+schedule.durationSeconds+10)*1000L
}
