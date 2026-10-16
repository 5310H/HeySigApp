package com.heysig.control

sealed class AppIntent {
    data class Control(val request: ManualRequest) : AppIntent()
    data class ReadLevel(val control: LevelControl) : AppIntent()
    data class ClarifyProgram(val slot: Int) : AppIntent()
    data object ReadProgram : AppIntent()
    data object ListPrograms : AppIntent()
    data object SaveSettings : AppIntent()
    data object RestoreSettings : AppIntent()
    data object Cancel : AppIntent()
    data class Unsupported(val explanation: String) : AppIntent()
}

/** Shared phone/watch vocabulary. Parsing never plays audio or guesses an unclear target. */
object VoiceCommands {
    private val numbers = mapOf("zero" to 0,"one" to 1,"two" to 2,"three" to 3,"four" to 4,
        "five" to 5,"six" to 6,"seven" to 7,"eight" to 8,"nine" to 9,"ten" to 10,
        "eleven" to 11,"twelve" to 12,"thirteen" to 13,"fourteen" to 14,"fifteen" to 15)
    private fun number(text: String): Int? {
        // Permit one sign only; "minus negative three" needs clarification, not +3.
        val match = Regex("^(?:(minus|negative) )?([a-z]+|[+-]?[0-9]+)$").matchEntire(text) ?: return null
        val token = match.groupValues[2]
        if(match.groupValues[1].isNotEmpty() && (token.startsWith("-") || token.startsWith("+"))) return null
        val value = numbers[token] ?: token.toIntOrNull() ?: return null
        return if(match.groupValues[1].isNotEmpty()) -value else value
    }
    fun parse(raw: String, fitting: Fitting): AppIntent {
        val input = raw.trim().lowercase().replace(Regex("\\s+")," ")
            .replace(Regex("^(sig3|sig) "),"")
        // Exact configured names retain punctuation; one sentence-ending mark is also accepted.
        val candidates = listOf(input, input.replace(Regex("[.!?]$"),"" )).distinct()
        if(candidates.last() in listOf("stop","cancel")) return AppIntent.Cancel
        val programPrefixes = listOf("set program to ","change program to ","set mode to ",
            "change mode to ","program to ","mode to ","set program ","change program ","set mode ","change mode ","program ","mode ")
        for (text in candidates) {
            val name = programPrefixes.firstOrNull { text.startsWith(it) }?.let { text.removePrefix(it).replace(Regex("^(number|slot) "),"") } ?: text
            fitting.programs.singleOrNull { it.name.lowercase() == name }?.let {
                return AppIntent.Control(ManualRequest.SelectProgram(it.slot))
            }
        }
        val text = candidates.last()
        fun request(request: ManualRequest) = AppIntent.Control(request)
        when (text) {
            "cancel", "stop" -> return AppIntent.Cancel
            "current program", "current mode", "what program", "what mode", "what is the current program" -> return AppIntent.ReadProgram
            "list programs", "what programs" -> return AppIntent.ListPrograms
            "save settings" -> return AppIntent.SaveSettings
            "restore settings" -> return AppIntent.RestoreSettings
            "battery", "battery check", "check battery", "battery listen", "listen to battery", "check both batteries" -> return request(ManualRequest.Battery(true,true))
            "check left battery", "left battery" -> return request(ManualRequest.Battery(true,false))
            "check right battery", "right battery" -> return request(ManualRequest.Battery(false,true))
            "mute", "mute volume", "volume mute" -> return request(ManualRequest.SetLevel(LevelControl.VOLUME,0))
            "maximum volume", "volume maximum", "volume max" -> return request(ManualRequest.SetLevel(LevelControl.VOLUME,15))
            "balance soft", "soft balance" -> return request(ManualRequest.SetLevel(LevelControl.SHARPNESS,-8))
            "balance sharp", "sharp balance" -> return request(ManualRequest.SetLevel(LevelControl.SHARPNESS,7))
            "tin mute", "tinnitus mute", "mute tinnitus", "mute tin" -> return request(ManualRequest.SetLevel(LevelControl.TINNITUS,0))
            "maximum tinnitus", "tinnitus max", "tinnitus maximum", "maximum tin", "tin max", "tin maximum" -> return request(ManualRequest.SetLevel(LevelControl.TINNITUS,15))
        }
        val target = programPrefixes.firstOrNull { text.startsWith(it) }?.let { text.removePrefix(it) }
        if (target != null) {
            val normalizedTarget = target.replace(Regex("^(number|slot) "),"").removePrefix("#")
            if(normalizedTarget == "to" && fitting.programs.any { it.slot == 2 }) return AppIntent.ClarifyProgram(2)
            val slot = if (normalizedTarget == "too") 2 else number(normalizedTarget)
            if (slot != null && fitting.programs.any { it.slot == slot }) return request(ManualRequest.SelectProgram(slot))
            return AppIntent.Unsupported("Choose a configured program name or slot")
        }
        val labels = mapOf("sound sharpness" to LevelControl.SHARPNESS,"volume" to LevelControl.VOLUME,"balance" to LevelControl.SHARPNESS,
            "sharpness" to LevelControl.SHARPNESS,"tin" to LevelControl.TINNITUS,"tinnitus" to LevelControl.TINNITUS)
        for ((label, control) in labels) {
            if (text in listOf("current $label","current $label level","what is the $label","what is the current $label","what is the $label level","what is the current $label level")) return AppIntent.ReadLevel(control)
        }
        val stripped = text.replace(Regex("^(set|change) "),"")
        for ((label,control) in labels) {
            if (stripped.startsWith("$label ")) {
                val value = number(stripped.removePrefix("$label ").removePrefix("to "))
                if (value == null) return AppIntent.Unsupported("Say an absolute $label number")
                if (value !in control.range) return AppIntent.Unsupported("$label must be ${control.range.first} to ${control.range.last}")
                return request(ManualRequest.SetLevel(control,value))
            }
        }
        return AppIntent.Unsupported("Say a program name, volume, balance, tin, battery check, current value, save settings, restore settings or cancel")
    }
}

/** Transport-independent command planning. No state update without caller-reported completion. */
class ManualSession(val fitting: Fitting) {
    var requested = RequestedState(); private set
    var snapshot: RequestedState? = null; private set
    fun validate(request: ManualRequest) = ManualValidation.validate(request, fitting, requested)
    fun completed(request: ManualRequest) { validate(request); requested = requested.completed(request) }
    fun save(): RequestedState {
        require(requested.program != null) { "No requested program to save" }
        return requested.copy(levels = requested.levels.toMap()).also { snapshot = it }
    }
    fun restorePlan(): List<ManualRequest> {
        val saved = requireNotNull(snapshot) { "No saved settings" }
        val slot = requireNotNull(saved.program)
        val first = ManualRequest.SelectProgram(slot)
        ManualValidation.validate(first,fitting,RequestedState())
        val plan = listOf<ManualRequest>(first) + LevelControl.entries.mapNotNull { control ->
            saved.levels[control]?.let { ManualRequest.SetLevel(control,it) }
        }
        var state = RequestedState()
        plan.forEach { ManualValidation.validate(it,fitting,state); state = state.completed(it) }
        return plan
    }
}
