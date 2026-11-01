package com.heysig.tools

import com.heysig.noise.ListeningEnvironment

fun main() {
    for(environment in ListeningEnvironment.entries) {
        check(sceneConsensus(listOf(environment to 0.8,environment to 0.65,environment to 0.9))==(environment to 0.65))
        check(sceneConsensus(listOf(environment to 0.8,environment to 0.649999,environment to 0.9)).first==null)
        check(sceneConsensus(listOf(environment to 0.8,null to 0.9,environment to 0.9)).first==null)
        val other=ListeningEnvironment.entries.first { it!=environment }
        check(sceneConsensus(listOf(environment to 0.8,other to 0.99,environment to 0.9)).first==null)
    }
    val quiet=ListeningEnvironment.QUIET
    check(sceneConsensus(List(32) { quiet to 0.7 })==(quiet to 0.7))
    for(invalid in listOf(emptyList(),List(2) { quiet to 0.9 },List(33) { quiet to 0.9 },List(3) { quiet to Double.NaN },List(3) { quiet to 1.01 })) {
        check(runCatching { sceneConsensus(invalid) }.isFailure)
    }
    println("PASS: all six unanimous outputs, minimum score, edge threshold, disagreement/abstention and invalid/oversized votes. Research policy only.")
}
