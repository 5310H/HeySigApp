package com.heysig.noise

fun main() {
    fun gate()=WatchAutomaticEvidenceGate("watch","session")
    fun send(g:WatchAutomaticEvidenceGate,sender:String="watch",session:String="session",profile:String=WatchAutomaticProfile.ID,
             sequence:Long=0,age:Long=10,score:Double=.9,rms:Double=-35.0,severity:String="MODERATE")=
        g.accept(sender,session,profile,sequence,age,60_000,severity,"SPEECH",score,rms)
    check(watchAutomaticAgeBound(100,200)==300L)
    for((age,grant) in listOf(30_000L to 1L,0L to 5000L,-1L to 0L)) check(runCatching { watchAutomaticAgeBound(age,grant) }.isFailure)
    val g=gate();check(send(g).second==59_990L)
    check(runCatching { send(g) }.isFailure)
    check(send(g,sequence=1).first.severity==MaskingSeverity.MODERATE)
    for(bad in listOf<()->Any>({send(gate(),sender="other")},{send(gate(),session="old")},
        {send(gate(),profile="old")},{send(gate(),sequence=1)},{send(gate(),age=30_001)},
        {send(gate(),age=-1)},{send(gate(),score=Double.NaN)},{send(gate(),score=.64)},
        {send(gate(),rms=1.0)},{send(gate(),severity="invalid")})) check(runCatching(bad).isFailure)
    check(send(gate(),score=0.0,severity="UNKNOWN").first.severity==MaskingSeverity.UNKNOWN)
    check(send(gate(),score=0.0,severity="QUIET").first.severity==MaskingSeverity.QUIET)
    check(runCatching { send(gate(),profile="hsgmask1-rnnoise-45556e0d-v1") }.isFailure)
    check(send(gate(),age=22_000).second==38_000L)
    val saved=NoiseSettings(programs=listOf(1,1,2,2,3,3))
    val timed=watchAutomaticDecisionSettings(saved)
    check(saved.freshnessSeconds==30 && timed.freshnessSeconds==55)
    var state=MaskingState()
    var decision=MaskingDecision(state,reason="")
    for(at in listOf(100_000L,145_000L,190_000L)) {
        decision=observeMasking(state,MaskingEvidence(MaskingSeverity.MODERATE,score=.9,scene=AutomaticScene.NOISY),
            at,at+22_000,timed,MaskingChoices(1,2,3),NoiseSafety(foreground=true,unlocked=true,audioFree=true),1)
        state=decision.state
    }
    check(decision.proposed==2 && state.count==3)
    g.close();check(runCatching { send(g,sequence=2) }.isFailure)
    // Rejecting a stale sample does not consume the next sequence.
    val stale=gate();check(runCatching { send(stale,age=30_001) }.isFailure);send(stale)
    println("PASS: selected watch/session/profile, fresh relative age, strict sequences, finite evidence, uncertain hold and closed session")
}
