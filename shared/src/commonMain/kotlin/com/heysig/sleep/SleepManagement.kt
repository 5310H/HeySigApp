package com.heysig.sleep

/** Routine state describes completed app commands, never sensed sleep. */
enum class SleepMode { AWAKE, ASLEEP, UNKNOWN }
data class SleepState(val mode: SleepMode = SleepMode.AWAKE, val override: Boolean = false,
    val pending: Boolean = false) {
    val blocksNoise: Boolean get() = mode != SleepMode.AWAKE || override || pending
    fun begin() = copy(pending=true)
    fun completed(sleep: Boolean, success: Boolean) = SleepState(
        if(success) { if(sleep) SleepMode.ASLEEP else SleepMode.AWAKE } else SleepMode.UNKNOWN,
        override=!success)
    fun manual() = copy(override=true)
    fun recovered() = if(pending) SleepState(SleepMode.UNKNOWN,true) else this
    fun resume() = copy(override=false)
}

enum class SleepReminderDecision { EARLY, DISABLED, INVALID, PAUSED, MISSED, DELIVER }
fun sleepReminderDecision(due: Long, now: Long, enabled: Boolean, valid: Boolean, state: SleepState): SleepReminderDecision = when {
    !enabled -> SleepReminderDecision.DISABLED
    !valid || due<=0 -> SleepReminderDecision.INVALID
    now<due -> SleepReminderDecision.EARLY
    state.override || state.pending -> SleepReminderDecision.PAUSED
    now-due>15*60_000L -> SleepReminderDecision.MISSED
    else -> SleepReminderDecision.DELIVER
}
