package com.heysig.noise

fun main() {
    val frames=List(5) { EnvironmentClassifier.requiredLabels.associateWith { if(it=="Silence") .02 else .01 } }
    val m=NoiseMeasurement(16000,48000,3,-51.0,-52.0,-50.0,-35.0,0.0,.2,emptySet())
    val acoustic=doubleArrayOf(.1,.2,.1,.1)
    check(QuietRoomReturn.candidate(m,frames,acoustic)) // Ordinary quiet does not need Silence >= .6 or -65 dBFS.
    check(!QuietRoomReturn.candidate(m.copy(rmsDbfs=-40.0),frames,acoustic))
    check(!QuietRoomReturn.candidate(m.copy(peakDbfs=-20.0),frames,acoustic))
    check(!QuietRoomReturn.candidate(m.copy(flags=setOf(MeasurementFlag.ALL_ZERO)),frames,acoustic))
    for(flag in MeasurementFlag.entries) check(!QuietRoomReturn.candidate(m.copy(flags=setOf(flag)),frames,acoustic))
    check(!QuietRoomReturn.candidate(m.copy(sampleCount=47999),frames,acoustic))
    check(!QuietRoomReturn.candidate(m.copy(rmsDbfs=Double.NaN),frames,acoustic))
    check(!QuietRoomReturn.candidate(m,frames.take(2),acoustic))
    // Even one event frame containing speech/music/noise must veto a quiet candidate.
    for(label in EnvironmentClassifier.requiredLabels.filterNot { it=="Silence" }) {
        val interrupted=frames.toMutableList().also { it[2]=it[2]+(label to if(label in setOf("Speech","Conversation","Narration, monologue","Hubbub, speech noise, speech babble","Crowd","Chatter","Music")) .15 else .35) }
        check(!QuietRoomReturn.candidate(m,interrupted,acoustic))
    }
    check(!QuietRoomReturn.candidate(m,frames.map { it-"Speech" },acoustic))
    val tonal=doubleArrayOf(.1,.2,.9,.1)
    check(!QuietRoomReturn.candidate(m,frames,tonal))
    check(!QuietRoomReturn.candidate(m,frames,tonal,.1))
    check(QuietRoomReturn.candidate(m,frames,tonal,.8))
    val hum=ShortArray(48000) { (100*kotlin.math.sin(2*kotlin.math.PI*60*it/16000)).toInt().toShort() }
    val tone=ShortArray(48000) { (100*kotlin.math.sin(2*kotlin.math.PI*440*it/16000)).toInt().toShort() }
    check(QuietRoomSignal.lowFrequencyFraction(hum)>.7)
    check(QuietRoomSignal.lowFrequencyFraction(tone)<.1)
    val rows=List(3) { DoubleArray(2052).joinToString(",") }
    val head=SpeechMaskingHead.decode("HSGMASK1\n2052\n0,0,0\n"+rows.joinToString("\n")+"\n")
    val pool=DoubleArray(2048).apply { this[0]=1.0 }
    val raw=head.classify(hum,pool,frames)
    check(raw.severity==MaskingSeverity.UNKNOWN && raw.quietCandidate) // No direct program decision before corroboration.
    check(!head.classify(tone,pool,frames).quietCandidate)
    check(!head.classify(ShortArray(48000),pool,frames).quietCandidate)
    hum.fill(0);tone.fill(0)
    val before=MaskingEvidence(reason="candidate",quietCandidate=true)
    val neural=SpeechNoiseEstimate(SeparationStatus.NO_RELIABLE_SPEECH,speechActivity=0.0,speechScore=.01,residualSpeechScore=.01)
    val quiet=QuietRoomReturn.corroborate(before,neural)
    check(quiet.severity==MaskingSeverity.QUIET && !quiet.quietCandidate)
    for(bad in listOf(neural.copy(status=SeparationStatus.INVALID),neural.copy(status=SeparationStatus.ESTIMATED),
        neural.copy(speechScore=.16),neural.copy(speechActivity=.06),neural.copy(residualSpeechScore=.16),
        neural.copy(residualSpeechScore=null),neural.copy(speechScore=Double.NaN))) {
        check(QuietRoomReturn.corroborate(before,bad).severity==MaskingSeverity.UNKNOWN)
    }
    check(QuietRoomReturn.corroborate(MaskingEvidence(),neural).severity==MaskingSeverity.UNKNOWN)
    val settings=NoiseSettings(confirmations=3,cooldownSeconds=60)
    val choices=MaskingChoices(1,3,6,5,true)
    val safety=NoiseSafety(foreground=true,unlocked=true,audioFree=true)
    var state=MaskingState(lastSuccess=0)
    check(observeMasking(state,quiet,30000,30010,settings,choices,safety,5).state.count==0)
    for(i in 0..1) {
        val at=61000L+i*13000
        val decision=observeMasking(state,quiet,at,at+10,settings,choices,safety,5)
        check(decision.proposed==if(i==1) 1 else null);state=decision.state
    }
    val completed=completeMasking(state,state.token,true,88000)
    val duplicate=observeMasking(completed,quiet,150000,150010,settings,choices,safety,1)
    check(duplicate.proposed==null)
    check(observeMasking(MaskingState(),before,1000,1010,settings,choices,safety,5).state.count==0)
    var interrupted=observeMasking(MaskingState(),quiet,1000,1010,settings,choices,safety,5).state
    val clearReturn=observeMasking(interrupted,MaskingEvidence(MaskingSeverity.CLEAR,score=.9),14000,14010,settings,choices,safety,5)
    check(clearReturn.proposed==1 && clearReturn.state.pending==1)
    interrupted=clearReturn.state
    val firstQuiet=observeMasking(interrupted,quiet,27000,27010,settings,choices,safety,5)
    check(firstQuiet.state.count==0 && firstQuiet.proposed==null)
    recordedQuietChecks()
    println("PASS: quiet room with low Silence score; per-frame activity/level/invalid/muted vetoes; neural speech and residual corroboration; cooldown and two-check Universal return, no Unknown timeout")
}

private fun recordedQuietChecks() {
    val relative="src/commonTest/fixtures/noise/quiet-return/evidence.tsv"
    val file=listOf(java.io.File("shared/$relative"),java.io.File(relative)).first { it.isFile }
    var roomTone=0;var activity=0;var context=0
    for(line in file.readLines().drop(1)) {
        val v=line.split('\t');check(v.size==11)
        val rms=v[2].toDouble();val peak=v[3].toDouble()
        val m=NoiseMeasurement(16000,48000,3,rms,rms,rms,peak,0.0,0.0,emptySet())
        val frames=v[10].split(';').map { frame ->
            val scores=frame.split(',').map { it.toDouble() }
            check(scores.size==EnvironmentClassifier.requiredLabels.size)
            EnvironmentClassifier.requiredLabels.zip(scores).toMap()
        }
        val candidate=QuietRoomReturn.candidate(m,frames,v[4].split(',').map { it.toDouble() }.toDoubleArray(),v[5].toDouble())
        val neural=SpeechNoiseEstimate(SeparationStatus.valueOf(v[6]),speechActivity=v[7].toDouble(),
            speechScore=v[8].toDouble(),residualSpeechScore=v[9].toDouble())
        val result=QuietRoomReturn.corroborate(MaskingEvidence(quietCandidate=candidate),neural)
        when(v[1]) {
            "QUIET" -> { check(result.severity==MaskingSeverity.QUIET) { "Room tone held: ${v[0]}" };roomTone++ }
            "NOT_QUIET" -> { check(result.severity!=MaskingSeverity.QUIET) { "False quiet: ${v[0]}" };activity++ }
            "CONTEXT_DEPENDENT" -> context++ // Forest/dripping water is not a room-silence reference.
            else -> error("Unknown reference")
        }
    }
    check(roomTone==4 && activity==14 && context==4)
}
