package com.heysig.app

import com.heysig.noise.*

/** Pure Kotlin owns decisions; this phone adapter supplies the paired neural audio measurement. */
class SpeechSeparationDetector(private val original:SpeechMaskingHead,private val bands:SeparationMaskingBands,
    private val denoiser:NeuralSeparation=NeuralSeparation()) {
    init { require(original.featureCount==2052) }
    var latestMeasurement:SpeechNoiseEstimate?=null;private set
    fun classify(pcm:ShortArray,pooled:DoubleArray?,frames:List<Map<String,Double>>):MaskingEvidence {
        latestMeasurement=null
        val before=original.classify(pcm,pooled,frames)
        if(before.severity in listOf(MaskingSeverity.QUIET,MaskingSeverity.UNKNOWN)) return before
        val measurement=denoiser.measure(pcm);latestMeasurement=measurement
        return bands.corroborate(before,measurement)
    }
}
