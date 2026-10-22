package com.heysig.sleep

import org.junit.Assert.*
import org.junit.Test

class AutomaticSleepTest {
    private fun decision(due: Long=1000,now: Long=1000,enabled: Boolean=true,current: Boolean=true,
        authorized: Boolean=true,valid: Boolean=true,state: SleepState=SleepState()) =
        automaticSleepDecision(due,now,enabled,current,authorized,valid,state)
    @Test fun onlyCurrentAuthorizedAndTimelyOccurrencesRun() {
        assertEquals(AutomaticSleepDecision.RUN,decision())
        assertEquals(AutomaticSleepDecision.RUN,decision(now=121000))
        assertEquals(AutomaticSleepDecision.MISSED,decision(now=121001))
        assertEquals(AutomaticSleepDecision.EARLY,decision(now=999))
        assertEquals(AutomaticSleepDecision.INVALID,decision(due=0))
        assertEquals(AutomaticSleepDecision.INVALID,decision(authorized=false))
        assertEquals(AutomaticSleepDecision.INVALID,decision(valid=false))
        assertEquals(AutomaticSleepDecision.STALE,decision(current=false))
        assertEquals(AutomaticSleepDecision.DISABLED,decision(enabled=false))
    }
    @Test fun manualAndPendingCommandsTakePriority() {
        assertEquals(AutomaticSleepDecision.PAUSED,decision(state=SleepState().manual()))
        assertEquals(AutomaticSleepDecision.PAUSED,decision(state=SleepState().begin()))
        assertEquals(AutomaticSleepDecision.RUN,decision(state=SleepState(SleepMode.ASLEEP)))
        assertEquals(AutomaticSleepDecision.PAUSED,decision(state=SleepState().begin().recovered()))
    }
}
