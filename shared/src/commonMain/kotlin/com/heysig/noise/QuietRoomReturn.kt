package com.heysig.noise

/** Fixed quiet-room corroboration, not SPL calibration, learning or an Unknown timeout. */
object QuietRoomReturn {
    private val speechMedia=setOf("Speech","Conversation","Narration, monologue",
        "Hubbub, speech noise, speech babble","Crowd","Chatter","Music")
    private val activityLabels=EnvironmentClassifier.requiredLabels.filterNot { it=="Silence" }
    fun candidate(measurement:NoiseMeasurement,frames:List<Map<String,Double>>,speechFeatures:DoubleArray,
        lowFrequencyFraction:Double?=null):Boolean {
        if(measurement.sampleRate!=16000 || measurement.sampleCount!=48000L || measurement.frameCount!=3 ||
            measurement.flags.isNotEmpty() || !measurement.rmsDbfs.isFinite() ||
            measurement.rmsDbfs !in -120.0..-45.0 || !measurement.peakDbfs.isFinite() ||
            measurement.peakDbfs !in measurement.rmsDbfs..-30.0 || frames.size !in 3..32 ||
            speechFeatures.size!=4 || speechFeatures.any { !it.isFinite() || it !in 0.0..1.0 }) return false
        // Do not mistake soft sustained tones/music for silence. A low-frequency
        // hum may accompany room tone, but higher-pitched periodic sound holds.
        if(speechFeatures[2]>=.7 && (lowFrequencyFraction==null || !lowFrequencyFraction.isFinite() ||
                lowFrequencyFraction !in .7..1.0)) return false
        return frames.all { frame ->
            frame.keys.containsAll(EnvironmentClassifier.requiredLabels) &&
                frame.values.all { it.isFinite() && it in 0.0..1.0 } &&
                activityLabels.all {
                    val bound=if(it in speechMedia) .15 else .35
                    frame.getValue(it)<bound
                }
        }
    }

    fun corroborate(before:MaskingEvidence,neural:SpeechNoiseEstimate):MaskingEvidence {
        if(!before.quietCandidate || before.severity!=MaskingSeverity.UNKNOWN) return before
        val residual=neural.residualSpeechScore
        val agreed=neural.status==SeparationStatus.NO_RELIABLE_SPEECH &&
            neural.speechActivity.isFinite() && neural.speechActivity in 0.0..0.05 &&
            neural.speechScore.isFinite() && neural.speechScore in 0.0..0.15 &&
            residual!=null && residual.isFinite() && residual in 0.0..0.15
        return if(agreed) MaskingEvidence(MaskingSeverity.QUIET,before.soundType,
            reason="Quiet room: low level and both speech checks inactive; requires repeated checks")
        else MaskingEvidence(soundType=before.soundType,
            reason="Quiet return not confirmed by neural speech checks; keep current program")
    }
}

/** Temporary scalar for the tonal veto. No waveform/profile survives this call. */
object QuietRoomSignal {
    fun lowFrequencyFraction(pcm:ShortArray):Double {
        require(pcm.size==48000)
        val mean=pcm.sumOf { it.toDouble() }/pcm.size
        var low=0.0;var totalPower=0.0;var lowPower=0.0
        for(sample in pcm) {
            val value=sample-mean;low=.96*low+.04*value
            totalPower+=value*value;lowPower+=low*low
        }
        return if(totalPower>0) (lowPower/totalPower).coerceIn(0.0,1.0) else 0.0
    }
}
