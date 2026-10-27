package com.heysig.voice

/** Unlock authorizes session start; the resulting bounded authority survives screen/PIN locking. */
data class WatchVoiceAccess(
    val activeSession: Boolean,
    val microphoneGranted: Boolean,
    val notificationsAllowed: Boolean,
    val deviceLocked: Boolean
) {
    val mayStartSession get() = !deviceLocked && microphoneGranted && notificationsAllowed
    val mayCapture get() = activeSession && microphoneGranted && notificationsAllowed
}
