package com.heysig.noise

/** Development-calibrated speech/residual bands, not wearer calibration or clinical SNR.
 * A neural denoiser can remove wanted speech or retain competing talkers. Its ratio
 * may corroborate the original masking detector; it cannot replace speech evidence.
 */
data class SeparationMaskingBands(val heavyMaximumDb:Double,val clearMinimumDb:Double,val marginDb:Double=2.0) {
    init {
        require(heavyMaximumDb.isFinite() && heavyMaximumDb in -40.0..20.0)
        require(clearMinimumDb.isFinite() && clearMinimumDb in -20.0..40.0)
        require(marginDb.isFinite() && marginDb in 0.0..10.0 && clearMinimumDb-heavyMaximumDb>2*marginDb)
    }
    fun severity(estimate:SpeechNoiseEstimate):MaskingSeverity {
        val ratio=estimate.retainedToResidualDb
        if(estimate.status !in listOf(SeparationStatus.ESTIMATED,SeparationStatus.SPEECH_IN_RESIDUAL) ||
            ratio==null || !ratio.isFinite() || ratio !in -80.0..80.0 ||
            !estimate.speechScore.isFinite() || estimate.speechScore !in .35..1.0 ||
            !estimate.speechActivity.isFinite() || estimate.speechActivity !in (20.0/280)..1.0)
            return MaskingSeverity.UNKNOWN
        return when {
            ratio<=heavyMaximumDb-marginDb -> MaskingSeverity.HEAVY
            ratio>=clearMinimumDb+marginDb -> MaskingSeverity.CLEAR
            ratio>=heavyMaximumDb+marginDb && ratio<=clearMinimumDb-marginDb -> MaskingSeverity.MODERATE
            else -> MaskingSeverity.UNKNOWN
        }
    }
    fun corroborate(original:MaskingEvidence,estimate:SpeechNoiseEstimate):MaskingEvidence {
        // Quiet has no speech/noise ratio; retain the independent strict quiet guard.
        if(original.severity in listOf(MaskingSeverity.QUIET,MaskingSeverity.UNKNOWN)) return original
        if(!original.score.isFinite() || original.score !in .65..1.0 || severity(estimate)!=original.severity)
            return MaskingEvidence(soundType=original.soundType,reason="Speech and separated-background estimates disagree; keep current program")
        return original.copy(reason=if(estimate.status==SeparationStatus.SPEECH_IN_RESIDUAL)
            "Masking estimates agree; removed sound includes speech-like activity, not verified pure noise"
            else "Speech and separated-background estimates agree; requires repeated fresh checks")
    }
    companion object {
        fun decode(text:String):SeparationMaskingBands {
            require(text.length in 10..200)
            val lines=text.trimEnd('\n').split('\n');require(lines.size==2 && lines[0]=="HSGSEPARATION1")
            val values=lines[1].split(',').map { it.toDouble() };require(values.size==3)
            return SeparationMaskingBands(values[0],values[1],values[2])
        }
    }
}
