package com.heysig.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.heysig.control.*
import com.heysig.sleep.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real journal/executor with isolated preferences, fake exact alarms and fake aid transport. */
object AutomaticSleepAcceptance {
    fun run(context: Context) {
        val core=context.getSharedPreferences("acceptance-auto-sleep-core",0)
        val prefs=context.getSharedPreferences("acceptance-auto-sleep-settings",0)
        check(core.edit().clear().putString("fitting","""{"control":12,"left":12,"right":12,"name1":"Awake","tin1":"SUPPORTED","name2":"Sleep","tin2":"UNKNOWN"}""")
            .putString("saved","""{"program":1,"VOLUME":6}""").commit())
        check(prefs.edit().clear().commit())
        var played=0
        val transport=object: CommandTransport {
            override fun inspect(allowLocked: Boolean) { check(allowLocked) }
            override fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean) { check(allowLocked && !cancelled.get());played++ }
        }
        val runtime=ManualRuntime.isolated(core,transport,prefs)
        data class Alarm(val due: Long,val generation: Long,val revision: Long)
        val backend=object: AutomaticSleepScheduler.Backend {
            var exact=true;var notifications=true;var scheduleFailure=false;var dispatchFailure=false
            val scheduled=mutableMapOf<Boolean,Alarm>()
            val dispatched=mutableListOf<String>()
            override fun exactAllowed()=exact
            override fun notificationsAllowed()=notifications
            override fun cancel(sleep: Boolean) { scheduled.remove(sleep) }
            override fun schedule(sleep: Boolean,due: Long,generation: Long,revision: Long) {
                check(!(scheduleFailure && !sleep)) { "Injected scheduling failure" }
                scheduled[sleep]=Alarm(due,generation,revision)
            }
            override fun dispatch(token: String) { check(!dispatchFailure);dispatched.add(token) }
            override fun stopPlayback() = Unit
            override fun status(enabled: Boolean,text: String) = Unit
        }
        val scheduler=AutomaticSleepScheduler(context,prefs,{runtime},backend)
        fun fire(sleep: Boolean,alarm: Alarm,now: Long=alarm.due) = scheduler.fire(sleep,alarm.due,alarm.generation,alarm.revision,now)
        fun enable()=scheduler.enable(runtime.sleepRoutine.editorRevision())
        try {
            val fit=requireNotNull(runtime.fitting)
            val store=runtime.sleepRoutine
            check(!enable().startsWith("Automatic sleep On"))
            check(store.save(SleepRoutine(1320,420,2,1),fit,store.editorRevision()).startsWith("Routine saved"))
            val saved=core.getString("saved",null)
            check(scheduler.enable().contains("review"))
            backend.exact=false;check(enable().contains("Alarms"));check(!scheduler.enabled)
            backend.exact=true;backend.notifications=false;check(enable().contains("notifications"));check(!scheduler.enabled)
            backend.notifications=true
            store.transition(store.state.manual())
            check(enable().startsWith("Automatic sleep On"));check(!store.state.override)
            check(backend.scheduled.size==2 && played==0)
            val beforeReopen=backend.scheduled.getValue(true)
            scheduler.refresh()
            check(backend.scheduled.getValue(true).generation>beforeReopen.generation)
            fire(true,beforeReopen);check(backend.dispatched.isEmpty())
            val bedtime=backend.scheduled.getValue(true)
            scheduler.fire(true,bedtime.due,bedtime.generation-1,bedtime.revision,bedtime.due)
            check(backend.dispatched.isEmpty())
            fire(true,bedtime,bedtime.due-1);check(backend.dispatched.isEmpty())
            fire(true,bedtime)
            val token=backend.dispatched.single()
            fire(true,bedtime);check(backend.dispatched.size==1)
            val claim=checkNotNull(scheduler.takeClaim(token,bedtime.due))
            check(scheduler.takeClaim(token,bedtime.due)==null)
            check(scheduler.authorized(claim))
            val done=CountDownLatch(1)
            var success=false
            Handler(Looper.getMainLooper()).post {
                runtime.execute(listOf(ManualRequest.SelectProgram(claim.slot)),false,false,{false},{scheduler.authorized(claim)},{},sleepTarget=claim.sleep) { text,ok ->
                    success=ok;scheduler.finish(claim,text,ok);done.countDown()
                }
            }
            check(done.await(20,TimeUnit.SECONDS) && success)
            check(played==1 && runtime.requested.program==2 && store.state.mode==SleepMode.ASLEEP)
            check(core.getString("saved",null)==saved && !prefs.contains("automaticClaim"))
            check(!scheduler.authorized(claim))
            store.transition(store.state.manual())
            fire(false,backend.scheduled.getValue(false))
            check(backend.dispatched.size==1 && prefs.getString("automaticResult","")!!.contains("paused"))

            check(enable().startsWith("Automatic sleep On"))
            val late=backend.scheduled.getValue(true)
            fire(true,late,late.due+120001)
            check(backend.dispatched.size==1 && prefs.getString("automaticResult","")!!.contains("missed"))
            val revoked=backend.scheduled.getValue(false)
            backend.exact=false;fire(false,revoked)
            check(!scheduler.enabled && backend.scheduled.isEmpty() && played==1)
            backend.exact=true

            backend.scheduleFailure=true
            check(!enable().startsWith("Automatic sleep On"))
            check(!scheduler.enabled && backend.scheduled.isEmpty())
            backend.scheduleFailure=false;check(enable().startsWith("Automatic sleep On"))
            backend.dispatchFailure=true
            fire(true,backend.scheduled.getValue(true))
            check(!scheduler.enabled && !prefs.contains("automaticClaim") && played==1)
            backend.dispatchFailure=false

            check(enable().startsWith("Automatic sleep On"))
            val interrupted=backend.scheduled.getValue(true)
            fire(true,interrupted)
            check(prefs.contains("automaticClaim"))
            scheduler.refresh()
            check(!scheduler.enabled && store.state==SleepState(SleepMode.UNKNOWN,true))
            check(!prefs.contains("automaticClaim") && played==1)
            check(enable().startsWith("Automatic sleep On"))
            val stale=backend.scheduled.getValue(true)
            check(store.save(SleepRoutine(1260,420,2,1),fit,store.editorRevision()).startsWith("Routine saved"))
            check(!scheduler.enabled && scheduler.enable().contains("review"))
            check(enable().startsWith("Automatic sleep On"))
            val count=backend.dispatched.size
            fire(true,stale);check(backend.dispatched.size==count)
            val stopped=backend.scheduled.getValue(true)
            scheduler.disable();fire(true,stopped)
            check(backend.dispatched.size==count && !scheduler.enabled && backend.scheduled.isEmpty())
            check(core.getString("saved",null)==saved)
        } finally { check(core.edit().clear().commit());check(prefs.edit().clear().commit()) }
    }
}
