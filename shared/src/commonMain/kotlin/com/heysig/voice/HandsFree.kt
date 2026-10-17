package com.heysig.voice

/** Controls are parsed separately from aid commands: disabling listening cannot select a program. */
enum class HandsFreeControl {
    ON, OFF, PAUSE, STATUS, END;
    companion object {
        fun parse(raw: String): HandsFreeControl? = when(raw.lowercase().trim().trimEnd('.','!','?').replace(Regex("\\s+")," ")) {
            "hands free on", "hands-free on", "turn hands free on", "turn listening on", "start listening", "turn on listening", "resume listening" -> ON
            "hands free off", "hands-free off", "turn hands free off", "turn listening off", "stop listening", "turn off listening" -> OFF
            "pause listening", "pause for 30 minutes", "pause listening for 30 minutes" -> PAUSE
            "hands free status", "hands-free status", "listening status" -> STATUS
            "end hands free session", "end hands-free session" -> END
            else -> null
        }
    }
}

/** Monotonic, memory-only session. Off invalidates every in-flight wake/recognition result. */
class HandsFreeSession {
    enum class Mode { OFF, WAKE, COMMAND, PAUSED }
    var mode = Mode.OFF; private set
    var generation = 0L; private set
    var resumeAt = 0L; private set
    fun on() { generation++; resumeAt=0; mode=Mode.WAKE }
    fun off() { generation++; resumeAt=0; mode=Mode.OFF }
    fun pause(now: Long) { require(now >= 0); generation++; resumeAt=now+30*60*1000; mode=Mode.PAUSED }
    fun resume(now: Long, token: Long): Boolean {
        if(token != generation || mode != Mode.PAUSED || now < resumeAt) return false
        on(); return true
    }
    fun wake(token: Long): Boolean {
        if(token != generation || mode != Mode.WAKE) return false
        generation++;mode=Mode.COMMAND; return true
    }
    fun complete(token: Long): Boolean {
        if(token != generation || mode != Mode.COMMAND) return false
        generation++;mode=Mode.WAKE; return true
    }
    fun accepts(token: Long) = token == generation && mode == Mode.COMMAND
}

object HandsFreeCompanion {
    const val PATH="/heysig/handsfree/v1"
    const val CAPABILITY="heysig_handsfree_v1"
    fun identity(text: String,fit: com.heysig.control.Fitting?): Any =
        HandsFreeControl.parse(text) ?: com.heysig.control.VoicePreference.parse(text) ?:
        if(fit!=null) com.heysig.control.VoiceCommands.parse(text,fit) else com.heysig.control.AppIntent.Unsupported("Save aid setup first")
}
