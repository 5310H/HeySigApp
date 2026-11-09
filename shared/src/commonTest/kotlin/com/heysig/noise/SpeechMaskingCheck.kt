package com.heysig.noise

fun main() {
    val settings=NoiseSettings(programs=listOf(1,1,2,2,3,3),confirmations=3,cooldownSeconds=60)
    val choices=MaskingChoices(1,2,3)
    val safe=NoiseSafety(foreground=true,unlocked=true,audioFree=true)
    fun evidence(s:MaskingSeverity)=MaskingEvidence(s,SoundType.UNKNOWN,.9,"test scalar evidence")
    fun feed(severity:MaskingSeverity,current:Int=1,start:Long=1000,initial:MaskingState=MaskingState(),config:NoiseSettings=settings,mapping:MaskingChoices=choices,guard:NoiseSafety=safe):MaskingDecision {
        var state=initial;var last=MaskingDecision(state,reason="")
        for(i in 0..2) { val t=start+i*10000;last=observeMasking(state,evidence(severity),t,t+20,config,mapping,guard,current);state=last.state }
        return last
    }
    check(feed(MaskingSeverity.MODERATE).proposed==2)
    check(feed(MaskingSeverity.HEAVY).proposed==3)
    check(feed(MaskingSeverity.CLEAR,current=3).proposed==1)
    check(feed(MaskingSeverity.QUIET,current=3).proposed==1)
    check(feed(MaskingSeverity.CLEAR).proposed==null)
    check(feed(MaskingSeverity.UNKNOWN).state.count==0)
    check(feed(MaskingSeverity.MODERATE,mapping=choices.copy(noisy=null)).proposed==null)
    check(feed(MaskingSeverity.HEAVY,guard=safe.copy(manualOverride=true)).proposed==null)
    check(feed(MaskingSeverity.HEAVY,guard=safe.copy(sleeping=true)).proposed==null)
    check(feed(MaskingSeverity.HEAVY,guard=safe.copy(audioFree=false)).proposed==null)
    check(feed(MaskingSeverity.HEAVY,guard=safe.copy(protectedPrograms=setOf(3))).proposed==null)
    check(feed(MaskingSeverity.MODERATE,current=3,guard=safe.copy(protectedPrograms=setOf(3))).proposed==null)
    check(feed(MaskingSeverity.HEAVY,guard=NoiseSafety(audioFree=true,microphoneSession=true)).proposed==3)
    check(feed(MaskingSeverity.HEAVY,guard=NoiseSafety(audioFree=true)).proposed==null)
    val pending=feed(MaskingSeverity.HEAVY).state
    check(feed(MaskingSeverity.HEAVY,initial=pending).proposed==null)
    check(completeMasking(pending,pending.token+1,true,22000)==pending)
    val complete=completeMasking(pending,pending.token,true,22000)
    check(feed(MaskingSeverity.QUIET,current=3,start=23000,initial=complete).state.count==0)
    check(feed(MaskingSeverity.QUIET,current=3,start=83000,initial=complete).proposed==1)
    val failed=completeMasking(pending,pending.token,false,22000)
    check(failed.halted && feed(MaskingSeverity.QUIET,current=3,start=83000,initial=failed).proposed==null)
    var first=observeMasking(MaskingState(),evidence(MaskingSeverity.MODERATE),1000,1100,settings,choices,safe,1).state
    check(first.count==1)
    check(observeMasking(first,evidence(MaskingSeverity.MODERATE),1000,1200,settings,choices,safe,1).state.count==0)
    check(observeMasking(first,evidence(MaskingSeverity.MODERATE),2000,34000,settings,choices,safe,1).state.count==0)
    check(observeMasking(first,evidence(MaskingSeverity.HEAVY),11000,11100,settings,choices,safe,1).state.count==1)
    check(observeMasking(first,evidence(MaskingSeverity.QUIET),41000,41100,settings,choices,safe,3).state.count==1)
    check(observeMasking(first,evidence(MaskingSeverity.UNKNOWN),11000,11100,settings,choices,safe,1).state.count==0)
    check(observeMasking(first,evidence(MaskingSeverity.MODERATE).copy(score=Double.NaN),11000,11100,settings,choices,safe,1).state.count==0)
    check(observeMasking(first,evidence(MaskingSeverity.MODERATE).copy(score=.4),11000,11100,settings,choices,safe,1).state.count==0)
    check(completeMasking(pending,pending.token,true,1000).halted)
    check(feed(MaskingSeverity.MODERATE,config=settings.copy(confirmations=1)).proposed==null) // third invocation awaits pending completion, never sends twice
    val two=observeMasking(first,evidence(MaskingSeverity.MODERATE),11000,11100,settings.copy(confirmations=1),choices,safe,1)
    check(two.proposed==2) // at least two even with a one-check legacy preference
    check(!SpeechMaskingValidation.qualified() && !EnvironmentReleaseGate.qualified())
    check(runCatching { SpeechMaskingHead.decode("HSGMASK1\n2052\nNaN") }.isFailure)
    val pcm=ShortArray(48000) { (sinForTest(it)*5000).toInt().toShort() }
    val acoustic=MaskingSignal.features(pcm);check(acoustic.size==4 && acoustic.all { it.isFinite() && it in 0.0..1.0 })
    val scaled=ShortArray(pcm.size) { (pcm[it]/2).toShort() };val quieter=MaskingSignal.features(scaled)
    check(acoustic.zip(quieter).all { (a,b) -> kotlin.math.abs(a-b)<.01 })
    pcm.fill(0);scaled.fill(0)
    println("PASS: speech masking first-use decisions, sustained return, unassigned hold, fresh agreement, duplicate/stale rejection, cooldown, manual/Therapy priority, completion tokens, protected targets; validation remains unaccepted")
}
private fun sinForTest(i:Int)=kotlin.math.sin(2*kotlin.math.PI*200*i/16000)
