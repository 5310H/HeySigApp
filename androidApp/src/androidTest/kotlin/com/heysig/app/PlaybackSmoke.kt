package com.heysig.app

import android.app.Instrumentation
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in development instrumentation. Only zeros are played; no acoustic aid command. */
class PlaybackSmoke : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            startActivitySync(Intent(targetContext,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            val player = SpeakerPlayback(targetContext)
            repeat(2) { index ->
                val report = player.play(ByteArray(60632),44100,AtomicBoolean(false))
                check(report.playedFrames >= report.commandFrames)
                result.putString("play${index+1}","${report.playedFrames}/${report.commandFrames} frames; ${report.elapsedMillis} ms; speaker; silence only")
            }
            val cancelled = AtomicBoolean(true)
            check(runCatching { player.play(ByteArray(60632),44100,cancelled) }.isFailure)
            result.putString("cancel","Already-cancelled playback rejected")
            result.putString("result","PASS: two silent PCM streams completed; no aid commands emitted")
            finish(Activity.RESULT_OK,result)
        } catch(e:Exception) {
            result.putString("result","FAIL: ${e.message}"); finish(Activity.RESULT_CANCELED,result)
        }
    }
}
