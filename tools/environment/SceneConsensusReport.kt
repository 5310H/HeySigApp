package com.heysig.tools

import com.heysig.noise.ListeningEnvironment
import java.io.File
import java.security.MessageDigest

/** Offline research policy. Does not participate in application dispatch. */
internal fun sceneConsensus(votes: List<Pair<ListeningEnvironment?,Double>>): Pair<ListeningEnvironment?,Double> {
    require(votes.size in 3..32 && votes.all { it.second.isFinite() && it.second in 0.0..1.0 })
    val first=votes.first().first
    if(first==null || votes.any { it.first!=first || it.second<0.65 }) return null to 0.0
    return first to votes.minOf { it.second }
}

/** Consume the actual shared-head reports, never reimplement their model/guard decisions. */
fun main(args: Array<String>) {
    require(args.size==2) { "Usage: head-reports.tsv consensus-report.tsv" }
    fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val index=File(args[0]);require(index.length() in 1..20_000)
    val entries=index.readLines().map { it.split('\t') }
    require(entries.first()==listOf("headSha256","reportSha256","file"))
    require(entries.size in 4..33 && entries.drop(1).all { it.size==3 })
    require(entries.drop(1).map { it[0] }.distinct().size==entries.size-1)
    require(entries.drop(1).map { it[2] }.distinct().size==entries.size-1)
    val header="clip\treference\tpartition\tsourceGroup\tseconds\tgain\toffsetSeconds\tdecision\tscore\toutcome"
    val reports=entries.drop(1).map { entry ->
        require(entry[0].matches(Regex("[0-9a-f]{64}")) && entry[1].matches(Regex("[0-9a-f]{64}")))
        require(entry[2].matches(Regex("model-[0-9]{3}\\.tsv")))
        val file=File(index.absoluteFile.parentFile,entry[2]);require(file.length() in 1..1_000_000)
        val bytes=file.readBytes();require(hash(bytes)==entry[1]) { "Changed component report" }
        val lines=bytes.decodeToString().trimEnd('\n').split('\n')
        require(lines.first()==header && lines.size in 2..4097)
        val rows=lines.drop(1).map { it.split('\t') }
        require(rows.all { it.size==10 })
        require(rows.map { listOf(it[0],it[4],it[5],it[6]) }.distinct().size==rows.size)
        rows.forEach { row ->
            val reference=ListeningEnvironment.valueOf(row[1])
            require(row[2] in listOf("development","validation") && row[4].toInt() in listOf(3,5,10) &&
                row[5].toDouble() in listOf(0.25,1.0) && row[6].toInt() in listOf(0,10,20))
            val prediction=if(row[7]=="UNCERTAIN") null else ListeningEnvironment.valueOf(row[7])
            val score=row[8].toDouble();require(score.isFinite() && score in 0.0..1.0)
            require(prediction==null || score>=0.65)
            require(row[9]==when { prediction==null -> "ABSTAIN";prediction==reference -> "CORRECT";else -> "WRONG" })
        }
        rows
    }
    val baseline=reports.first()
    require(reports.all { rows -> rows.size==baseline.size && rows.indices.all { rows[it].take(7)==baseline[it].take(7) } }) { "Component windows/references differ" }
    val output=mutableListOf(header)
    var accepted=0
    for(i in baseline.indices) {
        val row=baseline[i]
        val votes=reports.map { rows ->
            val result=rows[i]
            (if(result[7]=="UNCERTAIN") null else ListeningEnvironment.valueOf(result[7])) to result[8].toDouble()
        }
        val (decision,score)=sceneConsensus(votes)
        if(decision!=null) accepted++
        val outcome=when { decision==null -> "ABSTAIN";decision.name==row[1] -> "CORRECT";else -> "WRONG" }
        output.add((row.take(7)+listOf(decision?.name ?: "UNCERTAIN",score.toString(),outcome)).joinToString("\t"))
    }
    File(args[1]).apply { parentFile?.mkdirs();writeText(output.joinToString("\n")+"\n") }
    println("Unanimous ${reports.size}-head sensitivity diagnostic: $accepted / ${baseline.size} variants accepted. Overlapping training sets; NOT independent votes, calibrated confidence, or physical acceptance.")
}
