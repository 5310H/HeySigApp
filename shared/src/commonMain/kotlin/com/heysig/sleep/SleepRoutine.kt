package com.heysig.sleep

import com.heysig.control.Fitting

/** Local clock minutes, never elapsed time. Choices alone cannot dispatch commands. */
data class SleepRoutine(val sleepMinute: Int = 22*60, val wakeMinute: Int = 7*60,
    val sleepSlot: Int? = null, val wakeSlot: Int? = null) {
    fun validate(fitting: Fitting) {
        require(sleepMinute in 0..1439 && wakeMinute in 0..1439) { "Choose valid local clock times" }
        require(sleepMinute != wakeMinute) { "Sleep and wake times must differ" }
        require(fitting.programs.any { it.slot == sleepSlot }) { "Choose a configured sleep program" }
        require(fitting.programs.any { it.slot == wakeSlot }) { "Choose a configured wake program" }
    }
    companion object {
        fun clock(minute: Int): String = "${(minute/60).toString().padStart(2,'0')}:${(minute%60).toString().padStart(2,'0')}"
    }
}
sealed class SleepCommand {
    data object Configure: SleepCommand()
    data object Read: SleepCommand()
    data object Review: SleepCommand()
    data object Save: SleepCommand()
    data object Cancel: SleepCommand()
    data object Enable: SleepCommand()
    data object Disable: SleepCommand()
    data class Now(val sleep: Boolean): SleepCommand()
    data object Resume: SleepCommand()
    data object Override: SleepCommand()
    data class Time(val sleep: Boolean, val minute: Int): SleepCommand()
    data class Program(val sleep: Boolean, val slot: Int): SleepCommand()
    data class Invalid(val reason: String): SleepCommand()
}
object SleepVoice {
    private val numbers = listOf("zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve")
    private fun number(value: String) = value.toIntOrNull() ?: numbers.indexOf(value).takeIf { it >= 0 }
    fun time(raw: String): Int? {
        val text=raw.trim().lowercase().replace("a.m.","am").replace("p.m.","pm")
        if(text == "noon") return 12*60
        if(text == "midnight") return 0
        val clock=Regex("^([0-9]{1,2}):([0-9]{2})$").matchEntire(text)
        if(clock != null) {
            val hour=clock.groupValues[1].toInt();val minute=clock.groupValues[2].toInt()
            return if(hour in 0..23 && minute in 0..59) hour*60+minute else null
        }
        val ampm=Regex("^([a-z]+|[0-9]{1,2})(?::([0-9]{2}))? ?(am|pm)$").matchEntire(text) ?: return null
        val hour=number(ampm.groupValues[1]) ?: return null
        val minute=ampm.groupValues[2].ifEmpty { "0" }.toInt()
        if(hour !in 1..12 || minute !in 0..59) return null
        return (hour%12 + if(ampm.groupValues[3] == "pm") 12 else 0)*60+minute
    }
    fun parse(raw: String, fitting: Fitting): SleepCommand? {
        val text=raw.trim().lowercase().replace(Regex("\\s+")," ").replace(Regex("[.!?]$"),"")
        val original=raw.trim().lowercase().replace(Regex("\\s+")," ")
        if(fitting.programs.any { it.name.lowercase() in listOf(original,text) }) return null
        Regex("^(?:set |choose )?(sleep|wake) program (?:to )?(.+)$").matchEntire(original)?.let { match ->
            fitting.programs.singleOrNull { it.name.lowercase()==match.groupValues[2] }?.let {
                return SleepCommand.Program(match.groupValues[1]=="sleep",it.slot)
            }
        }
        when(text) {
            "sleep now", "start sleep routine", "bedtime now" -> return SleepCommand.Now(true)
            "wake now", "wake up now", "end sleep routine" -> return SleepCommand.Now(false)
            "resume sleep routine", "resume sleep automation" -> return SleepCommand.Resume
            "pause sleep routine", "override sleep routine" -> return SleepCommand.Override
            "configure sleep", "sleep routine", "open sleep settings", "configure sleep routine" -> return SleepCommand.Configure
            "read sleep routine", "read sleep settings", "read sleep schedule", "sleep status" -> return SleepCommand.Read
            "review sleep routine", "review sleep settings" -> return SleepCommand.Review
            "save sleep routine", "save sleep settings" -> return SleepCommand.Save
            "cancel sleep setup", "discard sleep changes" -> return SleepCommand.Cancel
            "enable sleep schedule", "enable sleep routine", "start automatic routine", "enable automatic sleep" -> return SleepCommand.Enable
            "disable sleep schedule", "disable sleep routine", "stop automatic routine", "disable automatic sleep" -> return SleepCommand.Disable
        }
        Regex("^(?:set )?(sleep|wake) (?:time (?:to )?|at )(.+)$").matchEntire(text)?.let {
            val minute=time(it.groupValues[2]) ?: return SleepCommand.Invalid("Say an explicit time, such as ten PM, seven AM or 22:00. Include AM or PM when not using a 24-hour clock.")
            return SleepCommand.Time(it.groupValues[1] == "sleep",minute)
        }
        Regex("^(?:set |choose )?(sleep|wake) program (?:to )?(.+)$").matchEntire(text)?.let {
            val target=it.groupValues[2]
            val program=fitting.programs.singleOrNull { p -> p.name.lowercase() == target }
                ?: fitting.programs.singleOrNull { p -> p.slot == number(target.removePrefix("number ").removePrefix("slot ")) }
                ?: return SleepCommand.Invalid("Choose one configured program name or slot for the routine")
            return SleepCommand.Program(it.groupValues[1] == "sleep",program.slot)
        }
        return null
    }
}
