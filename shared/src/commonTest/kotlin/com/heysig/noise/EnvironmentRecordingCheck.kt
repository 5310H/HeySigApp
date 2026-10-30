package com.heysig.noise

/** Model evidence from pinned recordings. Known classification failures remain explicit. */
fun checkEnvironmentRecordings(raw: String) {
    val rows=raw.trim().lines().map { it.split('\t') }
    val labels=rows.first().drop(3)
    check(labels.toSet()==EnvironmentClassifier.requiredLabels)
    val cases=rows.drop(1).groupBy { it.take(3) }
    check(cases.size==24)
    var eligible=0
    var restaurantErrors=0
    var uncertain=0
    for((key,frames) in cases) {
        val scores=frames.map { row ->
            check(row.size==labels.size+3)
            labels.zip(row.drop(3).map { it.toDouble() }).toMap()
        }
        val result=EnvironmentClassifier.classify(scores)
        if(result.confidence>=EnvironmentSettings().minimumConfidence) eligible++
        if(result.environment==null) uncertain++
        when(key[0]) {
            "heysig-vacuum.wav" -> check(result.environment==ListeningEnvironment.NON_SPEECH_NOISE)
            "heysig-restaurant.wav" -> {
                // Regression records a known error; it must not be represented as accepted accuracy.
                check(result.environment==ListeningEnvironment.CONVERSATION);restaurantErrors++
            }
            "heysig-babble.wav" -> check(result.environment in listOf(null,ListeningEnvironment.CONVERSATION))
            "heysig-traffic.wav" -> check(result.environment in listOf(null,ListeningEnvironment.NON_SPEECH_NOISE))
            else -> error("Unexpected recording")
        }
    }
    check(restaurantErrors==6 && uncertain>0 && eligible>0)
    println("PASS: 24 recording score regressions; $uncertain abstentions, $restaurantErrors known restaurant errors. NOT classifier qualification.")
}

fun main() {
    val relative="src/commonTest/fixtures/environment/evidence.tsv"
    val file=listOf(java.io.File("shared/$relative"),java.io.File(relative)).first { it.isFile }
    checkEnvironmentRecordings(file.readText())
}
