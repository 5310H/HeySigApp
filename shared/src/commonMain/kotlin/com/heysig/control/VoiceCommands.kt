package com.heysig.control

sealed class AppIntent {
    data class Control(val request: ManualRequest) : AppIntent()
    data class RelativeVolume(val delta: Int) : AppIntent()
    data class RelativeLevel(val control: LevelControl, val delta: Int) : AppIntent()
    data class ReadLevel(val control: LevelControl) : AppIntent()
    data class ClarifyProgram(val slot: Int) : AppIntent()
    data object ReadProgram : AppIntent()
    data object ListPrograms : AppIntent()
    data object SaveSettings : AppIntent()
    data object ReadSavedSettings : AppIntent()
    data object RestoreSettings : AppIntent()
    data object Refresh : AppIntent()
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
    /** A bounded grammar; relative intents need a known baseline before execution. */
    fun parse(raw: String, fitting: Fitting): AppIntent {
        val exact = parseExact(raw, fitting)
        if (exact !is AppIntent.Unsupported) return exact
        if (Regex("[.!?]{2}$").containsMatchIn(raw.trim())) return exact
        var text = raw.trim().lowercase().replace(Regex("\\s+"), " ")
            .replace(Regex("[.!?]$"), "")
            .replace(Regex("^(hey sig|sig3|sig) "), "")
        text = text.replace(Regex("^(please |could you |can you |would you )"), "")
            .removePrefix("please ").removeSuffix(" please").trim()
        // Only complete, anchored forms are rewritten. Negation, multiple targets and
        // additional clauses survive and fail ordinary parsing and range validation.
        val naturalProgram = Regex("^(?:switch|go) to (?:the )?(?:(?:program|mode) )?(.+)$").matchEntire(text)
        val trailingProgram = Regex("^(?:set|change|switch) (?:my |the )?(?:program|mode) to (.+)$").matchEntire(text)
        val level = Regex("^(set|change) (?:my |the )?(volume|balance|sharpness|sound sharpness|tin|tinnitus)(?: level)? to (.+)$").matchEntire(text)
        text = when {
            naturalProgram != null -> "program ${naturalProgram.groupValues[1]}"
            trailingProgram != null -> "program ${trailingProgram.groupValues[1]}"
            level != null -> "${level.groupValues[2]} ${level.groupValues[3]}"
            text in listOf("what program am i in", "which program am i using", "what program am i using") -> "current program"
            text in listOf("check my batteries", "check my battery", "check the batteries", "check the battery") -> "check battery"
            text == "check my left battery" -> "check left battery"
            text == "check my right battery" -> "check right battery"
            text == "save my settings" -> "save settings"
            text == "restore my settings" -> "restore settings"
            else -> text.replace(Regex("^what(?: is|'s) (?:my|the) (current )?"), "what is the $1")
        }
        return parseExact(text, fitting)
    }

    private fun parseExact(raw: String, fitting: Fitting): AppIntent {
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
        if(text in listOf("volume up","turn volume up","turn the volume up","turn my volume up","turn up volume","turn up the volume","increase volume","increase the volume","increase my volume","raise volume","raise the volume")) return AppIntent.RelativeVolume(1)
        if(text in listOf("volume down","turn volume down","turn the volume down","turn my volume down","turn down volume","turn down the volume","decrease volume","decrease the volume","decrease my volume","lower volume","lower the volume")) return AppIntent.RelativeVolume(-1)
        for ((label, control) in mapOf("balance" to LevelControl.SHARPNESS, "sharpness" to LevelControl.SHARPNESS,
            "sound sharpness" to LevelControl.SHARPNESS, "tin" to LevelControl.TINNITUS, "tinnitus" to LevelControl.TINNITUS)) {
            for ((direction, delta) in listOf("up" to 1, "down" to -1)) {
                val verb = if (delta > 0) "increase|raise" else "decrease|lower"
                if (Regex("^(?:$label $direction|turn (?:the |my )?$label $direction|turn $direction (?:the |my )?$label|(?:$verb) (?:the |my )?$label)$").matches(text))
                    return AppIntent.RelativeLevel(control, delta)
            }
        }
        when (text) {
            "cancel", "stop" -> return AppIntent.Cancel
            "current program", "current mode", "what program", "what mode", "what is the current program" -> return AppIntent.ReadProgram
            "list programs", "what programs" -> return AppIntent.ListPrograms
            "save settings" -> return AppIntent.SaveSettings
            "what is saved", "read saved settings", "what settings are saved", "show saved settings" -> return AppIntent.ReadSavedSettings
            "restore settings" -> return AppIntent.RestoreSettings
            "refresh", "refresh settings", "refresh current settings", "refresh hearing aids", "refresh aids" -> return AppIntent.Refresh
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
        return SavedSettings.snapshot(requested,fitting).also { snapshot = it }
    }
    fun restorePlan(): List<ManualRequest> = SavedSettings.plan(requireNotNull(snapshot) { "No saved settings" },fitting)
}
