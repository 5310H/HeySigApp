package com.heysig.noise

/** Experimental relative-index settings. Thresholds must be reviewed on each device. */
data class NoiseSettings(
    val programs: List<Int?> = List(6) { null },
    val boundaries: List<Double> = listOf(-60.0, -50.0, -40.0, -30.0, -20.0),
    val margin: Double = 2.0,
    val confirmations: Int = 3,
    val cooldownSeconds: Int = 60,
    val captureSeconds: Int = 5,
    val waitSeconds: Int = 10,
    val freshnessSeconds: Int = 30,
    val sleepProgram: Int? = null
) {
    fun validate(configuredSlots: Set<Int>) {
        require(sleepProgram == null || sleepProgram in configuredSlots) { "Protected Sleep program is unavailable; review settings" }
        require(programs.size == 6) { "Choose six noise level assignments" }
        require(programs.all { it == null || it in configuredSlots }) { "A noise level refers to an unavailable program; review assignments" }
        require(boundaries.size == 5 && boundaries.all { it.isFinite() }) { "Enter five finite thresholds" }
        require(margin.isFinite() && margin > 0) { "Hysteresis margin must be positive" }
        require(boundaries.zipWithNext().all { (a,b) -> b-a > 2*margin }) { "Thresholds must increase with gaps greater than twice the margin" }
        require(confirmations in 1..20) { "Confirmations must be 1 to 20" }
        require(cooldownSeconds in 0..3600 && captureSeconds in 1..10 && waitSeconds in 1..300) { "Timing is outside supported bounds" }
        require(freshnessSeconds in 2..900 && freshnessSeconds > captureSeconds+waitSeconds) { "Freshness must exceed capture plus wait" }
    }
}

data class NoiseObservation(
    val level: Int? = null,
    val target: Int? = null,
    val count: Int = 0,
    val proposed: Int? = null,
    val lastSampleMillis: Long? = null
)

/** Pure Observe reducer: it can propose a program, but cannot request playback. */
fun observeNoise(previous: NoiseObservation, index: Double, now: Long, settings: NoiseSettings): NoiseObservation {
    require(index.isFinite() && now >= 0)
    val fresh = previous.lastSampleMillis?.let { now >= it && now-it <= settings.freshnessSeconds*1000L } ?: false
    var level = if(fresh) previous.level else null
    if(level == null) level = 1 + settings.boundaries.count { index > it }
    else {
        while(level < 6 && index > settings.boundaries[level-1]+settings.margin) level++
        while(level > 1 && index < settings.boundaries[level-2]-settings.margin) level--
    }
    val target = settings.programs[level-1]
    val count = if(target == null) 0 else if(fresh && previous.target == target) minOf(previous.count+1,settings.confirmations) else 1
    return NoiseObservation(level,target,count,if(count >= settings.confirmations) target else null,now)
}

sealed class NoiseCommand {
    data object Open : NoiseCommand()
    data object Read : NoiseCommand()
    data object Observe : NoiseCommand()
    data object Sample : NoiseCommand()
    data object Stop : NoiseCommand()
    data object Save : NoiseCommand()
    data object Discard : NoiseCommand()
    data class Assign(val level: Int, val slot: Int?) : NoiseCommand()
    data class SetField(val field: String, val value: Double) : NoiseCommand()
    data object Automatic : NoiseCommand()
    data object Preview : NoiseCommand()
    data object TrialAutomatic : NoiseCommand()
    data object CalibrationStart : NoiseCommand()
    data object CalibrationConfirmStart : NoiseCommand()
    data object CalibrationSample : NoiseCommand()
    data object CalibrationReview : NoiseCommand()
    data object CalibrationStatus : NoiseCommand()
    data object CalibrationApply : NoiseCommand()
    data object CalibrationCancel : NoiseCommand()
}

/** Deliberately explicit: a noise level is never interpreted as a program command. */
object NoiseVoice {
    private fun number(raw: String): Double? {
        raw.toDoubleOrNull()?.let { return it }
        val small = listOf("zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen")
        val tens = mapOf("twenty" to 20,"thirty" to 30,"forty" to 40,"fifty" to 50,"sixty" to 60,"seventy" to 70,"eighty" to 80,"ninety" to 90)
        val negative = raw.startsWith("minus ") || raw.startsWith("negative ")
        val unsigned = when { raw.startsWith("minus ") -> raw.removePrefix("minus "); raw.startsWith("negative ") -> raw.removePrefix("negative "); else -> raw }
        val parts = unsigned.split(" point ")
        val words = parts[0].replace('-',' ').split(' ')
        val whole = if(words.size == 1) small.indexOf(words[0]).takeIf { it >= 0 } ?: tens[words[0]]
            else if(words.size == 2) tens[words[0]]?.let { t -> small.indexOf(words[1]).takeIf { it in 1..9 }?.let { t+it } } else null
        if(whole == null || parts.size > 2) return null
        val fraction = if(parts.size == 1) 0.0 else {
            val digits = parts[1].split(' ').map { small.indexOf(it).takeIf { n -> n in 0..9 } ?: return null }.joinToString("")
            "0.$digits".toDoubleOrNull() ?: return null
        }
        return (whole+fraction)*if(negative) -1 else 1
    }
    fun parse(raw: String): NoiseCommand? {
        val text = raw.lowercase().trim().trimEnd('.','!','?').replace(Regex("\\s+")," ")
        when(text) {
            "noise settings", "open noise settings", "configure noise" -> return NoiseCommand.Open
            "noise status", "read noise assignments", "read noise settings" -> return NoiseCommand.Read
            "observe noise", "start observe noise" -> return NoiseCommand.Observe
            "noise sample", "sample noise" -> return NoiseCommand.Sample
            "stop noise" -> return NoiseCommand.Stop
            "save noise settings" -> return NoiseCommand.Save
            "discard noise changes" -> return NoiseCommand.Discard
            "automatic noise", "start automatic noise" -> return NoiseCommand.Automatic
            "start automatic noise trial", "start uncalibrated noise trial" -> return NoiseCommand.TrialAutomatic
            "preview automatic noise", "start noise preview" -> return NoiseCommand.Preview
            "calibrate noise", "start noise calibration", "start new noise calibration" -> return NoiseCommand.CalibrationStart
            "confirm new noise calibration" -> return NoiseCommand.CalibrationConfirmStart
            "capture noise calibration sample", "next noise calibration sample" -> return NoiseCommand.CalibrationSample
            "noise calibration status", "read noise calibration progress" -> return NoiseCommand.CalibrationStatus
            "review noise calibration", "review noise calibration thresholds" -> return NoiseCommand.CalibrationReview
            "apply noise calibration", "apply noise calibration thresholds" -> return NoiseCommand.CalibrationApply
            "cancel noise calibration" -> return NoiseCommand.CalibrationCancel
        }
        Regex("assign program (.+) to noise level (.+)").matchEntire(text)?.let {
            val slot = number(it.groupValues[1]); val level = number(it.groupValues[2])
            if(slot != null && level != null && slot in 1.0..6.0 && level in 1.0..6.0 && slot%1==0.0 && level%1==0.0) return NoiseCommand.Assign(level.toInt(),slot.toInt())
        }
        Regex("leave noise level (.+) unused").matchEntire(text)?.let {
            val level = number(it.groupValues[1])
            if(level != null && level in 1.0..6.0 && level%1==0.0) return NoiseCommand.Assign(level.toInt(),null)
        }
        Regex("set noise (threshold [a-z0-9]+|margin|confirmations|cooldown|capture|wait|freshness) to (.+)").matchEntire(text)?.let {
            val value = number(it.groupValues[2]) ?: return null
            var field = it.groupValues[1]
            if(field.startsWith("threshold ")) {
                val boundary = number(field.removePrefix("threshold ")) ?: return null
                if(boundary !in 1.0..5.0 || boundary%1!=0.0) return null
                field = "threshold ${boundary.toInt()}"
            }
            return NoiseCommand.SetField(field,value)
        }
        return null
    }
}
