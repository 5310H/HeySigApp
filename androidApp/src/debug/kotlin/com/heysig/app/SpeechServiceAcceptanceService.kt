package com.heysig.app

import com.heysig.control.ManualRequest
import com.heysig.noise.*
import java.util.concurrent.atomic.AtomicBoolean

/** Debug-only real-mic/lifecycle harness. Isolated fitting/storage; transport cannot reach speakers. */
class SpeechServiceAcceptanceService:SpeechAutomaticService() {
    override val runtime by lazy {
        val prefs=getSharedPreferences("acceptance-speech-core",0)
        check(prefs.edit().clear().putString("fitting","""{"control":12,"left":12,"right":12,"name1":"Everyday","tin1":"SUPPORTED","name2":"Noisy","tin2":"SUPPORTED","name3":"Very Noisy","tin3":"SUPPORTED"}""")
            .putString("requested","""{"program":1}""").commit())
        val sleep=getSharedPreferences("acceptance-speech-sleep",0).also { check(it.edit().clear().commit()) }
        val phrases=getSharedPreferences("acceptance-speech-phrases",0).also { check(it.edit().clear().commit()) }
        ManualRuntime.isolated(prefs,object:CommandTransport {
            override fun inspect(allowLocked:Boolean) { check(allowLocked) }
            override fun play(pcm:ByteArray,rate:Int,cancelled:AtomicBoolean,allowLocked:Boolean) {
                check(allowLocked && pcm.isNotEmpty() && !cancelled.get());plays++ // Never an audio output adapter.
            }
        },sleep,phrases)
    }
    override fun detect(pcm:ShortArray,features:DoubleArray?,frames:List<Map<String,Double>>)=MaskingEvidence(severity,SoundType.SPEECH,.9,"debug-only controlled evidence; native detector tested separately")
    val program get()=runtime.requested.program
    val idle get()=!runtime.busy.get()
    fun manual() { runtime.execute(listOf(ManualRequest.SelectProgram(3)),false,false,{true},{true},{}) { _,_ -> } }
    companion object { var severity=MaskingSeverity.MODERATE;var plays=0 }
}
