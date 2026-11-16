package com.heysig.noise

/** A bounded display-only check; no saved fitting, program target or executor dependency. */
data class AcousticCheckWindow(val masking:MaskingSeverity, val separation:SpeechNoiseEstimate,
    val reductionSeverity:MaskingSeverity)

data class AcousticCheckResult(val television:TelevisionEvidence?, val windows:List<AcousticCheckWindow>) {
    init { require(windows.size==3) }
    /** Three disjoint three-second sections of a ten-second capture; not repeated live confirmations. */
    fun agreement(reductionOnly:Boolean=false):MaskingSeverity {
        val votes=windows.map { if(reductionOnly) it.reductionSeverity else it.masking }
        return MaskingSeverity.entries.firstOrNull { it!=MaskingSeverity.UNKNOWN && votes.count { v -> v==it }>=2 }
            ?: MaskingSeverity.UNKNOWN
    }
}
