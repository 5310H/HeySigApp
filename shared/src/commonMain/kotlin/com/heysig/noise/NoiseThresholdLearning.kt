package com.heysig.noise

import kotlin.math.abs
import kotlin.math.round

/** Local scalar observations, bound to the settings and microphone that produced them. */
data class NoiseLearningContext(val settings: NoiseSettings, val source: String, val fitting: String)

data class NoiseThresholdSuggestion(
    val context: NoiseLearningContext,
    val boundaries: List<Double>,
    val counts: List<Int>,
    val revision: Long,
    val reviewedAt: Long
) {
    fun approved(current: NoiseLearningContext, currentRevision: Long, now: Long): NoiseSettings {
        require(current == context && currentRevision == revision) { "Noise settings or measurements changed; review again" }
        require(now >= reviewedAt && now - reviewedAt < 60_000) { "Threshold review expired; review again" }
        require(boundaries.size==5 && counts.size==6 && boundaries.indices.all { i ->
            boundaries[i]==context.settings.boundaries[i] ||
                (counts[i]>=NoiseThresholdLearner.MIN_PER_LEVEL && counts[i+1]>=NoiseThresholdLearner.MIN_PER_LEVEL &&
                    abs(boundaries[i]-context.settings.boundaries[i]) in NoiseThresholdLearner.MIN_CHANGE..NoiseThresholdLearner.MAX_SHIFT)
        }) { "Suggested boundaries lack evidence or exceed the adjustment limit" }
        return context.settings.copy(boundaries=boundaries).also {
            it.validate((it.programs.filterNotNull() + listOfNotNull(it.sleepProgram)).toSet())
        }
    }
}

/** No audio, labels, files or automatic application. A suggestion is not calibration proof. */
class NoiseThresholdLearner(val context: NoiseLearningContext) {
    init {
        require(context.source.isNotBlank() && context.fitting.isNotBlank())
        context.settings.validate((context.settings.programs.filterNotNull()+listOfNotNull(context.settings.sleepProgram)).toSet())
    }
    private data class Sample(val index: Double, val at: Long)
    private val samples=mutableListOf<Sample>()
    var revision=0L; private set
    val sampleCount get()=samples.size
    private var lastAt: Long?=null

    fun add(index: Double, capturedAt: Long) {
        if(!index.isFinite() || index !in -120.0..0.0 || capturedAt < 0 ||
            lastAt?.let { capturedAt <= it } == true) return
        samples.add(Sample(index,capturedAt));lastAt=capturedAt;revision++
        samples.removeAll { capturedAt-it.at > MAX_AGE }
        if(samples.size > MAX_SAMPLES) samples.removeAt(0)
    }

    fun counts(): List<Int> = groups().map { it.size }
    private fun groups() = (0..5).map { level -> samples.filter {
        context.settings.boundaries.count { threshold -> it.index > threshold } == level
    }.map { it.index }.sorted() }

    fun suggest(now: Long): NoiseThresholdSuggestion? {
        if(samples.isEmpty() || now < samples.last().at || now-samples.last().at > MAX_AGE ||
            samples.last().at-samples.first().at < MIN_SPAN) return null
        val groups=groups()
        val settings=context.settings
        val proposed=settings.boundaries.toMutableList()
        for(i in 0..4) {
            val lower=groups[i];val upper=groups[i+1]
            if(lower.size < MIN_PER_LEVEL || upper.size < MIN_PER_LEVEL) continue
            // Use the middle half of each distribution so isolated extremes do not set a boundary.
            val low=lower[(lower.lastIndex*3)/4];val high=upper[upper.lastIndex/4]
            if(high-low <= settings.margin*2) continue
            val candidate=(round(((low+high)/2).coerceIn(proposed[i]-MAX_SHIFT,proposed[i]+MAX_SHIFT)*10)/10)
                .coerceIn(proposed[i]-MAX_SHIFT,proposed[i]+MAX_SHIFT)
            if(abs(candidate-proposed[i]) >= MIN_CHANGE) proposed[i]=candidate
        }
        if(proposed==settings.boundaries) return null
        if(runCatching { settings.copy(boundaries=proposed).validate(
                (settings.programs.filterNotNull()+listOfNotNull(settings.sleepProgram)).toSet()) }.isFailure) return null
        return NoiseThresholdSuggestion(context,proposed,groups.map { it.size },revision,now)
    }

    companion object {
        const val MAX_SAMPLES=240
        const val MIN_PER_LEVEL=10
        const val MIN_SPAN=5*60*1000L
        const val MAX_AGE=60*60*1000L
        const val MAX_SHIFT=2.0
        const val MIN_CHANGE=0.5
    }
}
