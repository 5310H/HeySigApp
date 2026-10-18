package com.heysig.control

/** Resolve at dispatch time using only the current program's known requested value. */
object LevelSteps {
    fun request(control: LevelControl, delta: Int, state: RequestedState, fitting: Fitting): ManualRequest.SetLevel {
        val label = when (control) {
            LevelControl.VOLUME -> "Volume"
            LevelControl.SHARPNESS -> "Balance"
            LevelControl.TINNITUS -> "Tinnitus"
        }
        require(delta == -1 || delta == 1) { "Use $label up or down for one step" }
        val baseline = requireNotNull(state.levels[control]) {
            "$label is not set for this program. Say an exact value from ${control.range.first} to ${control.range.last} first."
        }
        require(baseline in control.range) { "$label baseline is invalid. Say an exact value first." }
        val target = baseline + delta
        require(target in control.range) {
            "$label is already at ${if (delta > 0) "maximum (${control.range.last})" else "minimum (${control.range.first})"}."
        }
        return ManualRequest.SetLevel(control, target).also { ManualValidation.validate(it, fitting, state) }
    }
}

object VolumeSteps {
    fun request(delta: Int, state: RequestedState, fitting: Fitting): ManualRequest.SetLevel =
        LevelSteps.request(LevelControl.VOLUME, delta, state, fitting)
}
