package com.heysig.noise

import kotlin.math.exp
import kotlin.math.sqrt

/** Both form factors must use the same encoder/head before exchanging monitoring samples. */
object EnvironmentSceneProfile {
    const val ENCODER_SHA256="b2fc8fc3ff9c960bde60b5fea71335d4cb2a35c4d1b011c3a80927fd64d8a2e4"
    const val HEAD_SHA256="0500150700fc75a8fc4b4c59f0cd284fea0b8d61d9b5fe6de074b24358233bfd"
    fun requireCompatible(profile: String?) {
        require(profile==HEAD_SHA256) { "Update phone and watch for environmental monitoring" }
    }
}

/** Pool pretrained int8 embeddings, not waveform samples. No platform or storage APIs. */
class EnvironmentEmbeddingPool {
    private var count=0
    private val mean=DoubleArray(1024)
    private val squaredDeviations=DoubleArray(1024)
    fun add(frame: ByteArray) {
        require(frame.size==1024 && count<32)
        count++
        for(i in frame.indices) {
            val value=frame[i].toInt()+128.0
            val delta=value-mean[i]
            mean[i]+=delta/count
            squaredDeviations[i]+=delta*(value-mean[i])
        }
    }
    fun finish(): DoubleArray? {
        if(count !in 3..32) return null
        val features=DoubleArray(2048) { i ->
            if(i<1024) mean[i] else sqrt((squaredDeviations[i-1024]/count).coerceAtLeast(0.0))
        }
        val norm=sqrt(features.sumOf { it*it })
        if(!norm.isFinite() || norm==0.0) return null
        for(i in features.indices) features[i]/=norm
        return features
    }
}

/** A pinned, separately trained scene head. Loading a head never qualifies automatic dispatch. */
class EnvironmentSceneHead private constructor(
    private val temperature: Double,
    private val minimumSimilarity: Double,
    private val minimumMargin: Double,
    private val biases: DoubleArray,
    private val weights: List<DoubleArray>,
    private val centroids: List<DoubleArray>
) {
    fun classify(features: DoubleArray?, events: List<Map<String,Double>>): EnvironmentClassification {
        fun unknown()=EnvironmentClassification(null,0.0,"Uncertain sound; keeping current program")
        if(features==null || features.size!=2048 || features.any { !it.isFinite() || it !in 0.0..1.0 } ||
            features.sumOf { it*it } !in 0.99..1.01) return unknown()
        if(events.size !in 3..32 || events.any { frame ->
            !frame.keys.containsAll(EnvironmentClassifier.requiredLabels) || frame.values.any { !it.isFinite() || it !in 0.0..1.0 }
        }) return unknown()
        val logits=DoubleArray(6) { c ->
            (biases[c]+features.indices.sumOf { weights[c][it]*features[it] })/temperature
        }
        val peak=logits.maxOrNull() ?: return unknown()
        val probabilities=logits.map { exp(it-peak) }
        val total=probabilities.sum()
        val ordered=probabilities.indices.sortedByDescending { probabilities[it] }
        val winner=ordered[0]
        val score=probabilities[winner]/total
        if(score<0.5 || score-probabilities[ordered[1]]/total<minimumMargin) return unknown()
        val similarity=features.indices.sumOf { features[it]*centroids[winner][it] }
        if(similarity<minimumSimilarity) return unknown()
        fun mean(vararg labels: String)=events.map { frame -> labels.maxOf { frame.getValue(it) } }.average()
        val speech=mean("Speech","Conversation","Narration, monologue")
        val babble=mean("Hubbub, speech noise, speech babble","Crowd","Chatter")
        val music=mean("Music")
        val environment=classes[winner]
        // A learned scene score cannot override contradictory speech/music evidence.
        if(music>=0.5) return unknown()
        if(environment in listOf(ListeningEnvironment.QUIET,ListeningEnvironment.NON_SPEECH_NOISE)) {
            if(speech>=0.2 || babble>=0.2) return unknown()
        } else if(maxOf(speech,babble)<0.3) return unknown()
        return EnvironmentClassification(environment,score,"Experimental environment estimate")
    }

    companion object {
        private val classes=listOf(ListeningEnvironment.QUIET,ListeningEnvironment.CONVERSATION,
            ListeningEnvironment.SMALL_GROUP,ListeningEnvironment.RESTAURANT,ListeningEnvironment.LARGE_GROUP,
            ListeningEnvironment.NON_SPEECH_NOISE)
        private const val DIMENSIONS=2048
        private const val SIZE=64+6*(1+2*DIMENSIONS)*4
        fun decode(bytes: ByteArray, encoderSha256: String): EnvironmentSceneHead {
            require(bytes.size==SIZE && encoderSha256.matches(Regex("[0-9a-f]{64}")))
            require(bytes.copyOfRange(0,8).decodeToString()=="HSGENV1\n")
            val hash=bytes.copyOfRange(8,40).joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
            require(hash==encoderSha256) { "Scene head does not match the encoder" }
            var cursor=40
            fun integer(): Int {
                val value=(bytes[cursor].toInt() and 255) or ((bytes[cursor+1].toInt() and 255) shl 8) or
                    ((bytes[cursor+2].toInt() and 255) shl 16) or ((bytes[cursor+3].toInt() and 255) shl 24)
                cursor+=4
                return value
            }
            fun scalar(): Double=Float.fromBits(integer()).toDouble().also { require(it.isFinite()) }
            require(integer()==1024 && integer()==6 && integer()==1) // mean/std + L2 pooling v1
            val temperature=scalar().also { require(it in 0.05..100.0) }
            val similarity=scalar().also { require(it in 0.0..1.0) }
            val margin=scalar().also { require(it in 0.0..1.0) }
            val biases=DoubleArray(6)
            val weights=mutableListOf<DoubleArray>()
            val centroids=mutableListOf<DoubleArray>()
            for(c in 0..5) {
                biases[c]=scalar().also { require(it in -10_000.0..10_000.0) }
                weights.add(DoubleArray(DIMENSIONS) { scalar().also { require(it in -10_000.0..10_000.0) } })
                centroids.add(DoubleArray(DIMENSIONS) { scalar().also { require(it in 0.0..1.0) } }.also { center ->
                    require(center.sumOf { it*it } in 0.99..1.01)
                })
            }
            check(cursor==bytes.size)
            return EnvironmentSceneHead(temperature,similarity,margin,biases,weights,centroids)
        }
    }
}
