package com.heysig.app

import com.heysig.noise.*

/** Pure Kotlin owns decisions; this Android adapter supplies the paired neural audio measurement. */
class SpeechSeparationDetector(private val original:SpeechMaskingHead,private val bands:SeparationMaskingBands,
    private val denoiser:NeuralSeparation=NeuralSeparation()) {
    init { require(original.featureCount==2052) }
    companion object {
        fun load(context:android.content.Context):SpeechSeparationDetector {
            fun asset(path:String,hash:String):ByteArray {
                val bytes=context.assets.open(path).use { it.readBytes() }
                check(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }==hash) { "Unexpected speech/background model" }
                return bytes
            }
            val head=asset("masking/masking-baseline.txt",MaskingModelIdentity.BASELINE_SHA256)
            val bands=asset("separation/bands.txt",SeparationModelIdentity.BANDS_SHA256)
            return SpeechSeparationDetector(SpeechMaskingHead.decode(head.toString(Charsets.UTF_8)),SeparationMaskingBands.decode(bands.toString(Charsets.UTF_8)))
        }
    }
    var latestMeasurement:SpeechNoiseEstimate?=null;private set
    fun classify(pcm:ShortArray,pooled:DoubleArray?,frames:List<Map<String,Double>>):MaskingEvidence {
        latestMeasurement=null
        val before=original.classify(pcm,pooled,frames)
        if(before.severity in listOf(MaskingSeverity.QUIET,MaskingSeverity.UNKNOWN)) return before
        val measurement=denoiser.measure(pcm);latestMeasurement=measurement
        return bands.corroborate(before,measurement)
    }
}
