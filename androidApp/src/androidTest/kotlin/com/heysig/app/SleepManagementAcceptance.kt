package com.heysig.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.heysig.control.*
import com.heysig.sleep.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Isolated storage and fake transport: no user fitting, alarms or aid commands. */
object SleepManagementAcceptance {
    fun run(context: Context) {
        val core=context.getSharedPreferences("acceptance-sleep-runtime",0)
        val prefs=context.getSharedPreferences("acceptance-sleep-management",0)
        check(core.edit().clear().putString("fitting","""{"control":12,"left":12,"right":12,"name1":"Awake","tin1":"SUPPORTED","name2":"Sleep","tin2":"UNKNOWN"}""")
            .putString("saved","""{"program":1,"VOLUME":6}""").commit())
        check(prefs.edit().clear().commit())
        var failed=false
        var blocked=false
        var played=0
        val transport=object: CommandTransport {
            override fun inspect(allowLocked: Boolean) { check(!blocked) { "Injected audio blocker" } }
            override fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean) { played++;check(!failed) { "Injected transport failure" } }
        }
        val runtime=ManualRuntime.isolated(core,transport,prefs)
        val fit=requireNotNull(runtime.fitting)
        val store=runtime.sleepRoutine
        fun play(slot: Int,sleep: Boolean?=null,automatic:Boolean=false): Boolean {
            var ok=false;val done=CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                runtime.execute(listOf(ManualRequest.SelectProgram(slot)),false,automatic,{true},{false},{},sleepTarget=sleep) { _,success -> ok=success;done.countDown() }
            }
            check(done.await(20,TimeUnit.SECONDS));return ok
        }
        try {
            check(runCatching { store.target(true,fit) }.isFailure)
            check(store.save(SleepRoutine(1320,420,2,1),fit,store.editorRevision()).startsWith("Routine saved"))
            check(store.target(true,fit)==2 && store.target(false,fit)==1)
            check(play(2,true))
            check(store.actionResult()!!.startsWith("Sleep now sent"))
            check(store.state.mode==SleepMode.ASLEEP && store.state.blocksNoise)
            check(SleepRoutineStore(prefs).state==store.state)
            val before=played
            check(!play(1,automatic=true) && played==before)
            check(play(1))
            check(store.state.override && store.state.mode==SleepMode.ASLEEP)
            store.handle(SleepCommand.Resume,fit,"phone")
            check(store.state.blocksNoise)
            check(play(1,false))
            check(store.state==SleepState())
            val beforeBlocked=played
            blocked=true
            check(!play(2,true))
            check(played==beforeBlocked && store.state==SleepState())
            check(SleepRoutineStore(prefs).actionResult()!!.contains("Sleep now did not complete. Blocked: Injected audio blocker"))
            blocked=false
            failed=true
            check(!play(2,true))
            check(store.state==SleepState(SleepMode.UNKNOWN,true))
            check(runtime.saved?.levels?.get(LevelControl.VOLUME)==6)
            store.transition(SleepState(SleepMode.ASLEEP).begin())
            check(SleepRoutineStore(prefs).state==SleepState(SleepMode.UNKNOWN,true))
            store.setupChanged(fit.copy(controlAddress=13))
            check(runCatching { store.target(true,fit) }.isFailure)
            check(!prefs.getBoolean("reminders",false))
        } finally { check(core.edit().clear().commit());check(prefs.edit().clear().commit()) }
    }
}
