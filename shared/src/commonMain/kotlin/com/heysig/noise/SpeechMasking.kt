package com.heysig.noise

import kotlin.math.*

enum class MaskingSeverity(val label: String) {
    QUIET("Quiet"), CLEAR("Speech stands out"), MODERATE("Speech partly masked"), HEAVY("Speech heavily masked"), UNKNOWN("Not sure yet")
}
data class MaskingEvidence(val severity: MaskingSeverity=MaskingSeverity.UNKNOWN,
    val soundType: SoundType=SoundType.UNKNOWN, val score: Double=0.0, val reason: String="Uncertain sound; keep current program")

/** No calibration, enrollment, transcript or wearer learning. Fixed, development-trained weights. */
class SpeechMaskingHead private constructor(private val bias: DoubleArray,private val weights: List<DoubleArray>) {
    fun classify(pcm: ShortArray, pooled: DoubleArray?, frames: List<Map<String,Double>>): MaskingEvidence {
        if(pcm.size!=48000) return MaskingEvidence(reason="Masking requires a complete three-second capture")
        val measurement=NoiseAccumulator(16000,3).apply { add(pcm,pcm.size) }.measurement()
        return classifyFeatures(pooled,MaskingSignal.features(pcm),frames,measurement)
    }
    /** Identical scalar boundary for native capture and reproducible developer-corpus reporting. */
    fun classifyFeatures(pooled:DoubleArray?,speechFeatures:DoubleArray,frames:List<Map<String,Double>>,measurement:NoiseMeasurement):MaskingEvidence {
        fun hold(why:String)=MaskingEvidence(reason=why)
        if(measurement.sampleRate!=16000 || measurement.sampleCount!=48000L ||
            !measurement.rmsDbfs.isFinite() || measurement.rmsDbfs !in -120.0..0.0 ||
            !measurement.peakDbfs.isFinite() || measurement.peakDbfs !in measurement.rmsDbfs..0.0 ||
            speechFeatures.size!=4 || speechFeatures.any { !it.isFinite() || it !in 0.0..1.0 } ||
            pooled?.size!=2048 || pooled.any { !it.isFinite() || it !in 0.0..1.0 } ||
            pooled.sumOf { it*it } !in .99..1.01 || frames.size !in 3..32 || frames.any { f ->
                !f.keys.containsAll(EnvironmentClassifier.requiredLabels) || f.values.any { !it.isFinite() || it !in 0.0..1.0 }
            }) return hold("Incomplete or invalid masking evidence")
        if(measurement.flags.isNotEmpty() || measurement.peakDbfs-measurement.rmsDbfs>24) return hold("Interrupted, clipped or isolated sound; keep current program")
        fun event(vararg labels:String)=frames.map { f -> labels.maxOf { f.getValue(it) } }.average()
        val speech=event("Speech","Conversation","Narration, monologue")
        val babble=event("Hubbub, speech noise, speech babble","Crowd","Chatter")
        val type=SoundTypeClassifier.classify(frames)
        if(event("Music")>=.15) return hold("Music or mixed media; keep current program")
        if(measurement.rmsDbfs<=-65 && event("Silence")>=.6 && max(speech,babble)<.15)
            return MaskingEvidence(MaskingSeverity.QUIET,type,reason="Quiet evidence; requires repeated checks")
        if(max(speech,babble)<.15) return hold("No reliable speech evidence; keep current program")
        val x=DoubleArray(2052) { if(it<2048) pooled[it]*20 else 0.0 }
        speechFeatures.copyInto(x,2048)
        val logits=DoubleArray(3) { c -> bias[c]+x.indices.sumOf { weights[c][it]*x[it] } }
        val peak=logits.max();val scores=logits.map { exp(it-peak) };val total=scores.sum()
        val ordered=scores.indices.sortedByDescending { scores[it] };val top=scores[ordered[0]]/total
        if(top<.65 || top-scores[ordered[1]]/total<.15) return hold("Masking estimates disagree; keep current program")
        return MaskingEvidence(listOf(MaskingSeverity.CLEAR,MaskingSeverity.MODERATE,MaskingSeverity.HEAVY)[ordered[0]],type,top,
            "Experimental masking estimate, not measured intelligibility or target-speaker identity")
    }
    companion object {
        fun decode(text:String): SpeechMaskingHead {
            require(text.length in 20..300000)
            val lines=text.trimEnd('\n').split('\n');require(lines.size==6 && lines[0]=="HSGMASK1" && lines[1]=="2052")
            fun row(line:String,size:Int)=line.split(',').map { it.toDouble().also { n -> require(n.isFinite() && abs(n)<10000) } }.toDoubleArray().also { require(it.size==size) }
            return SpeechMaskingHead(row(lines[2],3),(3..5).map { row(lines[it],2052) })
        }
    }
}

/** Speech envelope modulation and pitch periodicity; scalars only, no saved waveform. */
object MaskingSignal {
    fun features(pcm:ShortArray):DoubleArray {
        require(pcm.size==48000)
        val x=DoubleArray(pcm.size) { pcm[it]/32768.0 }
        try {
            val energies=DoubleArray(150) { f -> (0 until 320).sumOf { val v=x[f*320+it];v*v }/320 }
            val sorted=energies.sorted();val depth=(10*log10(max(sorted[(149*.9).toInt()],1e-12)/max(sorted[(149*.1).toInt()],1e-12))/60).coerceIn(0.0,1.0)
            val envelope=energies.map { sqrt(it) };val mean=envelope.average();val centered=envelope.map { it-mean }
            val total=centered.sumOf { it*it }*150/2
            var power=0.0
            for(f in 4..16) { var real=0.0;var imag=0.0
                for(i in centered.indices) { val angle=2*PI*f*i/50;real+=centered[i]*cos(angle);imag-=centered[i]*sin(angle) }
                power+=real*real+imag*imag
            }
            var periodicity=0.0;var count=0
            for(start in 0..pcm.size-640 step 1600) {
                val average=(0 until 640).sumOf { x[start+it] }/640;var best=0.0
                for(lag in 64..320 step 8) { var dot=0.0;var a=0.0;var b=0.0
                    for(i in 0 until 640-lag) { val left=x[start+i]-average;val right=x[start+i+lag]-average;dot+=left*right;a+=left*left;b+=right*right }
                    val denominator=sqrt(a*b);if(denominator>1e-10) best=max(best,dot/denominator)
                }
                periodicity+=best;count++
            }
            val crossings=(1 until x.size).count { (x[it]>0)!=(x[it-1]>0) }.toDouble()/(x.size-1)
            return doubleArrayOf(depth,(power/max(1e-12,total)).coerceAtMost(1.0),periodicity/count,crossings)
        } finally { x.fill(0.0) }
    }
}

data class MaskingChoices(val everyday:Int?,val noisy:Int?,val veryNoisy:Int?) {
    init { require(listOf(everyday,noisy,veryNoisy).all { it==null || it in 1..6 }) }
    fun target(severity:MaskingSeverity)=when(severity) {
        MaskingSeverity.QUIET,MaskingSeverity.CLEAR -> everyday
        MaskingSeverity.MODERATE -> noisy
        MaskingSeverity.HEAVY -> veryNoisy
        MaskingSeverity.UNKNOWN -> null
    }
}
data class MaskingState(val severity:MaskingSeverity?=null,val count:Int=0,val lastCapture:Long?=null,
    val lastSuccess:Long?=null,val pending:Int?=null,val token:Long=0,val halted:Boolean=false)
data class MaskingDecision(val state:MaskingState,val proposed:Int?=null,val reason:String)

/** Pure preview/live policy. Completion/dispatch is owned by the existing validated executor. */
fun observeMasking(previous:MaskingState,evidence:MaskingEvidence,capturedAt:Long,deliveredAt:Long,
                   settings:NoiseSettings,choices:MaskingChoices,safety:NoiseSafety,lastRequested:Int?):MaskingDecision {
    fun hold(why:String)=MaskingDecision(previous.copy(severity=null,count=0,lastCapture=null),reason=why)
    safety.blocked()?.let { return hold(it) }
    if(lastRequested==null) return hold("Choose a daytime program first")
    if(lastRequested==settings.sleepProgram || lastRequested in safety.protectedPrograms) return hold("Therapy has priority")
    if(previous.halted || previous.pending!=null) return hold("Previous command unresolved; restart explicitly")
    if(!evidence.score.isFinite() || evidence.score !in 0.0..1.0 ||
        (evidence.severity !in listOf(MaskingSeverity.UNKNOWN,MaskingSeverity.QUIET) && evidence.score<.65)) return hold("Invalid or weak masking evidence")
    if(!isFreshNoiseSample(capturedAt,deliveredAt,settings.freshnessSeconds) || previous.lastCapture?.let { capturedAt<=it }==true) return hold("Delayed or repeated sample; checking again")
    if(previous.lastSuccess?.let { deliveredAt<it || deliveredAt-it<settings.cooldownSeconds*1000L }==true) return hold("Waiting for your cooldown; confirmations cleared")
    val target=choices.target(evidence.severity) ?: return hold(if(evidence.severity==MaskingSeverity.UNKNOWN) evidence.reason else "No program chosen for this situation; keeping current program")
    if(target==settings.sleepProgram || target in safety.protectedPrograms) return hold("Therapy stays under manual control")
    val fresh=previous.lastCapture?.let { capturedAt-it<=settings.freshnessSeconds*1000L }==true
    val needed=maxOf(2,settings.confirmations)
    val count=if(fresh && previous.severity==evidence.severity) minOf(needed,previous.count+1) else 1
    val next=previous.copy(severity=evidence.severity,count=count,lastCapture=capturedAt)
    if(count<needed) return MaskingDecision(next,reason="Checking again: $count of $needed")
    if(target==lastRequested) return MaskingDecision(next,reason="Current program already matches")
    require(previous.token<Long.MAX_VALUE)
    return MaskingDecision(next.copy(pending=target,token=previous.token+1),target,"Confirmed proposal")
}
fun completeMasking(previous:MaskingState,token:Long,success:Boolean,now:Long):MaskingState {
    require(now>=0)
    if(previous.pending==null || previous.token!=token) return previous
    if(previous.lastCapture?.let { now<it }==true) return previous.copy(pending=null,halted=true)
    return MaskingState(lastSuccess=if(success) now else previous.lastSuccess,token=token,halted=!success)
}
/** Evidence record, not an enable lock. Owner explicitly requested live operation before acceptance. */
object SpeechMaskingValidation {
    const val DETECTOR_ACCEPTED=false
    const val PHYSICAL_ACCEPTED=false
    fun qualified()=DETECTOR_ACCEPTED && PHYSICAL_ACCEPTED
}
