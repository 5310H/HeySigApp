package com.heysig.noise

fun main() {
    fun window(masking:MaskingSeverity,reduction:MaskingSeverity=masking)=AcousticCheckWindow(masking,SpeechNoiseEstimate(),reduction)
    val heavy=window(MaskingSeverity.HEAVY)
    val uncertain=window(MaskingSeverity.UNKNOWN,MaskingSeverity.MODERATE)
    check(AcousticCheckResult(null,listOf(heavy,heavy,uncertain)).agreement()==MaskingSeverity.HEAVY)
    check(AcousticCheckResult(null,listOf(heavy,uncertain,uncertain)).agreement()==MaskingSeverity.UNKNOWN)
    check(AcousticCheckResult(null,listOf(heavy,uncertain,uncertain)).agreement(true)==MaskingSeverity.MODERATE)
    check(AcousticCheckResult(null,listOf(window(MaskingSeverity.CLEAR),window(MaskingSeverity.MODERATE),heavy)).agreement()==MaskingSeverity.UNKNOWN)
    check(runCatching { AcousticCheckResult(null,listOf(heavy,heavy)) }.isFailure)
    println("PASS: bounded sound-check agreement; uncertainty stays explicit and reduction disagreement remains visible")
}
