package com.heysig.app

import android.content.Context
import com.heysig.control.*
import com.heysig.sleep.*

/** Isolated settings only. This adapter has no playback or scheduler dependency. */
object SleepRoutineAcceptance {
    fun run(context: Context) {
        val prefs=context.getSharedPreferences("acceptance-isolated-sleep",0)
        check(prefs.edit().clear().commit())
        val fit=Fitting(12,12,12,listOf(Program(1,"Universal",TinnitusCapability.SUPPORTED),Program(2,"Sleep",TinnitusCapability.UNKNOWN)))
        val settings=SleepRoutine(1320,420,2,1)
        try {
            var store=SleepRoutineStore(prefs)
            check(store.status(fit).contains("suggestions"))
            check(store.choices(fit).sleepSlot==null && store.choices(fit).wakeSlot==null)
            check(!store.save(SleepRoutine(),fit,store.editorRevision()).startsWith("Routine saved"))
            check(!prefs.contains("settings"))
            check(store.save(settings,fit,store.editorRevision()).startsWith("Routine saved"))
            check(store.status(fit).contains("Off"))
            check(!prefs.contains("enabled"))
            store=SleepRoutineStore(prefs)
            check(store.choices(fit)==settings)
            check(store.preview(fit).contains("No alarms"))
            check(store.handle(SleepCommand.Enable,fit,"phone",100).contains("unavailable"))
            check(store.status(fit).contains("Off"))
            store.handle(SleepCommand.Configure,fit,"watch",100)
            check(store.handle(SleepCommand.Configure,fit,"phone",100).contains("device"))
            check(store.handle(SleepCommand.Time(true,1260),fit,"phone",101).contains("device"))
            check(store.handle(SleepCommand.Time(true,1260),fit,"watch",101).contains("updated"))
            check(store.choices(fit)==settings)
            check(store.handle(SleepCommand.Save,fit,"watch",300_101).contains("expired"))
            check(store.choices(fit)==settings)
            store.handle(SleepCommand.Configure,fit,"phone",1)
            store.handle(SleepCommand.Time(true,1260),fit,"phone",2)
            check(store.handle(SleepCommand.Save,fit,"phone",3).startsWith("Routine saved"))
            check(SleepRoutineStore(prefs).choices(fit).sleepMinute==1260)
            val stale=store.editorRevision()
            store.handle(SleepCommand.Configure,fit,"phone",10)
            store.handle(SleepCommand.Time(true,1200),fit,"phone",11)
            store.handle(SleepCommand.Cancel,fit,"phone",12)
            check(store.choices(fit).sleepMinute==1260)
            check(store.save(settings,fit,stale).startsWith("Routine saved"))
            check(store.save(settings.copy(wakeMinute=480),fit,stale).contains("changed elsewhere"))
            store.setupChanged(fit.copy(controlAddress=13));store.setupChanged(fit)
            check(store.status(fit).contains("review"))
            check(!store.preview(fit).contains("Next sleep"))
            check(store.save(settings,fit,store.editorRevision()).startsWith("Routine saved"))
            check(store.preview(fit).contains("Next sleep"))
            val corrupt="{broken"
            check(prefs.edit().putString("settings",corrupt).commit())
            store=SleepRoutineStore(prefs)
            check(store.status(fit).contains("needs review"))
            check(prefs.getString("settings",null)==corrupt)
        } finally { check(prefs.edit().clear().commit()) }
    }
}
