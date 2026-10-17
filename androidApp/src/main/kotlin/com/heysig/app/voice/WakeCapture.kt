package com.heysig.app.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.*
import com.heysig.app.MicrophoneLease
import java.util.concurrent.atomic.AtomicBoolean

/** No recording or upload: PCM exists only in this worker until keyword detection or cancellation. */
class WakeCapture(private val context: Context, private val done: (Boolean,String?) -> Unit) : AutoCloseable {
    private val cancelled=AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord?=null
    fun start() {
        Thread({
            var engine: KeywordSpotter?=null
            var stream: OnlineStream?=null
            var ownsMicrophone=false
            var detected=false
            var failure: String?=null
            try {
                engine=WakeModel.create(context.assets)
                stream=engine.createStream()
                val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(minimum>0) { "Microphone format unavailable" }
                if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) throw SecurityException("Microphone permission unavailable")
                check(MicrophoneLease.acquire { cancelled.get() }) { "Microphone busy or cancelled" }
                ownsMicrophone=true
                val input=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(minimum,6400)).build()
                recorder=input
                check(input.state==AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
                if(cancelled.get()) return@Thread
                input.startRecording()
                val buffer=ShortArray(1600)
                val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                while(!cancelled.get()) {
                    check(!audio.isMicrophoneMute && audio.mode==AudioManager.MODE_NORMAL) { "Microphone muted or call active; listening stopped" }
                    val count=input.read(buffer,0,buffer.size)
                    check(count>0) { "Microphone interrupted; listening stopped" }
                    if(cancelled.get()) break
                    stream.acceptWaveform(FloatArray(count) { buffer[it]/32768f },16000)
                    while(engine.isReady(stream) && !cancelled.get()) {
                        engine.decode(stream)
                        if(engine.getResult(stream).keyword.isNotEmpty()) { detected=true; break }
                    }
                    if(detected) break
                }
            } catch(e: Exception) { if(!cancelled.get()) failure=e.message ?: "Wake detector unavailable" }
            catch(e: LinkageError) { if(!cancelled.get()) failure="Wake engine unavailable on this device" }
            finally {
                runCatching { recorder?.stop() };runCatching { recorder?.release() }; recorder=null
                if(ownsMicrophone) MicrophoneLease.release()
                runCatching { stream?.release() };runCatching { engine?.release() }
                done(!cancelled.get() && detected, failure)
            }
        },"heysig-wake").start()
    }
    override fun close() { cancelled.set(true); runCatching { recorder?.stop() } }
}

/** Shared model initialization lets instrumentation verify the real native engine without a microphone. */
object WakeModel {
    fun create(assets: android.content.res.AssetManager) = KeywordSpotter(assets,KeywordSpotterConfig(
        modelConfig=OnlineModelConfig(
            transducer=OnlineTransducerModelConfig(encoder="handsfree/encoder.onnx",decoder="handsfree/decoder.onnx",joiner="handsfree/joiner.onnx"),
            tokens="handsfree/tokens.txt",numThreads=1,debug=false,modelType="zipformer2"),
        keywordsFile="handsfree/keywords.txt",keywordsThreshold=0.5f))
}
