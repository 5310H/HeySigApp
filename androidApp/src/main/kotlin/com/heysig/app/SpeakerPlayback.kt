package com.heysig.app

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** Speaker-only transport. Returning normally means playback completed, never aid receipt. */
class SpeakerPlayback(private val context: Context) {
    fun inspect(): AudioDeviceInfo {
        check((context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive &&
            !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) { "Turn on and unlock this device" }
        check((context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) { "Turn off Do Not Disturb" }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        check(audio.mode == AudioManager.MODE_NORMAL) { "Call or communication audio is active" }
        check(audio.ringerMode != AudioManager.RINGER_MODE_SILENT) { "Unmute this device" }
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        check(maximum > 0 && audio.getStreamVolume(AudioManager.STREAM_MUSIC) >= (maximum + 1) / 2) { "Raise device media volume to at least 50%" }
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        check(outputs.none { it.type in setOf(3,4,7,8,11,12,22,23,26,27) }) { "Disconnect external audio outputs" }
        return outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: error("Built-in speaker unavailable")
    }
    data class PlaybackReport(val commandFrames: Int, val playedFrames: Long, val elapsedMillis: Long)

    fun play(pcm: ByteArray, rate: Int, cancelled: AtomicBoolean): PlaybackReport {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0) { "PCM must contain complete mono PCM16 frames" }
        require(rate in 8000..192000)
        val speaker = inspect()
        check(!cancelled.get()) { "Stopped" }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build()
        val focusLost = AtomicBoolean(false)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change -> if(change < 0) focusLost.set(true) }, Handler(Looper.getMainLooper()))
            .build()
        check(audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio focus unavailable; no command sent" }
        var track: AudioTrack? = null
        val started = SystemClock.elapsedRealtime()
        val deadline = started + maxOf(5000L, pcm.size.toLong()*1000/(rate*2)+3000)
        var writtenBytes = 0
        var played = 0L
        try {
            val minimum = AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "Unsupported PCM format" }
            val output = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minimum,4096)).setTransferMode(AudioTrack.MODE_STREAM).build()
            track = output
            check(output.state == AudioTrack.STATE_INITIALIZED) { "Audio playback could not initialize" }
            check(output.setPreferredDevice(speaker)) { "Speaker preference rejected" }
            // ARC can recreate a track as play() resolves its route. Preloading the whole
            // packet before play() lost that packet on Chromebook (0 frames delivered).
            output.play()
            // Silence after the unchanged packet lets a resampler drain its final frames.
            val data = pcm.copyOf(pcm.size + (rate / 20) * 2)
            fun guard() {
                check(!cancelled.get()) { "Stopped; aid receipt is uncertain" }
                check(!focusLost.get()) { "Audio focus lost; aid receipt is uncertain" }
                played = output.playbackHeadPosition.toLong() and 0xffffffffL
                check(SystemClock.elapsedRealtime() < deadline) {
                    "Playback timed out ($played/${pcm.size/2} command frames; $writtenBytes/${data.size} bytes queued)"
                }
                if(played > 0) check(output.routedDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { "Speaker route lost or unconfirmed" }
            }
            while(writtenBytes < data.size) {
                guard()
                val written = output.write(data,writtenBytes,minOf(4096,data.size-writtenBytes),AudioTrack.WRITE_NON_BLOCKING)
                check(written >= 0) { "Audio write failed ($written); aid receipt is uncertain" }
                writtenBytes += written
                if(written == 0) Thread.sleep(5)
            }
            while(played < pcm.size/2) { guard(); if(played < pcm.size/2) Thread.sleep(5) }
            check(!cancelled.get() && !focusLost.get()) { "Playback interrupted; aid receipt is uncertain" }
            check(output.routedDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { "Built-in speaker route could not be confirmed" }
            return PlaybackReport(pcm.size/2,played,SystemClock.elapsedRealtime()-started)
        } finally {
            track?.let { runCatching { it.stop() }; runCatching { it.release() } }
            audio.abandonAudioFocusRequest(focus)
        }
    }
}
