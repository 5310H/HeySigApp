package com.heysig.noise

import kotlin.math.*

enum class SeparationStatus { ESTIMATED, NO_RELIABLE_SPEECH, SPEECH_IN_RESIDUAL, INVALID }

/** Estimated retained speech versus removed residual, NOT true target-speaker SNR.
 * The residual may contain wanted speech, reverberation and denoiser artifacts.
 */
data class SpeechNoiseEstimate(
    val status:SeparationStatus=SeparationStatus.INVALID,
    val retainedToResidualDb:Double?=null,
    val retainedDbfs:Double?=null,
    val residualDbfs:Double?=null,
    val speechActivity:Double=0.0,
    val speechScore:Double=0.0,
    val residualSpeechScore:Double?=null
)

/** Aligned 16kHz, normalized float PCM supplied by an audio adapter. No stored profile.
 * Reference must pass through the SAME delay/filter path with suppression disabled.
 * Never subtract an unaligned denoiser output from microphone input.
 */
object SpeechNoiseSeparation {
    fun measure(reference:FloatArray,enhanced:FloatArray,speechScores:FloatArray,
                residualSpeechScores:FloatArray,alignmentVerified:Boolean):SpeechNoiseEstimate {
        if(!alignmentVerified || reference.size!=48000 || enhanced.size!=48000 ||
            speechScores.size!=300 || residualSpeechScores.size!=300 ||
            reference.any { !it.isFinite() || abs(it)>2 } || enhanced.any { !it.isFinite() || abs(it)>2 } ||
            (speechScores.asSequence()+residualSpeechScores.asSequence()).any { !it.isFinite() || it !in 0f..1f })
            return SpeechNoiseEstimate()
        var cleanPower=0.0;var residualPower=0.0;var weightedClean=0.0;var weightedResidual=0.0
        var speech=0.0;var removedSpeech=0.0;var active=0;var residualActive=0
        // Ignore the initial 200ms recurrent-state settling period within this capture.
        // No preceding recording, persistent noise profile or wearer calibration is used.
        for(f in 20 until 300) {
            val probability=speechScores[f].toDouble()
            speech+=probability;removedSpeech+=residualSpeechScores[f]
            if(probability>=.6) active++
            if(residualSpeechScores[f]>=.6) residualActive++
            for(i in f*160 until (f+1)*160) {
                val retained=enhanced[i].toDouble();val residual=reference[i]-retained
                cleanPower+=retained*retained;residualPower+=residual*residual
                weightedClean+=probability*retained*retained
                weightedResidual+=probability*residual*residual
            }
        }
        val score=speech/280;val activity=active/280.0;val removedScore=removedSpeech/280
        val reliable=active>=20 && score>=.35 && weightedClean>1e-14
        val ratio=if(reliable) (10*log10(max(weightedClean,1e-20)/max(weightedResidual,1e-20))).coerceIn(-80.0,80.0) else null
        return SpeechNoiseEstimate(
            when { !reliable -> SeparationStatus.NO_RELIABLE_SPEECH
                residualActive>=20 && removedScore>=.35 -> SeparationStatus.SPEECH_IN_RESIDUAL
                else -> SeparationStatus.ESTIMATED },ratio,
            10*log10(max(cleanPower/(280*160),1e-12)),
            10*log10(max(residualPower/(280*160),1e-12)),activity,score,removedScore)
    }
}
