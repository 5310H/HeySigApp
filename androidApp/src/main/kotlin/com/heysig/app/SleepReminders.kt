package com.heysig.app

import android.app.*
import android.content.*
import com.heysig.sleep.SleepPreview
import com.heysig.sleep.SleepReminderDecision
import com.heysig.sleep.sleepReminderDecision
import java.time.ZonedDateTime

/** Inexact local reminders only. No receiver may execute an aid command. */
class SleepReminders internal constructor(private val context: Context,
    private val prefs: SharedPreferences=context.getSharedPreferences("heysig-sleep-v1",Context.MODE_PRIVATE),
    private val runtimeProvider: () -> ManualRuntime={ ManualRuntime.get(context) },
    private val backend: Backend?=null) {
    /** Isolated acceptance checks replace platform effects, never the executor. */
    internal interface Backend {
        fun available(): Boolean
        fun cancel(sleep: Boolean)
        fun schedule(sleep: Boolean,due: Long)
        fun post(sleep: Boolean,test: Boolean)
    }
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    private fun alarm(sleep: Boolean)=PendingIntent.getBroadcast(context,if(sleep) 810 else 811,
        Intent(context,SleepReminderReceiver::class.java).setAction(if(sleep) SLEEP else WAKE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun available(): Boolean {
        backend?.let { return it.available() }
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Sleep routine reminders",NotificationManager.IMPORTANCE_DEFAULT))
        return notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun setEnabled(enabled: Boolean): String {
        val runtime=runtimeProvider()
        if(enabled) {
            val fit=runtime.fitting ?: return "Save aid setup first"
            runCatching { runtime.sleepRoutine.target(true,fit);runtime.sleepRoutine.target(false,fit) }.getOrElse { return it.message ?: "Review routine first" }
            if(!available()) return "Allow HeySig notifications before enabling sleep reminders"
        }
        if(!prefs.edit().putBoolean("reminders",enabled).commit()) return "Could not save reminder choice"
        return try {
            if(enabled) check(refresh()) { "Reminder scheduling unavailable" } else refresh()
            if(enabled) runtime.sleepRoutine.transition(runtime.sleepRoutine.state.resume())
            if(enabled) "Sleep reminders On. Tap a reminder, then Sleep now or Wake now to switch. Android may delay reminders." else "Sleep reminders Off."
        }
        catch(e: Exception) { prefs.edit().putBoolean("reminders",false).commit();runCatching { cancel() };"Reminders could not be scheduled. Open sleep settings to try again." }
    }
    private fun cancel() {
        for(sleep in listOf(true,false)) {
            if(backend!=null) backend.cancel(sleep)
            else { alarms.cancel(alarm(sleep));notifications.cancel(if(sleep) 810 else 811) }
        }
        check(prefs.edit().remove("sleepDue").remove("wakeDue").commit()) { "Could not clear previous reminder times" }
    }
    fun refresh(now: ZonedDateTime=ZonedDateTime.now()): Boolean {
        cancel()
        val runtime=runtimeProvider()
        val fit=runtime.fitting ?: return false
        if(!prefs.getBoolean("reminders",false) || !available()) return false
        if(runCatching { runtime.sleepRoutine.target(true,fit);runtime.sleepRoutine.target(false,fit) }.isFailure) return false
        val routine=runtime.sleepRoutine.choices(fit)
        for(sleep in listOf(true,false)) {
            val due=checkNotNull(SleepPreview.next(if(sleep) routine.sleepMinute else routine.wakeMinute,now)).toInstant().toEpochMilli()
            check(prefs.edit().putLong(if(sleep) "sleepDue" else "wakeDue",due).commit())
            if(backend!=null) backend.schedule(sleep,due) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarm(sleep))
        }
        return true
    }
    fun testReminder(): String = try {
        if(!available()) "Allow HeySig sleep notifications in Android settings, then try again."
        else {
            postReminder(true,test=true)
            "Test reminder posted. Open notifications and tap Sleep reminder test. No program changed; your schedule is unchanged."
        }
    } catch(e: Exception) { "Test reminder could not be posted. Check Android notification settings. No program changed." }

    private fun postReminder(sleep: Boolean,test: Boolean=false) {
        backend?.let { it.post(sleep,test);return }
        val id=if(test) 812 else if(sleep) 810 else 811
        val open=PendingIntent.getActivity(context,id,
            Intent(context,HomeActivity::class.java).putExtra("openSleep",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(id,Notification.Builder(context,CHANNEL)
            .setSmallIcon(com.heysig.app.R.drawable.ic_launcher_foreground)
            .setContentTitle(if(test) "Sleep reminder test" else if(sleep) "Bedtime routine" else "Wake-up routine")
            .setContentText(if(test) "Tap to open your sleep routine. No program will change." else "Tap to open HeySig and choose ${if(sleep) "Sleep now" else "Wake now"}.")
            .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
    }
    fun deliver(sleep: Boolean,now: Long=System.currentTimeMillis()) {
        val key=if(sleep) "sleepDue" else "wakeDue"
        val due=prefs.getLong(key,0)
        if(due==0L || now<due || !prefs.getBoolean("reminders",false)) return
        // Consume first; duplicates, process death and failed notification delivery never replay.
        if(!prefs.edit().remove(key).commit()) return
        val runtime=runtimeProvider()
        val valid=runtime.fitting?.let { runCatching { runtime.sleepRoutine.target(sleep,it) }.isSuccess }==true
        val decision=sleepReminderDecision(due,now,true,valid,runtime.sleepRoutine.state)
        refresh()
        if(decision!=SleepReminderDecision.DELIVER || !available()) {
            prefs.edit().putString("reminderResult",if(decision==SleepReminderDecision.PAUSED) "Routine paused by manual override." else "Reminder missed or unavailable. No program changed.").commit()
            return
        }
        postReminder(sleep)
        prefs.edit().putString("reminderResult",if(sleep) "Bedtime reminder delivered." else "Wake-up reminder delivered.").commit()
    }
    companion object {
        private const val CHANNEL="heysig-sleep-reminders"
        const val SLEEP="com.heysig.app.SLEEP_REMINDER"
        const val WAKE="com.heysig.app.WAKE_REMINDER"
    }
}

class SleepReminderReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        runCatching {
            val reminders=SleepReminders(context)
            when(intent.action) {
                SleepReminders.SLEEP -> reminders.deliver(true)
                SleepReminders.WAKE -> reminders.deliver(false)
                Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED -> reminders.refresh()
            }
        }.onFailure {
            context.getSharedPreferences("heysig-sleep-v1",Context.MODE_PRIVATE).edit()
                .putString("reminderResult","Reminder delivery or rescheduling failed. Open sleep settings to review; no program changed.").commit()
        }
    }
}
