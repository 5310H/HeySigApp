package com.heysig.tools

import com.heysig.noise.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Run the exact app decision code on frozen development evidence, without any device APIs. */
fun main(args: Array<String>) {
    require(args.size in 6..8) { "Usage: encoder.tflite head.bin corpus.json evidence.bin references.tsv report.tsv [development-source-group | --small-group-head specialist.bin]" }
    fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    fun hex(bytes: ByteArray)=bytes.joinToString("") { "%02x".format(it) }
    val heldGroup=args.getOrNull(6).takeIf { args.size==7 }
    require(heldGroup==null || (heldGroup.isNotBlank() && heldGroup.length<=200 && heldGroup.none { it in "\t\r\n" }))
    val encoderHash=hash(File(args[0]).readBytes())
    val headBytes=File(args[1]).readBytes()
    val head=EnvironmentSceneHead.decode(headBytes,hex(encoderHash))
    val specialist=if(args.size==8) {
        require(args[6]=="--small-group-head")
        EnvironmentSceneHead.decode(File(args[7]).readBytes(),hex(encoderHash))
    } else null
    val corpusHash=hash(File(args[2]).readBytes())
    val evidenceFile=File(args[3]);require(evidenceFile.length() in 76..64_000_000)
    val evidence=ByteBuffer.wrap(evidenceFile.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    fun bytes(size: Int)=ByteArray(size).also { evidence.get(it) }
    require(bytes(8).decodeToString()=="HSGFEA1\n")
    require(bytes(32).contentEquals(encoderHash) && bytes(32).contentEquals(corpusHash))
    val count=evidence.int;require(count in 1..4096)
    val lines=File(args[4]).readLines().map { it.split('\t') }
    require(lines.first()==listOf("index","clip","reference","partition","sourceGroup","seconds","gain","offsetSeconds"))
    val references=lines.drop(1)
    require(references.size==count && references.all { it.size==8 && it.all(String::isNotBlank) })
    require(references.map { listOf(it[1],it[5],it[6],it[7]) }.distinct().size==count)
    require(references.all { it[3] in listOf("development","validation") })
    require(references.groupBy { it[4] }.values.all { group -> group.map { it[3] }.distinct().size==1 })
    val results=mutableListOf<List<String>>()
    val output=mutableListOf("clip\treference\tpartition\tsourceGroup\tseconds\tgain\toffsetSeconds\tdecision\tscore\toutcome")
    for((i,reference) in references.withIndex()) {
        require(reference[0].toInt()==i && reference[5].toInt() in listOf(3,5,10) && reference[6].toDouble() in listOf(0.25,1.0))
        val features=DoubleArray(2048) { evidence.double }
        require(features.all { it.isFinite() && it in 0.0..1.0 } && features.sumOf { it*it } in 0.99..1.01)
        val frames=evidence.int
        require(frames==(reference[5].toInt()*16000-15600)/7680+1)
        val events=List(frames) { EnvironmentClassifier.requiredLabels.associateWith {
            evidence.double.also { score -> require(score.isFinite() && score in 0.0..1.0) }
        } }
        if(heldGroup!=null) {
            if(reference[4]!=heldGroup) continue
            require(reference[3]=="development") { "Fold reports must use development groups only" }
        }
        val primary=head.classify(features,events)
        val prediction=if(specialist==null) primary else EnvironmentSceneFusion.combine(primary,specialist.classify(features,events))
        val decision=prediction.environment?.takeIf { prediction.confidence>=EnvironmentSettings().minimumConfidence }
        val expected=ListeningEnvironment.valueOf(reference[2])
        val outcome=when { decision==null -> "ABSTAIN";decision==expected -> "CORRECT";else -> "WRONG" }
        val row=listOf(reference[1],reference[2],reference[3],reference[4],reference[5],reference[6],reference[7],
            decision?.name ?: "UNCERTAIN",prediction.confidence.toString(),outcome)
        results.add(row);output.add(row.joinToString("\t"))
    }
    require(!evidence.hasRemaining() && results.isNotEmpty())
    File(args[5]).apply { parentFile?.mkdirs();writeText(output.joinToString("\n")+"\n") }
    for(partition in listOf("development","validation")) {
        val rows=results.filter { it[2]==partition }
        println("$partition: ${rows.size} variants from ${rows.map { it[0] }.distinct().size} recordings / ${rows.map { it[3] }.distinct().size} source groups")
        for(environment in ListeningEnvironment.entries) {
            val cases=rows.filter { it[1]==environment.name }
            println("${environment.name}: ${cases.count { it[9]=="CORRECT" }} correct, ${cases.count { it[9]=="WRONG" }} wrong, ${cases.count { it[9]=="ABSTAIN" }} abstentions")
        }
    }
    println("Head ${hex(hash(headBytes))}; provisional references, correlated variants, NOT classifier/physical acceptance.")
    if(specialist!=null) println("Specialist ${hex(hash(File(args[7]).readBytes()))}; policy ${EnvironmentSceneProfile.POLICY}")
}
