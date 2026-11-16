package com.heysig.noise

fun main() {
    val settings=NoiseSettings(programs=listOf(1,1,2,2,3,3))
    val bands=SeparationMaskingBands(-4.0,8.0)
    val absent=SpeechNoiseEstimate(SeparationStatus.NO_RELIABLE_SPEECH,speechScore=.05,speechActivity=0.0)
    fun noise(db:Double,score:Double=.9,music:Double=0.0):CombinedSection {
        val m=NoiseMeasurement(16000,48000,3,db,db,db,db+5,0.0,.2,emptySet())
        return CombinedSection(MaskingEvidence(),absent,m,score,music)
    }
    check(CombinedAutomatic.section(noise(-15.0),settings,bands)==MaskingSeverity.HEAVY)
    check(CombinedAutomatic.section(noise(-35.0),settings,bands)==MaskingSeverity.MODERATE)
    check(CombinedAutomatic.section(noise(-55.0),settings,bands)==MaskingSeverity.CLEAR)
    check(CombinedAutomatic.section(noise(-70.0,0.0),settings,bands)==MaskingSeverity.QUIET)
    check(CombinedAutomatic.section(noise(-15.0,music=.9),settings,bands)==MaskingSeverity.UNKNOWN)
    check(CombinedAutomatic.section(noise(-15.0,score=.1),settings,bands)==MaskingSeverity.UNKNOWN)
    check(CombinedAutomatic.section(noise(-15.0).let { it.copy(measurement=it.measurement.copy(flags=setOf(MeasurementFlag.CLIPPING))) },settings,bands)==MaskingSeverity.UNKNOWN)
    fun combine(severity:MaskingSeverity,tv:TelevisionPresence)=CombinedAutomatic.combine(List(3) { severity },tv)
    val choices=MaskingChoices(1,2,3,5)
    check(choices.target(combine(MaskingSeverity.HEAVY,TelevisionPresence.PRESENT))==3)
    check(choices.target(combine(MaskingSeverity.MODERATE,TelevisionPresence.PRESENT))==2)
    check(choices.target(combine(MaskingSeverity.CLEAR,TelevisionPresence.PRESENT))==5)
    check(choices.target(combine(MaskingSeverity.CLEAR,TelevisionPresence.ABSENT))==1)
    check(choices.target(combine(MaskingSeverity.QUIET,TelevisionPresence.ABSENT))==1)
    check(choices.target(combine(MaskingSeverity.UNKNOWN,TelevisionPresence.UNKNOWN))==null)
    check(CombinedAutomatic.corrected(combine(MaskingSeverity.HEAVY,TelevisionPresence.PRESENT),AutomaticScene.TV).scene==AutomaticScene.VERY_NOISY)
    check(CombinedAutomatic.combine(List(3) { MaskingSeverity.CLEAR },TelevisionPresence.PRESENT,captureValid=false).scene==AutomaticScene.UNKNOWN)
    var state=MaskingState();var current=1;var at=1000L
    val safe=NoiseSafety(foreground=true,unlocked=true,audioFree=true)
    // One real reducer sequence: TV -> heavy noise -> moderate noise -> TV -> normal speech.
    for((evidence,target) in listOf(combine(MaskingSeverity.CLEAR,TelevisionPresence.PRESENT) to 5,
        combine(MaskingSeverity.HEAVY,TelevisionPresence.PRESENT) to 3,
        combine(MaskingSeverity.MODERATE,TelevisionPresence.PRESENT) to 2,
        combine(MaskingSeverity.CLEAR,TelevisionPresence.PRESENT) to 5,
        combine(MaskingSeverity.CLEAR,TelevisionPresence.ABSENT) to 1)) {
        var result=MaskingDecision(state,reason="")
        for(i in 0..2) {
            result=observeMasking(result.state,evidence,at+i*20000,at+i*20000+10,settings,choices,safe,current)
            if(result.proposed!=null) break
        }
        check(result.proposed==target)
        state=completeMasking(result.state,result.state.token,true,at+41000);current=target;at+=110000
    }
    // TV and ordinary speech both have CLEAR severity but must not share confirmation.
    val first=observeMasking(MaskingState(),combine(MaskingSeverity.CLEAR,TelevisionPresence.PRESENT),1000,1010,settings,choices,safe,3)
    val normal=observeMasking(first.state,combine(MaskingSeverity.CLEAR,TelevisionPresence.ABSENT),22000,22010,settings,choices,safe,3)
    check(normal.proposed==null && normal.state.count==1)
    val invalid=combine(MaskingSeverity.HEAVY,TelevisionPresence.PRESENT).copy(scene=AutomaticScene.TV)
    check(observeMasking(MaskingState(),invalid,1000,1010,settings,choices,safe,1).state.count==0)
    println("PASS: combined TV/noise priority, noise without speech, measured quiet return, one full switching sequence and target-bound confirmations")
}
