package com.heysig.voice

/** Phone owns both choices. Missing fields from an older peer never enable speech. */
data class WatchSpeechChoices(val prompts: Boolean = false, val responses: Boolean = false) {
    companion object { fun received(prompts: Boolean?, responses: Boolean?) = WatchSpeechChoices(prompts == true, responses == true) }
}

/** Main-thread turn ownership. Late callbacks and repeated shakes cannot start another capture/send. */
class WatchVoiceTurn {
    enum class Stage { IDLE, CONNECTING, PROMPTING, LISTENING, SENDING, REPLYING }
    var stage = Stage.IDLE; private set
    private var generation = 0L
    private var started = 0L
    fun begin(now: Long): Long? {
        if(stage != Stage.IDLE) return null
        started = now; generation++; stage = Stage.CONNECTING
        return generation
    }
    fun active(id: Long) = id == generation && stage != Stage.IDLE
    fun fresh(id: Long, now: Long) = active(id) && now-started in 0 until 60_000L
    fun advance(id: Long, from: Stage, to: Stage): Boolean {
        if(!active(id) || stage != from || to == Stage.IDLE) return false
        stage = to; return true
    }
    fun finish(id: Long) { if(active(id)) cancel() }
    fun cancel() { generation++; stage = Stage.IDLE }
}
