package com.heysig.control

import org.junit.Test
import org.junit.Assert.*

class ProgramComparisonTest {
    private val fit=Fitting(12,12,12,listOf(Program(1,"Universal",TinnitusCapability.UNKNOWN),Program(3,"Noisy",TinnitusCapability.UNKNOWN),Program(6,"Very Noisy",TinnitusCapability.UNKNOWN)))
    @Test fun preservesExplicitReturnAndIndependentFeedback() {
        val session=ProgramComparison(fit,3,6,1)
        assertEquals(ManualRequest.SelectProgram(1),session.target(1,fit))
        session.answer(ProgramComparison.Aspect.UNDERSTANDING,ProgramComparison.Preference.FIRST)
        session.answer(ProgramComparison.Aspect.COMFORT,ProgramComparison.Preference.SECOND)
        assertEquals(ProgramComparison.Preference.FIRST,session.answer(ProgramComparison.Aspect.UNDERSTANDING))
        assertNull(session.answer(ProgramComparison.Aspect.AWARENESS))
        session.clear();assertNull(session.answer(ProgramComparison.Aspect.UNDERSTANDING))
    }
    @Test fun rejectsStaleSetupAndUnreviewedPrograms() {
        val session=ProgramComparison(fit,1,3,1)
        assertTrue(runCatching { session.target(6,fit) }.isFailure)
        assertTrue(runCatching { session.target(1,fit.copy(controlAddress=13)) }.isFailure)
        assertTrue(runCatching { session.target(1,fit.copy(programs=fit.programs.reversed())) }.isFailure)
        assertTrue(runCatching { ProgramComparison(fit,1,1,1) }.isFailure)
        assertTrue(runCatching { ProgramComparison(fit,1,3,2) }.isFailure)
    }
}
