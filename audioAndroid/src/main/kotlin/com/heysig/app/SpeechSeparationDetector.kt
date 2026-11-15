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
    var latestDiagnostics="";private set
    fun classify(pcm:ShortArray,pooled:DoubleArray?,frames:List<Map<String,Double>>):MaskingEvidence {
        latestMeasurement=null
        val before=original.classify(pcm,pooled,frames)
        fun report(result:MaskingEvidence):MaskingEvidence {
            fun number(value:Double?)=value?.let { "%.3f".format(java.util.Locale.US,it) } ?: "unavailable"
            val input=if(pcm.isNotEmpty() && pcm.size<=48000) NoiseAccumulator(16000,3).apply { add(pcm,pcm.size) }.measurement() else null
            fun maximum(labels:Set<String>)=frames.maxOfOrNull { frame -> labels.maxOf { frame[it] ?: 0.0 } }
            val speech=setOf("Speech","Conversation","Narration, monologue","Hubbub, speech noise, speech babble","Crowd","Chatter")
            val neural=latestMeasurement
            val television=TelevisionSignal.summarize(frames)
            latestDiagnostics="Last completed check: input ${number(input?.rmsDbfs)} dBFS, peak ${number(input?.peakDbfs)}; original ${before.severity}, flags ${input?.flags}."+
                "\nEvent speech ${number(maximum(speech))}, music ${number(maximum(setOf("Music")))}; quiet candidate ${before.quietCandidate}."+
                "\nNeural ${neural?.status ?: "not run"}: speech ${number(neural?.speechScore)}, activity ${number(neural?.speechActivity)}, residual speech ${number(neural?.residualSpeechScore)}, ratio ${number(neural?.retainedToResidualDb)}."+
                "\nTV model score: mean ${number(television?.mean)}, peak ${number(television?.peak)}; radio ${number(television?.radioMean)}. Diagnostic only; TV recognition does not switch programs yet."
            return result
        }
        if(before.quietCandidate) {
            val measurement=denoiser.measure(pcm);latestMeasurement=measurement
            return report(QuietRoomReturn.corroborate(before,measurement))
        }
        if(before.severity in listOf(MaskingSeverity.QUIET,MaskingSeverity.UNKNOWN)) return report(before)
        val measurement=denoiser.measure(pcm);latestMeasurement=measurement
        return report(bands.corroborate(before,measurement))
    }
}
