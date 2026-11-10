package com.heysig.masking

import com.heysig.noise.*
import java.io.File

fun main(args:Array<String>) {
    val bands=SeparationMaskingBands.decode(File(args[0]).readText())
    val original=File(args[2]).readLines().drop(1).map { it.split('\t') }
    val settings=NoiseSettings(programs=listOf(1,1,2,2,3,3))
    val choices=MaskingChoices(1,2,3);val safety=NoiseSafety(foreground=true,unlocked=true,audioFree=true)
    File(args[3]).bufferedWriter().use { output ->
        output.write("index\tratioSeverity\tseverity\tscore\tmaskingProposal\treturnProposal\treason\n")
        File(args[1]).useLines { lines -> lines.drop(1).forEach { row ->
            val values=row.split('\t');val index=values[0].toInt();val before=original[index]
            require(before[0].toInt()==index)
            val m=SpeechNoiseEstimate(SeparationStatus.valueOf(values[1]),values[2].toDoubleOrNull(),
                values[3].toDoubleOrNull(),values[4].toDoubleOrNull(),values[5].toDouble(),values[6].toDouble(),values[7].toDoubleOrNull())
            val e=bands.corroborate(MaskingEvidence(MaskingSeverity.valueOf(before[1]),score=before[2].toDouble(),reason="Original speech evidence holds"),m)
            fun proposed(current:Int):Int? {
                var state=MaskingState();var target:Int?=null
                for(i in 1..3) { val t=i*15000L;val d=observeMasking(state,e,t,t+10,settings,choices,safety,current);state=d.state;target=d.proposed }
                return target
            }
            output.write("$index\t${bands.severity(m)}\t${e.severity}\t${e.score}\t${proposed(1) ?: "hold"}\t${proposed(2) ?: "hold"}\t${e.reason}\n")
        } }
    }
}
