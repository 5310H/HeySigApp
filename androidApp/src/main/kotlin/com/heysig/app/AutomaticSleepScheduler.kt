package com.heysig.app

import android.app.*
import android.content.*
import android.os.Build
import com.heysig.sleep.*
import java.time.ZonedDateTime
import java.util.UUID

/** Exact, explicitly reviewed program schedule. Receivers claim work but never play audio. */
class AutomaticSleepScheduler internal constructor(private val context: Context,
    private val prefs: SharedPreferences=context.getSharedPreferences("heysig-sleep-v1",Context.MODE_PRIVATE),
    private val runtimeProvider: () -> ManualRuntime={ ManualRuntime.get(context) },
    private val backend: Backend?=null) {
    internal interface Backend {
        fun exactAllowed(): Boolean
        fun notificationsAllowed(): Boolean
        fun cancel(sleep: Boolean)
        fun schedule(sleep: Boolean,due: Long,generation: Long,revision: Long)
        fun dispatch(token: String)
        fun stopPlayback()
        fun status(enabled: Boolean,text: String)
    }
    data class Claim(val token: String,val sleep: Boolean,val slot: Int,val due: Long,val generation: Long,val revision: Long)
    private val runtime get()=runtimeProvider()
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    val enabled get()=prefs.getBoolean("automaticEnabled",false)
    private val generation get()=prefs.getLong("automaticGeneration",0)
    fun exactAllowed()=backend?.exactAllowed() ?: (Build.VERSION.SDK_INT<31 || alarms.canScheduleExactAlarms())
    fun notificationsAllowed(): Boolean {
        backend?.let { return it.notificationsAllowed() }
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Automatic sleep routine",NotificationManager.IMPORTANCE_LOW))
        return notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    private fun key(sleep: Boolean)=if(sleep) "automaticSleepDue" else "automaticWakeDue"
    private fun alarm(sleep: Boolean,due: Long=0,gen: Long=generation,revision: Long=0)=PendingIntent.getBroadcast(context,if(sleep) 820 else 821,
        Intent(context,AutomaticSleepReceiver::class.java).setAction(if(sleep) SLEEP else WAKE)
            .putExtra("due",due).putExtra("generation",gen).putExtra("revision",revision),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun valid()=runtime.fitting?.let { fit -> runCatching { runtime.sleepRoutine.target(true,fit);runtime.sleepRoutine.target(false,fit) }.isSuccess }==true
    fun enable(reviewedRevision: Long?=null): String {
        if(!valid()) return "Review and save both sleep programs first. Automatic stays Off."
        if(runtime.busy.get() || runtime.sleepRoutine.state.pending) return "Wait for the current command before starting automatic sleep."
        val revision=runtime.sleepRoutine.editorRevision()
        if((reviewedRevision ?: prefs.getLong("automaticReviewedRevision",-1))!=revision)
            return "On the phone, review Start automatic routine before enabling scheduled program changes."
        if(!exactAllowed()) return "Allow Alarms & reminders for HeySig on the phone, then start automatic again."
        if(!notificationsAllowed()) return "Allow HeySig automatic sleep notifications, then start automatic again."
        return try {
            check(prefs.edit().putBoolean("automaticEnabled",true).putBoolean("reminders",false).putLong("automaticReviewedRevision",revision)
                .putLong("automaticGeneration",Math.addExact(generation,1)).remove("automaticClaim").commit())
            if(backend==null) SleepReminders(context).setEnabled(false)
            runtime.sleepRoutine.transition(runtime.sleepRoutine.state.resume())
            plan(ZonedDateTime.now())
            publishStatus()
            "Automatic sleep On. Future bedtime and morning programs will play without a tap, including while locked."
        } catch(e: Exception) { disable("Automatic could not start. No program changed.",report=true);"Automatic could not start. Review permissions and the saved routine." }
    }
    fun disable(result: String="Automatic sleep Off. No future automatic switches.",report: Boolean=false): String {
        check(prefs.edit().putBoolean("automaticEnabled",false).putLong("automaticGeneration",Math.addExact(generation,1))
            .remove("automaticSleepDue").remove("automaticWakeDue").remove("automaticClaim").remove("automaticClaimStarted")
            .putString("automaticResult",result).commit())
        cancelAlarms()
        if(backend!=null) backend.stopPlayback() else AutomaticSleepService.running?.cancelPlayback()
        publishStatus()
        if(report) reportOutcome(result)
        return result
    }
    private fun cancelAlarms() { for(sleep in listOf(true,false)) { if(backend!=null) backend.cancel(sleep) else alarms.cancel(alarm(sleep)) } }
    private fun plan(now: ZonedDateTime) {
        check(enabled && valid() && exactAllowed() && notificationsAllowed())
        check(prefs.getLong("automaticReviewedRevision",-1)==runtime.sleepRoutine.editorRevision())
        cancelAlarms()
        scheduleOne(true,now);scheduleOne(false,now)
    }
    private fun scheduleOne(sleep: Boolean,now: ZonedDateTime) {
        val fit=requireNotNull(runtime.fitting)
        val routine=runtime.sleepRoutine.choices(fit)
        val due=checkNotNull(SleepPreview.next(if(sleep) routine.sleepMinute else routine.wakeMinute,now)).toInstant().toEpochMilli()
        check(prefs.edit().putLong(key(sleep),due).commit())
        val revision=runtime.sleepRoutine.editorRevision()
        if(backend!=null) backend.schedule(sleep,due,generation,revision)
        else alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarm(sleep,due,generation,revision))
    }
    /** Clock/boot/update recovery only replans future occurrences; never resumes a claim. */
    fun refresh() {
        if(!enabled) { cancelAlarms();publishStatus();return }
        if(prefs.contains("automaticClaim")) {
            runtime.sleepRoutine.transition(SleepState(SleepMode.UNKNOWN,true))
            disable("An automatic action was interrupted. Review the aids and start automatic again; nothing was replayed.",report=true)
            return
        }
        try {
            check(prefs.edit().putLong("automaticGeneration",Math.addExact(generation,1)).commit())
            plan(ZonedDateTime.now());publishStatus()
        } catch(e: Exception) { disable("Automatic scheduling blocked. Review Alarms & reminders, notifications and routine choices.",report=true) }
    }
    fun fire(sleep: Boolean,due: Long,gen: Long,revision: Long,now: Long=System.currentTimeMillis()) {
        val current=gen==generation && revision==runtime.sleepRoutine.editorRevision() &&
            prefs.getLong("automaticReviewedRevision",-1)==revision && prefs.getLong(key(sleep),0)==due
        val decision=automaticSleepDecision(due,now,enabled,current,exactAllowed() && notificationsAllowed(),valid(),runtime.sleepRoutine.state)
        if(decision in listOf(AutomaticSleepDecision.DISABLED,AutomaticSleepDecision.STALE,AutomaticSleepDecision.EARLY)) return
        if(decision!=AutomaticSleepDecision.RUN) {
            if(decision==AutomaticSleepDecision.INVALID) { disable("Automatic action blocked: review permissions and saved routine.",report=true);return }
            check(prefs.edit().remove(key(sleep)).putString("automaticResult","${if(sleep) "Bedtime" else "Morning"} skipped: ${decision.name.lowercase()}. No automatic retry.").commit())
            scheduleOne(sleep,java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()));publishStatus();return
        }
        if(prefs.contains("automaticClaim")) { disable("Overlapping automatic action blocked. Review the routine.",report=true);return }
        val token=UUID.randomUUID().toString()
        check(prefs.edit().remove(key(sleep)).putString("automaticClaim",token).putBoolean("automaticClaimStarted",false)
            .putBoolean("automaticClaimSleep",sleep).putLong("automaticClaimDue",due).putLong("automaticClaimGeneration",gen)
            .putLong("automaticClaimRevision",revision).commit())
        try {
            scheduleOne(sleep,java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()))
            if(backend!=null) backend.dispatch(token) else AutomaticSleepService.launch(context,token)
        } catch(e: Exception) { disable("Automatic playback could not start. No retry; open the sleep routine to review.",report=true) }
    }
    fun takeClaim(token: String,now: Long=System.currentTimeMillis()): Claim? {
        if(token.isEmpty() || prefs.getString("automaticClaim",null)!=token || prefs.getBoolean("automaticClaimStarted",false)) return null
        val due=prefs.getLong("automaticClaimDue",0)
        val gen=prefs.getLong("automaticClaimGeneration",-1)
        val revision=prefs.getLong("automaticClaimRevision",-1)
        val decision=automaticSleepDecision(due,now,enabled,gen==generation && revision==runtime.sleepRoutine.editorRevision() && prefs.getLong("automaticReviewedRevision",-1)==revision,
            exactAllowed() && notificationsAllowed(),valid(),runtime.sleepRoutine.state)
        if(decision!=AutomaticSleepDecision.RUN) { disable("Automatic playback blocked before starting: ${decision.name.lowercase()}. No retry.",report=true);return null }
        val sleep=prefs.getBoolean("automaticClaimSleep",true)
        val slot=runtime.sleepRoutine.target(sleep,requireNotNull(runtime.fitting))
        if(!prefs.edit().putBoolean("automaticClaimStarted",true).commit()) return null
        return Claim(token,sleep,slot,due,gen,revision)
    }
    fun authorized(claim: Claim)=enabled && claim.generation==generation && claim.revision==runtime.sleepRoutine.editorRevision() && prefs.getLong("automaticReviewedRevision",-1)==claim.revision &&
        prefs.getString("automaticClaim",null)==claim.token && exactAllowed() && notificationsAllowed() && !runtime.sleepRoutine.state.override
    fun finish(claim: Claim,text: String,success: Boolean) {
        if(prefs.getString("automaticClaim",null)!=claim.token) return
        check(prefs.edit().remove("automaticClaim").remove("automaticClaimStarted").putString("automaticResult",text).commit())
        if(!success) disable(text,report=true) else publishStatus()
    }
    fun publishStatus() {
        val text=(runtime.fitting?.let { runtime.sleepRoutine.dashboard(it) } ?: "Review saved sleep routine")+
            prefs.getString("automaticResult",null)?.let { "\nLast result: $it" }.orEmpty()
        if(backend!=null) backend.status(enabled,text)
        else if(!enabled) notifications.cancel(NOTIFICATION)
        else if(notificationsAllowed()) notifications.notify(NOTIFICATION,notification(text))
    }
    private fun reportOutcome(text: String) {
        if(backend!=null) { backend.status(false,text);return }
        if(!notificationsAllowed()) return
        val open=PendingIntent.getActivity(context,827,Intent(context,HomeActivity::class.java).putExtra("openSleep",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(827,Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Automatic sleep needs review").setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
    }
    fun notification(text: String): Notification {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Automatic sleep routine",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(context,826,Intent(context,HomeActivity::class.java).putExtra("openSleep",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getBroadcast(context,826,Intent(context,AutomaticSleepReceiver::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("HeySig automatic sleep")
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).addAction(Notification.Action.Builder(null,"Stop automatic",stop).build()).build()
    }
    companion object {
        const val SLEEP="com.heysig.app.AUTO_SLEEP"
        const val WAKE="com.heysig.app.AUTO_WAKE"
        const val STOP="com.heysig.app.STOP_AUTO_SLEEP"
        const val CHANNEL="heysig-sleep-automatic"
        const val NOTIFICATION=826
    }
}

class AutomaticSleepReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val scheduler=AutomaticSleepScheduler(context)
        runCatching {
            when(intent.action) {
                AutomaticSleepScheduler.SLEEP,AutomaticSleepScheduler.WAKE -> scheduler.fire(intent.action==AutomaticSleepScheduler.SLEEP,
                    intent.getLongExtra("due",0),intent.getLongExtra("generation",-1),intent.getLongExtra("revision",-1))
                AutomaticSleepScheduler.STOP -> scheduler.disable()
                else -> scheduler.refresh()
            }
        }.onFailure { runCatching { scheduler.disable("Automatic sleep failed before dispatch. Open the routine to review; no retry.",report=true) } }
    }
}
