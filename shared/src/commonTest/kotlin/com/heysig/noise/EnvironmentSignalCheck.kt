package com.heysig.noise

import kotlin.math.abs

fun main() {
    val quiet=EnvironmentClassification(ListeningEnvironment.QUIET,0.9,"original")
    val speech=EnvironmentClassification(ListeningEnvironment.CONVERSATION,0.95,"verification")
    val uncertain=EnvironmentClassification(null,0.0,"uncertain")
    check(EnvironmentClassifier.confirmQuiet(quiet,speech).environment==null)
    check(EnvironmentClassifier.confirmQuiet(quiet,uncertain).environment==null)
    check(EnvironmentClassifier.confirmQuiet(quiet,null).environment==null)
    check(EnvironmentClassifier.confirmQuiet(quiet,quiet.copy(confidence=0.6)).confidence==0.6)
    check(EnvironmentClassifier.confirmQuiet(speech,quiet)==speech) // A second view cannot promote a new class.
    check(EnvironmentSignal.quietVerificationGain(ShortArray(48_000))==null)
    check(runCatching { EnvironmentSignal.quietVerificationGain(ShortArray(100)) }.isFailure)
    val low=ShortArray(48_000) { if(it%2==0) 100 else -100 }
    check(EnvironmentSignal.quietVerificationGain(low)==16.0)
    val moderate=ShortArray(48_000) { if(it%2==0) 1000 else -1000 }
    check(abs(EnvironmentSignal.quietVerificationGain(moderate)!!-1.6384)<1e-10)
    val transient=low.copyOf().also { it[0]=20_000 }
    val bounded=EnvironmentSignal.quietVerificationGain(transient)!!
    check(bounded*20_000/32768<=0.95+1e-10)
    for(value in listOf(Short.MIN_VALUE,Short.MAX_VALUE)) {
        check(EnvironmentSignal.quietVerificationGain(ShortArray(48_000) { value })==1.0)
    }
    checkQuietRecordingEvidence()
    println("PASS: Quiet disagreement/low-confidence rejection, no class promotion, zero PCM, gain/headroom bounds and full-range PCM")
}

private fun checkQuietRecordingEvidence() {
    val relative="src/commonTest/fixtures/environment/corpus"
    val directory=listOf(java.io.File("shared/$relative"),java.io.File(relative)).first { it.isDirectory }
    fun read(name: String): Map<List<String>,List<Map<String,Double>>> {
        val rows=java.io.File(directory,name).readLines().map { it.split('\t') }
        val labels=rows.first().drop(3)
        check(labels.toSet()==EnvironmentClassifier.requiredLabels && labels.distinct().size==labels.size)
        return rows.drop(1).groupBy { it.take(3) }.mapValues { (key, frames) ->
            check(key[1].toInt() in listOf(3,5,10) && key[2].toDouble() in listOf(0.25,1.0))
            check(frames.size==(key[1].toInt()*16000-15600)/7680+1)
            frames.map { row ->
                check(row.size==labels.size+3)
                labels.zip(row.drop(3).map { it.toDouble() }).toMap()
            }
        }
    }
    val original=read("quiet-original.tsv")
    val verification=read("quiet-verification.tsv")
    check(original.size==10 && original.keys==verification.keys)
    var falseQuiet=0
    var roomTone=0
    for((key,frames) in original) {
        val first=EnvironmentClassifier.classify(frames)
        val result=EnvironmentClassifier.confirmQuiet(first,EnvironmentClassifier.classify(verification.getValue(key)))
        if(key[0]=="fs-565535") {
            roomTone++
            // Existing room tone evidence is below switching confidence; never promote it.
            check(first.confidence<EnvironmentSettings().minimumConfidence)
            check(result.confidence<=first.confidence)
        } else {
            check(key[0] in listOf("fs-260124","fs-608996"))
            check(first.environment==ListeningEnvironment.QUIET && first.confidence>=EnvironmentSettings().minimumConfidence)
            check(result.environment==null)
            falseQuiet++
        }
    }
    check(falseQuiet==4 && roomTone==6)
    println("PASS: four recorded false Quiet decisions rejected; six room-tone cases never promoted. NOT six-class acceptance.")
}
