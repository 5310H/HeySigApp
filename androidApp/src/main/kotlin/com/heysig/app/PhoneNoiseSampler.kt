package com.heysig.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.heysig.noise.*
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground-only, bounded capture. No PCM is retained or written to storage. */
class PhoneNoiseSampler(private val context: Context, private val allowed: () -> Boolean) {
    private var worker: Thread? = null
    @Volatile private var cancellation = AtomicBoolean(true)
    @Synchronized fun stop() { cancellation.set(true) }
    @Synchronized fun start(settings: NoiseSettings, repeat: Boolean, report: (NoiseMeasurement?,String?) -> Unit): Boolean {
        if(worker?.isAlive == true) return false
        val cancel = AtomicBoolean(false); cancellation = cancel
        worker = Thread({
            try {
                // Let a watch's start acknowledgement finish before measuring nearby sound.
                val firstCapture = SystemClock.elapsedRealtime()+settings.waitSeconds.coerceAtLeast(10)*1000L
                while(!cancel.get() && SystemClock.elapsedRealtime()<firstCapture) Thread.sleep(25)
                do {
                    if(cancel.get()) break
                    try {
                        val value = capture(settings.captureSeconds,cancel)
                        if(!cancel.get()) report(value,null)
                    } catch(e:Exception) {
                        if(!cancel.get()) report(null,e.message ?: "Microphone capture failed")
                    }
                    if(!repeat) break
                    val until = SystemClock.elapsedRealtime()+settings.waitSeconds*1000L
                    while(!cancel.get() && SystemClock.elapsedRealtime()<until) Thread.sleep(25)
                } while(!cancel.get())
            } finally { cancel.set(true) }
        },"heysig-noise-observe").apply { start() }
        return true
    }
    private fun capture(seconds: Int, cancel: AtomicBoolean): NoiseMeasurement {
        check(allowed()) { "Capture paused for speech, playback or inactive app" }
        if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Allow microphone permission before sampling")
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        check(audio.mode == AudioManager.MODE_NORMAL && !audio.isMicrophoneMute) { "Microphone muted or call audio active" }
        val rate = 16000
        val minimum = AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Microphone format unavailable" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,4096))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not initialize" }
            check(recorder.setPreferredDevice(audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?: error("Built-in microphone unavailable"))) { "Microphone route unavailable" }
            val accumulator = NoiseAccumulator(rate,seconds)
            val buffer = ShortArray(1024)
            var total = 0
            val end = SystemClock.elapsedRealtime()+seconds*1000L+2000
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone capture did not start" }
            while(total < rate*seconds) {
                check(!cancel.get() && allowed()) { "Capture interrupted; sample discarded" }
                check(SystemClock.elapsedRealtime()<end) { "Microphone capture timed out" }
                val count = recorder.read(buffer,0,minOf(buffer.size,rate*seconds-total),AudioRecord.READ_NON_BLOCKING)
                check(count >= 0) { "Microphone read failed ($count)" }
                if(count == 0) { Thread.sleep(10); continue }
                check(recorder.routedDevice?.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC) { "Built-in microphone route changed" }
                accumulator.add(buffer,count); total += count
            }
            check(!cancel.get() && allowed()) { "Capture interrupted; sample discarded" }
            return accumulator.measurement()
        } finally { runCatching { recorder.stop() }; recorder.release() }
    }
}
