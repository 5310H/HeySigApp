package com.heysig.app

import android.content.Context
import com.heysig.control.Fitting
import com.heysig.sleep.*
import org.json.JSONObject
import java.time.*

/** Routine choices and durable command state. Android reminder delivery is separate. */
class SleepRoutineStore internal constructor(private val prefs: android.content.SharedPreferences) {
    constructor(context: Context): this(context.applicationContext.getSharedPreferences("heysig-sleep-v1",Context.MODE_PRIVATE))
    private data class Draft(val choices: SleepRoutine, val origin: String, val fitting: String,
        val revision: Long, val expires: Long)
    private var draft: Draft?=null
    private var error: String?=null
    private var saved: SleepRoutine?=null
    private var savedFitting: String?=null
    var state: SleepState = runCatching {
        SleepState(SleepMode.valueOf(prefs.getString("mode","AWAKE")!!),prefs.getBoolean("override",false),prefs.getBoolean("transition",false)).recovered()
    }.getOrElse { SleepState(SleepMode.UNKNOWN,true) }; private set
    fun transition(next: SleepState) {
        check(prefs.edit().putString("mode",next.mode.name).putBoolean("override",next.override).putBoolean("transition",next.pending).commit()) { "Could not save sleep state; no routine command enabled" }
        state=next
    }
    fun target(sleep: Boolean,fit: Fitting): Int {
        val routine=requireNotNull(validSaved(fit)) { "Review and save a valid sleep routine first" }
        return requireNotNull(if(sleep) routine.sleepSlot else routine.wakeSlot)
    }
    init {
        try {
            prefs.getString("settings",null)?.let { raw ->
                val json=JSONObject(raw);require(json.getInt("version")==1)
                val value=SleepRoutine(json.getInt("sleepMinute"),json.getInt("wakeMinute"),json.getInt("sleepSlot"),json.getInt("wakeSlot"))
                require(value.sleepMinute in 0..1439 && value.wakeMinute in 0..1439 && value.sleepMinute != value.wakeMinute)
                saved=value;savedFitting=json.getString("fitting")
            }
        } catch(e: Exception) { error="Stored routine needs review. Original data preserved; save reviewed choices to replace it." }
    }
    private fun revision()=prefs.getLong("revision",0)
    fun setupChanged(fit: Fitting) {
        if(saved != null && savedFitting != fit.toString() && !prefs.getBoolean("invalidated",false)) {
            check(prefs.edit().putBoolean("invalidated",true).putBoolean("reminders",false).commit()) { "Could not invalidate changed routine" }
            transition(SleepState(SleepMode.UNKNOWN,true))
            cancel()
        }
    }
    private fun validSaved(fit: Fitting): SleepRoutine? {
        setupChanged(fit)
        return saved?.takeIf { !prefs.getBoolean("invalidated",false) && savedFitting==fit.toString() && runCatching { it.validate(fit) }.isSuccess }
    }
    fun choices(fit: Fitting)=validSaved(fit) ?: saved ?: SleepRoutine()
    fun cancel() { draft=null }
    val hasDraft get()=draft != null
    fun isDraftFor(origin: String)=draft?.origin==origin
    private fun describe(value: SleepRoutine,fit: Fitting): String {
        fun name(slot: Int?)=fit.programs.find { it.slot==slot }?.name ?: "choose program"
        return "Sleep ${SleepRoutine.clock(value.sleepMinute)}: ${name(value.sleepSlot)}. Wake ${SleepRoutine.clock(value.wakeMinute)}: ${name(value.wakeSlot)}."
    }
    fun dashboard(fit: Fitting): String {
        val routine=validSaved(fit) ?: return "Needs setup · Choose and save bedtime and morning programs. " +
            (if(saved != null) "Your saved routine needs review after an aid setup change." else "")
        val enabled=prefs.getBoolean("reminders",false)
        val stateLabel=when {
            !enabled -> "Reminders Off · Enable reminders below."
            state.override -> "Paused · Choose Resume reminders below."
            else -> "Reminders On · Each program switch needs your confirmation."
        }
        val next=if(enabled && !state.override) {
            val now=ZonedDateTime.now()
            val event=listOf("Bedtime" to routine.sleepMinute,"Morning" to routine.wakeMinute)
                .mapNotNull { (label,minute) -> SleepPreview.next(minute,now)?.let { label to it } }
                .minByOrNull { it.second.toInstant() }
            event?.let { "\nNext reminder: ${it.first}, ${it.second.format(java.time.format.DateTimeFormatter.ofPattern("EEE h:mm a",java.util.Locale.getDefault()))} (${now.zone.id}). Android may delay it." }.orEmpty()
        } else ""
        return "$stateLabel\n${describe(routine,fit)}$next"
    }
    fun status(fit: Fitting): String {
        val valid=validSaved(fit)
        return "Sleep mode: ${state.mode.name.lowercase()}. ${if(state.override) "Manual override On. " else ""}Reminders ${if(prefs.getBoolean("reminders",false)) "On" else "Off"}. " + (error ?: if(saved==null) "No saved routine. 22:00 and 07:00 are suggestions only." else if(valid==null) "Setup changed; review and save the routine again." else describe(valid,fit)) +
            " " + prefs.getString("reminderResult","").orEmpty() + " Program switching requires your tap."
    }
    fun preview(fit: Fitting, now: ZonedDateTime=ZonedDateTime.now()): String {
        val value=validSaved(fit) ?: return "Save a valid routine to preview upcoming times."
        return SleepPreview.describe(value,now).let { if(prefs.getBoolean("reminders",false)) it.replace("Preview only","Reminder times").replace("No alarms are scheduled.","Android may delay reminders; delivery more than 15 minutes late is skipped.") else it }
    }
    fun save(value: SleepRoutine, fit: Fitting, expectedRevision: Long): String {
        try {
            value.validate(fit)
            check(draft == null || draft?.choices == value) { "A voice sleep draft is active. Review/save it by voice or say cancel sleep setup before saving different screen choices." }
            check(expectedRevision==revision()) { "Routine changed elsewhere. Review it again before saving." }
            val json=JSONObject().put("version",1).put("sleepMinute",value.sleepMinute).put("wakeMinute",value.wakeMinute)
                .put("sleepSlot",value.sleepSlot).put("wakeSlot",value.wakeSlot).put("fitting",fit.toString())
            check(prefs.edit().putString("settings",json.toString()).putLong("revision",expectedRevision+1).putBoolean("invalidated",false).putBoolean("reminders",false).commit()) { "Could not save routine; previous choices retained" }
            saved=value;savedFitting=fit.toString();error=null;draft=null
            return "Routine saved. ${describe(value,fit)} Schedule remains Off. No command sent."
        } catch(e: Exception) { return e.message ?: "Routine could not be saved" }
    }
    fun editorRevision()=revision()
    fun handle(command: SleepCommand,fit: Fitting,origin: String,now: Long=android.os.SystemClock.elapsedRealtime()): String {
        if(command==SleepCommand.Override || command==SleepCommand.Resume) return runCatching {
            transition(if(command==SleepCommand.Override) state.manual() else state.resume())
            if(command==SleepCommand.Override) "Sleep automation paused. No command sent." else "Sleep automation resumed for future occurrences. No command sent."
        }.getOrElse { it.message ?: "Sleep state could not be saved" }
        if(command is SleepCommand.Now) return "Open HeySig to run the saved sleep or wake program."
        if(command==SleepCommand.Disable) { cancel();return if(prefs.edit().putBoolean("reminders",false).commit()) "Sleep reminders Off. No pending sleep action." else "Could not disable reminders; try again" }
        if(command==SleepCommand.Enable) return "Schedule unavailable: automatic overnight switching has not passed acceptance. Choices can be saved and previewed; nothing is armed."
        if(command==SleepCommand.Read) return status(fit)+" "+preview(fit)
        if(command==SleepCommand.Cancel) { cancel();return "Sleep draft discarded. Saved choices retained. Nothing sent." }
        if(command is SleepCommand.Invalid) return command.reason
        if(command==SleepCommand.Configure) {
            if(draft?.let { it.origin != origin && now <= it.expires } == true) return "Finish or cancel the sleep draft on the device that started it."
            draft=Draft(choices(fit),origin,fit.toString(),revision(),now+5*60_000)
            return "Sleep routine draft. ${describe(draft!!.choices,fit)} Say set sleep time to ten PM, set wake time to seven AM, set sleep program to a configured name, set wake program to a configured name, review sleep routine, then save sleep routine. Save leaves scheduling Off."
        }
        val current=draft ?: return "Say configure sleep first. No changes saved."
        if(current.origin != origin) return "Finish or cancel the sleep draft on the device that started it."
        if(now>current.expires || current.fitting != fit.toString() || current.revision != revision()) {
            cancel();return "Sleep draft expired or setup changed. Say configure sleep to review again."
        }
        if(command==SleepCommand.Review) return "Draft. ${describe(current.choices,fit)} Say save sleep routine or cancel sleep setup. Saving leaves scheduling Off."
        if(command==SleepCommand.Save) return save(current.choices,fit,current.revision)
        val next=when(command) {
            is SleepCommand.Time -> if(command.sleep) current.choices.copy(sleepMinute=command.minute) else current.choices.copy(wakeMinute=command.minute)
            is SleepCommand.Program -> if(command.sleep) current.choices.copy(sleepSlot=command.slot) else current.choices.copy(wakeSlot=command.slot)
            else -> return "Say review sleep routine, save sleep routine or cancel sleep setup."
        }
        draft=current.copy(choices=next)
        return "Draft updated. ${describe(next,fit)} Not saved."
    }
}
