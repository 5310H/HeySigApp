package com.heysig.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.SystemClock
import com.heysig.noise.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** One worker, bounded in-memory audio; no files, transcript or network APIs. */
class EnvironmentAudio(private val context: Context, private val allowed: () -> Boolean,
                       private val acquire: ((()->Boolean)->Boolean) = { true },
                       private val release: ()->Unit = {}) {
    @Volatile private var cancelled=AtomicBoolean(true)
    private var worker: Thread?=null
    @Synchronized fun stop() { cancelled.set(true) }
    @Synchronized fun sample(seconds: Int, done: (EnvironmentClassification?, String?, Long)->Unit): Boolean {
        require(seconds in listOf(3,5,10))
        if(worker?.isAlive == true) return false
        val cancel=AtomicBoolean(false);cancelled=cancel
        worker=Thread({
            try {
                check(acquire { cancel.get() }) { "Microphone busy or capture cancelled" }
                val pcm=try { capture(seconds,cancel) } finally { release() }
                val capturedAt=SystemClock.elapsedRealtime()
                try {
                    // Losing access without an explicit stop must complete with an error;
                    // otherwise the caller stays stuck waiting for this sample forever.
                    check(!cancel.get() && allowed()) { "Capture access ended; sample discarded" }
                    val result=Yamnet(context).use { it.classify(pcm) }
                    check(!cancel.get() && allowed()) { "Capture access ended; sample discarded" }
                    done(result,null,capturedAt)
                } finally { pcm.fill(0) }
            } catch(e: Exception) {
                if(!cancel.get()) done(null,e.message ?: "Microphone/model unavailable",SystemClock.elapsedRealtime())
            } catch(e: LinkageError) {
                if(!cancel.get()) done(null,"Audio model runtime unavailable",SystemClock.elapsedRealtime())
            }
        },"heysig-environment").apply { start() }
        return true
    }
    private fun capture(seconds: Int,cancel: AtomicBoolean): ShortArray {
        fun checkAccess() {
            check(!cancel.get()) { "Capture interrupted; sample discarded" }
            val audio=context.getSystemService(AudioManager::class.java)
            val access=EnvironmentCaptureAccess(allowed(),!context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED,
                !audio.isMicrophoneMute && audio.mode==AudioManager.MODE_NORMAL)
            check(access.blocked()==null) { access.blocked() ?: "Microphone unavailable" }
        }
        checkAccess()
        val manager=context.getSystemService(AudioManager::class.java)
        check(!manager.isMicrophoneMute && manager.mode==AudioManager.MODE_NORMAL) { "Microphone muted or call active" }
        val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        check(minimum>0) { "16 kHz microphone unavailable" }
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Microphone permission is required")
        val recorder=AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,4096))
        val pcm=ShortArray(seconds*16000)
        var successful=false
        try {
            check(recorder.state==AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
            val mic=manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type==AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?: error("Built-in microphone unavailable")
            check(recorder.setPreferredDevice(mic)) { "Microphone routing failed" }
            recorder.startRecording()
            check(recorder.recordingState==AudioRecord.RECORDSTATE_RECORDING) { "Microphone start failed" }
            val accumulator=NoiseAccumulator(16000,seconds)
            val buffer=ShortArray(1024)
            var count=0
            val deadline=SystemClock.elapsedRealtime()+seconds*1000L+2000
            while(count<pcm.size) {
                checkAccess()
                check(SystemClock.elapsedRealtime()<deadline) { "Microphone capture timed out" }
                val size=recorder.read(buffer,0,minOf(buffer.size,pcm.size-count),AudioRecord.READ_NON_BLOCKING)
                check(size>=0) { "Microphone read failed" }
                if(size==0) { Thread.sleep(10);continue }
                check(recorder.routedDevice?.id==mic.id) { "Microphone route changed" }
                buffer.copyInto(pcm,count,0,size);count+=size;accumulator.add(buffer,size)
            }
            checkAccess()
            check(accumulator.measurement().flags.isEmpty()) { "Invalid, clipped or silent microphone capture" }
            successful=true
            return pcm
        } finally {
            runCatching { recorder.stop() };recorder.release()
            if(!successful) pcm.fill(0)
        }
    }
}

/** Pinned Google quantized YAMNet, CPU only, one inference thread. */
class Yamnet(context: Context): AutoCloseable {
    private val labelBytes=context.assets.open("environment/labels.txt").use { it.readBytes() }
    private val labels=labelBytes.toString(Charsets.UTF_8).trimEnd().lines()
    private val interpreter: Interpreter
    init {
        val bytes=context.assets.open("environment/yamnet.tflite").use { it.readBytes() }
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(hash=="10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de") { "Unexpected environment model" }
        check(MessageDigest.getInstance("SHA-256").digest(labelBytes).joinToString("") { "%02x".format(it) }==
            "8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051") { "Unexpected environment labels" }
        val model=ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        interpreter=Interpreter(model,Interpreter.Options().setNumThreads(1))
        check(labels.size==521 && labels.containsAll(EnvironmentClassifier.requiredLabels) && interpreter.getInputTensor(0).numElements()==15600 &&
            interpreter.getInputTensor(0).dataType()==DataType.FLOAT32 &&
            interpreter.getOutputTensor(0).numElements()==521 && interpreter.getOutputTensor(0).dataType()==DataType.FLOAT32) { "Unexpected model signature" }
    }
    fun classify(pcm: ShortArray): EnvironmentClassification {
        require(pcm.size in 48000..160000)
        val frames=mutableListOf<Map<String,Double>>()
        var start=0
        val input=ByteBuffer.allocateDirect(15600*4).order(ByteOrder.nativeOrder())
        val output=ByteBuffer.allocateDirect(521*4).order(ByteOrder.nativeOrder())
        while(start+15600<=pcm.size) {
            input.rewind();output.rewind()
            for(i in start until start+15600) input.putFloat(pcm[i]/32768f)
            input.rewind();interpreter.run(input,output);output.rewind()
            frames.add(labels.associateWith { output.float.toDouble() })
            start+=7680
        }
        return EnvironmentClassifier.classify(frames)
    }
    override fun close() { interpreter.close() }
}
