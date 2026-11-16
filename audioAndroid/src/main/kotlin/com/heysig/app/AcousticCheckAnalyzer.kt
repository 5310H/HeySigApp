package com.heysig.app

import android.content.Context
import com.heysig.audio.Yamnet
import com.heysig.noise.*

/** Explicit foreground diagnostics only. The capture worker retains ownership and erases PCM. */
class AcousticCheckAnalyzer(private val context:Context) {
    fun analyze(pcm:ShortArray,frames:List<Map<String,Double>>,cancelled:()->Boolean):AcousticCheckResult {
        require(pcm.size==160000)
        check(!cancelled()) { "Sound check cancelled" }
        val detector=SpeechSeparationDetector.load(context)
        val bands=context.assets.open("separation/bands.txt").bufferedReader().use { SeparationMaskingBands.decode(it.readText()) }
        val windows=mutableListOf<AcousticCheckWindow>()
        // Each section gets its own correctly pooled embeddings and matched neural reference.
        // Do not pass ten-second embeddings to the existing three-second masking head.
        for(start in listOf(0,48000,96000)) {
            check(!cancelled()) { "Sound check cancelled" }
            val section=pcm.copyOfRange(start,start+48000)
            try {
                var evidence=MaskingEvidence()
                Yamnet(context,observeEvidence={ input,features,events ->
                    evidence=detector.classify(input,features,events)
                }).use { it.classify(section,cancelled) }
                check(!cancelled()) { "Sound check cancelled" }
                // The live detector may not run separation when its first classifier abstains.
                // Measure that section anyway so the check exposes this disagreement.
                val neural=detector.latestMeasurement ?: NeuralSeparation().measure(section)
                windows+=AcousticCheckWindow(evidence.severity,neural,bands.severity(neural))
            } finally { section.fill(0) }
        }
        check(!cancelled()) { "Sound check cancelled" }
        return AcousticCheckResult(TelevisionSignal.summarize(frames),windows)
    }
}
