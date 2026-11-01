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
                       private val release: ()->Unit = {},
                       // Explicit development diagnostics only; ordinary monitoring has no observer.
                       private val observeScores: ((Map<String,Double>)->Unit)? = null) {
    private val worker=EnvironmentSampleWorker(allowed,{ seconds,cancel ->
        check(acquire { cancel.get() }) { "Microphone busy or capture cancelled" }
        try { capture(seconds,cancel) } finally { release() }
    },{ pcm,cancelled -> Yamnet(context,observeScores).use { model -> model.classify(pcm,cancelled) } },
        { SystemClock.elapsedRealtime() })
    fun stop() = worker.stop()
    fun busy() = worker.busy()
    fun sample(seconds: Int, done: (EnvironmentClassification?, String?, Long)->Unit) = worker.sample(seconds,done)
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
        val buffer=ShortArray(1024)
        var successful=false
        try {
            check(recorder.state==AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
            val mic=manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type==AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?: error("Built-in microphone unavailable")
            check(recorder.setPreferredDevice(mic)) { "Microphone routing failed" }
            recorder.startRecording()
            check(recorder.recordingState==AudioRecord.RECORDSTATE_RECORDING) { "Microphone start failed" }
            val accumulator=NoiseAccumulator(16000,seconds)
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
            buffer.fill(0)
            if(!successful) pcm.fill(0)
        }
    }
}

/** Pinned pretrained encoder and shared scene head; CPU only, one inference thread. */
class Yamnet(context: Context, private val observeScores: ((Map<String,Double>)->Unit)? = null): AutoCloseable {
    private val labelBytes=context.assets.open("environment/labels.txt").use { it.readBytes() }
    private val labels=labelBytes.toString(Charsets.UTF_8).trimEnd().lines()
    private val selectedLabels=EnvironmentClassifier.requiredLabels.associateWith { labels.indexOf(it) }
    private val interpreter: Interpreter
    private val head: EnvironmentSceneHead
    private val smallGroupHead: EnvironmentSceneHead
    init {
        val bytes=context.assets.open("environment/yamnet.tflite").use { it.readBytes() }
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(hash==EnvironmentSceneProfile.ENCODER_SHA256) { "Unexpected environment model" }
        check(MessageDigest.getInstance("SHA-256").digest(labelBytes).joinToString("") { "%02x".format(it) }==
            "8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051") { "Unexpected environment labels" }
        val headBytes=context.assets.open("environment/scene-head.bin").use { it.readBytes() }
        check(MessageDigest.getInstance("SHA-256").digest(headBytes).joinToString("") { "%02x".format(it) }==
            EnvironmentSceneProfile.HEAD_SHA256) { "Unexpected environment scene head" }
        head=EnvironmentSceneHead.decode(headBytes,hash)
        val specialistBytes=context.assets.open("environment/small-group-head.bin").use { it.readBytes() }
        check(MessageDigest.getInstance("SHA-256").digest(specialistBytes).joinToString("") { "%02x".format(it) }==
            EnvironmentSceneProfile.SMALL_GROUP_HEAD_SHA256) { "Unexpected Small Group scene head" }
        smallGroupHead=EnvironmentSceneHead.decode(specialistBytes,hash)
        val model=ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        val candidate=Interpreter(model,Interpreter.Options().setNumThreads(1))
        try {
            check(labels.size==521 && labels.containsAll(EnvironmentClassifier.requiredLabels) && candidate.getInputTensor(0).numElements()==15600 &&
                candidate.getInputTensor(0).dataType()==DataType.FLOAT32 &&
                candidate.getOutputTensorCount()==2 && candidate.getOutputTensor(0).numElements()==521 &&
                candidate.getOutputTensor(0).dataType()==DataType.FLOAT32 && candidate.getOutputTensor(1).numElements()==1024 &&
                candidate.getOutputTensor(1).dataType()==DataType.INT8 && candidate.getOutputTensor(1).quantizationParams().zeroPoint==-128 &&
                kotlin.math.abs(candidate.getOutputTensor(1).quantizationParams().scale-0.022328350692987442)<1e-9) { "Unexpected model signature" }
        } catch(e: Exception) { candidate.close();throw e }
        interpreter=candidate
    }
    fun classify(pcm: ShortArray, cancelled: () -> Boolean = { false }): EnvironmentClassification {
        check(!cancelled()) { "Inference cancelled; sample discarded" }
        val gain=EnvironmentSignal.inferenceGain(pcm)
            ?: return EnvironmentClassification(null,0.0,"No acoustic evidence; keeping current program")
        val frames=mutableListOf<Map<String,Double>>()
        val pool=EnvironmentEmbeddingPool()
        var start=0
        val input=ByteBuffer.allocateDirect(15600*4).order(ByteOrder.nativeOrder())
        val output=ByteBuffer.allocateDirect(521*4).order(ByteOrder.nativeOrder())
        val embedding=ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
        try {
            while(start+15600<=pcm.size) {
                check(!cancelled()) { "Inference cancelled; sample discarded" }
                input.rewind();output.rewind();embedding.rewind()
                for(i in start until start+15600) input.putFloat((pcm[i]/32768.0*gain).toFloat())
                input.rewind()
                interpreter.runForMultipleInputsOutputs(arrayOf(input),mutableMapOf<Int,Any>(0 to output,1 to embedding))
                output.rewind();embedding.rewind()
                frames.add(selectedLabels.mapValues { (_,index) -> output.getFloat(index*4).toDouble() })
                pool.add(ByteArray(1024).also { embedding.get(it) })
                start+=7680
            }
            check(!cancelled()) { "Inference cancelled; sample discarded" }
            observeScores?.invoke(EnvironmentClassifier.requiredLabels.associateWith { label -> frames.map { it.getValue(label) }.average() })
            val features=pool.finish()
            val primary=head.classify(features,frames)
            // A specialist cannot overwrite even a low-confidence primary estimate.
            val result=if(primary.environment!=null) primary else {
                check(!cancelled()) { "Inference cancelled; sample discarded" }
                EnvironmentSceneFusion.combine(primary,smallGroupHead.classify(features,frames))
            }
            check(!cancelled()) { "Inference cancelled; sample discarded" }
            return result
        } finally {
            // The worker clears PCM16; also clear this direct float waveform copy on every exit.
            input.clear();while(input.hasRemaining()) input.put(0.toByte())
        }
    }
    override fun close() { interpreter.close() }
}
