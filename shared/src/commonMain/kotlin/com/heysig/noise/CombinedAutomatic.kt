package com.heysig.noise

enum class AutomaticScene(val label:String) {
    NORMAL("Normal"), NOISY("Noisy"), VERY_NOISY("Very Noisy"), TV("TV"), UNKNOWN("Uncertain")
}
fun AutomaticScene.matches(severity:MaskingSeverity)=when(this) {
    AutomaticScene.NORMAL -> severity in listOf(MaskingSeverity.QUIET,MaskingSeverity.CLEAR)
    AutomaticScene.NOISY -> severity==MaskingSeverity.MODERATE
    AutomaticScene.VERY_NOISY -> severity==MaskingSeverity.HEAVY
    AutomaticScene.TV -> severity==MaskingSeverity.CLEAR
    AutomaticScene.UNKNOWN -> severity==MaskingSeverity.UNKNOWN
}
enum class TelevisionPresence { PRESENT, ABSENT, UNKNOWN }

object CombinedAutomaticProfile {
    const val ID="combined-10s-no-tv-noise-memory-v4"
    const val CAPTURE_SECONDS=10
}

/** One disjoint three-second section. All measurements are relative digital levels. */
data class CombinedSection(val masking:MaskingEvidence,val separation:SpeechNoiseEstimate,
    val measurement:NoiseMeasurement,val noiseScore:Double,val musicScore:Double)

/** Fixed local event support, including broadband noise rather than speech alone. */
object CombinedNoiseEvents {
    val labels=setOf("Vehicle","Traffic noise, roadway noise","Engine","Tools","Mechanisms",
        "Dishes, pots, and pans","Vacuum cleaner","Water","Wind","Mechanical fan",
        "Noise","Environmental noise","Static","White noise","Pink noise","Hiss","Rustle")
    fun score(frames:List<Map<String,Double>>):Double {
        if(frames.size!=5 || frames.any { f -> !f.keys.containsAll(labels) ||
            labels.any { label -> !f.getValue(label).isFinite() || f.getValue(label) !in 0.0..1.0 } }) return Double.NaN
        return frames.map { f -> labels.maxOf { f.getValue(it) } }.average()
    }
}

object CombinedAutomatic {
    fun corrected(evidence:MaskingEvidence,learned:AutomaticScene?):MaskingEvidence {
        // TV corrections from older versions remain stored for compatibility, but
        // TV is no longer an Automatic destination. Strong measured noise wins.
        if(learned==null || learned in listOf(AutomaticScene.UNKNOWN,AutomaticScene.TV) ||
            evidence.scene in listOf(AutomaticScene.NOISY,AutomaticScene.VERY_NOISY))
            return evidence
        return combine(List(3) { evidence.severity },when(evidence.scene) {
            AutomaticScene.TV -> TelevisionPresence.UNKNOWN
            AutomaticScene.NORMAL -> TelevisionPresence.ABSENT
            else -> TelevisionPresence.UNKNOWN
        },learned).copy(soundContext=evidence.soundContext)
    }
    fun section(value:CombinedSection,settings:NoiseSettings,bands:SeparationMaskingBands):MaskingSeverity {
        if(settings.boundaries.size!=5 || settings.boundaries.any { !it.isFinite() } ||
            !settings.margin.isFinite() || settings.margin<=0) return MaskingSeverity.UNKNOWN
        val m=value.measurement;val n=value.separation
        if(m.sampleRate!=16000 || m.sampleCount!=48000L || m.flags.isNotEmpty() ||
            listOf(m.rmsDbfs,m.backgroundProxyDbfs,m.peakDbfs).any { !it.isFinite() || it !in -120.0..0.0 } ||
            listOf(value.noiseScore,value.musicScore,n.speechScore,n.speechActivity).any { !it.isFinite() || it !in 0.0..1.0 })
            return MaskingSeverity.UNKNOWN
        if(value.masking.severity==MaskingSeverity.QUIET) return MaskingSeverity.QUIET
        if(m.peakDbfs-m.rmsDbfs>24) return MaskingSeverity.UNKNOWN
        val noSpeech=n.status==SeparationStatus.NO_RELIABLE_SPEECH && n.speechScore<.2 && n.speechActivity<.1
        if(noSpeech && value.musicScore<.15) {
            // An independent low-level return uses actual measurements, never time spent uncertain.
            if(m.backgroundProxyDbfs<=settings.boundaries[0] && m.peakDbfs<=settings.boundaries[1])
                return MaskingSeverity.QUIET
            val level=1+settings.boundaries.count { m.backgroundProxyDbfs>it+settings.margin }
            if(value.noiseScore>=.5 || level<=3)
                return when { level>=6 -> MaskingSeverity.HEAVY;level>=4 -> MaskingSeverity.MODERATE;else -> MaskingSeverity.CLEAR }
        }
        if(value.musicScore>=.15) return MaskingSeverity.UNKNOWN
        // The phone-side suppression estimate is the primary staged noise signal.
        // The trained speech classifier is a conservative fallback when the
        // reduction band cannot decide; neither path measures aid cancellation.
        val reduction=bands.severity(n)
        if(reduction!=MaskingSeverity.UNKNOWN) return reduction
        if(value.masking.severity in listOf(MaskingSeverity.CLEAR,MaskingSeverity.MODERATE,MaskingSeverity.HEAVY) &&
            value.masking.score>=.65) return value.masking.severity
        return MaskingSeverity.UNKNOWN
    }

    fun combine(sections:List<MaskingSeverity>,@Suppress("UNUSED_PARAMETER") television:TelevisionPresence,
                learned:AutomaticScene?=null,captureValid:Boolean=true):MaskingEvidence {
        if(!captureValid) return MaskingEvidence(reason="Unusable microphone measurement; keep current program",scene=AutomaticScene.UNKNOWN)
        if(sections.size!=3) return MaskingEvidence(reason="Incomplete ten-second sound check")
        fun votes(level:MaskingSeverity)=sections.count { it==level }
        val severity=when {
            votes(MaskingSeverity.HEAVY)>=2 -> MaskingSeverity.HEAVY
            votes(MaskingSeverity.MODERATE)>=2 -> MaskingSeverity.MODERATE
            votes(MaskingSeverity.QUIET)>=2 -> MaskingSeverity.QUIET
            sections.count { it==MaskingSeverity.CLEAR || it==MaskingSeverity.QUIET }>=2 -> MaskingSeverity.CLEAR
            else -> MaskingSeverity.UNKNOWN
        }
        // Noise level decides between the three saved noise programs. TV presence
        // is deliberately ignored; clear or quiet evidence means Universal.
        val scene=when {
            severity==MaskingSeverity.HEAVY -> AutomaticScene.VERY_NOISY
            severity==MaskingSeverity.MODERATE -> AutomaticScene.NOISY
            severity in listOf(MaskingSeverity.QUIET,MaskingSeverity.CLEAR) -> AutomaticScene.NORMAL
            learned!=null && learned !in listOf(AutomaticScene.UNKNOWN,AutomaticScene.TV) -> learned
            else -> AutomaticScene.UNKNOWN
        }
        val output=when(scene) {
            AutomaticScene.VERY_NOISY -> MaskingSeverity.HEAVY
            AutomaticScene.NOISY -> MaskingSeverity.MODERATE
            AutomaticScene.TV -> MaskingSeverity.UNKNOWN
            AutomaticScene.NORMAL -> if(severity==MaskingSeverity.QUIET) severity else MaskingSeverity.CLEAR
            AutomaticScene.UNKNOWN -> MaskingSeverity.UNKNOWN
        }
        val reason=if(scene==AutomaticScene.UNKNOWN) "Noise evidence is uncertain; keep current program"
            else if(learned==scene && severity==MaskingSeverity.UNKNOWN)
                "Similar to a correction you explicitly remembered"
            else "Ten-second noise evidence: "+scene.label
        return MaskingEvidence(output,score=if(output==MaskingSeverity.UNKNOWN) 0.0 else .9,reason=reason,scene=scene)
    }
}
