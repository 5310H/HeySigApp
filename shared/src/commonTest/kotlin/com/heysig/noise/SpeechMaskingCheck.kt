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
    val tv=choices.copy(television=5,watchingTv=true)
    check(feed(MaskingSeverity.CLEAR,mapping=tv).proposed==5)
    check(feed(MaskingSeverity.MODERATE,mapping=tv).proposed==5)
    check(feed(MaskingSeverity.HEAVY,mapping=tv).proposed==5)
    check(feed(MaskingSeverity.UNKNOWN,current=5,mapping=tv).proposed==null)
    check(feed(MaskingSeverity.CLEAR,current=5,mapping=tv).proposed==null)
    check(feed(MaskingSeverity.HEAVY,current=5,mapping=tv).proposed==null)
    check(feed(MaskingSeverity.QUIET,current=5,mapping=tv).proposed==1)
    check(feed(MaskingSeverity.QUIET,current=5,mapping=tv.copy(everyday=null)).proposed==null)
    check(feed(MaskingSeverity.HEAVY,mapping=tv,guard=safe.copy(protectedPrograms=setOf(5))).proposed==null)
    check(feed(MaskingSeverity.HEAVY,mapping=tv,config=settings.copy(sleepProgram=5)).proposed==null)
    check(feed(MaskingSeverity.HEAVY,mapping=tv,guard=safe.copy(manualOverride=true)).proposed==null)
    check(feed(MaskingSeverity.HEAVY,mapping=tv,guard=safe.copy(audioFree=false)).proposed==null)
    val tvPending=feed(MaskingSeverity.MODERATE,mapping=tv).state
    val tvComplete=completeMasking(tvPending,tvPending.token,true,22000)
    check(feed(MaskingSeverity.QUIET,current=5,start=23000,initial=tvComplete,mapping=tv).state.count==0)
    val tvReturn=feed(MaskingSeverity.QUIET,current=5,start=83000,initial=tvComplete,mapping=tv)
    check(tvReturn.proposed==1)
    check(feed(MaskingSeverity.CLEAR,current=5,start=83000,initial=tvComplete,mapping=tv).proposed==null)
    check(feed(MaskingSeverity.HEAVY,mapping=tv.copy(watchingTv=false)).proposed==3)
    check(runCatching { choices.copy(watchingTv=true) }.isFailure)
    check(runCatching { choices.copy(television=7) }.isFailure)
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
    check(agreeCancellationEvidence(evidence(MaskingSeverity.HEAVY),evidence(MaskingSeverity.MODERATE)).severity==MaskingSeverity.UNKNOWN)
    check(agreeCancellationEvidence(evidence(MaskingSeverity.CLEAR),evidence(MaskingSeverity.HEAVY)).severity==MaskingSeverity.UNKNOWN)
    check(agreeCancellationEvidence(evidence(MaskingSeverity.MODERATE),evidence(MaskingSeverity.MODERATE)).severity==MaskingSeverity.MODERATE)
    check(agreeCancellationEvidence(evidence(MaskingSeverity.UNKNOWN),evidence(MaskingSeverity.HEAVY)).severity==MaskingSeverity.UNKNOWN)
    check(agreeCancellationEvidence(evidence(MaskingSeverity.HEAVY),evidence(MaskingSeverity.UNKNOWN)).severity==MaskingSeverity.UNKNOWN)
    check(agreeCancellationEvidence(evidence(MaskingSeverity.QUIET).copy(score=0.0),evidence(MaskingSeverity.QUIET).copy(score=0.0)).severity==MaskingSeverity.QUIET)
    check(runCatching { SpeechMaskingHead.decode("HSGMASK1\n2052\nNaN") }.isFailure)
    val pcm=ShortArray(48000) { (sinForTest(it)*5000).toInt().toShort() }
    val acoustic=MaskingSignal.features(pcm);check(acoustic.size==4 && acoustic.all { it.isFinite() && it in 0.0..1.0 })
    val scaled=ShortArray(pcm.size) { (pcm[it]/2).toShort() };val quieter=MaskingSignal.features(scaled)
    check(acoustic.zip(quieter).all { (a,b) -> kotlin.math.abs(a-b)<.01 })
    pcm.fill(0);scaled.fill(0)
    cancellationChecks()
    println("PASS: speech masking first-use decisions, sustained return, unassigned hold, fresh agreement, duplicate/stale rejection, cooldown, manual/Therapy priority, completion tokens, protected targets; validation remains unaccepted")
}
private fun cancellationChecks() {
    check(NoiseCancellationSignal.features(ShortArray(48000)).all { it==0.0 })
    check(runCatching { NoiseCancellationSignal.features(ShortArray(47999)) }.isFailure)
    val random=kotlin.random.Random(42)
    val noise=DoubleArray(48000) { random.nextDouble(-1.0,1.0) }
    val voice=DoubleArray(48000) { i ->
        if(i%8000<4800) sinForTest(i)*(.5+.5*kotlin.math.sin(2*kotlin.math.PI*4*i/16000)) else 0.0
    }
    fun normalize(x:DoubleArray) { val rms=kotlin.math.sqrt(x.sumOf { it*it }/x.size);for(i in x.indices) x[i]/=rms }
    normalize(noise);normalize(voice)
    fun mix(ratio:Double):ShortArray {
        val x=DoubleArray(48000) { voice[it]+noise[it]*ratio };normalize(x)
        return try { ShortArray(48000) { (x[it]*2000).toInt().toShort() } } finally { x.fill(0.0) }
    }
    val clear=mix(.178);val buried=mix(2.512)
    val first=NoiseCancellationSignal.features(clear);val heavy=NoiseCancellationSignal.features(buried)
    check(first.all { it.isFinite() && it in 0.0..1.0 } && heavy.all { it.isFinite() && it in 0.0..1.0 })
    // Matched overall power, different foreground/background ratio: cancellation sees the difference.
    check(heavy[0]>first[0]+.15 && first[2]>heavy[2]+.15)
    val quiet=ShortArray(clear.size) { (clear[it]/2).toShort() }
    val scaled=NoiseCancellationSignal.features(quiet)
    check(first.zip(scaled).all { (a,b) -> kotlin.math.abs(a-b)<.003 })
    // Exercise the serialized v2 head through the same PCM entry point used by Automatic.
    // Artificial coefficients test wiring, not detector accuracy (measured on the corpus).
    val rows=List(3) { c -> DoubleArray(2058).apply { this[2052]=when(c) { 0 -> -80.0;2 -> 80.0;else -> 0.0 } }.joinToString(",") }
    val head=SpeechMaskingHead.decode("HSGMASK2\n2058\n8,-8,-8\n"+rows.joinToString("\n")+"\n")
    val pool=DoubleArray(2048).apply { this[0]=1.0 }
    val frame=EnvironmentClassifier.requiredLabels.associateWith { if(it=="Speech") .9 else 0.0 }
    check(head.classify(clear,pool,List(5) { frame }).severity==MaskingSeverity.CLEAR)
    check(head.classify(buried,pool,List(5) { frame }).severity==MaskingSeverity.HEAVY)
    check(runCatching { SpeechMaskingHead.decode("HSGMASK2\n2052\n8,-8,-8\n"+rows.joinToString("\n")) }.isFailure)
    // No persistent floor learned from the preceding noisy recording.
    check(first.contentEquals(NoiseCancellationSignal.features(clear)))
    clear.fill(0);buried.fill(0);quiet.fill(0);noise.fill(0.0);voice.fill(0.0)
}
private fun sinForTest(i:Int)=kotlin.math.sin(2*kotlin.math.PI*200*i/16000)
