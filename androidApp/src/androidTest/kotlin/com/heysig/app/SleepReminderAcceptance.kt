package com.heysig.app

import android.content.Context
import com.heysig.control.*
import com.heysig.sleep.*
import java.util.concurrent.atomic.AtomicBoolean

/** Real isolated preferences and reminder adapter; fake alarms/notifications, no aid playback. */
object SleepReminderAcceptance {
    fun run(context: Context) {
        val core=context.getSharedPreferences("acceptance-reminder-core",0)
        val prefs=context.getSharedPreferences("acceptance-reminder-sleep",0)
        check(core.edit().clear().putString("fitting","""{"control":12,"left":12,"right":12,"name1":"Awake","tin1":"SUPPORTED","name2":"Sleep","tin2":"UNKNOWN"}""").commit())
        check(prefs.edit().clear().commit())
        val transport=object: CommandTransport {
            override fun inspect(allowLocked: Boolean) { error("Reminders must not inspect aid playback") }
            override fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean) { error("Reminders must not play aid commands") }
        }
        val runtime=ManualRuntime.isolated(core,transport,prefs)
        val backend=object: SleepReminders.Backend {
            var allowed=true
            var fail=false
            val scheduled=mutableMapOf<Boolean,Long>()
            val posted=mutableListOf<Pair<Boolean,Boolean>>()
            override fun available()=allowed
            override fun cancel(sleep: Boolean) { scheduled.remove(sleep) }
            override fun schedule(sleep: Boolean,due: Long) {
                check(!(fail && !sleep)) { "Injected morning scheduling failure" }
                scheduled[sleep]=due
            }
            override fun post(sleep: Boolean,test: Boolean) { posted.add(sleep to test) }
        }
        val reminders=SleepReminders(context,prefs,{runtime},backend)
        try {
            val fit=requireNotNull(runtime.fitting)
            val store=runtime.sleepRoutine
            check(!reminders.setEnabled(true).contains("reminders On"))
            check(backend.scheduled.isEmpty())
            check(store.save(SleepRoutine(1320,420,2,1),fit,store.editorRevision()).startsWith("Routine saved"))
            val saved=prefs.getString("settings",null)
            store.transition(store.state.manual())
            check(reminders.setEnabled(true).startsWith("Sleep reminders On"))
            check(!store.state.override && prefs.getBoolean("reminders",false))
            check(backend.scheduled.size==2)
            check(backend.scheduled[true]==prefs.getLong("sleepDue",0))
            check(backend.scheduled[false]==prefs.getLong("wakeDue",0))
            check(store.dashboard(fit).contains("Next reminder requested"))

            val due=System.currentTimeMillis()-1000
            check(prefs.edit().putLong("sleepDue",due).commit())
            reminders.deliver(true,due+1000)
            check(backend.posted==listOf(true to false))
            reminders.deliver(true,due+1000)
            check(backend.posted.size==1) // Already consumed; no duplicate notification.
            store.transition(store.state.manual())
            check(prefs.edit().putLong("sleepDue",due).commit())
            reminders.deliver(true,due+1000)
            check(backend.posted.size==1 && prefs.getString("reminderResult","")!!.contains("paused"))
            store.transition(store.state.resume())
            check(prefs.edit().putLong("sleepDue",due-901_000).commit())
            reminders.deliver(true,due+1000)
            check(backend.posted.size==1 && prefs.getString("reminderResult","")!!.contains("missed"))

            check(reminders.setEnabled(false).contains("Off"))
            check(backend.scheduled.isEmpty() && !prefs.contains("sleepDue") && !prefs.contains("wakeDue"))
            check(reminders.testReminder().startsWith("Test reminder posted"))
            check(backend.posted.last()==(true to true))
            check(!prefs.getBoolean("reminders",false) && backend.scheduled.isEmpty())
            check(prefs.getString("settings",null)==saved && runtime.requested==RequestedState())

            backend.fail=true
            check(reminders.setEnabled(true).contains("could not be scheduled"))
            check(!prefs.getBoolean("reminders",false) && backend.scheduled.isEmpty())
            check(!prefs.contains("sleepDue") && !prefs.contains("wakeDue"))
            backend.fail=false;backend.allowed=false
            val posts=backend.posted.size
            check(reminders.testReminder().contains("Allow HeySig"))
            check(backend.posted.size==posts && !prefs.getBoolean("reminders",false))
        } finally { check(core.edit().clear().commit());check(prefs.edit().clear().commit()) }
    }
}
