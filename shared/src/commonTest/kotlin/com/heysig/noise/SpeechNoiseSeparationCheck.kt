package com.heysig.noise

import kotlin.math.*

fun main() {
    val speech=FloatArray(300) { .9f };val nonSpeech=FloatArray(300) { .1f }
    fun pair(snr:Double,gain:Double=.03):Pair<FloatArray,FloatArray> {
        val scale=10.0.pow(-snr/20)
        val normalization=gain/sqrt((1+scale*scale)/2)
        val clean=FloatArray(48000) { (normalization*sin(2*PI*1000*it/16000)).toFloat() }
        val reference=FloatArray(48000) { (clean[it]+normalization*scale*sin(2*PI*3000*it/16000)).toFloat() }
        return reference to clean
    }
    for(snr in listOf(15.0,3.0,-8.0)) {
        val (reference,clean)=pair(snr)
        val m=SpeechNoiseSeparation.measure(reference,clean,speech,nonSpeech,true)
        check(m.status==SeparationStatus.ESTIMATED && abs(checkNotNull(m.retainedToResidualDb)-snr)<.0001)
        check(abs(sqrt(reference.sumOf { it.toDouble()*it }/reference.size)-.03)<1e-6)
        val quiet=SpeechNoiseSeparation.measure(FloatArray(48000) { reference[it]/2 },FloatArray(48000) { clean[it]/2 },speech,nonSpeech,true)
        check(abs(checkNotNull(quiet.retainedToResidualDb)-snr)<.0001)
        check(abs(checkNotNull(m.retainedDbfs)-checkNotNull(quiet.retainedDbfs)-20*log10(2.0))<1e-5)
        check(SpeechNoiseSeparation.measure(reference,clean,speech,speech,true).status==SeparationStatus.SPEECH_IN_RESIDUAL)
        check(SpeechNoiseSeparation.measure(reference,clean,nonSpeech,nonSpeech,true).retainedToResidualDb==null)
        check(SpeechNoiseSeparation.measure(reference,clean,speech,nonSpeech,false).status==SeparationStatus.INVALID)
        check(SpeechNoiseSeparation.measure(reference,FloatArray(47999),speech,nonSpeech,true).status==SeparationStatus.INVALID)
        reference[100]=Float.NaN
        check(SpeechNoiseSeparation.measure(reference,clean,speech,nonSpeech,true).status==SeparationStatus.INVALID)
        reference.fill(0f);clean.fill(0f)
    }
    val zero=FloatArray(48000)
    check(SpeechNoiseSeparation.measure(zero,zero,speech,nonSpeech,true).status==SeparationStatus.NO_RELIABLE_SPEECH)
    check(SpeechNoiseSeparation.measure(zero,zero,FloatArray(300) { 1.01f },nonSpeech,true).status==SeparationStatus.INVALID)
    val (reference,clean)=pair(15.0)
    val unchangedReference=reference.copyOf();val unchangedClean=clean.copyOf()
    val first=SpeechNoiseSeparation.measure(reference,clean,speech,nonSpeech,true)
    SpeechNoiseSeparation.measure(zero,zero,nonSpeech,nonSpeech,true)
    check(first==SpeechNoiseSeparation.measure(reference,clean,speech,nonSpeech,true))
    check(reference.contentEquals(unchangedReference) && clean.contentEquals(unchangedClean))
    val bands=SeparationMaskingBands.decode("HSGSEPARATION1\n-4,8,2\n")
    fun estimate(ratio:Double)=first.copy(retainedToResidualDb=ratio)
    for((ratio,severity) in listOf(-6.0 to MaskingSeverity.HEAVY,-2.0 to MaskingSeverity.MODERATE,
        6.0 to MaskingSeverity.MODERATE,10.0 to MaskingSeverity.CLEAR,-5.0 to MaskingSeverity.UNKNOWN,8.0 to MaskingSeverity.UNKNOWN))
        check(bands.severity(estimate(ratio))==severity)
    val original=MaskingEvidence(MaskingSeverity.HEAVY,score=.9)
    check(bands.corroborate(original,estimate(-8.0)).severity==MaskingSeverity.HEAVY)
    check(bands.corroborate(original,estimate(15.0)).severity==MaskingSeverity.UNKNOWN)
    check(bands.corroborate(original.copy(severity=MaskingSeverity.CLEAR),estimate(-8.0)).severity==MaskingSeverity.UNKNOWN)
    check(bands.corroborate(original,estimate(3.0)).severity==MaskingSeverity.UNKNOWN)
    check(bands.corroborate(original,estimate(3.0).copy(status=SeparationStatus.SPEECH_IN_RESIDUAL)).severity==MaskingSeverity.UNKNOWN)
    check(bands.corroborate(original,estimate(3.0).copy(speechScore=.64)).severity==MaskingSeverity.UNKNOWN)
    check(bands.corroborate(original,estimate(-8.0).copy(status=SeparationStatus.NO_RELIABLE_SPEECH)).severity==MaskingSeverity.UNKNOWN)
    check(bands.corroborate(original.copy(severity=MaskingSeverity.QUIET,score=0.0),SpeechNoiseEstimate()).severity==MaskingSeverity.QUIET)
    check(bands.corroborate(original.copy(severity=MaskingSeverity.UNKNOWN),estimate(-8.0)).severity==MaskingSeverity.UNKNOWN)
    check(runCatching { SeparationMaskingBands.decode("HSGSEPARATION1\nNaN,8,2\n") }.isFailure)
    println("PASS: aligned speech/residual ratios, matched loudness, gain invariance, speech-in-residual warning, absent speech, invalid alignment/data, no retained state or input mutation")
}
