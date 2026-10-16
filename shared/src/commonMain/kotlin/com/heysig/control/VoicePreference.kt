package com.heysig.control

data class VoicePreference(val prompts: Boolean, val enabled: Boolean) {
    val key: String get() = if(prompts) "voicePrompts" else "spokenResponses"
    val description: String get() = "Voice ${if(prompts) "prompts" else "responses"} ${if(enabled) "on" else "off"}"
    companion object {
        fun parse(raw: String): VoicePreference? {
            val text = raw.lowercase().trim().trimEnd('.','!','?').replace(Regex("\\s+")," ")
                .replace(Regex("^turn (on|off) ((?:voice|spoken) (?:prompts|responses))$"),"$2 $1")
            val match = Regex("(?:turn |set )?(?:voice |spoken )(prompts|responses) (on|off)").matchEntire(text) ?: return null
            return VoicePreference(match.groupValues[1] == "prompts", match.groupValues[2] == "on")
        }
    }
}
