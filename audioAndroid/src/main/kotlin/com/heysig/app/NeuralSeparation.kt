package com.heysig.app

import com.heysig.noise.SpeechNoiseEstimate
import com.heysig.noise.SpeechNoiseSeparation

/** Phone/watch audio adapter (package retained for pinned JNI compatibility). Owned waveform buffers are discarded before returning scalars. */
class NeuralSeparation {
    init { loaded }
    private external fun analyzeNative(pcm:ShortArray):FloatArray?
    fun measure(pcm:ShortArray):SpeechNoiseEstimate {
        require(pcm.size==48000)
        val packed=checkNotNull(analyzeNative(pcm)) { "Speech separation unavailable" }
        var reference:FloatArray?=null;var enhanced:FloatArray?=null
        var speech:FloatArray?=null;var residualSpeech:FloatArray?=null
        try {
            check(packed.size==96600) { "Incomplete speech separation" }
            reference=packed.copyOfRange(0,48000);enhanced=packed.copyOfRange(48000,96000)
            speech=packed.copyOfRange(96000,96300);residualSpeech=packed.copyOfRange(96300,96600)
            return SpeechNoiseSeparation.measure(reference,enhanced,speech,residualSpeech,true)
        } finally {
            packed.fill(0f);reference?.fill(0f);enhanced?.fill(0f);speech?.fill(0f);residualSpeech?.fill(0f)
        }
    }
    companion object {
        private val loaded by lazy { System.loadLibrary("heysig_rnnoise") }
    }
}
