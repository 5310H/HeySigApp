package com.heysig.noise

import kotlin.math.exp
import kotlin.math.abs

/** Fixed TV/context head on the existing encoder. Scores remain estimates. */
class TelevisionHead private constructor(private val absentMaximum:Double,private val presentMinimum:Double,
    private val bias:Double,private val weights:DoubleArray) {
    fun score(features:DoubleArray?):Double? {
        if(features?.size!=2048 || features.any { !it.isFinite() || it !in 0.0..1.0 } ||
            features.sumOf { it*it } !in .99..1.01) return null
        val logit=(bias+features.indices.sumOf { weights[it]*features[it]*20 }).coerceIn(-60.0,60.0)
        return 1.0/(1.0+exp(-logit))
    }
    fun presence(features:DoubleArray?):TelevisionPresence {
        val value=score(features) ?: return TelevisionPresence.UNKNOWN
        return when { value>=presentMinimum -> TelevisionPresence.PRESENT
            value<=absentMaximum -> TelevisionPresence.ABSENT
            else -> TelevisionPresence.UNKNOWN }
    }
    companion object {
        fun decode(text:String):TelevisionHead {
            require(text.length in 30..100000)
            val rows=text.trimEnd('\n').split('\n');require(rows.size==4 && rows[0]=="HSGTV1")
            val bounds=rows[1].split(',').map { it.toDouble() }
            require(bounds.size==2 && bounds.all { it.isFinite() } && bounds[0] in 0.0.. .4 && bounds[1] in .6..1.0)
            val bias=rows[2].toDouble();require(bias.isFinite() && abs(bias)<10000)
            val weights=rows[3].split(',').map { it.toDouble().also { v -> require(v.isFinite() && abs(v)<10000) } }.toDoubleArray()
            require(weights.size==2048)
            return TelevisionHead(bounds[0],bounds[1],bias,weights)
        }
    }
}
