package com.heysig.noise

/** Display-only event evidence. Never a speech intelligibility score or a program request. */
enum class SoundType(val label: String) {
    SPEECH("Mostly speech"), MIXED("Speech and noise"), NOISE("Mostly noise"), UNKNOWN("Not sure yet")
}
object SoundTypeClassifier {
    fun classify(frames: List<Map<String,Double>>): SoundType {
        val required=EnvironmentClassifier.requiredLabels
        if(frames.size !in 3..32 || frames.any { frame ->
            !frame.keys.containsAll(required) || frame.values.any { !it.isFinite() || it !in 0.0..1.0 }
        }) return SoundType.UNKNOWN
        fun frameType(frame: Map<String,Double>): SoundType {
            fun score(vararg labels: String)=labels.maxOf { frame.getValue(it) }
            val speech=score("Speech","Conversation","Narration, monologue")
            val competing=score("Hubbub, speech noise, speech babble","Crowd","Chatter")
            val noise=score("Vehicle","Traffic noise, roadway noise","Engine","Tools","Mechanisms",
                "Dishes, pots, and pans","Vacuum cleaner","Water","Wind","Mechanical fan")
            val music=frame.getValue("Music")
            return when {
                music>=0.2 -> SoundType.UNKNOWN // TV/music/desired-talker context is unresolved.
                speech>=0.5 && maxOf(noise,competing)>=0.35 -> SoundType.MIXED
                speech>=0.6 && maxOf(noise,competing)<0.2 -> SoundType.SPEECH
                noise>=0.6 && maxOf(speech,competing)<0.2 -> SoundType.NOISE
                else -> SoundType.UNKNOWN
            }
        }
        val votes=frames.map(::frameType)
        val best=SoundType.entries.filter { it!=SoundType.UNKNOWN }.maxBy { type -> votes.count { it==type } }
        val count=votes.count { it==best }
        return if(count>=3 && count.toDouble()/votes.size>=0.75) best else SoundType.UNKNOWN
    }
}

/** Two matching fresh captures; uncertainty, priority loss and stale results clear the display. */
data class SoundTypeObservation(val candidate: SoundType=SoundType.UNKNOWN, val count: Int=0,
                                val lastSample: Long?=null, val shown: SoundType=SoundType.UNKNOWN) {
    fun next(type: SoundType, capturedAt: Long, deliveredAt: Long): SoundTypeObservation {
        if(!isFreshNoiseSample(capturedAt,deliveredAt,10) || type==SoundType.UNKNOWN) return SoundTypeObservation()
        val fresh=lastSample?.let { capturedAt>it && capturedAt-it<=20_000 } ?: false
        val agreed=if(fresh && candidate==type) minOf(count+1,2) else 1
        return SoundTypeObservation(type,agreed,capturedAt,if(agreed==2) type else SoundType.UNKNOWN)
    }
}
