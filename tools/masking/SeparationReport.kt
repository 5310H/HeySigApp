package com.heysig.masking

import com.heysig.noise.SpeechNoiseSeparation
import java.io.DataInputStream

/** Development adapter: two float PCM paths and two frame-score arrays, little endian. */
fun main() {
    val input=DataInputStream(System.`in`.buffered())
    fun floats(size:Int)=FloatArray(size) { Float.fromBits(Integer.reverseBytes(input.readInt())) }
    while(true) {
        val marker=input.read()
        if(marker<0) return
        check(marker==83)
        val reference=floats(48000);val enhanced=floats(48000)
        val speech=floats(300);val residualSpeech=floats(300)
        try {
            val m=SpeechNoiseSeparation.measure(reference,enhanced,speech,residualSpeech,true)
            println(listOf(m.status,m.retainedToResidualDb ?: "",m.retainedDbfs ?: "",m.residualDbfs ?: "",m.speechActivity,m.speechScore,m.residualSpeechScore ?: "").joinToString("\t"))
        } finally { reference.fill(0f);enhanced.fill(0f);speech.fill(0f);residualSpeech.fill(0f) }
    }
}
