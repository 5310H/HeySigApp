package com.heysig.app

import android.content.Context
import com.heysig.audio.Yamnet
import com.heysig.noise.*
import java.security.MessageDigest

/** One bounded ten-second capture supplies staged noise and speech-masking evidence. */
class CombinedSoundAnalyzer(private val context:Context) {
    private fun asset(path:String,hash:String):String {
        val bytes=context.assets.open(path).use { it.readBytes() }
        check(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }==hash) { "Unexpected combined sound model" }
        return bytes.toString(Charsets.UTF_8)
    }
    private val detector by lazy { SpeechSeparationDetector.load(context) }
    private val bands by lazy { SeparationMaskingBands.decode(asset("separation/bands.txt",SeparationModelIdentity.BANDS_SHA256)) }
    var detail="";private set
    var lastSections:List<CombinedSection> = emptyList();private set
    fun classify(pcm:ShortArray,embedding:DoubleArray?,frames:List<Map<String,Double>>,settings:NoiseSettings,
        cancelled:()->Boolean = { false }):MaskingEvidence {
        require(pcm.size==160000)
        lastSections=emptyList()
        check(!cancelled()) { "Sound check cancelled" }
        val sections=mutableListOf<CombinedSection>()
        var input:CombinedSection?=null
        // Reuse one stateless encoder for all three matched sections. No audio is retained.
        Yamnet(context,observeEvidence={ sound,features,events ->
            val masking=detector.classify(sound,features,events)
            val neural=detector.latestMeasurement ?: NeuralSeparation().measure(sound)
            val measurement=NoiseAccumulator(16000,3).apply { add(sound,sound.size) }.measurement()
            input=CombinedSection(masking,neural,measurement,CombinedNoiseEvents.score(events),
                events.map { it.getValue("Music") }.average())
        }).use { encoder ->
            for(start in listOf(0,48000,96000)) {
                check(!cancelled()) { "Sound check cancelled" }
                val section=pcm.copyOfRange(start,start+48000)
                try {
                    input=null
                    encoder.classify(section,cancelled)
                    sections+=checkNotNull(input) { "Incomplete sound section" }
                } finally { section.fill(0) }
            }
        }
        check(!cancelled()) { "Sound check cancelled" }
        val levels=sections.map { CombinedAutomatic.section(it,settings,bands) }
        lastSections=sections.toList()
        val measurement=NoiseAccumulator(16000,10).apply { add(pcm,pcm.size) }.measurement()
        val neural=sections.map { it.separation }
        fun scaledDb(v:Double)=((v+100)/100).coerceIn(0.0,1.0)
        fun level(v:MaskingSeverity)=when(v) {
            MaskingSeverity.QUIET -> 0.0
            MaskingSeverity.CLEAR -> .2
            MaskingSeverity.MODERATE -> .6
            MaskingSeverity.HEAVY -> 1.0
            MaskingSeverity.UNKNOWN -> .5
        }
        val scalars=listOf(scaledDb(measurement.rmsDbfs),neural.map { it.speechScore }.average(),
            sections.map { it.noiseScore }.average(),sections.map { it.musicScore }.average(),levels.map(::level).average(),
            ((neural.mapNotNull { it.retainedToResidualDb }.average().takeIf { it.isFinite() } ?: 0.0)+40).div(80).coerceIn(0.0,1.0),
            neural.map { it.speechActivity }.average(),scaledDb(measurement.backgroundProxyDbfs))
        val soundContext=AutomaticSoundContext.create(embedding,scalars)
        detail="Noise sections: "+levels.joinToString { it.label }+". Ten seconds analyzed together."
        return CombinedAutomatic.combine(levels,TelevisionPresence.UNKNOWN,captureValid=measurement.flags.isEmpty())
            .copy(soundContext=if(measurement.flags.isEmpty()) soundContext else null)
    }
}
