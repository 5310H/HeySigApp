package com.heysig.noise

fun main() {
    val frame=mapOf("Television" to .3,"Radio" to .2,"Speech" to .8,"Music" to .1)
    val evidence=TelevisionSignal.summarize(listOf(frame,frame,frame+("Television" to .9)))!!
    check(kotlin.math.abs(evidence.mean-.5)<1e-12 && evidence.peak==.9 && evidence.frames==3)
    check(kotlin.math.abs(evidence.speechMean-.8)<1e-12 && evidence.radioMean in .199.. .201)
    check(TelevisionSignal.summarize(List(5) { frame-"Television" })==null)
    for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,-.1,1.01))
        check(TelevisionSignal.summarize(List(5) { frame+("Television" to value) })==null)
    for(size in listOf(0,2,33)) check(TelevisionSignal.summarize(List(size) { frame })==null)
    check(TelevisionSignal.summarize(List(5) { frame+("Television" to 0.0) })!!.mean==0.0)
    println("PASS: TV scalar evidence; missing/invalid evidence is unavailable, never a TV decision")
}
