package com.heysig.tools

import com.heysig.noise.ListeningEnvironment
import java.io.File
import java.security.MessageDigest

/** Research only: preserve primary decisions; add only non-conflicting Small Group votes. */
internal fun smallGroupFallback(
    primary: Pair<ListeningEnvironment?, Double>,
    specialist: Pair<ListeningEnvironment?, Double>
): Pair<ListeningEnvironment?, Double> {
    require(listOf(primary, specialist).all { it.second.isFinite() && it.second in 0.0..1.0 })
    val first = primary.first?.takeIf { primary.second >= 0.65 }
    val small = specialist.first == ListeningEnvironment.SMALL_GROUP && specialist.second >= 0.65
    if (first != null) {
        if (small && first != ListeningEnvironment.SMALL_GROUP) return null to 0.0
        return first to primary.second
    }
    return if (small) ListeningEnvironment.SMALL_GROUP to specialist.second else null to 0.0
}

fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: primary.tsv specialist.tsv output.tsv" }
    val header = "clip\treference\tpartition\tsourceGroup\tseconds\tgain\toffsetSeconds\tdecision\tscore\toutcome"
    fun read(path: String): List<List<String>> {
        val file = File(path); require(file.length() in 1..1_000_000)
        val lines = file.readLines(); require(lines.first() == header && lines.size in 2..4097)
        val rows = lines.drop(1).map { it.split('\t') }
        require(rows.all { it.size == 10 })
        require(rows.map { listOf(it[0], it[4], it[5], it[6]) }.distinct().size == rows.size)
        for (row in rows) {
            val reference = ListeningEnvironment.valueOf(row[1])
            require(row[2] in listOf("development", "validation") && row[3].isNotBlank())
            require(row[4].toInt() in listOf(3,5,10) && row[5].toDouble() in listOf(0.25,1.0) && row[6].toInt() in listOf(0,10,20))
            val decision = if (row[7] == "UNCERTAIN") null else ListeningEnvironment.valueOf(row[7])
            val score = row[8].toDouble(); require(score.isFinite() && score in 0.0..1.0)
            require(decision == null || score >= 0.65)
            require(row[9] == when { decision == null -> "ABSTAIN"; decision == reference -> "CORRECT"; else -> "WRONG" })
        }
        return rows
    }
    val primary = read(args[0]); val specialist = read(args[1])
    require(primary.size == specialist.size && primary.indices.all { primary[it].take(7) == specialist[it].take(7) }) { "Component windows/references differ" }
    val output = mutableListOf(header)
    fun vote(row: List<String>) = (if (row[7] == "UNCERTAIN") null else ListeningEnvironment.valueOf(row[7])) to row[8].toDouble()
    for (i in primary.indices) {
        val row = primary[i]
        val (decision, score) = smallGroupFallback(vote(row), vote(specialist[i]))
        val outcome = when { decision == null -> "ABSTAIN"; decision.name == row[1] -> "CORRECT"; else -> "WRONG" }
        output.add((row.take(7) + listOf(decision?.name ?: "UNCERTAIN", score.toString(), outcome)).joinToString("\t"))
    }
    File(args[2]).writeText(output.joinToString("\n") + "\n")
    for (path in args.take(2)) {
        val sha = MessageDigest.getInstance("SHA-256").digest(File(path).readBytes()).joinToString("") { "%02x".format(it) }
        println("Input SHA-256 $sha")
    }
    println("Research fallback only, fixed .65 threshold. Viewed references; not runtime qualification or calibrated confidence.")
}
