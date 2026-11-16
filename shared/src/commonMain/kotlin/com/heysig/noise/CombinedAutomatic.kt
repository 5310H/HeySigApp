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
    const val ID="combined-10s-tv-7e9d09bb8726-memory-v1"
    const val CAPTURE_SECONDS=10
}

/** One disjoint three-second section. All measurements are relative digital levels. */
data class CombinedSection(val masking:MaskingEvidence,val separation:SpeechNoiseEstimate,
    val measurement:NoiseMeasurement,val noiseScore:Double,val musicScore:Double)

object CombinedAutomatic {
    fun corrected(evidence:MaskingEvidence,learned:AutomaticScene?):MaskingEvidence {
        if(learned==null || learned==AutomaticScene.UNKNOWN || evidence.scene in listOf(AutomaticScene.NOISY,AutomaticScene.VERY_NOISY))
            return evidence
        return combine(List(3) { evidence.severity },when(evidence.scene) {
            AutomaticScene.TV -> TelevisionPresence.PRESENT
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
        val noSpeech=n.status==SeparationStatus.NO_RELIABLE_SPEECH && n.speechScore<.2 && n.speechActivity<.1
        if(noSpeech && value.musicScore<.15) {
            // An independent low-level return uses actual measurements, never time spent uncertain.
            if(m.backgroundProxyDbfs<=settings.boundaries[0] && m.peakDbfs<=settings.boundaries[1])
                return MaskingSeverity.QUIET
            if(value.noiseScore>=.5) {
                val level=1+settings.boundaries.count { m.backgroundProxyDbfs>it+settings.margin }
                return when { level>=6 -> MaskingSeverity.HEAVY;level>=4 -> MaskingSeverity.MODERATE;else -> MaskingSeverity.CLEAR }
            }
        }
        val ratio=bands.severity(n)
        if(ratio!=MaskingSeverity.UNKNOWN && ratio==value.masking.severity) return ratio
        // Strong independently identified machinery/traffic can support a reduction estimate
        // even when the speech head abstains. Music and generic speech cannot do this.
        if(value.masking.severity==MaskingSeverity.UNKNOWN && value.noiseScore>=.6 && value.musicScore<.15)
            return ratio
        return MaskingSeverity.UNKNOWN
    }

    fun combine(sections:List<MaskingSeverity>,television:TelevisionPresence,learned:AutomaticScene?=null,captureValid:Boolean=true):MaskingEvidence {
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
        // Physical noise priority also applies to remembered TV/Normal corrections.
        val scene=when {
            severity==MaskingSeverity.HEAVY -> AutomaticScene.VERY_NOISY
            severity==MaskingSeverity.MODERATE -> AutomaticScene.NOISY
            learned!=null && learned!=AutomaticScene.UNKNOWN -> learned
            television==TelevisionPresence.PRESENT -> AutomaticScene.TV
            severity==MaskingSeverity.QUIET -> AutomaticScene.NORMAL
            severity==MaskingSeverity.CLEAR && television==TelevisionPresence.ABSENT -> AutomaticScene.NORMAL
            else -> AutomaticScene.UNKNOWN
        }
        val output=when(scene) {
            AutomaticScene.VERY_NOISY -> MaskingSeverity.HEAVY
            AutomaticScene.NOISY -> MaskingSeverity.MODERATE
            AutomaticScene.TV -> MaskingSeverity.CLEAR
            AutomaticScene.NORMAL -> if(severity==MaskingSeverity.QUIET) severity else MaskingSeverity.CLEAR
            AutomaticScene.UNKNOWN -> MaskingSeverity.UNKNOWN
        }
        val reason=if(scene==AutomaticScene.UNKNOWN) "TV/noise evidence is uncertain; keep current program"
            else if(learned==scene && severity !in listOf(MaskingSeverity.HEAVY,MaskingSeverity.MODERATE))
                "Similar to a correction you explicitly remembered"
            else "Ten-second TV and noise evidence: "+scene.label
        return MaskingEvidence(output,score=if(output==MaskingSeverity.UNKNOWN) 0.0 else .9,reason=reason,scene=scene)
    }
}
