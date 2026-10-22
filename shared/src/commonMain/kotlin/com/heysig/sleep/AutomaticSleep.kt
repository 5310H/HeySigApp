package com.heysig.sleep

/** An automatic occurrence is one-use and never catches up outside this window. */
enum class AutomaticSleepDecision { RUN, DISABLED, STALE, INVALID, EARLY, MISSED, PAUSED }
fun automaticSleepDecision(due: Long,now: Long,enabled: Boolean,current: Boolean,
    authorized: Boolean,valid: Boolean,state: SleepState): AutomaticSleepDecision = when {
    !enabled -> AutomaticSleepDecision.DISABLED
    !current -> AutomaticSleepDecision.STALE
    !authorized || !valid || due<=0 -> AutomaticSleepDecision.INVALID
    now<due -> AutomaticSleepDecision.EARLY
    now-due>120_000L -> AutomaticSleepDecision.MISSED
    state.override || state.pending -> AutomaticSleepDecision.PAUSED
    else -> AutomaticSleepDecision.RUN
}
