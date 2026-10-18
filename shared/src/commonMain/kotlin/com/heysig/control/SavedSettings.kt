package com.heysig.control

/** Saved settings are an explicit snapshot, independent of later requested-state updates. */
object SavedSettings {
    fun snapshot(state: RequestedState, fitting: Fitting): RequestedState {
        plan(state,fitting) // Validate all known fields before replacing an existing record.
        return state.copy(levels=state.levels.toMap())
    }
    fun plan(state: RequestedState, fitting: Fitting): List<ManualRequest> {
        val program=requireNotNull(state.program) { "Select a program before saving or restoring" }
        val requests=listOf<ManualRequest>(ManualRequest.SelectProgram(program)) +
            LevelControl.entries.mapNotNull { control -> state.levels[control]?.let { ManualRequest.SetLevel(control,it) } }
        var simulated=RequestedState()
        requests.forEach { ManualValidation.validate(it,fitting,simulated);simulated=simulated.completed(it) }
        return requests
    }
}
