package com.heysig.noise

/** Version identifies the fixed masking head, RNNoise and bands, not wearer settings. */
object WatchAutomaticProfile {
    const val ID="hsgmask1-rnnoise-45556e0d-v1"
    const val PATH="/heysig/watch-automatic/v1"
    const val CAPABILITY="heysig_watch_automatic_phone_v1"
    const val MAX_BYTES=4096
    const val MAX_AGE_MILLIS=15_000L
    const val CONTACT_TIMEOUT_MILLIS=45_000L
}

/** Shared policy validates compact watch evidence; no audio or device-clock synchronization. */
class WatchAutomaticEvidenceGate(private val node:String,private val session:String) {
    private var sequence=-1L
    private var closed=false
    fun accept(sender:String,sessionId:String,profile:String,next:Long,age:Long,now:Long,
               severity:String,type:String,score:Double,rms:Double):Pair<MaskingEvidence,Long> {
        check(!closed && sender==node && sessionId==session) { "Watch Automatic session changed" }
        require(profile==WatchAutomaticProfile.ID) { "Update both phone and watch before Automatic" }
        require(next==sequence+1 && next in 0..4095) { "Repeated, missing or reordered watch sample; restart explicitly" }
        require(age in 0..WatchAutomaticProfile.MAX_AGE_MILLIS && now>=age) { "Watch sample expired" }
        require(score.isFinite() && score in 0.0..1.0 && rms.isFinite() && rms in -120.0..0.0) { "Invalid watch evidence" }
        val level=MaskingSeverity.entries.firstOrNull { it.name==severity } ?: error("Invalid masking severity")
        val sound=SoundType.entries.firstOrNull { it.name==type } ?: error("Invalid sound type")
        require(level in listOf(MaskingSeverity.QUIET,MaskingSeverity.UNKNOWN) || score>=.65) { "Weak watch evidence" }
        sequence=next
        return MaskingEvidence(level,sound,score,if(level==MaskingSeverity.UNKNOWN)
            "Watch speech/background estimates are uncertain; keep current program" else "Watch speech/background estimates agree") to now-age
    }
    fun close() { closed=true }
}

/** Conservative age bound: grant elapsed includes return/transit time without clock sync. */
fun watchAutomaticAgeBound(reported:Long,grantElapsed:Long):Long {
    require(reported in 0..WatchAutomaticProfile.MAX_AGE_MILLIS && grantElapsed in 0..4999) { "Watch sample or grant expired" }
    return (reported+grantElapsed).also { require(it<=WatchAutomaticProfile.MAX_AGE_MILLIS) { "Watch sample expired in transit" } }
}
