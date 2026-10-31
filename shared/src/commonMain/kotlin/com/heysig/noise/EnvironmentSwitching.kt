package com.heysig.noise

enum class ListeningEnvironment(val id: Int, val label: String) {
    QUIET(1,"Quiet"), CONVERSATION(2,"Conversation"), SMALL_GROUP(3,"Small Group"),
    RESTAURANT(4,"Restaurant"), LARGE_GROUP(5,"Large Group"), NON_SPEECH_NOISE(6,"Non-Speech Noise")
}

/** Corpus/model and device acceptance is independent of legacy level-based physical acceptance. */
object EnvironmentReleaseGate {
    const val CLASSIFIER_ACCEPTANCE_PASSED=false
    fun qualified()=CLASSIFIER_ACCEPTANCE_PASSED && NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED
}

/** Bind labels too: replacing or renaming a fitting never silently redirects automation. */
data class EnvironmentProgram(val slot: Int, val name: String) {
    init { require(slot in 1..6 && name.isNotBlank() && name == name.trim()) }
}
data class SamplingSchedule(val intervalSeconds: Int = 30, val durationSeconds: Int = 3, val timeoutMinutes: Int = 30) {
    init {
        require(intervalSeconds in listOf(15,30,60))
        require(durationSeconds in listOf(3,5,10))
        require(timeoutMinutes in listOf(0,15,30,60))
    }
    fun expired(start: Long, now: Long) = now < start || (timeoutMinutes != 0 && now-start >= timeoutMinutes*60_000L)
}
data class EnvironmentSettings(
    val assignments: List<EnvironmentProgram?> = List(6) { null },
    val enabled: Boolean = false,
    val cooldownSeconds: Int = 120,
    val stabilitySeconds: Int = 15,
    val restartAfterManual: Boolean = true,
    val overrideSeconds: Int = 120,
    val minimumConfidence: Double = 0.65,
    val schedule: SamplingSchedule = SamplingSchedule()
) {
    init {
        require(assignments.size == 6)
        require(cooldownSeconds in 0..600 && stabilitySeconds in 0..120 && overrideSeconds in 0..600)
        require(minimumConfidence.isFinite() && minimumConfidence in 0.5..1.0)
    }
}
data class EnvironmentClassification(val environment: ListeningEnvironment?, val confidence: Double, val explanation: String) {
    init { require(confidence.isFinite() && confidence in 0.0..1.0 && explanation.length <= 300) }
}

/** YAMNet event evidence, not speaker counting or a calibrated location probability. */
object EnvironmentClassifier {
    val requiredLabels = setOf("Speech","Conversation","Narration, monologue","Hubbub, speech noise, speech babble",
        "Crowd","Chatter","Silence","Vehicle","Traffic noise, roadway noise","Engine","Tools","Mechanisms",
        "Dishes, pots, and pans","Vacuum cleaner","Water","Wind","Mechanical fan","Music")
    fun classify(frames: List<Map<String, Double>>): EnvironmentClassification {
        fun unknown(why: String) = EnvironmentClassification(null,0.0,why)
        if(frames.size < 3 || frames.size > 32 || frames.any { frame ->
                frame.isEmpty() || frame.values.any { !it.isFinite() || it !in 0.0..1.0 }
            }) return unknown("Insufficient or invalid model evidence")
        fun mean(vararg labels: String) = frames.map { f -> labels.maxOf { f[it] ?: 0.0 } }.average()
        val speech = mean("Speech","Conversation","Narration, monologue")
        val conversation = mean("Conversation")
        val babble = mean("Hubbub, speech noise, speech babble")
        val crowd = mean("Crowd","Chatter")
        val quiet = mean("Silence")
        val noise = mean("Vehicle","Traffic noise, roadway noise","Engine","Tools","Mechanisms","Dishes, pots, and pans","Vacuum cleaner","Water","Wind","Mechanical fan")
        val music = mean("Music")
        // No amplitude ordering: large groups may be quieter than one speaker.
        val candidates = listOf(
            ListeningEnvironment.QUIET to if(speech < 0.2 && noise < 0.2 && music < 0.2) quiet else 0.0,
            ListeningEnvironment.CONVERSATION to if(babble < 0.25 && crowd < 0.25 && noise < 0.25 && music < 0.25) speech else 0.0,
            ListeningEnvironment.SMALL_GROUP to if(crowd < 0.35 && babble in 0.25..0.65) minOf(conversation,babble)*1.15 else 0.0,
            ListeningEnvironment.RESTAURANT to if(speech > 0.3 && babble > 0.3 && noise > 0.2) minOf(speech,babble,noise)*1.15 else 0.0,
            ListeningEnvironment.LARGE_GROUP to if(babble > 0.35) minOf(crowd,babble) else 0.0,
            ListeningEnvironment.NON_SPEECH_NOISE to if(speech < 0.2 && babble < 0.2 && crowd < 0.2 && music < 0.3) noise else 0.0
        ).sortedByDescending { it.second }
        val best = candidates[0]
        if(best.second < 0.5 || best.second-candidates[1].second < 0.15) return unknown("Ambiguous sound; keep current program")
        return EnvironmentClassification(best.first,best.second.coerceIn(0.0,1.0),
            "Experimental event-score mapping; group size and location are unverified")
    }
}

enum class SwitchingPhase { DISABLED, MONITORING, CLASSIFICATION_PENDING, STABILITY_WAIT, COOLDOWN, MANUAL_OVERRIDE, ERROR }
data class EnvironmentState(
    val monitoring: Boolean = false,
    val startedAt: Long = 0,
    val candidate: ListeningEnvironment? = null,
    val candidateSince: Long = 0,
    val lastSampleAt: Long? = null,
    val cooldownUntil: Long = 0,
    val overrideUntil: Long = 0,
    val pending: EnvironmentProgram? = null,
    val token: Long = 0,
    val phase: SwitchingPhase = SwitchingPhase.DISABLED,
    val reason: String = "Monitoring off"
) {
    fun remainingSeconds(now: Long) = ((cooldownUntil-now).coerceAtLeast(0)+999)/1000
}
data class EnvironmentDecision(val state: EnvironmentState, val target: EnvironmentProgram? = null)

/** No Android types, wall clock, calibration, playback, storage or implicit simulation. */
object EnvironmentSwitching {
    fun start(previous: EnvironmentState, now: Long) = previous.copy(monitoring=true,startedAt=now,
        candidate=null,lastSampleAt=null,phase=SwitchingPhase.MONITORING,reason="Waiting for classification")
    fun stop(previous: EnvironmentState) = previous.copy(monitoring=false,candidate=null,lastSampleAt=null,
        phase=SwitchingPhase.DISABLED,reason="Monitoring off")
    fun manual(previous: EnvironmentState, now: Long, settings: EnvironmentSettings): EnvironmentState = previous.copy(
        candidate=null,lastSampleAt=null,overrideUntil=now+settings.overrideSeconds*1000L,
        cooldownUntil=if(settings.restartAfterManual) now+settings.cooldownSeconds*1000L else previous.cooldownUntil,
        phase=SwitchingPhase.MANUAL_OVERRIDE,reason="Manual selection has priority")
    fun sample(previous: EnvironmentState, result: EnvironmentClassification, now: Long, settings: EnvironmentSettings,
               available: Set<EnvironmentProgram>, current: Int?, safe: Boolean, sleepSlots: Set<Int> = emptySet()): EnvironmentDecision {
        require(now >= 0)
        fun hold(phase: SwitchingPhase, reason: String, clear: Boolean = true) = EnvironmentDecision(previous.copy(
            phase=phase,reason=reason,candidate=if(clear) null else previous.candidate,lastSampleAt=if(clear) null else previous.lastSampleAt))
        if(!previous.monitoring) return hold(SwitchingPhase.DISABLED,"Monitoring off")
        if(settings.schedule.expired(previous.startedAt,now)) return EnvironmentDecision(stop(previous).copy(reason="Monitoring session expired"))
        if(previous.pending != null) return hold(SwitchingPhase.CLASSIFICATION_PENDING,"Waiting for playback completion")
        if(!safe || current == null || current in sleepSlots) return hold(SwitchingPhase.MANUAL_OVERRIDE,"Foreground, audio, manual or Sleep priority")
        if(now < previous.startedAt || previous.lastSampleAt?.let { now <= it } == true) return hold(SwitchingPhase.ERROR,"Invalid sample clock/order")
        val environment = result.environment
        if(environment == null || result.confidence < settings.minimumConfidence) return hold(SwitchingPhase.CLASSIFICATION_PENDING,"Uncertain classification; keeping current program")
        val target = settings.assignments[environment.ordinal]
            ?: return hold(SwitchingPhase.MONITORING,"${environment.label} is unassigned")
        if(target !in available) return hold(SwitchingPhase.ERROR,"Assigned program changed or is unavailable; review assignment")
        if(target.slot in sleepSlots) return hold(SwitchingPhase.MANUAL_OVERRIDE,"Sleep requires manual selection")
        val gap = settings.schedule.intervalSeconds*2000L
        val stable = previous.candidate == environment && previous.lastSampleAt?.let { now-it <= gap } == true
        val state = previous.copy(candidate=environment,candidateSince=if(stable) previous.candidateSince else now,lastSampleAt=now)
        fun wait(phase: SwitchingPhase, reason: String) = EnvironmentDecision(state.copy(phase=phase,reason=reason))
        // Monitoring and candidate evidence continue through cooldown and override.
        if(now < previous.overrideUntil) return wait(SwitchingPhase.MANUAL_OVERRIDE,"Manual override active")
        if(settings.cooldownSeconds > 0 && now < previous.cooldownUntil) return wait(SwitchingPhase.COOLDOWN,"Cooldown active")
        if(!settings.enabled) return wait(SwitchingPhase.MONITORING,"Automatic switching off; monitoring only")
        if(target.slot == current) return wait(SwitchingPhase.MONITORING,"Already last requested; no command")
        if(!stable || now-state.candidateSince < settings.stabilitySeconds*1000L) return wait(SwitchingPhase.STABILITY_WAIT,"Waiting for stable environment")
        check(state.token < Long.MAX_VALUE)
        return EnvironmentDecision(state.copy(pending=target,token=state.token+1,
            phase=SwitchingPhase.CLASSIFICATION_PENDING,reason="Stable classification; request program"),target)
    }
    fun complete(previous: EnvironmentState, token: Long, success: Boolean, now: Long, settings: EnvironmentSettings): EnvironmentState {
        require(now >= 0)
        if(previous.pending == null || previous.token != token) return previous
        return previous.copy(pending=null,candidate=null,lastSampleAt=null,
            cooldownUntil=if(success) now+settings.cooldownSeconds*1000L else previous.cooldownUntil,
            monitoring=previous.monitoring && success,phase=if(!success) SwitchingPhase.ERROR else if(settings.cooldownSeconds>0) SwitchingPhase.COOLDOWN else SwitchingPhase.MONITORING,
            reason=if(success) "Program command played; aid reception unconfirmed" else "Playback failed; monitoring stopped")
    }
}

sealed class EnvironmentCommand {
    data class Automatic(val enabled: Boolean): EnvironmentCommand()
    data class Monitor(val enabled: Boolean): EnvironmentCommand()
    data class Select(val environment: ListeningEnvironment): EnvironmentCommand()
    data class Cooldown(val seconds: Int): EnvironmentCommand()
    data object Status: EnvironmentCommand()
}
object EnvironmentVoice {
    fun parse(raw: String): EnvironmentCommand? {
        val text=raw.lowercase().trim().trimEnd('.','!','?').replace('’','\'').replace(Regex("\\s+")," ")
        when(text) {
            "automatic on" -> return EnvironmentCommand.Automatic(true)
            "automatic off" -> return EnvironmentCommand.Automatic(false)
            "start monitoring" -> return EnvironmentCommand.Monitor(true)
            "stop monitoring" -> return EnvironmentCommand.Monitor(false)
            "what's my status", "what is my status", "environment status" -> return EnvironmentCommand.Status
        }
        ListeningEnvironment.entries.firstOrNull {
            text == "${it.label.lowercase()} mode" || (it == ListeningEnvironment.NON_SPEECH_NOISE && text == "noise mode")
        }?.let { return EnvironmentCommand.Select(it) }
        // 'Normal' has no universal semantic meaning; resolve by configured program name in the existing parser.
        Regex("cooldown (zero|one|two|three|four|five|six|seven|eight|nine|ten|[0-9]+) minutes?").matchEntire(text)?.let {
            val words=listOf("zero","one","two","three","four","five","six","seven","eight","nine","ten")
            val n=it.groupValues[1].toIntOrNull() ?: words.indexOf(it.groupValues[1])
            if(n in 0..10) return EnvironmentCommand.Cooldown(n*60)
        }
        return null
    }
}
