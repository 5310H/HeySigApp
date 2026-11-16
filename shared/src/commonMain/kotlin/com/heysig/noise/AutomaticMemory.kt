package com.heysig.noise

import kotlin.math.abs
import kotlin.math.sqrt

/** Compact embedding projection plus eight scalar descriptors; never audio or words. */
data class AutomaticSoundContext(val values:List<Double>) {
    init {
        require(values.size==40 && values.all { it.isFinite() && it in -1.0..1.0 })
        require(values.take(32).sumOf { it*it } in .99..1.01 && values.drop(32).all { it in 0.0..1.0 })
    }
    fun similarity(other:AutomaticSoundContext)=values.take(32).indices.sumOf { values[it]*other.values[it] }
    fun closeTo(other:AutomaticSoundContext)=similarity(other)>=.995 &&
        (32..39).all { abs(values[it]-other.values[it])<=.15 } &&
        (32..39).sumOf { abs(values[it]-other.values[it]) }/8<=.06
    companion object {
        fun create(embedding:DoubleArray?,scalars:List<Double>):AutomaticSoundContext? {
            if(embedding?.size!=2048 || embedding.any { !it.isFinite() || it !in 0.0..1.0 } ||
                embedding.sumOf { it*it } !in .99..1.01 || scalars.size!=8 ||
                scalars.any { !it.isFinite() || it !in 0.0..1.0 }) return null
            val projected=DoubleArray(32) { j -> embedding.indices.sumOf { i ->
                var hash=i xor (j*0x9e3779b9.toInt())
                hash=(hash xor (hash ushr 16))*0x45d9f3b
                hash=(hash xor (hash ushr 16))*0x45d9f3b
                embedding[i]*if((hash xor (hash ushr 16)) and 1==0) 1.0 else -1.0
            } }
            val norm=sqrt(projected.sumOf { it*it })
            if(norm<=1e-12 || !norm.isFinite()) return null
            return AutomaticSoundContext(projected.map { (it/norm).coerceIn(-1.0,1.0) }+scalars)
        }
    }
}

data class AutomaticExperience(val scope:String,val context:AutomaticSoundContext,val scene:AutomaticScene,val savedAt:Long) {
    init { require(scope.isNotBlank() && scope.length<=2048 && scene!=AutomaticScene.UNKNOWN && savedAt>=0) }
}

/** User-reviewed corrections only. Caller owns explicit Save, storage and opt-in. */
data class AutomaticMemory(val experiences:List<AutomaticExperience> = emptyList()) {
    init { require(experiences.size<=24) }
    fun remember(value:AutomaticExperience):AutomaticMemory =
        AutomaticMemory((experiences.filterNot { it.scope==value.scope && it.context.closeTo(value.context) }+value).takeLast(24))
    fun recall(scope:String,context:AutomaticSoundContext):AutomaticScene? {
        val matches=experiences.filter { it.scope==scope && it.context.closeTo(context) }
            .sortedByDescending { it.context.similarity(context) }
        val best=matches.firstOrNull() ?: return null
        if(matches.any { it.scene!=best.scene && best.context.similarity(context)-it.context.similarity(context)<.002 }) return null
        return best.scene
    }
}
