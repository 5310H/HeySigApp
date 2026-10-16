package com.heysig.control

/** Stable slot identity; labels never establish actual aid processing or tinnitus support. */
data class Program(val slot: Int, val name: String, val tinnitus: TinnitusCapability) {
    init { require(slot in 1..6); require(name.isNotBlank()); require(name == name.trim()) }
}
enum class TinnitusCapability { UNKNOWN, SUPPORTED, UNSUPPORTED }
enum class LevelControl(val range: IntRange) {
    VOLUME(0..15), SHARPNESS(-8..7), TINNITUS(0..15)
}
data class Fitting(val controlAddress: Int, val leftAddress: Int, val rightAddress: Int,
    val programs: List<Program>) {
    init {
        require(listOf(controlAddress, leftAddress, rightAddress).all { it in 0..255 }) { "Addresses must be 0–255" }
        require(programs.isNotEmpty() && programs.size <= 6) { "Configure 1–6 programs" }
        require(programs.map { it.slot }.distinct().size == programs.size) { "Duplicate program slot" }
        require(programs.map { it.name.lowercase() }.distinct().size == programs.size) { "Program names must be unique" }
    }
}
sealed class ManualRequest {
    data class SelectProgram(val slot: Int) : ManualRequest()
    data class SetLevel(val control: LevelControl, val level: Int) : ManualRequest()
    data class Battery(val left: Boolean, val right: Boolean) : ManualRequest()
}
/** Only the active request context can supply values; switching programs invalidates levels. */
data class RequestedState(val program: Int? = null, val levels: Map<LevelControl, Int> = emptyMap()) {
    init { require(program == null || program in 1..6); require(program != null || levels.isEmpty())
        require(levels.all { (control, value) -> value in control.range }) }
    fun completed(request: ManualRequest): RequestedState = when(request) {
        is ManualRequest.SelectProgram -> RequestedState(request.slot)
        is ManualRequest.SetLevel -> { require(program != null); copy(levels = levels + (request.control to request.level)) }
        is ManualRequest.Battery -> this
    }
}
object ManualValidation {
    fun validate(request: ManualRequest, fitting: Fitting, state: RequestedState) {
        when (request) {
            is ManualRequest.SelectProgram -> require(fitting.programs.any { it.slot == request.slot }) { "Configure this program slot first" }
            is ManualRequest.SetLevel -> {
                require(request.level in request.control.range) { "Level is outside ${request.control.range}" }
                val program = fitting.programs.find { it.slot == state.program }
                require(program != null) { "Select a program before setting levels" }
                if(request.control == LevelControl.TINNITUS) require(program.tinnitus == TinnitusCapability.SUPPORTED) {
                    if(program.tinnitus == TinnitusCapability.UNSUPPORTED) "Tinnitus unavailable for this program" else "Confirm tinnitus support in Setup first"
                }
            }
            is ManualRequest.Battery -> require(request.left || request.right) { "Choose a battery side" }
        }
    }
}
