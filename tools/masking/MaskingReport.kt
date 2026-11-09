package com.heysig.masking

import com.heysig.noise.*
import java.io.File

fun main(args:Array<String>) {
    val head=SpeechMaskingHead.decode(File(args[0]).readText())
    val labels=EnvironmentClassifier.requiredLabels.toList()
    val settings=NoiseSettings(programs=listOf(1,1,2,2,3,3))
    val safety=NoiseSafety(foreground=true,unlocked=true,audioFree=true)
    val choices=MaskingChoices(1,2,3)
    File(args[2]).bufferedWriter().use { out ->
        out.write("index\tseverity\tscore\tmaskingProposal\tlevelProposal\treturnProposal\treason\n")
        File(args[1]).useLines { lines -> lines.forEach { line ->
            val fields=line.split('\t');val values=fields[1].split(',').map { it.toDouble() }
            require(values.size==2052+labels.size+2)
            val features=values.take(2048).map { it/20 }.toDoubleArray()
            val acoustic=values.subList(2048,2052).toDoubleArray()
            val frame=labels.indices.associate { labels[it] to values[2052+it] }
            val rms=values[values.size-2];val peak=values.last()
            val measurement=NoiseMeasurement(16000,48000,3,rms,rms,rms,peak,0.0,0.0,emptySet())
            val evidence=head.classifyFeatures(features,acoustic,List(5) { frame },measurement)
            var state=MaskingState();var returning=MaskingState();var levelState=NoiseAutomaticState()
            var proposal:Int?=null;var returnProposal:Int?=null;var level:Int?=null
            for(i in 1..3) {
                val t=i*15000L
                val d=observeMasking(state,evidence,t,t+10,settings,choices,safety,1);state=d.state;proposal=d.proposed
                val r=observeMasking(returning,evidence,t,t+10,settings,choices,safety,2);returning=r.state;returnProposal=r.proposed
                val l=automaticNoise(levelState,rms,t,settings,safety,1);levelState=l.state;level=l.target
            }
            out.write("${fields[0]}\t${evidence.severity}\t${evidence.score}\t${proposal ?: "hold"}\t${level ?: "hold"}\t${returnProposal ?: "hold"}\t${evidence.reason}\n")
        } }
    }
}
