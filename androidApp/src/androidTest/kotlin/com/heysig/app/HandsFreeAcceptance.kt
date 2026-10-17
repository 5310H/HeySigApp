package com.heysig.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.heysig.control.*
import com.heysig.app.voice.WakeModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real controller, isolated storage, fake transport. Never requests mic access or plays aid tones. */
object HandsFreeAcceptance {
    fun run(context: Context) {
        val main=Handler(Looper.getMainLooper())
        val store=context.getSharedPreferences("acceptance-isolated-handsfree",0)
        check(store.edit().clear().putString("fitting","""{"control":12,"left":12,"right":12,"name1":"Awake","tin1":"SUPPORTED","name2":"Sleep","tin2":"SUPPORTED"}""").putString("requested","""{"program":1}""").putString("saved","""{"program":1,"VOLUME":6}""").commit())
        val began=CountDownLatch(1);val proceed=CountDownLatch(1)
        var plays=0;var inspections=0
        var cancellationCase=false
        val cancelBegan=CountDownLatch(1);val cancelProceed=CountDownLatch(1)
        val transport=object: CommandTransport {
            override fun inspect(allowLocked: Boolean) { check(allowLocked);inspections++ }
            override fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean) {
                check(allowLocked && pcm.isNotEmpty() && rate==44100)
                check(store.getBoolean("pending",false)) { "Playback began before durable journal" }
                plays++
                if(cancellationCase) { cancelBegan.countDown();check(cancelProceed.await(10,TimeUnit.SECONDS)) }
                else { began.countDown();check(proceed.await(10,TimeUnit.SECONDS)) }
                check(!cancelled.get()) { "Stopped" }
            }
        }
        val runtime=ManualRuntime.isolated(store,transport)
        try {
            val blocked=CountDownLatch(1);var blockedOk=true
            main.post { runtime.execute(listOf(ManualRequest.SelectProgram(2)),false,false,{false},{false},{}) { _,ok -> blockedOk=ok;blocked.countDown() } }
            check(blocked.await(10,TimeUnit.SECONDS));check(!blockedOk);check(plays==0 && inspections==0)
            val done=CountDownLatch(1);var completedOk=false
            main.post { runtime.execute(listOf(ManualRequest.SelectProgram(2)),false,false,{false},{true},{}) { _,ok -> completedOk=ok;done.countDown() } }
            check(began.await(10,TimeUnit.SECONDS))
            val second=CountDownLatch(1);var secondOk=true
            main.post { runtime.execute(listOf(ManualRequest.SelectProgram(1)),false,false,{false},{true},{}) { _,ok -> secondOk=ok;second.countDown() } }
            check(second.await(10,TimeUnit.SECONDS));check(!secondOk);check(plays==1)
            proceed.countDown();check(done.await(10,TimeUnit.SECONDS));check(completedOk)
            check(runtime.requested.program==2 && !store.getBoolean("pending",true))
            check(runtime.saved?.program==2)
            cancellationCase=true
            val stopped=CountDownLatch(1);var stoppedOk=true
            main.post { runtime.execute(listOf(ManualRequest.SelectProgram(1)),false,false,{false},{true},{}) { _,ok -> stoppedOk=ok;stopped.countDown() } }
            check(cancelBegan.await(10,TimeUnit.SECONDS));runtime.cancelled.set(true);cancelProceed.countDown()
            check(stopped.await(10,TimeUnit.SECONDS));check(!stoppedOk)
            check(runtime.requested.program==null && runtime.saved?.program==2 && !store.getBoolean("pending",true))
            // Corrupt/interrupted state is unknown; it never replays on process initialization.
            check(store.edit().putString("requested","""{"program":1}""").putBoolean("pending",true).commit())
            val restarted=ManualRuntime.isolated(store,transport)
            check(restarted.requested.program==null && restarted.saved?.program==2 && plays==2)
            check(store.edit().putBoolean("pending",false).commit())
            val failing=object: SharedPreferences by store {
                override fun edit(): SharedPreferences.Editor {
                    val editor=store.edit()
                    return object: SharedPreferences.Editor by editor {
                        override fun putBoolean(key: String,value: Boolean): SharedPreferences.Editor { editor.putBoolean(key,value);return this }
                        override fun putString(key: String,value: String?): SharedPreferences.Editor { editor.putString(key,value);return this }
                        override fun commit()=false
                    }
                }
            }
            val bad=ManualRuntime.isolated(failing,transport)
            val failed=CountDownLatch(1);var failureOk=true
            main.post { bad.execute(listOf(ManualRequest.SelectProgram(1)),false,false,{false},{true},{}) { _,ok -> failureOk=ok;failed.countDown() } }
            check(failed.await(10,TimeUnit.SECONDS));check(!failureOk);check(plays==2 && !store.getBoolean("pending",true))
            // Load and decode silence through the actual bundled native KWS model, with no microphone.
            val engine=WakeModel.create(context.assets)
            val stream=engine.createStream()
            try {
                stream.acceptWaveform(FloatArray(16000),16000)
                while(engine.isReady(stream)) engine.decode(stream)
                check(engine.getResult(stream).keyword.isEmpty())
            } finally { stream.release();engine.release() }
        } finally { proceed.countDown();cancelProceed.countDown();check(store.edit().clear().commit()) }
    }
}
