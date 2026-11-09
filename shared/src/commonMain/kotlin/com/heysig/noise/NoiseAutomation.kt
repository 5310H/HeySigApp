package com.heysig.noise

/** Scalar calibration only: three bounded captures in each of six user-chosen environments. */
data class NoiseCalibration(val levels: List<List<Double>> = List(6) { emptyList() }) {
    init {
        require(levels.size == 6 && levels.all { row -> row.size <= 3 && row.all { it.isFinite() && it in -120.0..0.0 } })
    }
    fun add(level: Int, measurement: NoiseMeasurement): NoiseCalibration {
        require(level in 1..6 && measurement.flags.isEmpty()) { "Use a valid, uninterrupted sample" }
        require(levels[level-1].size < 3) { "This level already has three samples" }
        return copy(levels = levels.mapIndexed { i,row -> if(i == level-1) row + measurement.rmsDbfs else row })
    }
    fun thresholds(margin: Double): List<Double> {
        require(margin.isFinite() && margin > 0)
        require(levels.all { it.size == 3 }) { "Capture three samples for each of six levels" }
        // Entire measured ranges must remain apart across both hysteresis edges.
        require(levels.zipWithNext().all { (a,b) -> b.min() - a.max() > 2*margin }) {
            "Measured levels overlap or are too close. Recheck environments; do not increase sound to unsafe levels."
        }
        return levels.zipWithNext().map { (a,b) -> (a.max()+b.min())/2 }
    }
}

/** Bound to the capture configuration and microphone source; editing either invalidates it. */
data class NoiseCalibrationProfile(val samples: NoiseCalibration, val source: String, val captureSeconds: Int, val margin: Double) {
    fun matches(settings: NoiseSettings, currentSource: String): Boolean = runCatching {
        source.isNotBlank() && source == currentSource && captureSeconds == settings.captureSeconds &&
            margin == settings.margin && samples.thresholds(margin) == settings.boundaries
    }.getOrDefault(false)
}

/** One-minute review bound to the exact samples, draft and fitting; no persistence or audio. */
data class NoiseCalibrationReview(val draft: NoiseSettings,val profile: NoiseCalibrationProfile,val fitting: String,val reviewedAt: Long) {
    fun apply(current: NoiseSettings,samples: NoiseCalibration,source: String,currentFitting: String,now: Long): NoiseSettings {
        require(reviewedAt>=0 && now>=reviewedAt && now-reviewedAt<60_000L) { "Calibration review expired; review again" }
        require(current==draft && samples==profile.samples && source==profile.source && source.isNotBlank() && currentFitting==fitting && profile.captureSeconds==current.captureSeconds && profile.margin==current.margin) { "Calibration changed; review again" }
        return current.copy(boundaries=samples.thresholds(current.margin))
    }
}

/** Validate delivery age using monotonic timestamps supplied by the platform. */
fun isFreshNoiseSample(capturedAt: Long, deliveredAt: Long, freshnessSeconds: Int): Boolean =
    freshnessSeconds in 2..900 && capturedAt >= 0 && deliveredAt >= capturedAt &&
        deliveredAt-capturedAt <= freshnessSeconds*1000L

/** Runtime priorities; calibration is optional and acceptance is recorded separately. */
data class NoiseSafety(
    val foreground: Boolean = false,
    val unlocked: Boolean = false,
    val audioFree: Boolean = false,
    val manualOverride: Boolean = false,
    val sleeping: Boolean = false,
    val protectedPrograms: Set<Int> = emptySet(),
    /** Explicit, visible-notification microphone service; never a persisted enable flag. */
    val microphoneSession: Boolean = false
) {
    fun blocked(): String? = when {
        (!foreground || !unlocked) && !microphoneSession -> "Keep HeySig open and the device unlocked"
        !audioFree -> "Speech, playback or a pending command has priority"
        manualOverride -> "Manual override has priority; restart explicitly"
        sleeping -> "Therapy has priority; choose a waking program manually before restarting"
        else -> null
    }
}

data class NoiseAutomaticState(
    val observation: NoiseObservation = NoiseObservation(),
    val lastSuccessMillis: Long? = null,
    val pendingTarget: Int? = null,
    val token: Long = 0,
    val halted: Boolean = false
)
data class NoiseDecision(val state: NoiseAutomaticState, val target: Int? = null, val reason: String)

/** Pure reducer: target is a request for the adapter, never direct audio playback. */
fun automaticNoise(previous: NoiseAutomaticState, index: Double?, now: Long, settings: NoiseSettings,
                   safety: NoiseSafety, lastRequested: Int?): NoiseDecision {
    require(now >= 0)
    fun blocked(reason:String) = NoiseDecision(previous.copy(observation=NoiseObservation()),reason=reason)
    safety.blocked()?.let { return blocked(it) }
    if(lastRequested == null) return blocked("Last requested program is unknown; select a waking program manually first")
    if((settings.sleepProgram != null && lastRequested == settings.sleepProgram) || lastRequested in safety.protectedPrograms) return blocked("Therapy has priority; choose a waking program manually")
    if(previous.halted) return blocked("Previous playback failed or was interrupted; restart explicitly")
    if(previous.pendingTarget != null) return blocked("Waiting for the current command result")
    if(index == null || !index.isFinite()) return blocked("Sample rejected; confirmations cleared")
    val last = previous.lastSuccessMillis
    if(last != null && (now < last || now-last < settings.cooldownSeconds*1000L)) return blocked("Cooldown; confirmations cleared")
    val observed = observeNoise(previous.observation,index,now,settings)
    if((settings.sleepProgram != null && observed.target == settings.sleepProgram) || observed.target?.let { it in safety.protectedPrograms } == true) return blocked("Therapy requires manual selection; no automatic request")
    val state = previous.copy(observation=observed)
    val target = observed.proposed ?: return NoiseDecision(state,reason=if(observed.target == null) "Level ${observed.level} has no assigned program; keeping current program" else "Confirming target")
    if(target == lastRequested) return NoiseDecision(state,reason="Already last requested; no command")
    check(previous.token < Long.MAX_VALUE)
    return NoiseDecision(state.copy(pendingTarget=target,token=previous.token+1),target,"Confirmed target")
}

/** Failed/late results cannot establish a new baseline or enable another automatic request. */
fun completeNoise(previous: NoiseAutomaticState, token: Long, success: Boolean, now: Long): NoiseAutomaticState {
    require(now >= 0)
    if(previous.pendingTarget == null || token != previous.token) return previous
    return previous.copy(observation=NoiseObservation(),pendingTarget=null,
        lastSuccessMillis=if(success) now else previous.lastSuccessMillis,halted=!success)
}
