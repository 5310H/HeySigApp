package com.heysig.app

import android.app.*
import android.content.*
import com.heysig.sleep.SleepPreview
import com.heysig.sleep.SleepReminderDecision
import com.heysig.sleep.sleepReminderDecision
import java.time.ZonedDateTime

/** Inexact local reminders only. No receiver may execute an aid command. */
class SleepReminders(private val context: Context) {
    private val prefs=context.getSharedPreferences("heysig-sleep-v1",Context.MODE_PRIVATE)
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    private fun alarm(sleep: Boolean)=PendingIntent.getBroadcast(context,if(sleep) 810 else 811,
        Intent(context,SleepReminderReceiver::class.java).setAction(if(sleep) SLEEP else WAKE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun available(): Boolean {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Sleep routine reminders",NotificationManager.IMPORTANCE_DEFAULT))
        return notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun setEnabled(enabled: Boolean): String {
        val runtime=ManualRuntime.get(context)
        if(enabled) {
            val fit=runtime.fitting ?: return "Save aid setup first"
            runCatching { runtime.sleepRoutine.target(true,fit);runtime.sleepRoutine.target(false,fit) }.getOrElse { return it.message ?: "Review routine first" }
            if(!available()) return "Allow HeySig notifications before enabling sleep reminders"
        }
        if(!prefs.edit().putBoolean("reminders",enabled).commit()) return "Could not save reminder choice"
        return try {
            refresh()
            if(enabled) runtime.sleepRoutine.transition(runtime.sleepRoutine.state.resume())
            if(enabled) "Sleep reminders On. Tap a reminder, then Sleep now or Wake now to switch. Android may delay reminders." else "Sleep reminders Off."
        }
        catch(e: Exception) { prefs.edit().putBoolean("reminders",false).commit();cancel();"Reminders could not be scheduled. Open sleep settings to try again." }
    }
    private fun cancel() {
        for(sleep in listOf(true,false)) { alarms.cancel(alarm(sleep));notifications.cancel(if(sleep) 810 else 811) }
    }
    fun refresh(now: ZonedDateTime=ZonedDateTime.now()) {
        cancel()
        val runtime=ManualRuntime.get(context)
        val fit=runtime.fitting ?: return
        if(!prefs.getBoolean("reminders",false) || !available()) return
        if(runCatching { runtime.sleepRoutine.target(true,fit);runtime.sleepRoutine.target(false,fit) }.isFailure) return
        val routine=runtime.sleepRoutine.choices(fit)
        for(sleep in listOf(true,false)) {
            val due=SleepPreview.next(if(sleep) routine.sleepMinute else routine.wakeMinute,now)?.toInstant()?.toEpochMilli() ?: continue
            check(prefs.edit().putLong(if(sleep) "sleepDue" else "wakeDue",due).commit())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarm(sleep))
        }
    }
    fun deliver(sleep: Boolean,now: Long=System.currentTimeMillis()) {
        val key=if(sleep) "sleepDue" else "wakeDue"
        val due=prefs.getLong(key,0)
        if(due==0L || now<due || !prefs.getBoolean("reminders",false)) return
        // Consume first; duplicates, process death and failed notification delivery never replay.
        if(!prefs.edit().remove(key).commit()) return
        val runtime=ManualRuntime.get(context)
        val valid=runtime.fitting?.let { runCatching { runtime.sleepRoutine.target(sleep,it) }.isSuccess }==true
        val decision=sleepReminderDecision(due,now,true,valid,runtime.sleepRoutine.state)
        refresh()
        if(decision!=SleepReminderDecision.DELIVER || !available()) {
            prefs.edit().putString("reminderResult",if(decision==SleepReminderDecision.PAUSED) "Routine paused by manual override." else "Reminder missed or unavailable. No program changed.").commit()
            return
        }
        val open=PendingIntent.getActivity(context,if(sleep) 810 else 811,
            Intent(context,HomeActivity::class.java).putExtra("openSleep",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(if(sleep) 810 else 811,Notification.Builder(context,CHANNEL)
            .setSmallIcon(com.heysig.app.R.drawable.ic_launcher_foreground)
            .setContentTitle(if(sleep) "Bedtime routine" else "Wake-up routine")
            .setContentText("Tap to open HeySig and choose ${if(sleep) "Sleep now" else "Wake now"}.")
            .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
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
