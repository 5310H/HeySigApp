package com.heysig.noise

import kotlin.math.abs
import kotlin.math.sqrt

fun main() {
    val abstention=EnvironmentClassification(null,0.0,"Uncertain")
    for(environment in ListeningEnvironment.entries) {
        val specialist=EnvironmentClassification(environment,0.9,"Specialist")
        val fallback=EnvironmentSceneFusion.combine(abstention,specialist)
        check(fallback.environment==if(environment==ListeningEnvironment.SMALL_GROUP) environment else null)
        if(environment==ListeningEnvironment.SMALL_GROUP) check(fallback.confidence==specialist.confidence)
        for(primaryEnvironment in ListeningEnvironment.entries) {
            for(score in listOf(0.5,0.649999,0.65,0.99)) {
                val primary=EnvironmentClassification(primaryEnvironment,score,"Primary")
                check(EnvironmentSceneFusion.combine(primary,specialist)===primary)
            }
        }
    }
    for(score in listOf(0.0,0.5,0.649999)) {
        check(EnvironmentSceneFusion.combine(abstention,EnvironmentClassification(ListeningEnvironment.SMALL_GROUP,score,"Specialist"))===abstention)
    }
    check(EnvironmentSceneFusion.combine(abstention,EnvironmentClassification(ListeningEnvironment.SMALL_GROUP,0.65,"Specialist")).confidence==0.65)
    check(EnvironmentSceneFusion.combine(abstention,EnvironmentClassification(null,0.99,"Unknown specialist"))===abstention)
    EnvironmentSceneProfile.requireCompatible(EnvironmentSceneProfile.PROFILE_ID)
    for(profile in listOf(null,"","legacy-event-mapper","00".repeat(32),EnvironmentSceneProfile.HEAD_SHA256,EnvironmentSceneProfile.SMALL_GROUP_HEAD_SHA256)) {
        check(runCatching { EnvironmentSceneProfile.requireCompatible(profile) }.isFailure)
    }
    val encoder="00".repeat(32)
    fun bytes(target: Int=0, bias: Float=4f, center: Int=0): ByteArray {
        val output=ByteArray(64+6*4097*4)
        "HSGENV1\n".encodeToByteArray().copyInto(output)
        var cursor=40
        fun integer(value: Int) { for(i in 0..3) output[cursor++]=(value ushr (i*8)).toByte() }
        fun scalar(value: Float)=integer(value.toBits())
        integer(1024);integer(6);integer(1)
        scalar(1f);scalar(0.9f);scalar(0.15f)
        for(c in 0..5) {
            scalar(if(c==target) bias else 0f)
            repeat(2048) { scalar(0f) }
            repeat(2048) { scalar(if(it==center) 1f else 0f) }
        }
        check(cursor==output.size)
        return output
    }
    fun events(speech: Double=0.0, music: Double=0.0)=List(5) {
        EnvironmentClassifier.requiredLabels.associateWith { label -> when(label) { "Speech" -> speech;"Music" -> music;else -> 0.0 } }
    }
    val pool=EnvironmentEmbeddingPool()
    repeat(2) { pool.add(ByteArray(1024) { -128 }) }
    check(pool.finish()==null)
    val samplePool=EnvironmentEmbeddingPool()
    repeat(3) { index -> samplePool.add(ByteArray(1024) { if(it==0) (-128+(index+1)*10).toByte() else -128 }) }
    val features=checkNotNull(samplePool.finish())
    val norm=sqrt(400.0+200.0/3)
    check(abs(features[0]-20/norm)<1e-12 && abs(features[1024]-sqrt(200.0/3)/norm)<1e-12)
    check(features.indices.filter { it!=0 && it!=1024 }.all { features[it]==0.0 })
    val head=EnvironmentSceneHead.decode(bytes(),encoder)
    val quiet=head.classify(features,events())
    check(quiet.environment==ListeningEnvironment.QUIET && quiet.confidence in 0.91..0.92)
    check(head.classify(features,events(speech=0.8)).environment==null)
    check(head.classify(features,events(music=0.8)).environment==null)
    check(head.classify(null,events()).environment==null)
    check(head.classify(DoubleArray(2048),events()).environment==null)
    check(head.classify(features.copyOf().also { it[0]=Double.NaN },events()).environment==null)
    check(head.classify(features,events().map { it-"Speech" }).environment==null)
    check(head.classify(features,events().map { it+("Music" to Double.NaN) }).environment==null)
    check(EnvironmentSceneHead.decode(bytes(center=2047),encoder).classify(features,events()).environment==null)
    check(EnvironmentSceneHead.decode(bytes(bias=0f),encoder).classify(features,events()).environment==null)
    for(c in 1..5) {
        val classifier=EnvironmentSceneHead.decode(bytes(target=c),encoder)
        val result=classifier.classify(features,events(speech=if(c==5) 0.0 else 0.8))
        check(result.environment==ListeningEnvironment.entries[c])
        if(c!=5) check(classifier.classify(features,events()).environment==null)
    }
    check(runCatching { EnvironmentSceneHead.decode(bytes(),"11".repeat(32)) }.isFailure)
    for(invalid in listOf(bytes().dropLast(1).toByteArray(),bytes()+0,bytes().also { it[0]=0 },bytes().also { it[41]=0 })) {
        check(runCatching { EnvironmentSceneHead.decode(invalid,encoder) }.isFailure)
    }
    val invalidFloat=bytes().also { for(i in 0..3) it[52+i]=(Float.NaN.toBits() ushr (i*8)).toByte() }
    check(runCatching { EnvironmentSceneHead.decode(invalidFloat,encoder) }.isFailure)
    val empty=EnvironmentEmbeddingPool()
    repeat(3) { empty.add(ByteArray(1024) { -128 }) };check(empty.finish()==null)
    check(runCatching { empty.add(ByteArray(100)) }.isFailure)
    val bounded=EnvironmentEmbeddingPool()
    repeat(32) { bounded.add(ByteArray(1024) { 127 }) }
    check(bounded.finish()!=null && runCatching { bounded.add(ByteArray(1024)) }.isFailure)
    check(!EnvironmentReleaseGate.qualified())
    println("PASS: embedding mean/std/L2 pooling, scene softmax, six outputs, novelty and contradictory evidence rejection, strict head decoding and bounded frames")
}
