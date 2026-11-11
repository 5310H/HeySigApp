package com.heysig.noise

fun main() {
    fun gate()=WatchAutomaticEvidenceGate("watch","session")
    fun send(g:WatchAutomaticEvidenceGate,sender:String="watch",session:String="session",profile:String=WatchAutomaticProfile.ID,
             sequence:Long=0,age:Long=10,score:Double=.9,rms:Double=-35.0,severity:String="MODERATE")=
        g.accept(sender,session,profile,sequence,age,20_000,severity,"SPEECH",score,rms)
    check(watchAutomaticAgeBound(100,200)==300L)
    for((age,grant) in listOf(15_000L to 1L,0L to 5000L,-1L to 0L)) check(runCatching { watchAutomaticAgeBound(age,grant) }.isFailure)
    val g=gate();check(send(g).second==19_990L)
    check(runCatching { send(g) }.isFailure)
    check(send(g,sequence=1).first.severity==MaskingSeverity.MODERATE)
    for(bad in listOf<()->Any>({send(gate(),sender="other")},{send(gate(),session="old")},
        {send(gate(),profile="old")},{send(gate(),sequence=1)},{send(gate(),age=15_001)},
        {send(gate(),age=-1)},{send(gate(),score=Double.NaN)},{send(gate(),score=.64)},
        {send(gate(),rms=1.0)},{send(gate(),severity="invalid")})) check(runCatching(bad).isFailure)
    check(send(gate(),score=0.0,severity="UNKNOWN").first.severity==MaskingSeverity.UNKNOWN)
    check(send(gate(),score=0.0,severity="QUIET").first.severity==MaskingSeverity.QUIET)
    g.close();check(runCatching { send(g,sequence=2) }.isFailure)
    // Rejecting a stale sample does not consume the next sequence.
    val stale=gate();check(runCatching { send(stale,age=15_001) }.isFailure);send(stale)
    println("PASS: selected watch/session/profile, fresh relative age, strict sequences, finite evidence, uncertain hold and closed session")
}
