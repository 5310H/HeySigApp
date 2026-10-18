package com.heysig.sleep

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure JVM clock planning shared by Android/Wear; no clock reads or event delivery. */
object SleepPreview {
    fun next(minute: Int, now: ZonedDateTime): ZonedDateTime? {
        require(minute in 0..1439)
        for(offset in 0L..2L) {
            val local=now.toLocalDate().plusDays(offset).atTime(minute/60,minute%60)
            val offsets=now.zone.rules.getValidOffsets(local)
            if(offsets.isEmpty()) continue
            val occurrence=ZonedDateTime.ofLocal(local,now.zone,offsets.first())
            if(occurrence.toInstant().isAfter(now.toInstant())) return occurrence
        }
        return null
    }
    fun describe(value: SleepRoutine, now: ZonedDateTime): String {
        val format=DateTimeFormatter.ofPattern("EEE MMM d HH:mm XXX",Locale.getDefault())
        fun date(minute: Int)=next(minute,now)?.format(format) ?: "no valid occurrence in the next three days"
        return "Preview only · ${now.zone.id}. Next sleep: ${date(value.sleepMinute)}. Next wake: ${date(value.wakeMinute)}. Repeated clock times use the first occurrence; skipped clock times are omitted. No alarms are scheduled."
    }
}
