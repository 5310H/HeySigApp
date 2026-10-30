package com.heysig.app.voice

import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.*

/** One deliberate foreground turn. Provider fallback is disclosed by the caller. */
class ForegroundVoice(private val context: Context, private val active: ()->Boolean,
                      private val result: (List<String>)->Unit, private val report: (String)->Unit): AutoCloseable {
    private val handler=Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer?=null
    private var generation=0
    val listening get()=recognizer!=null
    fun start(seconds: Int) {
        require(seconds in 3..15)
        if(listening || !active()) return
        close()
        val token=generation
        // Give a cancelled bounded PCM capture time to release its AudioRecord.
        handler.postDelayed({
            if(token!=generation || !active()) return@postDelayed
            try {
                val speech=if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
                recognizer=speech
                speech.setRecognitionListener(object: RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { if(token==generation) { ShakeTrigger.acknowledge(context);report("Listening · speak a command") } }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(value: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) { if(token==generation) { close();report("Recognition ended ($error). Tap Speak or shake to try again.") } }
                    override fun onResults(results: Bundle?) {
                        if(token!=generation) return
                        val words=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.take(5).orEmpty()
                        val valid=active();close()
                        if(valid && words.isNotEmpty()) result(words) else report("No command recognized")
                    }
                    override fun onPartialResults(results: Bundle?) {}
                    override fun onEvent(type: Int,params: Bundle?) {}
                })
                speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US")
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5)
                })
                handler.postDelayed({ if(token==generation) { close();report("Voice window expired; microphone off") } },seconds*1000L)
            } catch(e: Exception) { close();report("Recognition unavailable; use typed commands") }
        },300)
    }
    override fun close() {
        generation++;handler.removeCallbacksAndMessages(null)
        recognizer?.cancel();recognizer?.destroy();recognizer=null
    }
}
