package com.heysig.noise

/** Raw model scores, not calibrated probabilities or permission to switch programs. */
data class TelevisionEvidence(val frames:Int, val mean:Double, val peak:Double,
    val radioMean:Double, val speechMean:Double, val musicMean:Double)

object TelevisionSignal {
    val labels=setOf("Television","Radio","Speech","Music")
    /** Missing optional labels in older phone/watch evidence must not mean TV is absent. */
    fun summarize(frames:List<Map<String,Double>>):TelevisionEvidence? {
        if(frames.size !in 3..32 || frames.any { f -> labels.any { label ->
            val value=f[label]; value==null || !value.isFinite() || value !in 0.0..1.0
        } }) return null
        fun mean(label:String)=frames.map { it.getValue(label) }.average()
        return TelevisionEvidence(frames.size,mean("Television"),frames.maxOf { it.getValue("Television") },
            mean("Radio"),mean("Speech"),mean("Music"))
    }
}
