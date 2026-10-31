package com.heysig.tools

import com.heysig.noise.*
import java.io.File

/** Evaluate the actual shared mapper; never duplicate its rules in Python. */
fun main(args: Array<String>) {
    require(args.size in 3..4) { "Usage: evidence.tsv references.tsv report.tsv [verification.tsv]" }
    val references=File(args[1]).readLines().drop(1).map { it.split('\t') }
    require(references.isNotEmpty() && references.all { it.size==7 })
    require(references.all { it.all(String::isNotBlank) && it[2] in listOf("development","validation") &&
        it[5].matches(Regex("[0-9a-f]{64}")) })
    references.forEach { ListeningEnvironment.valueOf(it[1]) }
    require(references.map { it[0] }.distinct().size==references.size)
    require(references.map { it[5] }.distinct().size==references.size) { "Duplicate recording bytes" }
    for(column in listOf(3,4,5)) require(references.groupBy { it[column] }.values.all { rows -> rows.map { it[2] }.distinct().size==1 }) {
        "Source/creator/hash crosses development and validation"
    }
    val byId=references.associateBy { it[0] }
    val rows=File(args[0]).readLines().map { it.split('\t') }
    require(rows.first().take(3)==listOf("clip","seconds","gain"))
    val labels=rows.first().drop(3)
    require(labels.distinct().size==labels.size && labels.containsAll(EnvironmentClassifier.requiredLabels))
    val cases=rows.drop(1).groupBy { it.take(3) }
    val verification=if(args.size==4) File(args[3]).readLines().map { it.split('\t') }.let {
        require(it.first()==rows.first())
        it.drop(1).groupBy { row -> row.take(3) }.also { other -> require(other.keys==cases.keys) }
    } else null
    val output=mutableListOf("clip\treference\tpartition\tsourceGroup\tseconds\tgain\tprediction\tscore\tdecision\toutcome")
    var correct=0;var wrong=0;var abstain=0
    for((key,frames) in cases) {
        require(key.size==3)
        val reference=checkNotNull(byId[key[0]]) { "Unreferenced recording" }
        val expected=ListeningEnvironment.valueOf(reference[1])
        val seconds=key[1].toInt()
        require(seconds in listOf(3,5,10) && key[2].toDouble() in listOf(0.25,1.0))
        require(frames.size==(seconds*16000-15600)/7680+1) { "Missing/extra inference frame" }
        fun parse(frames: List<List<String>>)=frames.map { row ->
            require(row.size==labels.size+3)
            val scores=row.drop(3).map { it.toDouble() }
            require(scores.all { it.isFinite() && it in 0.0..1.0 })
            labels.zip(scores).toMap()
        }
        val original=EnvironmentClassifier.classify(parse(frames))
        val prediction=if(verification!=null) {
            val checkFrames=verification.getValue(key)
            require(checkFrames.size==frames.size)
            EnvironmentClassifier.confirmQuiet(original,EnvironmentClassifier.classify(parse(checkFrames)))
        } else original
        val decision=prediction.environment?.takeIf { prediction.confidence>=EnvironmentSettings().minimumConfidence }
        val outcome=when { decision==null -> { abstain++;"ABSTAIN" };decision==expected -> { correct++;"CORRECT" };else -> { wrong++;"WRONG" } }
        output.add(listOf(key[0],expected.name,reference[2],reference[3],key[1],key[2],prediction.environment?.name ?: "UNCERTAIN",
            prediction.confidence.toString(),decision?.name ?: "UNCERTAIN",outcome).joinToString("\t"))
    }
    File(args[2]).apply { parentFile?.mkdirs();writeText(output.joinToString("\n")+"\n") }
    println("${cases.size} duration/gain cases; $correct correct, $wrong wrong, $abstain abstentions at the switching threshold")
    for(partition in listOf("development","validation")) {
        val clips=references.filter { it[2]==partition }
        println("$partition: ${clips.size} recordings / ${clips.map { it[3] }.distinct().size} source groups; classes ${clips.map { it[1] }.distinct().sorted()}")
    }
    println("Variants are NOT independent recordings. Creator descriptions are provisional. This report does not enable automatic dispatch.")
}
