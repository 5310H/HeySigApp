package com.heysig.noise

/** Versioned length-prefixed fields, independent of platform JSON/preferences. */
object EnvironmentSettingsCodec {
    fun encode(settings: EnvironmentSettings): String {
        val fields=mutableListOf("1",settings.enabled.toString(),settings.cooldownSeconds.toString(),
            settings.stabilitySeconds.toString(),settings.restartAfterManual.toString(),settings.overrideSeconds.toString(),
            settings.minimumConfidence.toString(),settings.schedule.intervalSeconds.toString(),
            settings.schedule.durationSeconds.toString(),settings.schedule.timeoutMinutes.toString())
        settings.assignments.forEach { fields.add(it?.slot?.toString() ?: "");fields.add(it?.name ?: "") }
        return fields.joinToString("") { "${it.length}:$it" }
    }
    fun decode(raw: String): EnvironmentSettings {
        require(raw.length<=16384) { "Environment settings too large" }
        var cursor=0
        fun field(): String {
            val end=raw.indexOf(':',cursor)
            require(end in cursor..minOf(cursor+5,raw.lastIndex)) { "Invalid environment settings" }
            val size=raw.substring(cursor,end).toInt()
            cursor=end+1
            require(size>=0 && size<=raw.length-cursor)
            return raw.substring(cursor,cursor+size).also { cursor+=size }
        }
        require(field()=="1") { "Unsupported environment settings" }
        val enabled=field().toBooleanStrict();val cooldown=field().toInt();val stability=field().toInt()
        val restart=field().toBooleanStrict();val override=field().toInt();val confidence=field().toDouble()
        val schedule=SamplingSchedule(field().toInt(),field().toInt(),field().toInt())
        val assignments=List(6) {
            val slot=field();val name=field()
            if(slot.isEmpty()) { require(name.isEmpty());null } else EnvironmentProgram(slot.toInt(),name)
        }
        require(cursor==raw.length)
        return EnvironmentSettings(assignments,enabled,cooldown,stability,restart,override,confidence,schedule)
    }
}
